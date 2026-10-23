package amneziawg

import (
	"errors"
	"fmt"
	"log"
	"net"
	"net/http"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/amnezia-vpn/amneziawg-go/v3/device"
	"github.com/amnezia-vpn/amneziawg-go/v3/tun"
	"github.com/txthinking/socks5"

	"amneziawgmobile/internal/netstack"
)

const (
	// Version is the embedded AmneziaWG implementation generation.
	Version = "3.1"

	// buildID makes native startup logs distinguish fresh APK builds from
	// stale installations during field diagnostics.
	buildID = "2026-10-23-r3"

	defaultSOCKSHost = "127.0.0.1"
	defaultSOCKSPort = 1080
	readyTimeout     = 5 * time.Second
	udpIdleTimeout   = 2 * time.Minute
)

type deviceBundle struct {
	device *device.Device
	tun    tun.Device
	net    *netstack.Net
}

// Client owns one AmneziaWG device and its loopback SOCKS5 listener.
// It is safe to run several clients in the same process.
type Client struct {
	mu sync.Mutex

	rawConfig string
	config    *Config
	protector SocketProtector
	logs      *logBuffer

	socksHost string
	socksPort int

	bundle  *deviceBundle
	server  *socks5.Server
	handler *socksHandler
	running bool
}

func New() *Client {
	client := &Client{}
	client.initLocked()
	return client
}

// initLocked supplies defaults for clients created as Go zero values. gomobile's
// Java/Kotlin constructor Client() does not call New(), so every entry point
// must tolerate a Client whose fields were never initialized.
func (c *Client) initLocked() {
	if c.logs == nil {
		c.logs = newLogBuffer()
	}
	if c.socksHost == "" {
		c.socksHost = defaultSOCKSHost
	}
	if c.socksPort == 0 {
		c.socksPort = defaultSOCKSPort
	}
}

// SetConfigJSON accepts the JSON document defined by Config.
func (c *Client) SetConfigJSON(raw string) error {
	cfg, err := parseConfig(raw)
	if err != nil {
		return err
	}
	c.mu.Lock()
	c.initLocked()
	c.rawConfig = raw
	c.config = cfg
	c.mu.Unlock()
	return nil
}

// SetProtector installs the Android VpnService.protect callback.
func (c *Client) SetProtector(protector SocketProtector) {
	c.mu.Lock()
	c.initLocked()
	c.protector = protector
	c.mu.Unlock()
}

// SetSocks sets the loopback SOCKS5 bind address.
func (c *Client) SetSocks(host string, port int) error {
	host = strings.TrimSpace(host)
	if host == "" || port < 1 || port > 65535 {
		return errors.New("invalid socks address")
	}
	c.mu.Lock()
	c.initLocked()
	c.socksHost = host
	c.socksPort = port
	c.mu.Unlock()
	return nil
}

const startTimeout = 20 * time.Second

// Start brings the device up and begins serving SOCKS5 on the configured port.
func (c *Client) Start() error {
	c.mu.Lock()
	c.initLocked()
	if c.running {
		c.mu.Unlock()
		return nil
	}
	if c.config == nil {
		c.mu.Unlock()
		return errors.New("amneziawg config is not set")
	}
	config := c.config
	protector := c.protector
	host := c.socksHost
	port := c.socksPort
	logs := c.logs
	c.mu.Unlock()

	bundle, err := startDeviceWithTimeout(config, protector, logs)
	if err != nil {
		return err
	}

	addr := net.JoinHostPort(host, strconv.Itoa(port))
	server, err := socks5.NewClassicServer(addr, host, "", "", 0, 0)
	if err != nil {
		stopDevice(bundle)
		return fmt.Errorf("socks listen: %w", err)
	}
	handler := &socksHandler{
		tnet:   bundle.net,
		logs:   logs,
		relays: make(map[string]*udpRelay),
	}
	go func() {
		if serveErr := server.ListenAndServe(handler); serveErr != nil {
			logs.Printf("socks server stopped: %v", serveErr)
		}
	}()
	if err := waitForListener(addr, readyTimeout); err != nil {
		handler.closeAll()
		_ = server.Shutdown()
		stopDevice(bundle)
		return err
	}

	c.mu.Lock()
	c.bundle = bundle
	c.server = server
	c.handler = handler
	c.running = true
	c.mu.Unlock()
	logs.Printf("amneziawg %s listening on %s", Version, addr)
	return nil
}

// WaitReady waits until the SOCKS listener accepts TCP connections.
func (c *Client) WaitReady(timeoutMillis int) error {
	if timeoutMillis <= 0 {
		timeoutMillis = int(readyTimeout / time.Millisecond)
	}
	c.mu.Lock()
	c.initLocked()
	host := c.socksHost
	port := c.socksPort
	running := c.running
	c.mu.Unlock()
	if !running {
		return errors.New("amneziawg client is not running")
	}
	return waitForListener(net.JoinHostPort(host, strconv.Itoa(port)), time.Duration(timeoutMillis)*time.Millisecond)
}

