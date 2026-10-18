// Package openflux provides a gomobile-compatible OpenFlux client API.
//
// It is a thin facade over the upstream OpenFlux mobile bridge
// (openflux-mobile, the package the official OpenFlux Android app binds):
// the tunnel runs the same code path as the upstream clients — Session
// profiles with a classic fallback, codec negotiation, captcha handling and
// cookie persistence — while this package keeps the openflux.* Java API the
// Veil app was built against.
//
// The tunnel is exposed as a loopback SOCKS5 listener (Start/WaitReady/Stop),
// which the host app consumes as a plain SOCKS outbound.
package openflux

import (
	"errors"
	"fmt"
	"net"
	"strconv"
	"strings"
	"sync"
	"time"

	ofmobile "openflux-mobile"
)

// Sentinel errors returned across the gomobile boundary.
var (
	ErrAlreadyRunning = errors.New("openflux client is already running")
	ErrNotRunning     = errors.New("openflux client has not been started")
	ErrReadyTimeout   = errors.New("openflux transport readiness timed out")
	ErrInvalidConfig  = errors.New("invalid openflux configuration")
	ErrUnsupported    = errors.New("unsupported openflux transport")
	ErrAddressInUse   = errors.New("openflux SOCKS address is unavailable")
	ErrStartFailed    = errors.New("openflux transport failed to start")
	ErrBridgeBusy     = errors.New("another openflux tunnel is already running")
)

const (
	defaultTransportType = "yandex"
	defaultCodec         = "batched"
	defaultSOCKSHost     = "127.0.0.1"
	defaultSOCKSPort     = 1080
	readyPollInterval    = 100 * time.Millisecond
)

// proxyState tracks the one tunnel the upstream mobile bridge can run at a
// time: StartProxy is process-global there, so every Client shares it.
var proxyState struct {
	mu       sync.Mutex
	owner    *Client
	addr     string
	ownerKey string
}

// Client owns the OpenFlux client configuration. Configure it with the Set*
// methods, then call Start.
type Client struct {
	mu sync.Mutex

	transportType string
	docURL        string
	maxToken      string
	maxUID        int64
	encryptionKey string
	codec         string
	socksHost     string
	socksPort     int
	debug         bool
	cookieStore   string

	running bool
	addr    string
}

// New returns an idle client with documented defaults.
func New() *Client {
	return &Client{
		transportType: defaultTransportType,
		codec:         defaultCodec,
		socksHost:     defaultSOCKSHost,
		socksPort:     defaultSOCKSPort,
	}
}

// SetTransport selects yandex, vyandex, boards, mailru, avito, oneme or cupsonline.
func (c *Client) SetTransport(transportType string) error {
	if !supportedTransport(transportType) {
		return fmt.Errorf("%w: %q", ErrUnsupported, transportType)
	}
	c.mu.Lock()
	c.transportType = transportType
	c.mu.Unlock()
	return nil
}

// SetURL sets the transport payload: the Yandex/Vyandex/Boards/Mail.ru/Avito
// document URL or the Cups.online base64 room list; ignored for oneme.
func (c *Client) SetURL(url string) {
	c.mu.Lock()
	c.docURL = strings.TrimSpace(url)
	c.mu.Unlock()
}

// SetMaxCredentials sets the MAX (oneme) token and the peer user id to dial.
func (c *Client) SetMaxCredentials(token string, uid int64) {
	c.mu.Lock()
	c.maxToken = strings.TrimSpace(token)
	c.maxUID = uid
	c.mu.Unlock()
}

// SetEncryptionKey enables the profile's shared secret. An empty secret keeps
// the classic no-key profile, which cannot talk to a keyed exit node.
func (c *Client) SetEncryptionKey(secret string) {
	c.mu.Lock()
	c.encryptionKey = strings.TrimSpace(secret)
	c.mu.Unlock()
}

