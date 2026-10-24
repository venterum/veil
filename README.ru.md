<img src="docs/iko.svg" align="left" width="120" style="margin-right:20px; margin-bottom:10px;" alt="Veil logo"/>

### Veil

**Xray-клиент для Android** — с поддержкой Material 3, AmneziaWG, olcRTC и OpenFlux.

<br clear="left"/>

<p align="center">
  <a href="https://developer.android.com/about/versions/oreo"><img src="https://raw.githubusercontent.com/ziadOUA/m3-Markdown-Badges/master/badges/Android/android1.svg" alt="Android 8.0+"></a>
  <a href="https://kotlinlang.org"><img src="https://raw.githubusercontent.com/ziadOUA/m3-Markdown-Badges/master/badges/Kotlin/kotlin1.svg" alt="Kotlin"></a>
  <a href="LICENSE"><img src="https://raw.githubusercontent.com/ziadOUA/m3-Markdown-Badges/master/badges/LicenceGPLv3/licencegplv31.svg" alt="Лицензия: GPL v3"></a>
</p>

<p align="center">
  <a href="https://github.com/venterum/veil/releases"><b>Скачать APK</b></a>
  &nbsp;&middot;&nbsp;
  <a href="https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/venterum/veil">Obtainium</a>
  &nbsp;&middot;&nbsp;
  <a href="#скриншоты">Скриншоты</a>
  &nbsp;&middot;&nbsp;
  <a href="#документация">Документация</a>
  &nbsp;&middot;&nbsp;
  <a href="README.md">English</a>
</p>

---

> **Вышел Veil 1.0.0** — первый стабильный релиз: поддержка AmneziaWG, главный экран на Compose и AMOLED-тема. Подробности в [релизах](https://github.com/venterum/veil/releases) · [сообщить об ошибке](https://github.com/venterum/veil/issues).

Открытый Xray-клиент для Android. Поддерживает стандартные протоколы из Xray, **AmneziaWG**, **olcRTC** и **OpenFlux**. Совместим по конфигурации с [v2rayNG](https://github.com/2dust/v2rayNG).

## Скриншоты

<p align="center">
  <kbd>
    <img src="docs/screenshots/index.png" width="220" style="border-radius: 12px;" alt="Главный экран">
    <br>
  </kbd>
  &nbsp;&nbsp;
  <kbd>
    <img src="docs/screenshots/panel.png" width="220" style="border-radius: 12px;" alt="Панель серверов">
    <br>
  </kbd>
  &nbsp;&nbsp;
  <kbd>
    <img src="docs/screenshots/settings.png" width="220" style="border-radius: 12px;" alt="Настройки">
    <br>
  </kbd>
</p>

## Возможности

- **Протоколы** — VMess, VLESS, Shadowsocks, Trojan, SOCKS, HTTP, WireGuard и Hysteria2, а также три туннельных типа профилей: AmneziaWG, olcRTC и OpenFlux.
- **AmneziaWG** — обфускация AWG 1.5–3.x, импорт из `.conf` / `amneziawg://` / `vpn://` и нативное устройство `amneziawg-go` поверх netstack gVisor (пока только как основной профиль).
- **olcRTC** — carriers Jitsi / Telemost / WbStream; транспорты DataChannel / VP8 / SEI.
- **OpenFlux** — транспорты Yandex Docs / Volga / Board / MAX / Cups.online / Mail.ru / Avito через upstream-мост OpenFlux mobile; нужен собственный exit node.
- **Режимы** — VPN (TUN), только прокси (SOCKS5), гибридный (SOCKS5 с переключателем TUN в приложении) и root.
- **Цепочки прокси** — соединение через несколько серверов по порядку.
- **Маршрутизация** — готовые пресеты (обход RU / CN / IR), блокировка рекламы и трекеров, прокси на приложение, свои правила, загрузка GeoIP / Geosite.
- **Подписки** — импорт по URL с кастомными HTTP-заголовками и HWID, отдельные обновление и пинг для каждой подписки.
- **Импорт и экспорт** — QR-коды, буфер обмена, файлы, share-ссылки, `v2rayng://install-sub`, резервные копии.
- **Интерфейс** — Material 3 Expressive, AMOLED-тема, девять языков, виджет на главный экран. Без аналитики и телеметрии.

## Установка

Нужен Android 8.0 (API 26) или новее.

| APK | Для чего |
|---|---|
| `veil_*_arm64-v8a.apk` | Большинство современных телефонов — **рекомендуется** |
| `veil_*_armeabi-v7a.apk` | Старые 32-битные устройства |
| `veil_*_x86_64.apk` | Эмуляторы x86_64 |
| `veil_*_x86.apk` | Эмуляторы x86 |
| `veil_*_universal.apk` | Любое устройство; файл больше |

Скачать можно в [релизах](https://github.com/venterum/veil/releases). Для автообновлений добавьте репозиторий в [Obtainium](https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/venterum/veil); Veil также сам проверяет новые версии на GitHub (О приложении → Обновление).

## Документация

| Раздел | EN | RU |
|---|---|---|
| Архитектура | [architecture.md](docs/en/architecture.md) | [architecture.md](docs/ru/architecture.md) |
| Сборка | [build.md](docs/en/build.md) | [build.md](docs/ru/build.md) |
| olcRTC | [olcrtc.md](docs/en/olcrtc.md) | [olcrtc.md](docs/ru/olcrtc.md) |
| AmneziaWG | [amneziawg.md](docs/en/amneziawg.md) | [amneziawg.md](docs/ru/amneziawg.md) |

Вся документация: [docs](docs/index.md).

## Сборка из исходников

См. [Сборка](docs/ru/build.md) — JDK 17+, Android SDK 37, NDK, Go 1.27 и gomobile. Релизный AAR собирается отдельно и объединяет Xray, olcRTC, OpenFlux и AmneziaWG; стандартный AAR от upstream их не содержит.

## Поддержать проект

Если Veil оказался полезен, разработку можно поддержать в USDT:

| Сеть                 | Адрес                                        |
|----------------------|----------------------------------------------|
| **Tron** (TRC-20)            | `TSv3rM2MS7Q9Nyrc1JR43y4xRjDrZmWDSU`         |
| **BNB Smart Chain** (BEP-20) | `0x2dfCb2fd70Dd8d03D1c831CC4635Edcb9De80F45` |

Спасибо ❤️

## Правовая информация

Только для законного использования. Вы несёте ответственность за соблюдение законов своей юрисдикции и за серверы, которые используете. Автор не поддерживает и не поощряет незаконную деятельность.

## Благодарности

| Проект | Роль |
|---|---|
| [Xray-core](https://github.com/XTLS/Xray-core) / [v2ray-core](https://github.com/v2fly/v2ray-core) | Ядро |
| [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) | TUN → SOCKS5 |
| [olcRTC](https://github.com/openlibrecommunity/olcrtc) | Транспорт WebRTC |
| [OpenFlux](https://github.com/p1neappleXpress/OpenFlux) | Туннель через подключаемые транспорты |
| [amneziawg-go](https://github.com/amnezia-vpn/amneziawg-go) | Устройство AmneziaWG |
| [gVisor](https://gvisor.dev) | Userspace-сетевой стек |
| [v2rayNG](https://github.com/2dust/v2rayNG) | Основа проекта |
| [Google Sans](https://fonts.google.com/specimen/Google+Sans) | Шрифт (SIL OFL 1.1) |

## Лицензия

[GNU General Public License v3.0](LICENSE).
