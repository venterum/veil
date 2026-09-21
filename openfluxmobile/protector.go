package openflux

import "sync"

// SocketProtector protects sockets from Android VPN routing. Implement it in
// the host app (Kotlin) and pass it to SetProtector.
type SocketProtector interface {
	Protect(fd int) bool
}

var (
	protectorMu sync.Mutex
	protectorFn func(int) bool
)

// SetProtector installs a process-wide Android VpnService socket protector.
// Passing nil clears it. Protection is process-wide even if multiple clients
// coexist.
func (c *Client) SetProtector(p SocketProtector) {
	protectorMu.Lock()
	defer protectorMu.Unlock()
	if p == nil {
		protectorFn = nil
		return
	}
	protectorFn = func(fd int) bool { return p.Protect(fd) }
}

// ProtectSocket is exported for transports that dial sockets directly; it calls
// the installed protector or returns true when none is set.
func ProtectSocket(fd int) bool {
	protectorMu.Lock()
	fn := protectorFn
	protectorMu.Unlock()
	if fn == nil {
		return true
	}
	return fn(fd)
}