// SetCodec picks the preferred classic framing: "batched" (default, zstd +
// coalescing) or "legacy" (per-packet LZ4). Both framings are accepted on
// receive and the peer's framing wins once it speaks.
func (c *Client) SetCodec(codec string) error {
	codec = strings.TrimSpace(codec)
	if codec == "" {
		codec = defaultCodec
	}
	if codec != "batched" && codec != "legacy" {
		return fmt.Errorf("%w: codec %q", ErrInvalidConfig, codec)
	}
	c.mu.Lock()
	c.codec = codec
	c.mu.Unlock()
	return nil
}

// SetSocks sets the local SOCKS5 bind address.
func (c *Client) SetSocks(host string, port int) error {
	host = strings.TrimSpace(host)
	if host == "" || port < 1 || port > 65535 {
		return fmt.Errorf("%w: invalid SOCKS address", ErrInvalidConfig)
	}
	c.mu.Lock()
	c.socksHost = host
	c.socksPort = port
	c.mu.Unlock()
	return nil
}

// SetDebug toggles verbose logging: on enables operational logs and hexdumps,
// off keeps operational logs (the level the log screen reads).
func (c *Client) SetDebug(on bool) {
	c.mu.Lock()
	c.debug = on
	c.mu.Unlock()
}

// SetCookieStore enables persisting solved-captcha cookies across restarts.
// The path is process-wide (the upstream bridge keeps one jar per transport);
// an empty path leaves persistence off.
func (c *Client) SetCookieStore(path string) error {
	path = strings.TrimSpace(path)
	if path == "" {
		return nil
	}
	if msg := ofmobile.SetCookieStorePath(path); msg != "" {
		return fmt.Errorf("%w: %s", ErrInvalidConfig, msg)
	}
	c.mu.Lock()
	c.cookieStore = path
	c.mu.Unlock()
	return nil
}

// Start builds the transport and begins serving SOCKS5. It returns nil if the
// client is already running.
func (c *Client) Start() error {
	c.mu.Lock()
	if c.running {
		c.mu.Unlock()
		return nil
	}
	transportType := c.transportType
	docURL := c.docURL
	maxToken := c.maxToken
	maxUID := c.maxUID
	secret := c.encryptionKey
	codec := c.codec
	cookieStore := c.cookieStore
	debug := c.debug
	addr := net.JoinHostPort(c.socksHost, strconv.Itoa(c.socksPort))
	key := c.profileKeyLocked()
	c.mu.Unlock()

	if usesURL(transportType) && docURL == "" {
		return fmt.Errorf("%w: document URL is empty", ErrInvalidConfig)
	}
	if transportType == "oneme" && maxToken == "" {
		return fmt.Errorf("%w: MAX token is empty", ErrInvalidConfig)
	}

	// Bind the SOCKS port up front so "address already in use" surfaces to the
	// caller instead of failing later inside the bridge.
	probe, err := net.Listen("tcp", addr)
	if err != nil {
		return fmt.Errorf("%w: %s: %v", ErrAddressInUse, addr, err)
	}
	_ = probe.Close()

	// Reserve the process-global tunnel; the upstream bridge runs one at a
	// time and StartProxy would silently no-op on a second start.
	proxyState.mu.Lock()
	if proxyState.owner != nil {
		proxyState.mu.Unlock()
		return ErrBridgeBusy
	}
	proxyState.owner = c
	proxyState.addr = addr
	proxyState.ownerKey = key
	proxyState.mu.Unlock()

	release := func() {
		proxyState.mu.Lock()
		if proxyState.owner == c {
			proxyState.owner = nil
			proxyState.addr = ""
			proxyState.ownerKey = ""
		}
		proxyState.mu.Unlock()
	}

	if cookieStore != "" {
		if msg := ofmobile.SetCookieStorePath(cookieStore); msg != "" {
			release()
			return fmt.Errorf("%w: %s", ErrInvalidConfig, msg)
		}
	}
	ofmobile.SetDebugLevel(debugLevel(debug))

	msg := ofmobile.StartProxy(
		transportType, docURL, secret, codec,
		maxToken, strconv.FormatInt(maxUID, 10),
		addr, "", "", "",
	)
	if msg != "" {
		release()
		return fmt.Errorf("%w: %s", ErrStartFailed, msg)
	}

	c.mu.Lock()
	c.running = true
	c.addr = addr
	c.mu.Unlock()
	return nil
}

