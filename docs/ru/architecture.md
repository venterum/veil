# Архитектура

veil использует **три независимых Go-модуля**, скомпилированных в один AAR через gomobile.

## Схема

```
Приложение (Android)
     │
     ├── VPN service (VpnService) ── TUN ── hev-socks5-tunnel ──┐
     └── Proxy service ─────────────────────────────────────────┤
                                                                │
                                                          Xray core
                                                     (libv2ray.aar)
                                                     routing, DNS, mux,
                                                     per-app proxy, geoip
                                                                │
                               ┌────────────────────────────────┼──────────────────────────────┐
                               │                                │                              │
                     Стандартный протокол                  olcRTC профиль                OpenFlux профиль
                     (VMess, VLESS, ...)                   (EConfigType.OLCRTC)          (EConfigType.OPENFLUX)
                               │                                │                              │
                          интернет                     SOCKS5 → 127.0.0.1:10809      SOCKS5 → 127.0.0.1:10811
                                                                │                              │
                                                          olcRTC transport              OpenFlux transport
                                                          (gomobile AAR)                (gomobile AAR)
                                                                │                              │
                                                    WebRTC/SFU сервер          Yandex Docs / MAX / Cups.online
                                                                │                              │
                                                     удалённый olcRTC сервер      удалённый OpenFlux exit node
                                                                │                              │
                                                           интернет                       интернет
```

## Три модуля в одном AAR

`libv2ray.aar` — кастомная сборка, объединяющая три Go-модуля:

| Модуль                                | Пакет в AAR  | Назначение                                                        |
| ------------------------------------- | ------------ | ----------------------------------------------------------------- |
| `github.com/2dust/AndroidLibXrayLite` | `libv2ray.*` | Ядро Xray: роутинг, протоколы, DNS, статистика                    |
| `olcrtc/mobile`                       | `mobile.*`   | Транспорт olcRTC: WebRTC-туннель, SOCKS5-сервер                   |
| `openfluxmobile`                      | `openflux.*` | Транспорт OpenFlux: TCP-туннель со сменными транспортами, SOCKS5  |

Исходники модулей **не модифицированы**. Они компилируются вместе в единый `libgojni.so` и `classes.jar`.

## Общий процесс

Все компоненты Xray, olcRTC и OpenFlux работают в **одном процессе** Android:

```
:RunSoLibV2RayDaemon
  ├── CoreVpnService / CoreProxyOnlyService   — точка входа
  ├── OlcrtcProxyService                       — управление olcRTC
  ├── OpenFluxProxyService                     — управление OpenFlux
  ├── CoreTestService                          — тестирование
  └── ...                                       — остальные сервисы
```

## Запуск olcRTC

1. `CoreServiceManager.doStartCoreLoop()` определяет `EConfigType.OLCRTC`
2. Запускается `OlcrtcManager.start(config)`:
   - Запускает Go-клиент olcRTC через instance-based API `mobile.Runtime` (`Mobile.new_()` → сеттеры → `start()`/`waitReady()`)
   - Открывает SOCKS5-сервер на `127.0.0.1:{port}`
3. Xray core запускается со стандартным конфигом
4. `CoreOutboundBuilder.toOutboundOlcrtc()` создаёт SOCKS5 outbound на `127.0.0.1:{port}`
5. Трафик: Xray → SOCKS5 → olcRTC → WebRTC → интернет

## Запуск OpenFlux

1. `CoreServiceManager.doStartCoreLoop()` определяет `EConfigType.OPENFLUX`
2. Запускается `OpenFluxManager.start(config)`:
   - Запускает Go-клиент OpenFlux через биндинг `openflux.*` (транспорт, URL документа, креды MAX, опциональный ключ шифрования → `start()`/`waitReady()`)
   - Открывает SOCKS5-сервер на `127.0.0.1:{port}` (по умолчанию `10811`)
3. Xray core запускается со стандартным конфигом
4. `CoreOutboundBuilder.toOutboundOpenflux()` создаёт SOCKS5 outbound на `127.0.0.1:{port}`
5. Трафик: Xray → SOCKS5 → OpenFlux → сменный транспорт → exit node → интернет

## Стандартные протоколы (без olcRTC / OpenFlux)

Xray работает штатно — `CoreConfigManager` строит конфиг, outbound указывает напрямую на удалённый сервер. Ни один из транспортов не запускается.
