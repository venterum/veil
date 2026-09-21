# Building

## Requirements

- JDK 17+
- Android SDK: `platforms;android-37`, `build-tools;37.0.0`, `platform-tools`
- Android NDK (tested with r27c)
- Go 1.26.4+ (CI builds with 1.27.x), gomobile (`go install golang.org/x/mobile/cmd/gomobile@latest && gomobile init`)
- mage (`go install github.com/magefile/mage@latest`)

## Preparation

```bash
git clone --recurse-submodules https://github.com/venterum/veil
```

## Step 1: hev-socks5-tunnel

```bash
export NDK_HOME=$ANDROID_HOME/ndk/<ndk-version>
bash compile-hevtun.sh
cp -r libs veil/app/
```

## Step 2: libv2ray.aar (Xray + olcRTC + OpenFlux combined)

Build the combined AAR from three Go modules with ELF segments aligned to 16 KB:

```bash
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/<ndk-version>
bash compile-libv2ray.sh
```

The script uses `gomobile bind` with `-Wl,-z,max-page-size=16384`, so the resulting `libgojni.so` is compatible with 16 KB page-size devices.

Alternatively, download the standard `libv2ray.aar` from [AndroidLibXrayLite releases](https://github.com/2dust/AndroidLibXrayLite/releases) — olcRTC and OpenFlux will **not work** because the standard AAR does not contain the `mobile.*` / `openflux.*` packages.

## Step 3: APK

```bash
echo "sdk.dir=$ANDROID_HOME" > veil/local.properties
cd veil
./gradlew assembleDebug
```

APKs are at `veil/app/build/outputs/apk/debug/`.

## Output layout

```
veil/app/libs/
  ├── libv2ray.aar          # Xray core + olcRTC + OpenFlux (gomobile)
  ├── arm64-v8a/libhev-socks5-tunnel.so
  ├── armeabi-v7a/libhev-socks5-tunnel.so
  ├── x86/libhev-socks5-tunnel.so
  └── x86_64/libhev-socks5-tunnel.so
```

## Note

- The standard upstream AAR from 2dust **does not contain** `mobile.*` / `openflux.*` — olcRTC and OpenFlux will not work without the custom build.
- `olcrtc/` and `OpenFlux/` are git submodules with their own history; the `openfluxmobile/` gomobile binding lives in this repository.
