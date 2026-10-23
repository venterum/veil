# AmneziaWG

Veil runs [AmneziaWG](https://docs.amnezia.org/documentation/amnezia-wg/) through a native
sidecar built on `amneziawg-go` and a gVisor userspace network stack. The sidecar serves
SOCKS5 on loopback; Xray uses it as a regular outbound, so routing, DNS, per-app proxy and
the notification/statistics code work exactly as with other profiles.

## Import

AmneziaWG profiles can be imported from:

- a wg-quick `.conf` file (clipboard, file picker, QR code);
- an `amneziawg://` URI (Veil sharing / subscription entries);
- an AmneziaVPN `vpn://` key that contains an `amnezia-awg` container.

A `.conf` without any AmneziaWG keys is imported as a regular WireGuard profile. A profile
is classified as AmneziaWG when at least one obfuscation key (`Jc`, `S1`–`S4`, `H1`–`H4`,
`I1`–`I5`, `HeaderProtectionKey`, …) is present.

## Supported parameters

Standard WireGuard keys:

`PrivateKey`, `Address`, `DNS`, `MTU`, `PublicKey`, `PresharedKey`, `Endpoint`,
`AllowedIPs`, `PersistentKeepalive`.

AmneziaWG parameters:

| Key | Notes |
|---|---|
| `Jc`, `Jmin`, `Jmax` | Junk packet count and size range |
| `S1`–`S4` | Init / response / cookie / transport padding |
| `H1`–`H4` | Header values or ranges; ranges must not overlap |
| `I1`–`I5` | CPS signature packets (`<b 0x…>`, `<r N>`, `<rc N>`, `<rd N>`, `<t>`) |
| `HeaderProtectionKey` | 32-byte base64 key; requires `S1`–`S4` ≥ 12 |
| `ContentPaddingAddition` | Transport padding range |
| `RekeyAfterTime`, `RekeyTimeout`, `RejectAfterTime`, `KeepaliveTimeout`, `MaxHandshakeAttempts` | Timing ranges |
| `RandomTrailers`, `DisableCookies` | `on` / `off` |

Unknown wg-quick keys (`Table`, `PostUp`, …) are ignored. Multiple peers are not supported
yet; the first `[Peer]` section is imported.

## Runtime

1. `CoreServiceManager` starts `AmneziaWgManager` before building the Xray config.
2. The Go client creates an AmneziaWG device over a gVisor netstack and listens on
   `127.0.0.1:10812` (or the next free port). The port is stored in the profile as
   `localSocksPort`.
3. Xray receives a SOCKS outbound pointing at that loopback port.
4. The device's UDP sockets are passed to `VpnService.protect()` when available.

Real-ping uses the same native client and issues a short HTTP request through the tunnel.

## Limitations

- One peer per profile.
- AmneziaWG as a **primary** profile only; proxy-chain and policy-group membership is not
  supported yet.
- `vpn://` full-access backups that store server-side protocol data instead of a client
  `.conf` are not parsed; connection exports (the normal “share connection” key) are.
- AmneziaWG 1.5, 2.0 and 3.x configurations are accepted; the embedded runtime is 3.1.
