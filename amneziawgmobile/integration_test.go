package amneziawg

import (
	"crypto/rand"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"io"
	"net"
	"net/netip"
	"testing"
	"time"

	"github.com/amnezia-vpn/amneziawg-go/v3/conn"
	"github.com/amnezia-vpn/amneziawg-go/v3/device"
	"github.com/amnezia-vpn/amneziawg-go/v3/tun/netstack"
	"github.com/txthinking/socks5"
	"golang.org/x/crypto/curve25519"
)

func TestTunnelThroughLocalServer(t *testing.T) {
	serverKey := newKeyPair(t)
	clientKey := newKeyPair(t)
	serverPort := freeUDPPort(t)
	const awgParams = "jc=4\njmin=50\njmax=1000\ns1=30\ns2=31\ns3=32\ns4=33\nh1=100\nh2=200\nh3=300\nh4=400\n"

	serverTun, serverNet, err := netstack.CreateNetTUN(
		[]netip.Addr{netip.MustParseAddr("10.66.66.1")}, nil, 1420)
	if err != nil {
		t.Fatalf("server netstack: %v", err)
	}
	serverLogger := &device.Logger{Verbosef: func(string, ...any) {}, Errorf: func(string, ...any) {}}
	serverDevice := device.NewDevice(serverTun, conn.NewStdNetBind(), serverLogger)
	serverUAPI := fmt.Sprintf(
		"private_key=%s\nlisten_port=%d\n%spublic_key=%s\nallowed_ip=10.66.66.2/32\n",
		serverKey.privHex, serverPort, awgParams, clientKey.pubHex)
	if err := serverDevice.IpcSet(serverUAPI); err != nil {
		t.Fatalf("server IpcSet: %v", err)
	}
	if err := serverDevice.Up(); err != nil {
		t.Fatalf("server Up: %v", err)
	}
	defer serverDevice.Close()

	listener, err := serverNet.ListenTCPAddrPort(netip.AddrPortFrom(netip.MustParseAddr("10.66.66.1"), 0))
	if err != nil {
		t.Fatalf("server listen: %v", err)
	}
	defer listener.Close()
	go func() {
		for {
			conn, err := listener.Accept()
			if err != nil {
				return
			}
			go func() {
				defer conn.Close()
				_, _ = io.Copy(conn, conn)
			}()
		}
	}()

	client := New()
	clientJSON := fmt.Sprintf(`{
		"privateKey": %q,
		"addresses": ["10.66.66.2/32"],
		"mtu": 1420,
		"peer": {
			"publicKey": %q,
			"endpoint": "127.0.0.1:%d",
			"allowedIps": ["10.66.66.1/32"]
		},
		"awg": {
			"jc": 4, "jmin": 50, "jmax": 1000,
			"s1": 30, "s2": 31, "s3": 32, "s4": 33,
			"h1": "100", "h2": "200", "h3": "300", "h4": "400"
		}
	}`, clientKey.privBase64, serverKey.pubBase64, serverPort)
	if err := client.SetConfigJSON(clientJSON); err != nil {
		t.Fatalf("client config: %v", err)
	}
	socksPort := freePort(t)
	if err := client.SetSocks("127.0.0.1", socksPort); err != nil {
		t.Fatalf("client socks: %v", err)
	}
	if err := client.Start(); err != nil {
		t.Fatalf("client start: %v", err)
	}
	defer client.Stop()

	socksClient, err := socks5.NewClient(net.JoinHostPort("127.0.0.1", itoa(socksPort)), "", "", 5, 5)
	if err != nil {
		t.Fatalf("socks client: %v", err)
	}
	target := listener.Addr().String()
	conn, err := socksClient.Dial("tcp", target)
	if err != nil {
		t.Fatalf("socks dial %s: %v", target, err)
	}
	defer conn.Close()
	_ = conn.SetDeadline(time.Now().Add(10 * time.Second))

	payload := []byte("amneziawg loopback test")
	if _, err := conn.Write(payload); err != nil {
		t.Fatalf("write: %v", err)
	}
	reply := make([]byte, len(payload))
	if _, err := io.ReadFull(conn, reply); err != nil {
		t.Fatalf("read: %v", err)
	}
	if string(reply) != string(payload) {
		t.Fatalf("echo mismatch: got %q", reply)
	}

	udpListener, err := serverNet.ListenUDPAddrPort(netip.AddrPortFrom(netip.MustParseAddr("10.66.66.1"), 0))
	if err != nil {
		t.Fatalf("server udp listen: %v", err)
	}
	defer udpListener.Close()
	go func() {
		buffer := make([]byte, 2048)
		for {
			n, source, err := udpListener.ReadFrom(buffer)
			if err != nil {
				return
			}
			_, _ = udpListener.WriteTo(buffer[:n], source)
		}
	}()

	udpClient, err := socks5.NewClient(net.JoinHostPort("127.0.0.1", itoa(socksPort)), "", "", 10, 10)
	if err != nil {
		t.Fatalf("socks udp client: %v", err)
	}
	udpConn, err := udpClient.Dial("udp", udpListener.LocalAddr().String())
	if err != nil {
		t.Fatalf("socks udp dial: %v", err)
	}
	defer udpConn.Close()
	_ = udpConn.SetDeadline(time.Now().Add(10 * time.Second))
	udpPayload := []byte("amneziawg udp test")
	if _, err := udpConn.Write(udpPayload); err != nil {
		t.Fatalf("udp write: %v", err)
	}
	udpReply := make([]byte, 2048)
	n, err := udpConn.Read(udpReply)
	if err != nil {
		t.Fatalf("udp read: %v", err)
	}
	if string(udpReply[:n]) != string(udpPayload) {
		t.Fatalf("udp echo mismatch: got %q", udpReply[:n])
	}
}

type keyPair struct {
	privBase64 string
	privHex    string
	pubBase64  string
	pubHex     string
}

func newKeyPair(t *testing.T) keyPair {
	t.Helper()
	private := make([]byte, 32)
	if _, err := rand.Read(private); err != nil {
		t.Fatalf("generate key: %v", err)
	}
	private[0] &= 248
	private[31] &= 127
	private[31] |= 64
	public, err := curve25519.X25519(private, curve25519.Basepoint)
	if err != nil {
		t.Fatalf("derive public key: %v", err)
	}
	return keyPair{
		privBase64: base64.StdEncoding.EncodeToString(private),
		privHex:    hex.EncodeToString(private),
		pubBase64:  base64.StdEncoding.EncodeToString(public),
		pubHex:     hex.EncodeToString(public),
	}
}

func freeUDPPort(t *testing.T) int {
	t.Helper()
	conn, err := net.ListenUDP("udp4", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1)})
	if err != nil {
		t.Fatalf("free udp port: %v", err)
	}
	defer conn.Close()
	return conn.LocalAddr().(*net.UDPAddr).Port
}
