package amneziawg

import (
	"strings"
	"testing"
)

// gomobile's Kotlin constructor Client() creates a Go zero value; it does not
// call New(). Make sure that path is fully usable.
func TestZeroValueClientStartStop(t *testing.T) {
	port := freePort(t)
	raw := `{
		"privateKey": "` + testKey(1) + `",
		"addresses": ["10.66.66.2/32"],
		"mtu": 1420,
		"peer": {
			"publicKey": "` + testKey(2) + `",
			"endpoint": "127.0.0.1:51820",
			"allowedIps": ["0.0.0.0/0"]
		}
	}`

	var client Client
	if err := client.SetConfigJSON(raw); err != nil {
		t.Fatalf("SetConfigJSON: %v", err)
	}
	if err := client.SetSocks("127.0.0.1", port); err != nil {
		t.Fatalf("SetSocks: %v", err)
	}
	if err := client.Start(); err != nil {
		t.Fatalf("Start: %v", err)
	}
	if !client.IsRunning() {
		t.Fatal("client should be running")
	}
	logs := client.ReadLog()
	if !strings.Contains(logs, "starting device build="+buildID) {
		t.Fatalf("missing build marker in native log:\n%s", logs)
	}
	client.Stop()
	if client.IsRunning() {
		t.Fatal("client should be stopped")
	}
}

func TestNilLogBufferIsSafe(t *testing.T) {
	var logs *logBuffer
	logs.Printf("nil log buffer %d", 1)
	logs.Errorf("nil log buffer error")
	if got := logs.Read(); got != "" {
		t.Fatalf("nil log buffer read = %q, want empty", got)
	}
}
