// Package openflux provides a gomobile-compatible OpenFlux client API.
//
// It wraps the SOCKS5 client tunnel (OpenFlux client mode) so an Android/iOS
// host app can run the tunnel in-process and route traffic through the local
// SOCKS5 listener it exposes. The API deliberately mirrors the olcRTC mobile
// runtime: configure with Set*, then Start/WaitReady/Stop.
package openflux

import (
	"errors"
	"fmt"
	"net"
	"strconv"
	"strings"
	"sync"
	"time"

	"universal-bypass-tool/socks5"
	"universal-bypass-tool/transport"
	"universal-bypass-tool/transport/cupsonline"
	"universal-bypass-tool/transport/oneme"
	"universal-bypass-tool/transport/yandex"
	"universal-bypass-tool/tunnel"
	"universal-bypass-tool/utils"
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
)

const (
	defaultTransportType = "yandex"
	defaultSOCKSHost     = "127.0.0.1"
	defaultSOCKSPort     = 1080
	readyPollInterval    = 100 * time.Millisecond
)

// Client owns a single OpenFlux client lifecycle. Configure it with the Set*
// methods, then call Start.
type Client struct {
	mu sync.Mutex

	transportType string
	docURL        string
	maxToken      string
	maxUID        int64
	encryptionKey string
	socksHost     string
	socksPort     int
	debug         bool

	running bool
	socks   *socks5.SOCKS5Server
	trans   transport.Transport
}

// New returns an idle client with documented defaults.
func New() *Client {
	utils.SetOutput(logBuffer)
	return &Client{
		transportType: defaultTransportType,
		socksHost:     defaultSOCKSHost,
		socksPort:     defaultSOCKSPort,
	}
}

// SetTransport selects yandex, vyandex, oneme or cupsonline.
func (c *Client) SetTransport(transportType string) error {
	if !supportedTransport(transportType) {
		return fmt.Errorf("%w: %q", ErrUnsupported, transportType)
	}
	c.mu.Lock()
	c.transportType = transportType
	c.mu.Unlock()
	return nil
}

// SetURL sets the transport payload: the Yandex/Vyandex document URL, the
// Cups.online base64 room list, or (for oneme) is ignored.
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

// SetEncryptionKey enables AES-256-GCM with the given shared secret. An empty
// secret disables encryption (the transport's provider sees plaintext).
func (c *Client) SetEncryptionKey(secret string) {
	c.mu.Lock()
	c.encryptionKey = strings.TrimSpace(secret)
	c.mu.Unlock()
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

// SetDebug toggles verbose per-packet logging (off by default; it is costly).
func (c *Client) SetDebug(on bool) {
	c.mu.Lock()
	c.debug = on
	c.mu.Unlock()
	utils.SetDebug(on)
}

// Start builds the transport and begins serving SOCKS5. It returns nil if the
// client is already running.
func (c *Client) Start() error {
	c.mu.Lock()
	defer c.mu.Unlock()

	if c.running {
		return nil
	}

	addr := net.JoinHostPort(c.socksHost, strconv.Itoa(c.socksPort))

	// Bind the SOCKS port up front so "address already in use" surfaces to the
	// caller instead of failing later in a background goroutine.
	probe, err := net.Listen("tcp", addr)
	if err != nil {
		return fmt.Errorf("%w: %s: %v", ErrAddressInUse, addr, err)
	}
	_ = probe.Close()

	trans, err := c.buildTransport()
	if err != nil {
		return err
	}
	if err := trans.Start(); err != nil {
		return fmt.Errorf("%w: %v", ErrStartFailed, err)
	}

	// Transports (notably yandex) connect asynchronously. Capture the
	// connection-phase logs, then drop back to quiet once a link is up so the
	// per-packet logging does not tax the tunnel.
	if !c.debug {
		utils.SetDebug(true)
	}
	utils.SafeGo("openflux.debug-autoroff", func() {
		deadline := time.Now().Add(30 * time.Second)
		for time.Now().Before(deadline) {
			if trans.IsConnected() {
				break
			}
			time.Sleep(200 * time.Millisecond)
		}
		c.mu.Lock()
		userDebug := c.debug
		c.mu.Unlock()
		if !userDebug {
			utils.SetDebug(false)
		}
	})

	tun := tunnel.NewTCPTunnel(trans, false)
	srv := socks5.NewSOCKS5Server(addr, tun)
	if err := srv.Bind(); err != nil {
		_ = trans.Stop()
		return fmt.Errorf("%w: %s: %v", ErrAddressInUse, addr, err)
	}

	c.trans = trans
	c.socks = srv
	c.running = true

	utils.SafeGo("openflux.socks", func() {
		if err := srv.Start(); err != nil {
			utils.Debugf("[OPENFLUX] SOCKS5 server stopped: %v", err)
		}
	})
	return nil
}

// WaitReady blocks until the transport reports a live connection or the
// timeout elapses. A value <= 0 uses a 15s default.
func (c *Client) WaitReady(timeoutMillis int) error {
	c.mu.Lock()
	trans := c.trans
	c.mu.Unlock()
	if trans == nil {
		return ErrNotRunning
	}
	if timeoutMillis <= 0 {
		timeoutMillis = 15000
	}
	deadline := time.Now().Add(time.Duration(timeoutMillis) * time.Millisecond)
	for {
		if trans.IsConnected() {
			return nil
		}
		if time.Now().After(deadline) {
			return ErrReadyTimeout
		}
		time.Sleep(readyPollInterval)
	}
}

// Stop closes the SOCKS listener and the transport.
func (c *Client) Stop() error {
	c.mu.Lock()
	defer c.mu.Unlock()

	if !c.running {
		return nil
	}
	if c.socks != nil {
		_ = c.socks.Close()
	}
	if c.trans != nil {
		_ = c.trans.Stop()
	}
	c.socks = nil
	c.trans = nil
	c.running = false
	return nil
}

// IsRunning reports whether the client has been started.
func (c *Client) IsRunning() bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.running
}

