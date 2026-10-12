package openflux

import ofmobile "openflux-mobile"

// ReadLog drains the tunnel's buffered log lines (newline-separated). Safe to
// call from any goroutine; the host app polls it to display tunnel logs.
func ReadLog() string {
	return ofmobile.ReadLogs()
}
