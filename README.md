# v2rayMaterial

A V2Ray / Xray client for Android with a **Material 3** makeover.

`v2rayMaterial` is a fork of [v2rayNG](https://github.com/2dust/v2rayNG) that keeps all of the original
proxy functionality (powered by [Xray core](https://github.com/XTLS/Xray-core) and the
[v2fly core](https://github.com/v2fly/v2ray-core)) while modernizing the user interface:
the app is migrated to Material 3 components, ships with the **Google Sans** typeface, and lets you
switch back to the device's system font at any time.

[![API](https://img.shields.io/badge/API-24%2B-yellow.svg?style=flat)](https://developer.android.com/about/versions/nougat)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-blue.svg)](https://kotlinlang.org)
[![Material 3](https://img.shields.io/badge/Material-3-6750A4.svg)](https://m3.material.io)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

---

## What's different from v2rayNG

This fork focuses purely on the look & feel — the networking layer is untouched and stays in sync
with upstream behaviour.

| Area | Change |
| --- | --- |
| **Font** | Google Sans Flex is bundled and applied app-wide through the app theme. |
| **System font toggle** | A new *Use system font* switch in **Settings → UI** renders the UI with the device's default font (`Theme.DeviceDefault`) instead of Google Sans. |
| **Switches** | `SwitchCompat` → `MaterialSwitch` everywhere, including all toggles on the settings screen. |
| **Dialogs** | `AlertDialog` → `MaterialAlertDialogBuilder`. |
| **Buttons** | `Button` → `MaterialButton`. |
| **Checkboxes** | `AppCompatCheckBox` → `MaterialCheckBox`. |
| **Misc widgets** | `AppCompatTextView` / `AppCompatImageView` replaced with plain `TextView` / `ImageView` so they inherit the Material 3 theme. |

> The Material 3 dynamic color (Material You) support inherited from v2rayNG remains available
> via **Settings → UI → Dynamic colors** on Android 12+.

> [!NOTE]
> Google Sans is the bundled brand font for this app. Proprietary system-UI fonts (e.g. the
> Google Sans used by the system on Pixel devices) are not accessible to third-party apps, so the
> *Use system font* option falls back to the platform/OEM default font (Roboto on stock Android).

## Features (inherited from v2rayNG)

- Supports VMess, VLESS, Shadowsocks, Trojan, SOCKS, WireGuard and Hysteria2.
- Subscription management and QR-code import/export.
- Per-app proxy, routing rules, DNS, fragmentation, mux and more.
- VPN service backed by `hev-socks5-tunnel` or the xray-core tunnel.
- geoip / geosite rule support.

For end-user documentation see the upstream [wiki](https://github.com/2dust/v2rayNG/wiki).

## Project layout

```
.
├── V2rayNG/                 # Android application (Gradle project)
│   └── app/
│       ├── src/main/res/font/google_sans_flex.ttf
│       └── ...
├── hev-socks5-tunnel/       # git submodule – native TUN tunnel
├── AndroidLibXrayLite/      # git submodule – Go sources for libv2ray.aar
├── compile-hevtun.sh        # builds the native libhev-socks5-tunnel libraries
└── README.md
```

## Building from source

### Requirements

- JDK 17+ (the project targets JVM 17; CI builds with JDK 21)
- Android SDK: `platforms;android-37`, `build-tools;37.0.0`, `platform-tools`
- Android NDK (tested with r27c; CI uses `29.0.14206865`) for the native tunnel
- Gradle wrapper is included (Gradle 9.5.1 / AGP 9.2.1)

### Steps

1. **Clone with submodules**

   ```bash
   git clone --recurse-submodules <repo-url>
   # or, in an existing checkout:
   git submodule update --init --recursive
   ```

2. **Build the native `hev-socks5-tunnel` libraries**

   ```bash
   export NDK_HOME=$ANDROID_HOME/ndk/<ndk-version>
   bash compile-hevtun.sh
   cp -r libs V2rayNG/app/        # copies the per-ABI .so files into app/libs
   ```

3. **Provide the Xray core (`libv2ray.aar`)**

   Download `libv2ray.aar` from the
   [AndroidLibXrayLite releases](https://github.com/2dust/AndroidLibXrayLite/releases)
   (or build it yourself from the `AndroidLibXrayLite` submodule) and place it in
   `V2rayNG/app/libs/`.

4. **Point Gradle at your SDK**

   ```bash
   echo "sdk.dir=$ANDROID_HOME" > V2rayNG/local.properties
   ```

5. **Build the APK**

   ```bash
   cd V2rayNG
   ./gradlew assembleFdroidDebug      # or: assemblePlaystoreDebug / assembleRelease
   ```

   The APKs are emitted to `V2rayNG/app/build/outputs/apk/<flavor>/<buildType>/`, split per ABI
   (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) plus a universal variant.

### Build flavors

| Flavor | Application ID | Notes |
| --- | --- | --- |
| `fdroid` | `com.v2ray.ang.fdroid` | F-Droid distribution |
| `playstore` | `com.v2ray.ang` | Play Store distribution |

## Tech stack

- Kotlin, Android View system with View Binding
- Material Components for Android (Material 3 Expressive theme)
- MMKV for settings storage, OkHttp, Gson, Coroutines
- Xray core via `libv2ray.aar`, native TUN via `hev-socks5-tunnel`

## Credits

- [v2rayNG](https://github.com/2dust/v2rayNG) by 2dust — the upstream project this fork is based on.
- [Xray-core](https://github.com/XTLS/Xray-core) and [v2fly/v2ray-core](https://github.com/v2fly/v2ray-core).
- [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) by heiher.
- Google Sans Flex (bundled under its respective font license).

## License

Released under the **GNU General Public License v3.0**, same as upstream v2rayNG.
See [LICENSE](LICENSE) for the full text.
