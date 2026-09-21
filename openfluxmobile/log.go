package openflux

import (
	"strings"
	"sync"
)

const maxLogLines = 1000

var logBuffer = &ringLog{}

type ringLog struct {
	mu    sync.Mutex
	lines []string
}

func (r *ringLog) Write(p []byte) (int, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	for _, line := range strings.Split(strings.TrimRight(string(p), "\n"), "\n") {
		r.lines = append(r.lines, line)
	}
	if len(r.lines) > maxLogLines {
		r.lines = r.lines[len(r.lines)-maxLogLines:]
	}
	return len(p), nil
}

// ReadLog drains buffered log lines (newline-separated). Safe to call from any
// goroutine; the host app polls it to display tunnel logs.
func ReadLog() string {
	logBuffer.mu.Lock()
	defer logBuffer.mu.Unlock()
	if len(logBuffer.lines) == 0 {
		return ""
	}
	out := strings.Join(logBuffer.lines, "\n")
	logBuffer.lines = logBuffer.lines[:0]
	return out
}