// Stop tears down the SOCKS listener and the device.
func (c *Client) Stop() {
	c.mu.Lock()
	c.initLocked()
	server := c.server
	handler := c.handler
	bundle := c.bundle
	c.server = nil
	c.handler = nil
	c.bundle = nil
	c.running = false
	c.mu.Unlock()

	if handler != nil {
		handler.closeAll()
	}
	if server != nil {
		_ = server.Shutdown()
	}
	if bundle != nil {
		stopDevice(bundle)
	}
	c.logs.Printf("amneziawg stopped")
}

// IsRunning reports whether Start succeeded and Stop has not been called.
func (c *Client) IsRunning() bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.running
}

// Ping measures an HTTP HEAD (falling back to GET) through the tunnel.
// It reuses the live device when it is already connected, otherwise it builds
// a temporary one. Returns -1 on failure.
func (c *Client) Ping(url string, timeoutMillis int) int64 {
	c.mu.Lock()
	c.initLocked()
	config := c.config
	protector := c.protector
	running := c.running
	tnet := (*netstack.Net)(nil)
	if c.bundle != nil {
		tnet = c.bundle.net
	}
	logs := c.logs
	c.mu.Unlock()

	if config == nil {
		return -1
	}
	timeout := time.Duration(timeoutMillis)
	if timeout <= 0 {
		timeout = 15 * time.Second
	} else {
		timeout *= time.Millisecond
	}
	if running && tnet != nil {
		return pingThrough(tnet, url, timeout)
	}

	bundle, err := startDeviceWithTimeout(config, protector, logs)
	if err != nil {
		logs.Printf("ping device start failed: %v", err)
		return -1
	}
	defer stopDevice(bundle)
	return pingThrough(bundle.net, url, timeout)
}

// ReadLog drains the in-memory native log buffer.
func (c *Client) ReadLog() string {
	c.mu.Lock()
	c.initLocked()
	logs := c.logs
	c.mu.Unlock()
	return logs.Read()
}

// Stats returns the peer traffic counters and last handshake time. It never
// exposes private or preshared keys.
func (c *Client) Stats() string {
	c.mu.Lock()
	bundle := c.bundle
	c.mu.Unlock()
	if bundle == nil || bundle.device == nil {
		return ""
	}
	uapi, err := bundle.device.IpcGet()
	if err != nil {
		return "stats_error=" + err.Error()
	}
	var tx, rx, handshakeSec, handshakeNsec, endpoint string
	for _, line := range strings.Split(uapi, "\n") {
		switch {
		case strings.HasPrefix(line, "tx_bytes="):
			tx = strings.TrimPrefix(line, "tx_bytes=")
		case strings.HasPrefix(line, "rx_bytes="):
			rx = strings.TrimPrefix(line, "rx_bytes=")
		case strings.HasPrefix(line, "last_handshake_time_sec="):
			handshakeSec = strings.TrimPrefix(line, "last_handshake_time_sec=")
		case strings.HasPrefix(line, "last_handshake_time_nsec="):
			handshakeNsec = strings.TrimPrefix(line, "last_handshake_time_nsec=")
		case strings.HasPrefix(line, "endpoint="):
			endpoint = strings.TrimPrefix(line, "endpoint=")
		}
	}
	return fmt.Sprintf("tx=%s rx=%s handshake=%s.%s endpoint=%s", tx, rx, handshakeSec, handshakeNsec, endpoint)
}

// VersionString is exposed to gomobile as a plain string.
func (c *Client) VersionString() string {
	return Version
}

type deviceResult struct {
	bundle *deviceBundle
	err    error
}

// startDeviceWithTimeout turns a stuck gVisor/device initialization into a
// visible error plus a goroutine dump instead of an endless "connecting" UI.
func startDeviceWithTimeout(config *Config, protector SocketProtector, logs *logBuffer) (*deviceBundle, error) {
	result := make(chan deviceResult, 1)
	go func() {
		defer func() {
			if recovered := recover(); recovered != nil {
				buffer := make([]byte, 1<<20)
				n := runtime.Stack(buffer, true)
				logs.Printf("device start panic: %v\n%s", recovered, buffer[:n])
				result <- deviceResult{err: fmt.Errorf("amneziawg device start panic: %v", recovered)}
			}
		}()
		bundle, err := startDevice(config, protector, logs)
		result <- deviceResult{bundle: bundle, err: err}
	}()
	select {
	case value := <-result:
		return value.bundle, value.err
	case <-time.After(startTimeout):
		logs.Printf("device start timed out after %s; goroutine dump follows", startTimeout)
		buffer := make([]byte, 1<<20)
		n := runtime.Stack(buffer, true)
		logs.Printf("%s", buffer[:n])
		return nil, errors.New("amneziawg device start timed out")
	}
}

