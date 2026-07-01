# Сборка

## Требования

- JDK 17+
- Android SDK: `platforms;android-37`, `build-tools;37.0.0`, `platform-tools`
- Android NDK (тестировалось с r27c)
- Go 1.26+, gomobile (`go install golang.org/x/mobile/cmd/gomobile@latest && gomobile init`)
- mage (`go install github.com/magefile/mage@latest`)

## Подготовка

```bash
git clone --recurse-submodules <repo-url>
git submodule update --init --recursive
```

Репозиторий `olcrtc` должен быть рядом с проектом:

```
../olcrtc/
```

## Шаг 1: hev-socks5-tunnel

```bash
export NDK_HOME=$ANDROID_HOME/ndk/<ndk-version>
bash compile-hevtun.sh
cp -r libs veil/app/
```

## Шаг 2: libv2ray.aar (Xray + olcRTC)

Сборка объединённого AAR из двух Go-модулей с выравниванием ELF-сегментов под 16 KB:

```bash
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/<ndk-version>
bash compile-libv2ray.sh
```

Скрипт использует `gomobile bind` с флагом `-Wl,-z,max-page-size=16384`, поэтому результирующий `libgojni.so` будет совместим с устройствами на 16 KB страницах памяти.

Либо скачать стандартный `libv2ray.aar` из [релизов AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite/releases) — olcRTC работать **не будет** (в стандартном AAR нет пакета `mobile.*`).

## Шаг 3: APK

```bash
echo "sdk.dir=$ANDROID_HOME" > veil/local.properties
cd veil
./gradlew assembleDebug
```

APK в `veil/app/build/outputs/apk/debug/`.

## Файлы на выходе

```
veil/app/libs/
  ├── libv2ray.aar          # Xray core + olcRTC (gomobile)
  ├── arm64-v8a/libhev-socks5-tunnel.so
  ├── armeabi-v7a/libhev-socks5-tunnel.so
  ├── x86/libhev-socks5-tunnel.so
  └── x86_64/libhev-socks5-tunnel.so
```

## Примечание

- Стандартный AAR от 2dust **не содержит** `mobile.*` — olcRTC не будет работать без кастомной сборки.
