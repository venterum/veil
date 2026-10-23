package amneziawg

import (
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/netip"
	"strconv"
	"strings"
	"time"
)

const (
	defaultMTU = 1420
	maxMTU     = 65535
	minMTU     = 576
)

// Config is the JSON document exchanged with the Android app. Keys are
// base64-encoded like a wg-quick configuration; hex is accepted as well.
type Config struct {
	PrivateKey string       `json:"privateKey"`
	Addresses  []string     `json:"addresses"`
	DNS        []string     `json:"dns"`
	MTU        int          `json:"mtu"`
	Peer       *PeerConfig  `json:"peer"`
	AWG        *Obfuscation `json:"awg"`
}

// PeerConfig describes the single WireGuard peer this client connects to.
type PeerConfig struct {
	PublicKey           string   `json:"publicKey"`
	PreSharedKey        string   `json:"preSharedKey"`
	Endpoint            string   `json:"endpoint"`
	AllowedIPs          []string `json:"allowedIps"`
	PersistentKeepalive string   `json:"persistentKeepalive"`
}

// Obfuscation carries the AmneziaWG-only parameters. Nil/empty values leave
// the corresponding UAPI line out, which keeps the device on standard
// WireGuard behaviour for that mechanism.
type Obfuscation struct {
	Jc                     *int    `json:"jc"`
	Jmin                   *int    `json:"jmin"`
	Jmax                   *int    `json:"jmax"`
	S1                     *int    `json:"s1"`
	S2                     *int    `json:"s2"`
	S3                     *int    `json:"s3"`
	S4                     *int    `json:"s4"`
	H1                     *string `json:"h1"`
	H2                     *string `json:"h2"`
	H3                     *string `json:"h3"`
	H4                     *string `json:"h4"`
	I1                     *string `json:"i1"`
	I2                     *string `json:"i2"`
	I3                     *string `json:"i3"`
	I4                     *string `json:"i4"`
	I5                     *string `json:"i5"`
	HeaderProtectionKey    *string `json:"headerProtectionKey"`
	ContentPaddingAddition *string `json:"contentPaddingAddition"`
	RekeyAfterTime         *string `json:"rekeyAfterTime"`
	RekeyTimeout           *string `json:"rekeyTimeout"`
	RejectAfterTime        *string `json:"rejectAfterTime"`
	KeepaliveTimeout       *string `json:"keepaliveTimeout"`
	MaxHandshakeAttempts   *string `json:"maxHandshakeAttempts"`
	RandomTrailers         *bool   `json:"randomTrailers"`
	DisableCookies         *bool   `json:"disableCookies"`
}

func parseConfig(raw string) (*Config, error) {
	var cfg Config
	if err := json.Unmarshal([]byte(raw), &cfg); err != nil {
		return nil, fmt.Errorf("invalid amneziawg config: %w", err)
	}
	if err := cfg.validate(); err != nil {
		return nil, err
	}
	return &cfg, nil
}

func (c *Config) validate() error {
	if strings.TrimSpace(c.PrivateKey) == "" {
		return errors.New("private key is empty")
	}
	if _, err := normalizeKey(c.PrivateKey); err != nil {
		return fmt.Errorf("private key: %w", err)
	}
	if len(c.Addresses) == 0 {
		return errors.New("at least one local address is required")
	}
	for _, address := range c.Addresses {
		if _, err := parsePrefix(address); err != nil {
			return fmt.Errorf("address %q: %w", address, err)
		}
	}
	if c.Peer == nil {
		return errors.New("peer is required")
	}
	if strings.TrimSpace(c.Peer.PublicKey) == "" {
		return errors.New("peer public key is empty")
	}
	if _, err := normalizeKey(c.Peer.PublicKey); err != nil {
		return fmt.Errorf("peer public key: %w", err)
	}
	if strings.TrimSpace(c.Peer.PreSharedKey) != "" {
		if _, err := normalizeKey(c.Peer.PreSharedKey); err != nil {
			return fmt.Errorf("preshared key: %w", err)
		}
	}
	if strings.TrimSpace(c.Peer.Endpoint) == "" {
		return errors.New("peer endpoint is empty")
	}
	if c.MTU == 0 {
		c.MTU = defaultMTU
	}
	if c.MTU < minMTU || c.MTU > maxMTU {
		return fmt.Errorf("mtu %d is out of range", c.MTU)
	}
	if c.AWG != nil {
		if err := c.AWG.validate(); err != nil {
			return err
		}
	}
	return nil
}

