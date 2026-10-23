package amneziawg

import (
	"context"
	"net"
	"net/netip"
	"time"
)

// SocketProtector lets the Android layer exclude the tunnel's UDP sockets
// from the outer VpnService (VpnService.protect).
type SocketProtector interface {
	Protect(fd int64) bool
}

func contextWithTimeout(timeout time.Duration) (context.Context, context.CancelFunc) {
	return context.WithTimeout(context.Background(), timeout)
}

// lookupHost is only used for endpoint hostnames. Veil's own package is
// excluded from the VPN interface, so the system resolver operates on the
// underlying network.
func lookupHost(ctx context.Context, host string, _ SocketProtector) ([]netip.Addr, error) {
	addrs, err := net.DefaultResolver.LookupNetIP(ctx, "ip", host)
	if err != nil {
		return nil, err
	}
	return addrs, nil
}