func startDevice(config *Config, protector SocketProtector, logs *logBuffer) (*deviceBundle, error) {
	addresses, err := config.localAddresses()
	if err != nil {
		return nil, err
	}
	logs.Printf("starting device build=%s addresses=%v mtu=%d endpoint=%s", buildID, addresses, config.MTU, config.Peer.Endpoint)
	if config.AWG != nil {
		logs.Printf("awg obfuscation: jc=%v jmin=%v jmax=%v s1=%v s2=%v s3=%v s4=%v h1=%v h2=%v h3=%v h4=%v i1=%v i2=%v i3=%v i4=%v i5=%v hp=%v",
			config.AWG.Jc, config.AWG.Jmin, config.AWG.Jmax,
			config.AWG.S1, config.AWG.S2, config.AWG.S3, config.AWG.S4,
			config.AWG.H1, config.AWG.H2, config.AWG.H3, config.AWG.H4,
			config.AWG.I1, config.AWG.I2, config.AWG.I3, config.AWG.I4, config.AWG.I5,
			config.AWG.HeaderProtectionKey != nil)
	} else {
		logs.Printf("awg obfuscation: none")
	}
	logs.Printf("creating netstack")
	tunDevice, tnet, err := netstack.CreateNetTUN(addresses, config.dnsServers(), config.MTU)
	if err != nil {
		logs.Printf("create netstack failed: %v", err)
		return nil, fmt.Errorf("create netstack: %w", err)
	}
	logs.Printf("netstack created")
	bind := newProtectedBind(protector, logs)
	logger := &device.Logger{
		Verbosef: func(format string, args ...any) { logs.Printf(format, args...) },
		Errorf:   func(format string, args ...any) { logs.Errorf(format, args...) },
	}
	dev := device.NewDevice(tunDevice, bind, logger)
	logs.Printf("device created")
	uapi, err := config.buildUAPI(protector)
	if err != nil {
		logs.Printf("build UAPI failed: %v", err)
		dev.Close()
		return nil, err
	}
	// Bring the device up before applying the peer configuration. Up() opens
	// the UDP sockets, and with no peers configured it cannot emit a handshake.
	// That gives us a chance to protect the sockets with VpnService.protect
	// before IpcSet starts the peer, whose persistent keepalive immediately
	// sends the first handshake/signature packet.
	logs.Printf("UAPI built (%d bytes), opening sockets", len(uapi))
	if err := dev.Up(); err != nil {
		logs.Printf("device Up failed: %v", err)
		dev.Close()
		return nil, fmt.Errorf("bring amneziawg up: %w", err)
	}
	logs.Printf("device up, sockets protected, applying UAPI")
	if err := dev.IpcSet(uapi); err != nil {
		logs.Printf("IpcSet failed: %v", err)
		dev.Close()
		return nil, fmt.Errorf("apply amneziawg config: %w", err)
	}
	logs.Printf("UAPI applied, peer started")
	return &deviceBundle{device: dev, tun: tunDevice, net: tnet}, nil
}

func stopDevice(bundle *deviceBundle) {
	if bundle == nil || bundle.device == nil {
		return
	}
	bundle.device.Close()
	bundle.device = nil
	bundle.net = nil
	bundle.tun = nil
}

func pingThrough(tnet *netstack.Net, url string, timeout time.Duration) int64 {
	if strings.TrimSpace(url) == "" {
		return -1
	}
	transport := &http.Transport{
		DialContext:       tnet.DialContext,
		DisableKeepAlives: true,
	}
	client := &http.Client{Transport: transport, Timeout: timeout}
	start := time.Now()
	request, err := http.NewRequest(http.MethodHead, url, nil)
	if err != nil {
		return -1
	}
	response, err := client.Do(request)
	if err == nil {
		_ = response.Body.Close()
		if response.StatusCode < 500 {
			return time.Since(start).Milliseconds()
		}
	}
	request, err = http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		return -1
	}
	response, err = client.Do(request)
	if err != nil {
		return -1
	}
	_ = response.Body.Close()
	return time.Since(start).Milliseconds()
}

func waitForListener(addr string, timeout time.Duration) error {
	deadline := time.Now().Add(timeout)
	for {
		conn, err := net.DialTimeout("tcp", addr, 200*time.Millisecond)
		if err == nil {
			_ = conn.Close()
			return nil
		}
		if time.Now().After(deadline) {
			return fmt.Errorf("socks listener %s did not become ready", addr)
		}
		time.Sleep(50 * time.Millisecond)
	}
}

type logBuffer struct {
	mu sync.Mutex
	b  strings.Builder
}

func newLogBuffer() *logBuffer {
	return &logBuffer{}
}

func (l *logBuffer) Printf(format string, args ...any) {
	line := fmt.Sprintf(format, args...)
	log.Print(line)
	if l == nil {
		return
	}
	l.mu.Lock()
	if l.b.Len() < 64*1024 {
		l.b.WriteString(line)
		l.b.WriteByte('\n')
	}
	l.mu.Unlock()
}

func (l *logBuffer) Errorf(format string, args ...any) {
	l.Printf(format, args...)
}

func (l *logBuffer) Read() string {
	if l == nil {
		return ""
	}
	l.mu.Lock()
	defer l.mu.Unlock()
	value := l.b.String()
	l.b.Reset()
	return value
}