func (o *Obfuscation) validate() error {
	for name, value := range map[string]*int{
		"jc": o.Jc, "jmin": o.Jmin, "jmax": o.Jmax,
		"s1": o.S1, "s2": o.S2, "s3": o.S3, "s4": o.S4,
	} {
		if value == nil {
			continue
		}
		if *value < 0 || *value > 65535 {
			return fmt.Errorf("%s %d is out of range", name, *value)
		}
	}
	if o.Jmin != nil && o.Jmax != nil && *o.Jmin > *o.Jmax {
		return errors.New("jmin must not be greater than jmax")
	}
	if o.HeaderProtectionKey != nil && strings.TrimSpace(*o.HeaderProtectionKey) != "" {
		if _, err := normalizeKey(*o.HeaderProtectionKey); err != nil {
			return fmt.Errorf("header protection key: %w", err)
		}
		for name, value := range map[string]*int{"s1": o.S1, "s2": o.S2, "s3": o.S3, "s4": o.S4} {
			if value == nil || *value < 12 {
				return fmt.Errorf("%s must be at least 12 when header protection is used", name)
			}
		}
	}
	for name, value := range map[string]*string{
		"h1": o.H1, "h2": o.H2, "h3": o.H3, "h4": o.H4,
		"content_padding_addition": o.ContentPaddingAddition,
		"rekey_after_time":         o.RekeyAfterTime,
		"rekey_timeout":            o.RekeyTimeout,
		"reject_after_time":        o.RejectAfterTime,
		"keepalive_timeout":        o.KeepaliveTimeout,
		"max_handshake_attempts":   o.MaxHandshakeAttempts,
	} {
		if value == nil {
			continue
		}
		if err := validateRange(name, *value); err != nil {
			return err
		}
	}
	return nil
}

func validateRange(name, value string) error {
	value = strings.TrimSpace(value)
	if value == "" || isOff(value) {
		return nil
	}
	parts := strings.Split(value, "-")
	if len(parts) > 2 {
		return fmt.Errorf("%s %q is not a valid range", name, value)
	}
	for _, part := range parts {
		if part == "" {
			return fmt.Errorf("%s %q is not a valid range", name, value)
		}
		if _, err := strconv.ParseUint(part, 10, 32); err != nil {
			return fmt.Errorf("%s %q is not a valid range", name, value)
		}
	}
	if len(parts) == 2 {
		first, _ := strconv.ParseUint(parts[0], 10, 32)
		last, _ := strconv.ParseUint(parts[1], 10, 32)
		if first > last {
			return fmt.Errorf("%s %q has an inverted range", name, value)
		}
	}
	return nil
}

func isOff(value string) bool {
	normalized := strings.ToLower(strings.TrimSpace(value))
	return normalized == "off" || normalized == "(off)"
}

func normalizeKey(value string) (string, error) {
	value = strings.TrimSpace(value)
	if value == "" {
		return "", errors.New("key is empty")
	}
	if len(value) == 64 {
		if _, err := hex.DecodeString(value); err == nil {
			return strings.ToLower(value), nil
		}
	}
	encodings := []*base64.Encoding{
		base64.StdEncoding,
		base64.RawStdEncoding,
		base64.URLEncoding,
		base64.RawURLEncoding,
	}
	for _, encoding := range encodings {
		decoded, err := encoding.DecodeString(value)
		if err != nil {
			continue
		}
		if len(decoded) != 32 {
			return "", fmt.Errorf("key must be 32 bytes, got %d", len(decoded))
		}
		return hex.EncodeToString(decoded), nil
	}
	return "", errors.New("key is not valid base64 or hex")
}

func parsePrefix(value string) (netip.Prefix, error) {
	value = strings.TrimSpace(value)
	if value == "" {
		return netip.Prefix{}, errors.New("empty prefix")
	}
	if strings.Contains(value, "/") {
		prefix, err := netip.ParsePrefix(value)
		if err != nil {
			return netip.Prefix{}, err
		}
		return prefix.Masked(), nil
	}
	addr, err := netip.ParseAddr(value)
	if err != nil {
		return netip.Prefix{}, err
	}
	bits := 128
	if addr.Is4() {
		bits = 32
	}
	return netip.PrefixFrom(addr, bits), nil
}

func (c *Config) localAddresses() ([]netip.Addr, error) {
	addresses := make([]netip.Addr, 0, len(c.Addresses))
	for _, value := range c.Addresses {
		prefix, err := parsePrefix(value)
		if err != nil {
			return nil, err
		}
		addresses = append(addresses, prefix.Addr())
	}
	return addresses, nil
}

func (c *Config) dnsServers() []netip.Addr {
	servers := make([]netip.Addr, 0, len(c.DNS))
	for _, value := range c.DNS {
		value = strings.TrimSpace(value)
		if value == "" {
			continue
		}
		if addr, err := netip.ParseAddr(value); err == nil {
			servers = append(servers, addr)
		}
	}
	if len(servers) == 0 {
		servers = []netip.Addr{
			netip.MustParseAddr("1.1.1.1"),
			netip.MustParseAddr("1.0.0.1"),
		}
	}
	return servers
}

func (c *Config) allowedPrefixes() ([]netip.Prefix, error) {
	values := c.Peer.AllowedIPs
	if len(values) == 0 {
		values = []string{"0.0.0.0/0", "::/0"}
	}
	prefixes := make([]netip.Prefix, 0, len(values))
	for _, value := range values {
		prefix, err := parsePrefix(value)
		if err != nil {
			return nil, err
		}
		prefixes = append(prefixes, prefix)
	}
	return prefixes, nil
}

