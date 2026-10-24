# AmneziaWG

Veil запускает [AmneziaWG](https://docs.amnezia.org/documentation/amnezia-wg/) через нативный
sidecar на базе `amneziawg-go` и userspace-сетевого стека gVisor. Sidecar поднимает SOCKS5
на loopback; Xray использует его как обычный outbound, поэтому роутинг, DNS, per-app proxy и
код уведомлений/статистики работают так же, как для остальных профилей.

## Импорт

Профили AmneziaWG импортируются из:

- wg-quick `.conf` (буфер обмена, выбор файла, QR-код);
- URI `amneziawg://` (шеринг Veil / записи подписки);
- ключа AmneziaVPN `vpn://`, содержащего контейнер `amnezia-awg`.

`.conf` без ключей AmneziaWG импортируется как обычный профиль WireGuard. Профиль
считается AmneziaWG, если присутствует хотя бы один ключ обфускации (`Jc`, `S1`–`S4`,
`H1`–`H4`, `I1`–`I5`, `HeaderProtectionKey`, …).

## Поддерживаемые параметры

Стандартные ключи WireGuard:

`PrivateKey`, `Address`, `DNS`, `MTU`, `PublicKey`, `PresharedKey`, `Endpoint`,
`AllowedIPs`, `PersistentKeepalive`.

Параметры AmneziaWG:

| Ключ | Примечание |
|---|---|
| `Jc`, `Jmin`, `Jmax` | Количество junk-пакетов и диапазон их размера |
| `S1`–`S4` | Паддинг init / response / cookie / transport пакетов |
| `H1`–`H4` | Значения или диапазоны заголовков; диапазоны не должны пересекаться |
| `I1`–`I5` | CPS-пакеты подписи (`<b 0x…>`, `<r N>`, `<rc N>`, `<rd N>`, `<t>`) |
| `HeaderProtectionKey` | 32-байтовый base64-ключ; требует `S1`–`S4` ≥ 12 |
| `ContentPaddingAddition` | Диапазон паддинга transport-пакетов |
| `RekeyAfterTime`, `RekeyTimeout`, `RejectAfterTime`, `KeepaliveTimeout`, `MaxHandshakeAttempts` | Диапазоны таймингов |
| `RandomTrailers`, `DisableCookies` | `on` / `off` |

Неизвестные ключи wg-quick (`Table`, `PostUp`, …) игнорируются. Несколько пиров пока не
поддерживаются: используется одна секция `[Peer]` (повторные секции сливаются).

## Работа

1. `CoreServiceManager` запускает `AmneziaWgManager` до сборки конфига Xray.
2. Go-клиент создаёт устройство AmneziaWG поверх netstack gVisor и слушает
   `127.0.0.1:10812` (или следующий свободный порт). Порт сохраняется в профиле как
   `localSocksPort`.
3. Xray получает SOCKS-outbound на этот loopback-порт.
4. UDP-сокеты устройства передаются в `VpnService.protect()`, если это доступно.

Netstack включает IPv6 только для туннелей, у которых есть IPv6-адрес: IPv4-only туннель
не поднимает NDP/MLD, что раньше приводило к зависанию при старте. В логах native-клиент
печатает маркер сборки (`starting device build=<id>`), по нему видно, что запущен свежий
APK, а не старая установка.

Real-ping использует тот же нативный клиент и отправляет короткий HTTP-запрос через туннель.

## Ограничения

- Один пир на профиль.
- AmneziaWG — только как **основной** профиль; прокси-цепочки и membership в policy-группах
  пока не поддерживаются.
- Полные бэкапы `vpn://`, где хранятся серверные данные протокола вместо клиентского
  `.conf`, не разбираются; экспорты подключения (обычный ключ «поделиться подключением») — да.
- Принимаются конфигурации AmneziaWG 1.5, 2.0 и 3.x; встроенный рантайм — 3.1.
