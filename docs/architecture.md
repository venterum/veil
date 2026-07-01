# Архитектура

veil использует **два независимых Go-модуля**, скомпилированных в один AAR через gomobile.

## Общая схема

```
Приложение (Android)
     │
     ├── VPN service (VpnService) ── TUN ── hev-socks5-tunnel ──┐
     └── Proxy service              ─────────────────────────────┤
                                                                │
                                                          Xray core
                                                     (libv2ray.aar)
                                                     routing, DNS, mux,
                                                     per-app proxy, geoip
                                                                │
                               ┌────────────────────────────────┤
                               │                                │
                     Стандартный протокол                  olcRTC профиль
                     (VMess, VLESS, ...)                   (EConfigType.OLCRTC)
                               │                                │
                          интернет                     SOCKS5 → 127.0.0.1:10809
                                                                │
                                                          olcRTC transport
                                                          (gomobile AAR)
                                                                │
                                                    WebRTC/SFU сервер
                                                                │
                                                     удалённый olcRTC сервер
                                                                │
                                                           интернет
```

## Два модуля в одном AAR

`libv2ray.aar` — это кастомная сборка, объединяющая два Go-модуля:

| Модуль | Пакет в AAR | Назначение |
|---|---|---|
| `github.com/2dust/AndroidLibXrayLite` | `libv2ray.*` | Ядро Xray: роутинг, протоколы, DNS, статистика |
| `olcrtc/mobile` | `mobile.*` | Транспорт olcRTC: WebRTC-туннель, SOCKS5-сервер |

Исходники обоих модулей **не модифицированы**. Они компилируются вместе в единый `libgojni.so` и `classes.jar`.

## Процессорный общий процесс

Все компоненты Xray и olcRTC работают в **одном процессе** Android:

```
:RunSoLibV2RayDaemon
  ├── CoreVpnService / CoreProxyOnlyService   — точка входа
  ├── OlcrtcProxyService                       — управление olcRTC
  ├── CoreTestService                          — тестирование
  └── ...                                       — остальные сервисы
```

## Как работает olcRTC

1. `CoreServiceManager.doStartCoreLoop()` определяет `EConfigType.OLCRTC`
2. Запускается `OlcrtcManager.start(config)`:
   - Поднимается Go-процесс olcRTC через `Mobile.startWithTransport()`
   - Процесс открывает SOCKS5-сервер на `127.0.0.1:{port}`
3. Xray core запускается со стандартным конфигом
4. `CoreOutboundBuilder.toOutboundOlcrtc()` создаёт SOCKS5 outbound на `127.0.0.1:{port}`
5. Весь трафик идёт: Xray → SOCKS5 → olcRTC → WebRTC → интернет

## Стандартные протоколы (без olcRTC)

Xray работает штатно: конфиг строится `CoreConfigManager`, outbound указывает напрямую на удалённый сервер. olcRTC не запускается.
