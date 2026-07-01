# Architecture

veil uses **two independent Go modules** compiled into a single AAR via gomobile.

## Flow

```
App (Android)
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
                     Standard protocol                     olcRTC profile
                     (VMess, VLESS, ...)                   (EConfigType.OLCRTC)
                               │                                │
                          internet                     SOCKS5 → 127.0.0.1:10809
                                                                │
                                                          olcRTC transport
                                                          (gomobile AAR)
                                                                │
                                                    WebRTC/SFU server
                                                                │
                                                     remote olcRTC server
                                                                │
                                                           internet
```

## Two modules, one AAR

`libv2ray.aar` is a custom build combining two Go modules:

| Module | Java package | Role |
|---|---|---|
| `github.com/2dust/AndroidLibXrayLite` | `libv2ray.*` | Xray core: routing, protocols, DNS, stats |
| `olcrtc/mobile` | `mobile.*` | olcRTC transport: WebRTC tunnel, SOCKS5 server |

Neither module's source code is modified. They are compiled together into a single `libgojni.so` + `classes.jar`.

## Shared process

All Xray and olcRTC components run in the same Android process:

```
:RunSoLibV2RayDaemon
  ├── CoreVpnService / CoreProxyOnlyService   — entry point
  ├── OlcrtcProxyService                       — olcRTC lifecycle
  ├── CoreTestService                          — testing
  └── ...                                       — other services
```

## olcRTC startup

1. `CoreServiceManager.doStartCoreLoop()` detects `EConfigType.OLCRTC`
2. `OlcrtcManager.start(config)` is called:
   - Starts the olcRTC Go process via `Mobile.startWithTransport()`
   - Opens a SOCKS5 server on `127.0.0.1:{port}`
3. Xray core starts with a standard config
4. `CoreOutboundBuilder.toOutboundOlcrtc()` creates a SOCKS5 outbound to `127.0.0.1:{port}`
5. Traffic flows: Xray → SOCKS5 → olcRTC → WebRTC → internet

## Standard protocols (no olcRTC)

Xray works normally — `CoreConfigManager` builds the config, outbound points directly to the remote server. olcRTC is not started.