// buildUAPI renders the WireGuard cross-platform configuration protocol text.
// Device-level keys must precede the first public_key line.
func (c *Config) buildUAPI(protector SocketProtector) (string, error) {
	if c.AWG != nil {
		if err := c.AWG.validate(); err != nil {
			return "", err
		}
	}
	privateKey, err := normalizeKey(c.PrivateKey)
	if err != nil {
		return "", err
	}
	publicKey, err := normalizeKey(c.Peer.PublicKey)
	if err != nil {
		return "", err
	}
	allowed, err := c.allowedPrefixes()
	if err != nil {
		return "", err
	}
	endpoint, err := resolveEndpoint(c.Peer.Endpoint, protector)
	if err != nil {
		return "", err
	}

	var builder strings.Builder
	write := func(key, value string) {
		builder.WriteString(key)
		builder.WriteByte('=')
		builder.WriteString(value)
		builder.WriteByte('\n')
	}
	write("private_key", privateKey)
	if o := c.AWG; o != nil {
		writeInt := func(key string, value *int) {
			if value != nil && *value != 0 {
				write(key, strconv.Itoa(*value))
			}
		}
		writeInt("jc", o.Jc)
		writeInt("jmin", o.Jmin)
		writeInt("jmax", o.Jmax)
		writeInt("s1", o.S1)
		writeInt("s2", o.S2)
		writeInt("s3", o.S3)
		writeInt("s4", o.S4)
		writeString := func(key string, value *string) {
			if value == nil {
				return
			}
			trimmed := strings.TrimSpace(*value)
			if trimmed != "" && !isOff(trimmed) {
				write(key, trimmed)
			}
		}
		writeString("h1", o.H1)
		writeString("h2", o.H2)
		writeString("h3", o.H3)
		writeString("h4", o.H4)
		writeString("i1", o.I1)
		writeString("i2", o.I2)
		writeString("i3", o.I3)
		writeString("i4", o.I4)
		writeString("i5", o.I5)
		if o.HeaderProtectionKey != nil && strings.TrimSpace(*o.HeaderProtectionKey) != "" {
			key, err := normalizeKey(*o.HeaderProtectionKey)
			if err != nil {
				return "", err
			}
			write("header_protection_key", key)
		}
		writeString("content_padding_addition", o.ContentPaddingAddition)
		writeString("rekey_after_time", o.RekeyAfterTime)
		writeString("rekey_timeout", o.RekeyTimeout)
		writeString("reject_after_time", o.RejectAfterTime)
		writeString("keepalive_timeout", o.KeepaliveTimeout)
		writeString("max_handshake_attempts", o.MaxHandshakeAttempts)
		if o.RandomTrailers != nil {
			write("random_trailers", strconv.FormatBool(*o.RandomTrailers))
		}
		if o.DisableCookies != nil {
			write("disable_cookies", strconv.FormatBool(*o.DisableCookies))
		}
	}
	write("public_key", publicKey)
	if strings.TrimSpace(c.Peer.PreSharedKey) != "" {
		psk, err := normalizeKey(c.Peer.PreSharedKey)
		if err != nil {
			return "", err
		}
		write("preshared_key", psk)
	}
	write("endpoint", endpoint)
	for _, prefix := range allowed {
		write("allowed_ip", prefix.String())
	}
	if keepalive := strings.TrimSpace(c.Peer.PersistentKeepalive); keepalive != "" && !isOff(keepalive) {
		if err := validateRange("persistent_keepalive_interval", keepalive); err != nil {
			return "", err
		}
		write("persistent_keepalive_interval", keepalive)
	}
	return builder.String(), nil
}

// resolveEndpoint converts a wg-quick endpoint (which may contain a hostname)
// into the IP:port form required by the userspace bind. Names are resolved
// through a protected socket so the lookup bypasses the outer VPN.
func resolveEndpoint(endpoint string, protector SocketProtector) (string, error) {
	endpoint = strings.TrimSpace(endpoint)
	host, port, err := net.SplitHostPort(endpoint)
	if err != nil {
		return "", fmt.Errorf("invalid endpoint %q: %w", endpoint, err)
	}
	if _, err := strconv.ParseUint(port, 10, 16); err != nil {
		return "", fmt.Errorf("invalid endpoint port %q", port)
	}
	if addr, err := netip.ParseAddr(host); err == nil {
		return net.JoinHostPort(addr.String(), port), nil
	}
	ctx, cancel := contextWithTimeout(5 * time.Second)
	defer cancel()
	ips, err := lookupHost(ctx, host, protector)
	if err != nil {
		return "", fmt.Errorf("resolve endpoint %q: %w", host, err)
	}
	if len(ips) == 0 {
		return "", fmt.Errorf("resolve endpoint %q: no addresses", host)
	}
	var chosen netip.Addr
	for _, ip := range ips {
		if ip.Is4() {
			chosen = ip
			break
		}
	}
	if !chosen.IsValid() {
		chosen = ips[0]
	}
	return net.JoinHostPort(chosen.String(), port), nil
}
