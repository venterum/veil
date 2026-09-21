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
	openfluxmobile v0.0.0
	universal-bypass-tool v0.0.0
	golang.org/x/mobile v0.0.0-20260520154334-0e4426e1883d
)

replace github.com/2dust/AndroidLibXrayLite => $__dir/AndroidLibXrayLite

replace github.com/openlibrecommunity/olcrtc => $__dir/olcrtc

replace openfluxmobile => $__dir/openfluxmobile

replace universal-bypass-tool => $__dir/OpenFlux

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
  -androidapi 21 \
  -ldflags "-s -w -checklinkname=0 -extldflags=-Wl,-z,max-page-size=16384" \
  -o "$__dir/veil/app/libs/libv2ray.aar" \
  github.com/2dust/AndroidLibXrayLite \
  github.com/openlibrecommunity/olcrtc/mobile \
  openfluxmobile