// IsConnected reports whether the transport has a live connection.
func (c *Client) IsConnected() bool {
	c.mu.Lock()
	trans := c.trans
	c.mu.Unlock()
	return trans != nil && trans.IsConnected()
}

// StatsJSON returns a small JSON blob with transport stats.
func (c *Client) StatsJSON() string {
	c.mu.Lock()
	trans := c.trans
	running := c.running
	c.mu.Unlock()
	if trans == nil {
		return fmt.Sprintf(`{"running":%t,"connected":false}`, running)
	}
	s := trans.Stats()
	return fmt.Sprintf(
		`{"running":%t,"connected":%t,"bytesSent":%d,"bytesReceived":%d,"packetsSent":%d,"packetsRecv":%d,"reconnects":%d,"uptimeSec":%d}`,
		running, s.Connected, s.BytesSent, s.BytesReceived, s.PacketsSent, s.PacketsRecv, s.Reconnects,
		int64(s.Uptime/time.Second),
	)
}

func (c *Client) buildTransport() (transport.Transport, error) {
	cfg := transport.DefaultConfig()
	var inner transport.Transport
	switch c.transportType {
	case "yandex", "":
		inner = yandex.NewYandexDocsTransport(c.docURL, cfg)
	case "vyandex":
		inner = yandex.NewYandexVolgaTransport(c.docURL, cfg)
	case "oneme":
		inner = oneme.NewOneMeTransport(false, c.maxToken, c.maxUID, cfg)
	case "cupsonline":
		inner = cupsonline.NewCupsonlineTransport(c.docURL, cfg, true)
	default:
		return nil, fmt.Errorf("%w: %q", ErrUnsupported, c.transportType)
	}

	if c.encryptionKey != "" {
		context := c.transportType
		if c.docURL != "" {
			context = c.docURL
		}
		encrypted, err := transport.NewEncryptedTransport(inner, c.encryptionKey, context, false)
		if err != nil {
			return nil, err
		}
		inner = encrypted
	}
	return transport.NewCompressedTransport(inner), nil
}

func supportedTransport(transportType string) bool {
	switch transportType {
	case "yandex", "vyandex", "oneme", "cupsonline":
		return true
	default:
		return false
	}
}
