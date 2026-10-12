#!/bin/bash
set -o errexit
set -o pipefail
set -o nounset

# Build the combined libv2ray.aar (Xray core + olcRTC + OpenFlux) with 16 KB ELF alignment.
#
# Requires:
#   - Go 1.26.4+ and gomobile (go install golang.org/x/mobile/cmd/gomobile@latest)
#   - Android SDK (ANDROID_HOME)
#   - Android NDK r27+ (ANDROID_NDK_HOME)

__dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ -z "${ANDROID_HOME:-}" ]]; then
  echo "ANDROID_HOME is not set" >&2
  exit 1
fi

if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  echo "ANDROID_NDK_HOME is not set" >&2
  exit 1
fi

TMPDIR=$(mktemp -d)
cleanup() {
  rm -rf "$TMPDIR"
}
trap 'cleanup' EXIT

mkdir -p "$TMPDIR/assets"
ln -sfn "$__dir/AndroidLibXrayLite/assets/"* "$TMPDIR/assets/"

cat > "$TMPDIR/go.mod" <<EOF
module libv2ray

go 1.27

require (
	github.com/2dust/AndroidLibXrayLite v0.0.0
	github.com/openlibrecommunity/olcrtc v0.0.0
	github.com/p1neappleXpress/OpenFlux v0.0.0
	openflux-mobile v0.0.0
	openfluxmobile v0.0.0
	golang.org/x/mobile v0.0.0-20260520154334-0e4426e1883d
)

replace github.com/2dust/AndroidLibXrayLite => $__dir/AndroidLibXrayLite

replace github.com/openlibrecommunity/olcrtc => $__dir/olcrtc

replace github.com/p1neappleXpress/OpenFlux => $__dir/OpenFlux

replace openflux-mobile => $__dir/OpenFlux/mobile

replace openfluxmobile => $__dir/openfluxmobile

// olcrtc implements pion's transport/v4 interfaces, while OpenFlux v0.5.1
// (telemost) pins the transport/v5 family. Keep the whole pion stack at the
// last transport/v4 releases (the set webrtc v4.2.20 needs) so both modules
// can coexist in one binary. Only the telemost/MAX carriers use pion.
replace github.com/pion/webrtc/v4 => github.com/pion/webrtc/v4 v4.2.20

replace github.com/pion/datachannel => github.com/pion/datachannel v1.6.2

replace github.com/pion/dtls/v3 => github.com/pion/dtls/v3 v3.1.8

replace github.com/pion/ice/v4 => github.com/pion/ice/v4 v4.4.2

replace github.com/pion/interceptor => github.com/pion/interceptor v0.1.48

replace github.com/pion/mdns/v2 => github.com/pion/mdns/v2 v2.2.0

replace github.com/pion/rtcp => github.com/pion/rtcp v1.2.17

replace github.com/pion/sctp => github.com/pion/sctp v1.11.1

replace github.com/pion/sdp/v3 => github.com/pion/sdp/v3 v3.0.19

replace github.com/pion/srtp/v3 => github.com/pion/srtp/v3 v3.0.13

replace github.com/pion/stun/v4 => github.com/pion/stun/v4 v4.0.0

replace github.com/pion/turn/v5 => github.com/pion/turn/v5 v5.1.0

replace github.com/pion/transport/v4 => github.com/pion/transport/v4 v4.1.0

$(sed -n '/^replace /p' "$__dir/AndroidLibXrayLite/go.mod")
EOF

cat > "$TMPDIR/imports.go" <<'EOF'
package libv2ray

import (
	_ "github.com/2dust/AndroidLibXrayLite"
	_ "github.com/openlibrecommunity/olcrtc/mobile"
	_ "openfluxmobile"
)
EOF

cd "$TMPDIR"
go mod tidy

export ANDROID_HOME
export ANDROID_NDK_HOME
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_NDK_HOME:$PATH"

gomobile bind \
  -target=android \
  -androidapi 26 \
  -ldflags "-s -w -checklinkname=0 -extldflags=-Wl,-z,max-page-size=16384" \
  -o "$__dir/veil/app/libs/libv2ray.aar" \
  github.com/2dust/AndroidLibXrayLite \
  github.com/openlibrecommunity/olcrtc/mobile \
  openfluxmobile
