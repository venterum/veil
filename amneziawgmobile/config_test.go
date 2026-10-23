package amneziawg

import (
	"encoding/base64"
	"encoding/hex"
	"net"
	"strings"
	"testing"
)

func testKey(byteValue byte) string {
	raw := make([]byte, 32)
	for i := range raw {
		raw[i] = byteValue
	}
	return base64.StdEncoding.EncodeToString(raw)
}

func minimalConfig() *Config {
	return &Config{
		PrivateKey: testKey(1),
		Addresses:  []string{"10.66.66.2/32"},
		MTU:        1420,
		Peer: &PeerConfig{
			PublicKey: testKey(2),
			Endpoint:  "127.0.0.1:51820",
		},
	}
}

func TestNormalizeKey(t *testing.T) {
	raw := make([]byte, 32)
	for i := range raw {
		raw[i] = byte(i)
	}
	base64Key := base64.StdEncoding.EncodeToString(raw)
	hexKey := hex.EncodeToString(raw)

	got, err := normalizeKey(base64Key)
	if err != nil {
		t.Fatalf("base64 key: %v", err)
	}
	if got != hexKey {
		t.Fatalf("got %s want %s", got, hexKey)
	}
	got, err = normalizeKey(strings.ToUpper(hexKey))
	if err != nil {
		t.Fatalf("hex key: %v", err)
	}
	if got != hexKey {
		t.Fatalf("got %s want %s", got, hexKey)
	}
	if _, err := normalizeKey("not-a-key"); err == nil {
		t.Fatal("expected invalid key error")
	}
}

func TestBuildUAPIPlacement(t *testing.T) {
	config := minimalConfig()
	jc, jmin, jmax := 4, 50, 1000
	s1, s2, s3, s4 := 30, 31, 32, 33
	h1, h2, h3, h4 := "100-200", "300-400", "500-600", "700-800"
	i1 := "<b 0xdeadbeef><r 20>"
	hpk := testKey(3)
	randomTrailers := true
	config.AWG = &Obfuscation{
		Jc: &jc, Jmin: &jmin, Jmax: &jmax,
		S1: &s1, S2: &s2, S3: &s3, S4: &s4,
		H1: &h1, H2: &h2, H3: &h3, H4: &h4,
		I1:                  &i1,
		HeaderProtectionKey: &hpk,
		RandomTrailers:      &randomTrailers,
	}
	uapi, err := config.buildUAPI(nil)
	if err != nil {
		t.Fatalf("buildUAPI: %v", err)
	}
	publicIndex := strings.Index(uapi, "public_key=")
	if publicIndex < 0 {
		t.Fatal("missing public_key")
	}
	for _, key := range []string{"private_key=", "jc=", "jmin=", "jmax=", "s1=", "s2=", "s3=", "s4=", "h1=", "h2=", "h3=", "h4=", "i1=", "header_protection_key=", "random_trailers=true"} {
		index := strings.Index(uapi, key)
		if index < 0 {
			t.Fatalf("missing %s in:\n%s", key, uapi)
		}
		if index > publicIndex {
			t.Fatalf("%s must appear before public_key", key)
		}
	}
	if !strings.Contains(uapi, "endpoint=127.0.0.1:51820\n") {
		t.Fatalf("missing endpoint:\n%s", uapi)
	}
	if !strings.Contains(uapi, "allowed_ip=0.0.0.0/0\n") || !strings.Contains(uapi, "allowed_ip=::/0\n") {
		t.Fatalf("missing default allowed ips:\n%s", uapi)
	}
}

func TestHeaderProtectionRequiresPadding(t *testing.T) {
	config := minimalConfig()
	s1 := 8
	hpk := testKey(3)
	config.AWG = &Obfuscation{S1: &s1, HeaderProtectionKey: &hpk}
	if _, err := config.buildUAPI(nil); err == nil {
		t.Fatal("expected header protection validation error")
	}
}

func TestConfigRequiresPeer(t *testing.T) {
	config := minimalConfig()
	config.Peer = nil
	if err := config.validate(); err == nil {
		t.Fatal("expected peer validation error")
	}
}

func TestClientStartStop(t *testing.T) {
	port := freePort(t)
	raw := `{
		"privateKey": "` + testKey(1) + `",
		"addresses": ["10.66.66.2/32"],
		"dns": ["1.1.1.1"],
		"mtu": 1420,
		"peer": {
			"publicKey": "` + testKey(2) + `",
			"endpoint": "127.0.0.1:51820",
			"allowedIps": ["0.0.0.0/0"]
		},
		"awg": {
			"jc": 4,
			"jmin": 50,
			"jmax": 1000,
			"s1": 30,
			"s2": 30,
			"h1": "100-200",
			"h2": "300-400"
		}
	}`
	client := New()
	if err := client.SetConfigJSON(raw); err != nil {
		t.Fatalf("SetConfigJSON: %v", err)
	}
	if err := client.SetSocks("127.0.0.1", port); err != nil {
		t.Fatalf("SetSocks: %v", err)
	}
	if err := client.Start(); err != nil {
		t.Fatalf("Start: %v", err)
	}
	defer client.Stop()
	if !client.IsRunning() {
		t.Fatal("client should be running")
	}
	conn, err := net.Dial("tcp", net.JoinHostPort("127.0.0.1", itoa(port)))
	if err != nil {
		t.Fatalf("socks listener: %v", err)
	}
	_ = conn.Close()
	client.Stop()
	if client.IsRunning() {
		t.Fatal("client should be stopped")
	}
}

func freePort(t *testing.T) int {
	t.Helper()
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("free port: %v", err)
	}
	defer listener.Close()
	return listener.Addr().(*net.TCPAddr).Port
}

func itoa(value int) string {
	if value == 0 {
		return "0"
	}
	var buf [20]byte
	pos := len(buf)
	for value > 0 {
		pos--
		buf[pos] = byte('0' + value%10)
		value /= 10
	}
	return string(buf[pos:])
}
