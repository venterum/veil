module openfluxmobile

go 1.26.6

require openflux-mobile v0.0.0

require (
	github.com/dlclark/regexp2/v2 v2.5.2 // indirect
	github.com/dop251/goja v0.0.0-20260926152631-39ec2650adc9 // indirect
	github.com/dop251/goja_nodejs v0.0.0-20260918173711-b481721df8a2 // indirect
	github.com/go-sourcemap/sourcemap v2.1.4+incompatible // indirect
	github.com/google/btree v1.1.2 // indirect
	github.com/google/pprof v0.0.0-20240727154555-813a5fbdbec8 // indirect
	github.com/google/uuid v1.6.0 // indirect
	github.com/gorilla/websocket v1.5.3 // indirect
	github.com/jlaffaye/ftp v0.2.4 // indirect
	github.com/klauspost/compress v1.20.0 // indirect
	github.com/p1neappleXpress/OpenFlux v0.0.0 // indirect
	github.com/pierrec/lz4/v4 v4.1.27 // indirect
	github.com/pion/datachannel v1.6.3 // indirect
	github.com/pion/dtls/v3 v3.1.9 // indirect
	github.com/pion/ice/v4 v4.4.4 // indirect
	github.com/pion/interceptor v0.1.49 // indirect
	github.com/pion/logging v0.2.4 // indirect
	github.com/pion/mdns/v2 v2.2.1 // indirect
	github.com/pion/randutil v0.1.0 // indirect
	github.com/pion/rtcp v1.2.18 // indirect
	github.com/pion/rtp v1.10.5 // indirect
	github.com/pion/sctp v1.11.3 // indirect
	github.com/pion/sdp/v3 v3.0.20 // indirect
	github.com/pion/srtp/v3 v3.1.0 // indirect
	github.com/pion/stun/v4 v4.0.1 // indirect
	github.com/pion/transport/v5 v5.1.1 // indirect
	github.com/pion/turn/v5 v5.1.2 // indirect
	github.com/pion/webrtc/v4 v4.2.22 // indirect
	github.com/skip2/go-qrcode v0.0.0-20200617195104-da1b6568686e // indirect
	github.com/wlynxg/anet v0.0.5 // indirect
	github.com/xjasonlyu/windivert-go v0.0.0-20201010013527-4239d0afa76f // indirect
	golang.org/x/crypto v0.57.0 // indirect
	golang.org/x/exp v0.0.0-20250711185948-6ae5c78190dc // indirect
	golang.org/x/net v0.59.0 // indirect
	golang.org/x/sys v0.48.0 // indirect
	golang.org/x/text v0.42.0 // indirect
	golang.org/x/time v0.15.0 // indirect
	gvisor.dev/gvisor v0.0.0-20260530041128-23ef90c42be7 // indirect
)

// Only needed for standalone builds/IDE; the combined libv2ray module supplies
// its own replace directives when it binds this package.
replace github.com/p1neappleXpress/OpenFlux => ../OpenFlux

replace openflux-mobile => ../OpenFlux/mobile
