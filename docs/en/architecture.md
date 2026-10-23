# Architecture

veil uses **four independent Go modules** compiled into a single AAR via gomobile.

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
                     Standard protocol                     olcRTC profile                OpenFlux profile              AmneziaWG profile
                     (VMess, VLESS, ...)                   (EConfigType.OLCRTC)          (EConfigType.OPENFLUX)        (EConfigType.AMNEZIAWG)
                               │                                │                              │                              │
                          internet                     SOCKS5 → 127.0.0.1:10809      SOCKS5 → 127.0.0.1:10811      SOCKS5 → 127.0.0.1:10812
                                                                │                              │                              │
                                                          olcRTC transport              OpenFlux transport            amneziawg-go device
                                                          (gomobile AAR)                (gomobile AAR)                + gVisor netstack
                                                                │                              │                              │
                                                    WebRTC/SFU server          Yandex Docs / Volga / Board / MAX / Cups / Mail.ru / Avito   UDP → AWG endpoint
                                                                │                              │                              │
                                                     remote olcRTC server        remote OpenFlux exit node          AmneziaWG server
                                                                │                              │                              │
                                                            internet                       internet                       internet
```

## Four modules, one AAR

`libv2ray.aar` is a custom build combining four Go modules:

| Module | Java package | Role |
|---|---|---|
| `github.com/2dust/AndroidLibXrayLite` | `libv2ray.*` | Xray core: routing, protocols, DNS, stats |
| `olcrtc/mobile` | `mobile.*` | olcRTC transport: WebRTC tunnel, SOCKS5 server |
| `openfluxmobile` | `openflux.*` | OpenFlux transport: a facade over the upstream `openflux-mobile` bridge (Session profiles with a classic fallback, codec negotiation, captcha cookies), exposing a local SOCKS5 server |
| `amneziawgmobile` | `amneziawg.*` | AmneziaWG: `amneziawg-go` device over a gVisor netstack, exposing a local SOCKS5 server |

No module's source code is modified. They are compiled together into a single `libgojni.so` + `classes.jar`.

## Shared process

All Xray, olcRTC, OpenFlux and AmneziaWG components run in the same Android process:

```
:RunSoLibV2RayDaemon
  ├── CoreVpnService / CoreProxyOnlyService   — entry point
  ├── OlcrtcProxyService                       — olcRTC lifecycle
  ├── OpenFluxProxyService                     — OpenFlux lifecycle
  ├── AmneziaWgManager                         — AWG lifecycle (no separate service)
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
   - Starts the OpenFlux Go client through the `openflux.*` facade over the upstream `openflux-mobile` bridge (the code path the official OpenFlux clients use): transport (document URL for Yandex Docs / Volga / Board / Cups.online / Mail.ru / Avito, token + uid for MAX, optional encryption key), preferred codec, and a cookie store in the app's files dir for solved captchas → `start()`/`waitReady()`
   - Opens a SOCKS5 server on `127.0.0.1:{port}` (default `10811`)
3. Xray core starts with a standard config
4. `CoreOutboundBuilder.toOutboundOpenflux()` creates a SOCKS5 outbound to `127.0.0.1:{port}`
5. Traffic flows: Xray → SOCKS5 → OpenFlux → pluggable transport → exit node → internet

## AmneziaWG startup

1. `CoreServiceManager.doStartCoreLoop()` detects `EConfigType.AMNEZIAWG`
2. `AmneziaWgManager.start(config)` is called:
   - Builds the native JSON config (keys, addresses, DNS, MTU, peer, AWG parameters)
   - Starts the `amneziawg-go` device over a gVisor netstack
   - Opens a SOCKS5 server on `127.0.0.1:{port}` (default `10812`) and stores the port in `localSocksPort`
3. Xray core starts with a standard config
4. `CoreOutboundBuilder.toOutboundAmneziawg()` creates a SOCKS5 outbound to `127.0.0.1:{port}`
5. Traffic flows: Xray → SOCKS5 → amneziawg-go → UDP → AWG server

The native client also exposes `Ping`, so the “real ping” action measures latency through the tunnel.

## Standard protocols (no transport sidecar)

Xray works normally — `CoreConfigManager` builds the config, outbound points directly to the remote server. No transport sidecar is started.
