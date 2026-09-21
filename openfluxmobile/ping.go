package openflux

import (
	"context"
	"io"
	"net"
	"net/http"
	"net/url"
	"strconv"
	"time"
)

const (
	defaultPingURL  = "https://www.google.com/generate_204"
	pingSamples     = 3
	pingSampleDelay = 80 * time.Millisecond
)

// Ping starts the client if it is not already running, waits for readiness and
// measures the best HTTP round-trip through the local SOCKS5 listener. Returns
// the latency in milliseconds, or -1 on failure. When Ping starts the client it
// also stops it again before returning.
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

	startedHere, ok := c.ensureStarted()
	if !ok {
		return -1
	}
	if startedHere {
		defer func() { _ = c.Stop() }()
	}
	if err := c.WaitReady(timeoutMillis); err != nil {
		return -1
	}

	c.mu.Lock()
	addr := net.JoinHostPort(c.socksHost, strconv.Itoa(c.socksPort))
	c.mu.Unlock()

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

// Check starts the client if needed and reports "ok" or an error string. Useful
// for a UI connectivity test button.
func (c *Client) Check(timeoutMillis int) string {
	if timeoutMillis <= 0 {
		timeoutMillis = 15000
	}
	startedHere, ok := c.ensureStarted()
	if !ok {
		return "start failed"
	}
	if startedHere {
		defer func() { _ = c.Stop() }()
	}
	if err := c.WaitReady(timeoutMillis); err != nil {
		return err.Error()
	}
	return "ok"
}

// ensureStarted starts the client when idle. The bool is false if starting
// failed; the first return value reports whether this call did the start.
func (c *Client) ensureStarted() (bool, bool) {
	c.mu.Lock()
	running := c.running
	c.mu.Unlock()
	if running {
		return false, true
	}
	if err := c.Start(); err != nil {
		return false, false
	}
	return true, true
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
