# olcRTC

olcRTC (OpenLibreCommunity RTC) is an encrypted TCP-over-WebRTC tunnel. Traffic is disguised as a video call on allowed services. Inside: XChaCha20-Poly1305 encryption and smux multiplexing over WebRTC data/video channels.

## Supported providers

| Provider | Value | Notes |
|---|---|---|
| Jitsi Meet | `jitsi` | Self-hosted or public instances |
| WbStream | `wbstream` | Requires `vp8channel` or `seichannel` (`datachannel` does not work in guest mode) |
| Yandex Telemost | `telemost` | |

## Transports

| Transport | Description |
|---|---|
| `datachannel` | WebRTC DataChannel. Simple, fast. Recommended for Jitsi |
| `vp8channel` | VP8 video stream. Required for WbStream |
| `seichannel` | SEI insertion into H264 stream |
| `videochannel` | Full video stream with resolution/bitrate tuning |

## URI format

```
olcrtc://<Auth>?<Transport><params>@<RoomID>#<EncryptionKey>$<Comment>
```

### Fields

| Part | Description |
|---|---|
| `<Auth>` | Provider: `jitsi`, `wbstream`, `telemost` |
| `<Transport>` | Transport: `datachannel`, `vp8channel`, `seichannel`, `videochannel` |
| `<params>` | `key=value&key=value` in angle brackets. Optional |
| `<RoomID>` | Room identifier or full URL (for Jitsi: `https://host/room`) |
| `<EncryptionKey>` | 64 hex characters (32 bytes). Generate: `openssl rand -hex 32` |
| `<Comment>` | Free-form label. Optional |

### Transport parameters

#### `vp8channel`

| Key | Description |
|---|---|
| `fps` | Video stream FPS |
| `batchSize` | Frames per tick |

#### `seichannel`

| Key | Description |
|---|---|
| `fps` | FPS |
| `batchSize` | Frames per tick |
| `frag` | Fragment size (bytes) |
| `ack-ms` | ACK timeout (ms) |

#### `videochannel`

| Key | Description |
|---|---|
| `video-w` | Width (px) |
| `video-h` | Height (px) |
| `video-fps` | FPS |
| `video-bitrate` | Bitrate, e.g. `5000k` |
| `video-codec` | `qrcode` or `tile` |
| `video-qr-size` | QR fragment size (bytes) |
| `video-qr-recovery` | Error correction: `low` / `medium` / `high` / `highest` |
| `video-tile-module` | Tile size (1..270 px) |
| `video-tile-rs` | RS parity (0..200%) |

## Examples

### Jitsi + DataChannel (recommended)

```
olcrtc://jitsi?datachannel@https://meet.example.com/myroom#a3482e88686a4a58812699c9df64b7bfa3482e88686a4a58812699c9df64b7bf$My room
```

### WbStream + VP8

```
olcrtc://wbstream?vp8channel<fps=60&batchSize=64>@room-01#a3482e88686a4a58812699c9df64b7bfa3482e88686a4a58812699c9df64b7bf$olc free sub
```

## Configuration in app

From the **"+" → Add [olcRTC]** menu:

- **Address / Port** — shows `127.0.0.1:10809` (SOCKS port for Xray loopback)
- **Carrier** — provider selection
- **Transport** — transport selection
- **Server URL** — custom Jitsi/Telemost host
- **Room ID** — room identifier
- **Client ID** — device UUID (auto-generated if empty)
- **Encryption key** — 64 hex characters
- **Engine** — transport parameters as `key=value&key=value`

Recommended start: `jitsi + datachannel`.
