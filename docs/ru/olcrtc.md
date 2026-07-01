# olcRTC

olcRTC (OpenLibreCommunity RTC) — зашифрованный TCP-over-WebRTC туннель. Трафик маскируется под видеозвонок на разрешённых сервисах. Внутри — XChaCha20-Poly1305 и smux-мультиплексирование поверх WebRTC каналов.

## Провайдеры

| Провайдер | Значение | Примечание |
|---|---|---|
| Jitsi Meet | `jitsi` | Self-hosted или публичные инстансы |
| WbStream | `wbstream` | Требуется `vp8channel` или `seichannel` (`datachannel` не работает в guest-режиме) |
| Yandex Telemost | `telemost` | |

## Транспорты

| Транспорт | Описание |
|---|---|
| `datachannel` | WebRTC DataChannel. Простой, быстрый. Рекомендуется для Jitsi |
| `vp8channel` | Видеопоток VP8. Требуется для WbStream |
| `seichannel` | SEI-вставки в H264 |
| `videochannel` | Полноценный видеопоток с настройками разрешения/битрейта |

## URI-формат

```
olcrtc://<Auth>?<Transport><параметры>@<RoomID>#<EncryptionKey>$<Комментарий>
```

### Поля

| Часть | Описание |
|---|---|
| `<Auth>` | Провайдер: `jitsi`, `wbstream`, `telemost` |
| `<Transport>` | Транспорт: `datachannel`, `vp8channel`, `seichannel`, `videochannel` |
| `<параметры>` | `key=value&key=value` в угловых скобках. Опционально |
| `<RoomID>` | ID комнаты или полный URL (для Jitsi: `https://host/room`) |
| `<EncryptionKey>` | 64 hex-символа (32 байта). Генерация: `openssl rand -hex 32` |
| `<Комментарий>` | Произвольная метка. Опционально |

### Параметры транспортов

#### `vp8channel`

| Ключ | Описание |
|---|---|
| `fps` | FPS видеопотока |
| `batchSize` | Кадров за тик |

#### `seichannel`

| Ключ | Описание |
|---|---|
| `fps` | FPS |
| `batchSize` | Кадров за тик |
| `frag` | Размер фрагмента (байт) |
| `ack-ms` | Таймаут ACK (мс) |

#### `videochannel`

| Ключ | Описание |
|---|---|
| `video-w` | Ширина (пикс) |
| `video-h` | Высота (пикс) |
| `video-fps` | FPS |
| `video-bitrate` | Битрейт, например `5000k` |
| `video-codec` | `qrcode` или `tile` |
| `video-qr-size` | Размер QR-фрагмента (байт) |
| `video-qr-recovery` | Коррекция: `low` / `medium` / `high` / `highest` |
| `video-tile-module` | Размер тайла (1..270 пикс) |
| `video-tile-rs` | RS-избыточность (0..200%) |

## Примеры

### Jitsi + DataChannel (рекомендуется)

```
olcrtc://jitsi?datachannel@https://meet.example.com/myroom#a3482e88686a4a58812699c9df64b7bfa3482e88686a4a58812699c9df64b7bf$Моя комната
```

### WbStream + VP8

```
olcrtc://wbstream?vp8channel<fps=60&batchSize=64>@room-01#a3482e88686a4a58812699c9df64b7bfa3482e88686a4a58812699c9df64b7bf$olc free sub
```

## Настройка в приложении

Меню **"+" → Add [olcRTC]**:

- **Address / Port** — `127.0.0.1:10809` (SOCKS-порт для loopback Xray)
- **Carrier** — выбор провайдера
- **Transport** — выбор транспорта
- **Server URL** — кастомный хост Jitsi/Telemost
- **Room ID** — идентификатор комнаты
- **Client ID** — UUID устройства (автогенерация, если пусто)
- **Encryption key** — 64 hex-символа
- **Engine** — параметры транспорта `key=value&key=value`

Рекомендуемый старт: `jitsi + datachannel`.
