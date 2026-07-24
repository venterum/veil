<p align="center">
  <img src="docs/iko.svg" width="52" height="52" alt="Veil icon" valign="middle"/>&nbsp;&nbsp;<strong style="font-size:2em">Veil</strong>
</p>

<p align="center">
  <strong>Клиент V2Ray / Xray для Android — Material 3 · туннель olcRTC · три режима подключения</strong>
</p>

<p align="center">
  <a href="https://developer.android.com/about/versions/nougat"><img src="https://raw.githubusercontent.com/ziadOUA/m3-Markdown-Badges/master/badges/Android/android1.svg" alt="Android"/></a>
  <a href="https://kotlinlang.org"><img src="https://raw.githubusercontent.com/ziadOUA/m3-Markdown-Badges/master/badges/Kotlin/kotlin1.svg" alt="Kotlin"/></a>
  <a href="LICENSE"><img src="https://raw.githubusercontent.com/ziadOUA/m3-Markdown-Badges/master/badges/LicenceGPLv3/licencegplv31.svg" alt="Лицензия: GPL v3"/></a>
</p>

> [!WARNING]
> **Ранняя разработка.** Возможны баги, вылеты и ломающие изменения. Используйте на свой страх и риск.

Основан на [v2rayNG](https://github.com/2dust/v2rayNG) от 2dust.  
📖 [English README](README.md)

---

## ⚖️ Правовая оговорка

> [!CAUTION]
> **Прочтите перед использованием.**

Приложение предназначено только для легального использования. Скачивая, устанавливая или запуская Veil, вы принимаете следующее:

1. **Соответствие законодательству.** Законы об использовании VPN-клиентов и прокси-инструментов сильно различаются от страны к стране. Вы сами несёте ответственность за то, чтобы ваше использование приложения не нарушало местное, национальное и международное законодательство.

2. **Сторонние серверы.** Авторы не несут ответственности за политику, практики или правовой статус сторонних VPN/прокси-серверов, к которым вы подключаетесь через Veil. Выбор серверов и весь проходящий через них трафик — полностью на вашей совести.

> Автор Veil не поощряет никакую незаконную деятельность. **Используйте ответственно.**

---

## Загрузка

Готовые APK выложены на [странице релизов](https://github.com/venterum/veil/releases).

---

## Что умеет

| Категория | Описание |
|---|---|
| **Протоколы** | VMess, VLESS, Shadowsocks, Trojan, SOCKS, WireGuard, Hysteria2 |
| **Туннель olcRTC** | Зашифрованный TCP-over-WebRTC; носители: Jitsi, Telemost, WbStream; транспорты: DataChannel, VP8, SEI |
| **Режимы подключения** | VPN (весь трафик через TUN), Proxy (только SOCKS5, без TUN), Hybrid (SOCKS5 + опциональный TUN-переключатель прямо в приложении) |
| **Подписки** | Управление, импорт и экспорт через QR-код |
| **Маршрутизация** | Прокси по приложению, правила, DNS, geoip/geosite |
| **Дополнительно** | Фрагментация, мультиплексирование (mux), раздельное туннелирование |
| **VPN-бэкенд** | `hev-socks5-tunnel` или встроенный туннель xray-core |
| **Интерфейс** | Material 3, шрифт Google Sans Flex, виджет на рабочем столе |

---

## Скриншоты

| Главный экран | Панель серверов | Настройки | olcRTC + детали |
|:---:|:---:|:---:|:---:|
| ![](docs/screenshots/index.png) | ![](docs/screenshots/panel.png) | ![](docs/screenshots/settings.png) | ![](docs/screenshots/olcrtc+details.png) |

---

## Как это устроено

```text
Трафик приложения
      ↓
VPN / Proxy-сервис Android
      ↓
Xray core  (маршрутизация · DNS · mux · фрагментация)
      ↓
SOCKS5 → 127.0.0.1:{порт}      ← только для olcRTC-профилей; обычные протоколы идут напрямую
      ↓
Go-транспорт olcRTC  (WebRTC → SFU-сервер → удалённый olcRTC → интернет)
```

### Ядро

Xray core и транспорт olcRTC собираются в **единый `libv2ray.aar`** через gomobile:

| Go-модуль | Пакет | Роль |
|---|---|---|
| `github.com/2dust/AndroidLibXrayLite` | `libv2ray.*` | Xray core (маршрутизация, протоколы, DNS) |
| `olcrtc/mobile` | `mobile.*` | WebRTC-транспорт olcRTC (SOCKS5-сервер) |

Оба модуля используются без изменений. Они работают в одном процессе (`:RunSoLibV2RayDaemon`) и общаются через loopback SOCKS5. Для стандартных протоколов Xray подключается к удалённому серверу напрямую; для olcRTC-профилей — гонит трафик через локальный SOCKS5-прокси olcRTC, который уже тащит его по WebRTC.

---

## Структура репозитория

```
.
├── veil/                     # Android-приложение (Gradle)
│   └── app/
│       ├── src/main/java/com/v2ray/ang/core/OlcrtcManager.kt
│       ├── src/main/java/com/v2ray/ang/fmt/OlcrtcFmt.kt
│       ├── src/main/java/com/v2ray/ang/ui/OlcrtcActivity.kt
│       └── ...
├── olcrtc/                  # git-подмодуль — Go-транспорт olcRTC
├── hev-socks5-tunnel/       # git-подмодуль — нативный TUN-туннель
├── AndroidLibXrayLite/      # git-подмодуль — Go-исходники привязок Xray core
├── compile-hevtun.sh        # сборка нативных библиотек libhev-socks5-tunnel
├── compile-libv2ray.sh      # сборка libv2ray.aar (Xray + olcRTC)
└── README.md
```

---

## Сборка из исходников

### Что нужно

| Инструмент | Версия |
|---|---|
| JDK | 17+ |
| Android SDK | `platforms;android-37`, `build-tools;37.0.0`, `platform-tools` |
| Android NDK | нужен для нативного TUN-туннеля |
| Go + gomobile | Go 1.26+; `go install golang.org/x/mobile/cmd/gomobile@latest && gomobile init` |

### Пошагово

1. **Клонировать** вместе с подмодулями

   ```bash
   git clone --recurse-submodules https://github.com/venterum/veil
   ```

2. **Собрать нативные библиотеки `hev-socks5-tunnel`**

   ```bash
   export NDK_HOME=$ANDROID_HOME/ndk/<версия-ndk>
   bash compile-hevtun.sh
   cp -r libs veil/app/
   ```

3. **Собрать `libv2ray.aar`** (Xray core + olcRTC в одном файле)

   ```bash
   export ANDROID_HOME=/path/to/android-sdk
   export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/<версия-ndk>
   bash compile-libv2ray.sh
   ```

   В результирующем AAR будет:
   - `libv2ray.*` — привязки Xray core из `AndroidLibXrayLite`
   - `mobile.*` — Go-транспорт olcRTC через gomobile
   - `libgojni.so` — нативный Go-бинарник для всех ABI (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`)
   - `geoip.dat`, `geosite.dat` — правила маршрутизации

4. **Собрать APK**

   ```bash
   echo "sdk.dir=$ANDROID_HOME" > veil/local.properties
   cd veil
   ./gradlew assembleDebug
   ```

   APK будут в `veil/app/build/outputs/apk/debug/`, по одному на каждый ABI.

---

## Переезд с v2rayNG

Veil — форк [v2rayNG](https://github.com/2dust/v2rayNG) с тем же форматом конфигурации, так что всё переносится без лишних движений *(доступно с версии 2.0.x)*:

1. Открыть v2rayNG → боковое меню → **Резервное копирование и восстановление** → **Создать резервную копию** → **Локально**
2. Перекинуть файлы конфигурации на нужное устройство
3. Открыть Veil → боковое меню → **Резервное копирование и восстановление** → **Восстановить конфигурацию** → **Локально**, выбрать файл

Стандартные протоколы (VMess, VLESS, Shadowsocks, Trojan, SOCKS, WireGuard, Hysteria2), подписки и настройки — полностью совместимы. olcRTC-профили работают только в Veil.

---

## Документация

Полная документация (EN / RU) находится в папке [docs](docs/index.md).

---

## Благодарности

| Проект | Автор | За что |
|---|---|---|
| [v2rayNG](https://github.com/2dust/v2rayNG) | 2dust | Апстрим, на котором основан форк |
| [Xray-core](https://github.com/XTLS/Xray-core) / [v2ray-core](https://github.com/v2fly/v2ray-core) | XTLS / v2fly | Основной прокси-движок |
| [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) | heiher | Нативный туннель TUN→SOCKS5 |
| [olcRTC](https://github.com/openlibrecommunity/olcrtc) | openlibrecommunity | Зашифрованный транспорт на WebRTC |
| [Google Sans / Google Sans Flex](https://fonts.google.com/specimen/Google+Sans) | Google | Шрифт (SIL OFL 1.1) |

---

## Лицензия

**GNU General Public License v3.0** — полный текст в файле [LICENSE](LICENSE).
