package amneziawg

import "testing"

type recordingProtector struct {
	fds []int64
}

func (p *recordingProtector) Protect(fd int64) bool {
	p.fds = append(p.fds, fd)
	return true
}

func TestProtectedBindProtectsOpenedSockets(t *testing.T) {
	protector := &recordingProtector{}
	bind := newProtectedBind(protector, newLogBuffer())

	fns, _, err := bind.Open(0)
	if err != nil {
		t.Fatalf("Open: %v", err)
	}
	defer bind.Close()

	if len(fns) == 0 {
		t.Fatal("bind returned no receive functions")
	}
	if len(protector.fds) == 0 {
		t.Fatal("no socket was passed to the protector")
	}
}
