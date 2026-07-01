# veil

A V2Ray / Xray client for Android with Material 3 UI and olcRTC tunnel support.

[![API](https://img.shields.io/badge/API-24%2B-yellow.svg?style=flat)](https://developer.android.com/about/versions/nougat)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-blue.svg)](https://kotlinlang.org)
[![Material 3](https://img.shields.io/badge/Material-3-6750A4.svg)](https://m3.material.io)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

Based on [v2rayNG](https://github.com/2dust/v2rayNG) by 2dust.

## Features

- Standard protocols: VMess, VLESS, Shadowsocks, Trojan, SOCKS, WireGuard, Hysteria2
- **olcRTC** — encrypted TCP-over-WebRTC tunnel (carriers: Jitsi, Telemost, WbStream; transports: DataChannel, VP8, SEI)
- Subscription management and QR-code import/export
- Per-app proxy, routing rules, DNS, fragmentation, mux and more
- VPN service backed by `hev-socks5-tunnel` or the xray-core tunnel
- geoip / geosite rule support
- Material 3 UI with Google Sans Flex typeface and system font toggle

## Architecture

```text
app -> Xray core -> SOCKS5 -> olcrtc client -> WebRTC/SFU -> olcrtc server -> internet
```

olcRTC runs as a separate Go process (gomobile AAR), Xray core routes traffic to its local SOCKS5 port.

## Project layout

```
.
├── V2rayNG/                 # Android application (Gradle project)
│   └── app/
│       ├── src/main/java/com/v2ray/ang/core/OlcrtcManager.kt
│       ├── src/main/java/com/v2ray/ang/fmt/OlcrtcFmt.kt
│       ├── src/main/java/com/v2ray/ang/ui/OlcrtcActivity.kt
│       └── ...
├── hev-socks5-tunnel/       # git submodule – native TUN tunnel
├── AndroidLibXrayLite/      # git submodule – Go sources for libv2ray.aar
├── compile-hevtun.sh        # builds the native libhev-socks5-tunnel libraries
└── README.md
```

> `olcrtc/` and `olcbox/` are separate projects maintained independently.

## Building from source

### Requirements

- JDK 17+
- Android SDK: `platforms;android-37`, `build-tools;37.0.0`, `platform-tools`
- Android NDK for the native tunnel
- Go 1.26+ and [mage](https://github.com/magefile/mage) (for olcrtc AAR)

### Steps

1. **Clone with submodules**

   ```bash
   git clone --recurse-submodules <repo-url>
   git submodule update --init --recursive
   ```

2. **Build `hev-socks5-tunnel` native libraries**

   ```bash
   export NDK_HOME=$ANDROID_HOME/ndk/<ndk-version>
   bash compile-hevtun.sh
   cp -r libs V2rayNG/app/
   ```

3. **Build olcrtc AAR** (from your olcrtc checkout)

   ```bash
   cd olcrtc
   mage mobile
   cp build/olcrtc.aar ../V2rayNG/app/libs/
   ```

4. **Provide Xray core (`libv2ray.aar`)**

   Download from [AndroidLibXrayLite releases](https://github.com/2dust/AndroidLibXrayLite/releases) and place in `V2rayNG/app/libs/`.

5. **Build the APK**

   ```bash
   echo "sdk.dir=$ANDROID_HOME" > V2rayNG/local.properties
   cd V2rayNG
   ./gradlew assembleDebug
   ```

## Tech stack

- Kotlin, Android View system with View Binding
- Material Components for Android (Material 3 Expressive theme)
- MMKV, OkHttp, Gson, Coroutines
- Xray core via `libv2ray.aar`, native TUN via `hev-socks5-tunnel`
- olcRTC (Go) via gomobile AAR

## Credits

- [v2rayNG](https://github.com/2dust/v2rayNG) by 2dust — upstream project
- [Xray-core](https://github.com/XTLS/Xray-core) and [v2fly/v2ray-core](https://github.com/v2fly/v2ray-core)
- [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) by heiher
- [olcRTC](https://github.com/openlibrecommunity/olcrtc) by openlibrecommunity
- Google Sans Flex (bundled under its respective font license)

## License

GNU General Public License v3.0. See [LICENSE](LICENSE).
