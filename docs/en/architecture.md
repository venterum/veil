# Architecture

veil uses **three independent Go modules** compiled into a single AAR via gomobile.

## Flow

```
App (Android)
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
                     Standard protocol                     olcRTC profile                OpenFlux profile
                     (VMess, VLESS, ...)                   (EConfigType.OLCRTC)          (EConfigType.OPENFLUX)
                               │                                │                              │
                          internet                     SOCKS5 → 127.0.0.1:10809      SOCKS5 → 127.0.0.1:10811
                                                                │                              │
                                                          olcRTC transport              OpenFlux transport
                                                          (gomobile AAR)                (gomobile AAR)
                                                                │                              │
                                                    WebRTC/SFU server          Yandex Docs / MAX / Cups.online
                                                                │                              │
                                                     remote olcRTC server        remote OpenFlux exit node
                                                                │                              │
                                                            internet                       internet
```

## Three modules, one AAR

`libv2ray.aar` is a custom build combining three Go modules:

| Module | Java package | Role |
|---|---|---|
| `github.com/2dust/AndroidLibXrayLite` | `libv2ray.*` | Xray core: routing, protocols, DNS, stats |
| `olcrtc/mobile` | `mobile.*` | olcRTC transport: WebRTC tunnel, SOCKS5 server |
| `openfluxmobile` | `openflux.*` | OpenFlux transport: TCP tunnel with pluggable transports, SOCKS5 server |

No module's source code is modified. They are compiled together into a single `libgojni.so` + `classes.jar`.

## Shared process

All Xray, olcRTC and OpenFlux components run in the same Android process:

```
:RunSoLibV2RayDaemon
  ├── CoreVpnService / CoreProxyOnlyService   — entry point
  ├── OlcrtcProxyService                       — olcRTC lifecycle
  ├── OpenFluxProxyService                     — OpenFlux lifecycle
  ├── CoreTestService                          — testing
  └── ...                                       — other services
```

## olcRTC startup

1. `CoreServiceManager.doStartCoreLoop()` detects `EConfigType.OLCRTC`
2. `OlcrtcManager.start(config)` is called:
   - Starts the olcRTC Go client via the instance-based `mobile.Runtime` API (`Mobile.new_()` → setters → `start()`/`waitReady()`)
   - Opens a SOCKS5 server on `127.0.0.1:{port}`
3. Xray core starts with a standard config
4. `CoreOutboundBuilder.toOutboundOlcrtc()` creates a SOCKS5 outbound to `127.0.0.1:{port}`
5. Traffic flows: Xray → SOCKS5 → olcRTC → WebRTC → internet

## OpenFlux startup

1. `CoreServiceManager.doStartCoreLoop()` detects `EConfigType.OPENFLUX`
2. `OpenFluxManager.start(config)` is called:
   - Starts the OpenFlux Go client via the `openflux.*` binding (transport, document URL, MAX credentials, optional encryption key → `start()`/`waitReady()`)
   - Opens a SOCKS5 server on `127.0.0.1:{port}` (default `10811`)
3. Xray core starts with a standard config
4. `CoreOutboundBuilder.toOutboundOpenflux()` creates a SOCKS5 outbound to `127.0.0.1:{port}`
5. Traffic flows: Xray → SOCKS5 → OpenFlux → pluggable transport → exit node → internet

## Standard protocols (no olcRTC / OpenFlux)

Xray works normally — `CoreConfigManager` builds the config, outbound points directly to the remote server. Neither transport is started.
