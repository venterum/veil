package amneziawg

import (
	"io"
	"net"
	"sync"
	"time"

	"github.com/amnezia-vpn/amneziawg-go/v3/tun/netstack"
	"github.com/txthinking/socks5"
)

// socksHandler dials through the AmneziaWG netstack instead of the host
// network. Keeping the dialer per tunnel avoids process-global state, so a
// latency probe can run a second device while the main tunnel is up.
type socksHandler struct {
	tnet *netstack.Net
	logs *logBuffer

	mu     sync.Mutex
	relays map[string]*udpRelay
	closed bool
}

type udpRelay struct {
	key      string
	remote   net.Conn
	client   *net.UDPAddr
	atyp     byte
	dstAddr  []byte
	dstPort  []byte
	lastUsed time.Time
}

func (h *socksHandler) TCPHandle(s *socks5.Server, client *net.TCPConn, request *socks5.Request) error {
	if request.Cmd == socks5.CmdUDP {
		atyp, addr, port, err := socks5.ParseAddress(s.ServerAddr.String())
		if err != nil {
			writeReply(client, socks5.RepHostUnreachable)
			return err
		}
		reply := socks5.NewReply(socks5.RepSuccess, atyp, addr, port)
		if _, err := reply.WriteTo(client); err != nil {
			return err
		}
		// The association lives as long as the control connection.
		_, _ = io.Copy(io.Discard, client)
		return nil
	}
	if request.Cmd != socks5.CmdConnect {
		writeReply(client, socks5.RepCommandNotSupported)
		return socks5.ErrUnsupportCmd
	}
	destination := request.Address()
	if h.logs != nil {
		h.logs.Printf("socks tcp -> %s", destination)
	}
	remote, err := h.tnet.Dial("tcp", destination)
	if err != nil {
		if h.logs != nil {
			h.logs.Printf("socks tcp dial %s failed: %v", destination, err)
		}
		writeReply(client, socks5.RepHostUnreachable)
		return err
	}
	defer remote.Close()
	if err := writeReply(client, socks5.RepSuccess); err != nil {
		return err
	}

	done := make(chan struct{}, 2)
	go func() {
		_, _ = io.Copy(remote, client)
		closeWrite(remote)
		closeRead(client)
		done <- struct{}{}
	}()
	go func() {
		_, _ = io.Copy(client, remote)
		closeWrite(client)
		closeRead(remote)
		done <- struct{}{}
	}()
	<-done
	return nil
}

func (h *socksHandler) UDPHandle(s *socks5.Server, client *net.UDPAddr, datagram *socks5.Datagram) error {
	if datagram.Frag != 0 {
		return nil
	}
	key := client.String() + "|" + datagram.Address()

	h.mu.Lock()
	if h.closed {
		h.mu.Unlock()
		return nil
	}
	relay := h.relays[key]
	if relay == nil {
		if h.logs != nil {
			h.logs.Printf("socks udp -> %s", datagram.Address())
		}
		remote, err := h.tnet.Dial("udp", datagram.Address())
		if err != nil {
			if h.logs != nil {
				h.logs.Printf("socks udp dial %s failed: %v", datagram.Address(), err)
			}
			h.mu.Unlock()
			return err
		}
		relay = &udpRelay{
			key:      key,
			remote:   remote,
			client:   &net.UDPAddr{IP: append(net.IP(nil), client.IP...), Port: client.Port, Zone: client.Zone},
			atyp:     datagram.Atyp,
			dstAddr:  append([]byte(nil), datagram.DstAddr...),
			dstPort:  append([]byte(nil), datagram.DstPort...),
			lastUsed: time.Now(),
		}
		h.relays[key] = relay
		go h.readUDP(s, relay)
	}
	relay.lastUsed = time.Now()
	h.mu.Unlock()

	_, err := relay.remote.Write(datagram.Data)
	return err
}

func (h *socksHandler) readUDP(server *socks5.Server, relay *udpRelay) {
	buffer := make([]byte, 65535)
	defer h.removeRelay(relay)
	for {
		_ = relay.remote.SetReadDeadline(time.Now().Add(udpIdleTimeout))
		n, err := relay.remote.Read(buffer)
		if err != nil {
			return
		}
		reply := socks5.NewDatagram(relay.atyp, relay.dstAddr, relay.dstPort, append([]byte(nil), buffer[:n]...))
		if _, err := server.UDPConn.WriteToUDP(reply.Bytes(), relay.client); err != nil {
			return
		}
	}
}

func (h *socksHandler) removeRelay(relay *udpRelay) {
	h.mu.Lock()
	if h.relays[relay.key] == relay {
		delete(h.relays, relay.key)
	}
	h.mu.Unlock()
	_ = relay.remote.Close()
}

func (h *socksHandler) closeAll() {
	h.mu.Lock()
	h.closed = true
	relays := make([]*udpRelay, 0, len(h.relays))
	for key, relay := range h.relays {
		relays = append(relays, relay)
		delete(h.relays, key)
	}
	h.mu.Unlock()
	for _, relay := range relays {
		_ = relay.remote.Close()
	}
}

func writeReply(writer io.Writer, code byte) error {
	reply := socks5.NewReply(code, socks5.ATYPIPv4, []byte{0x00, 0x00, 0x00, 0x00}, []byte{0x00, 0x00})
	_, err := reply.WriteTo(writer)
	return err
}

func closeWrite(conn net.Conn) {
	if tcp, ok := conn.(*net.TCPConn); ok {
		_ = tcp.CloseWrite()
	}
}

func closeRead(conn net.Conn) {
	if tcp, ok := conn.(*net.TCPConn); ok {
		_ = tcp.CloseRead()
	}
}
