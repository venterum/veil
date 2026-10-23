package amneziawg

import (
	"github.com/amnezia-vpn/amneziawg-go/v3/conn"
)

// protectedBind wraps conn.StdNetBind so every UDP socket opened by the
// WireGuard device is handed to VpnService.protect before it can send
// anything. This is more reliable than peeking at file descriptors after
// device.Up(): StdNetBind may open only one address family, and the
// PeekLookAtSocketFd helpers panic when the corresponding socket is nil.
type protectedBind struct {
	*conn.StdNetBind
	protector SocketProtector
	logs      *logBuffer
}

func newProtectedBind(protector SocketProtector, logs *logBuffer) *protectedBind {
	std, ok := conn.NewStdNetBind().(*conn.StdNetBind)
	if !ok {
		panic("amneziawg: unexpected bind implementation")
	}
	return &protectedBind{
		StdNetBind: std,
		protector:  protector,
		logs:       logs,
	}
}

func (b *protectedBind) Open(port uint16) ([]conn.ReceiveFunc, uint16, error) {
	fns, actualPort, err := b.StdNetBind.Open(port)
	if err != nil {
		return nil, 0, err
	}
	if b.protector != nil {
		b.protect("IPv4", b.PeekLookAtSocketFd4)
		b.protect("IPv6", b.PeekLookAtSocketFd6)
	}
	return fns, actualPort, nil
}

func (b *protectedBind) protect(name string, peek func() (int, error)) {
	defer func() {
		if recovered := recover(); recovered != nil {
			b.logf("%s socket not opened: %v", name, recovered)
		}
	}()

	fd, err := peek()
	if err != nil {
		b.logf("%s socket not available: %v", name, err)
		return
	}
	if fd < 0 {
		b.logf("%s socket has invalid fd %d", name, fd)
		return
	}
	if !b.protector.Protect(int64(fd)) {
		if b.logs != nil {
			b.logs.Errorf("failed to protect %s socket fd=%d", name, fd)
		}
		return
	}
	b.logf("protected %s socket fd=%d", name, fd)
}

func (b *protectedBind) logf(format string, args ...any) {
	if b.logs != nil {
		b.logs.Printf(format, args...)
	}
}
