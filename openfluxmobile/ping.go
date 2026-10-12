package openflux

import (
	"context"
	"io"
	"net/http"
	"net/url"
	"time"
)

const (
	defaultPingURL  = "https://www.google.com/generate_204"
	pingSamples     = 3
	pingSampleDelay = 80 * time.Millisecond
)

// Ping measures the best HTTP round-trip through the local SOCKS5 listener.
// It starts the tunnel when it is not running and stops it again before
// returning. Returns the latency in milliseconds, or -1 on failure.
//
// The upstream bridge runs one tunnel per process: when a tunnel is already
// up, the probe reuses it only if it was started from the same profile, and
// returns -1 otherwise.
func (c *Client) Ping(timeoutMillis int, targetURL string) int64 {
	if targetURL == "" {
		targetURL = defaultPingURL
	}
	if _, err := url.ParseRequestURI(targetURL); err != nil {
		return -1
	}
	if timeoutMillis <= 0 {
		timeoutMillis = 15000
	}

	addr, done, err := c.withTunnel(timeoutMillis)
	if err != nil {
		return -1
	}
	defer done()

	sampleTimeout := time.Duration(timeoutMillis) * time.Millisecond
	if sampleTimeout > 5*time.Second {
		sampleTimeout = 5 * time.Second
	}
	httpClient := newSOCKSPingClient(addr, sampleTimeout)
	defer httpClient.CloseIdleConnections()

	ctx := context.Background()
	_ = pingOnce(ctx, httpClient, targetURL, sampleTimeout) // warmup
	var best int64
	for i := 0; i < pingSamples; i++ {
		if ms := pingOnce(ctx, httpClient, targetURL, sampleTimeout); ms > 0 && (best == 0 || ms < best) {
			best = ms
		}
		if i < pingSamples-1 {
			time.Sleep(pingSampleDelay)
		}
	}
	if best == 0 {
		return -1
	}
	return best
}

// Check reports "ok" once the tunnel is up, or an error string. Useful for a
// UI connectivity test button.
func (c *Client) Check(timeoutMillis int) string {
	if timeoutMillis <= 0 {
		timeoutMillis = 15000
	}
	_, done, err := c.withTunnel(timeoutMillis)
	if err != nil {
		return err.Error()
	}
	defer done()
	return "ok"
}

// withTunnel returns the address of a connected SOCKS5 listener and a cleanup
// that stops a tunnel this call started. A tunnel another client already owns
// is reused only when it was started from the same profile.
func (c *Client) withTunnel(timeoutMillis int) (string, func(), error) {
	if addr, ownerKey, ok := runningProxy(); ok {
		if ownerKey != c.profileKey() {
			return "", nil, ErrBridgeBusy
		}
		if err := waitConnected(timeoutMillis); err != nil {
			return "", nil, err
		}
		return addr, func() {}, nil
	}

	if err := c.Start(); err != nil {
		return "", nil, err
	}
	if err := c.WaitReady(timeoutMillis); err != nil {
		_ = c.Stop()
		return "", nil, err
	}
	c.mu.Lock()
	addr := c.addr
	c.mu.Unlock()
	return addr, func() { _ = c.Stop() }, nil
}

func newSOCKSPingClient(socksAddr string, timeout time.Duration) *http.Transport {
	return &http.Transport{
		Proxy:                 http.ProxyURL(&url.URL{Scheme: "socks5", Host: socksAddr}),
		DisableKeepAlives:     false,
		MaxIdleConns:          4,
		MaxIdleConnsPerHost:   4,
		IdleConnTimeout:       10 * time.Second,
		ForceAttemptHTTP2:     false,
		TLSHandshakeTimeout:   timeout,
		ResponseHeaderTimeout: timeout,
		ExpectContinueTimeout: 500 * time.Millisecond,
	}
}

func pingOnce(parentCtx context.Context, httpClient *http.Transport, targetURL string, timeout time.Duration) int64 {
	ctx, cancel := context.WithTimeout(parentCtx, timeout)
	defer cancel()

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, targetURL, http.NoBody)
	if err != nil {
		return -1
	}
	req.Header.Set("User-Agent", "Veil-OpenFlux")
	req.Header.Set("Connection", "keep-alive")
	req.Header.Set("Cache-Control", "no-cache")

	client := &http.Client{
		Transport: httpClient,
		Timeout:   timeout,
		CheckRedirect: func(*http.Request, []*http.Request) error {
			return http.ErrUseLastResponse
		},
	}
	started := time.Now()
	resp, err := client.Do(req)
	if err != nil {
		return -1
	}
	defer func() { _ = resp.Body.Close() }()
	_, _ = io.Copy(io.Discard, resp.Body)
	if resp.StatusCode < http.StatusOK || resp.StatusCode > http.StatusPermanentRedirect {
		return -1
	}
	return time.Since(started).Milliseconds()
}