// WaitReady blocks until the transport reports a live connection or the
// timeout elapses. A value <= 0 uses a 15s default.
func (c *Client) WaitReady(timeoutMillis int) error {
	c.mu.Lock()
	running := c.running
	c.mu.Unlock()
	if !running || !ofmobile.ProxyIsRunning() {
		return ErrNotRunning
	}
	return waitConnected(timeoutMillis)
}

// Stop closes the SOCKS listener and the transport.
func (c *Client) Stop() error {
	c.mu.Lock()
	wasRunning := c.running
	c.running = false
	c.addr = ""
	c.mu.Unlock()

	proxyState.mu.Lock()
	owns := proxyState.owner == c
	if owns {
		proxyState.owner = nil
		proxyState.addr = ""
		proxyState.ownerKey = ""
	}
	proxyState.mu.Unlock()

	if owns || wasRunning {
		ofmobile.StopProxy()
	}
	return nil
}

// IsRunning reports whether this client has been started.
func (c *Client) IsRunning() bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.running
}

// IsConnected reports whether the tunnel has a live connection.
func (c *Client) IsConnected() bool {
	return ofmobile.ProxyIsRunning() && ofmobile.ProxyIsConnected()
}

// StatsJSON returns a small JSON blob with the tunnel's counters.
func (c *Client) StatsJSON() string {
	return fmt.Sprintf(
		`{"running":%t,"connected":%t,"bytesSent":%d,"bytesReceived":%d,"transport":%q}`,
		ofmobile.ProxyIsRunning(), ofmobile.ProxyIsConnected(),
		ofmobile.ProxyBytesSent(), ofmobile.ProxyBytesReceived(),
		ofmobile.CurrentTransport(),
	)
}

// profileKey identifies the configuration a running tunnel was started with,
// so a probe can tell whether it may reuse the process-global tunnel.
func (c *Client) profileKey() string {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.profileKeyLocked()
}

// profileKeyLocked is profileKey for callers that already hold c.mu.
func (c *Client) profileKeyLocked() string {
	return strings.Join([]string{
		c.transportType, c.docURL, c.encryptionKey, c.maxToken,
		strconv.FormatInt(c.maxUID, 10), c.codec,
	}, "\x1f")
}

// runningProxy reports the process-global tunnel, when one is up.
func runningProxy() (addr string, ownerKey string, ok bool) {
	if !ofmobile.ProxyIsRunning() {
		return "", "", false
	}
	proxyState.mu.Lock()
	defer proxyState.mu.Unlock()
	return proxyState.addr, proxyState.ownerKey, true
}

// waitConnected polls the bridge until the tunnel is connected.
func waitConnected(timeoutMillis int) error {
	if timeoutMillis <= 0 {
		timeoutMillis = 15000
	}
	deadline := time.Now().Add(time.Duration(timeoutMillis) * time.Millisecond)
	for {
		if !ofmobile.ProxyIsRunning() {
			return ErrNotRunning
		}
		if ofmobile.ProxyIsConnected() {
			return nil
		}
		if time.Now().After(deadline) {
			return ErrReadyTimeout
		}
		time.Sleep(readyPollInterval)
	}
}

// debugLevel maps the app's on/off switch to the core's levels: on adds
// hexdumps, off keeps operational logs (no per-packet logging).
func debugLevel(verbose bool) int {
	if verbose {
		return 3
	}
	return 2
}

// usesURL reports whether the transport is configured with a URL; MAX
// (oneme) is configured with a token/uid instead.
func usesURL(transportType string) bool {
	return transportType != "oneme"
}

func supportedTransport(transportType string) bool {
	switch transportType {
	case "yandex", "vyandex", "boards", "mailru", "avito", "oneme", "cupsonline":
		return true
	default:
		return false
	}
}
