# NetProbe

English | [简体中文](README.zh-CN.md)

An Android probe for exploring the network information available to apps. Use it on your own device to inspect what apps can read and how granting permissions changes that view. NetProbe helps you understand the network telemetry that Android software developers can collect.

## Features

| Category | Information | Availability and controls |
|---|---|---|
| Network overview | Default network, VPN, metering, internet validation | Read while the app is in the foreground |
| Interfaces and IP | IPv4/IPv6, prefixes, address types, interface state, MTU | Switch between interfaces; copy addresses and CIDR notation |
| DNS and routes | DNS, Private DNS, NAT64, routes, HTTP proxy | Information exposed by the system for the current network |
| Carrier | SIM and registered operator, MCC/MNC, country, roaming, Carrier ID, signal | Basic fields available directly; per-subscription information requires phone permission |
| Subscription records | Current SIMs, display names, eSIM, system-retained subscription history | Phone permission; history requires Android 14+ and a manual read |
| Service state | Radio technology, registration domains, PLMN, radio signals | Some queries require phone and location permissions |
| Wi-Fi | SSID/BSSID, frequency, security type, link speeds | Precise location permission and system location enabled |
| System properties | Fixed queries for device, build, carrier, and network properties | Manually read through `getprop`; visibility follows system policy |
| Native interfaces | Interface and address view from Bionic `getifaddrs()` | Manual read; experimental feature |
| Public network checks | IPv4/IPv6 connectivity, public IP, Cloudflare location | Connect to Cloudflare or your own HTTPS service after confirmation |
| Interface language | Simplified Chinese, English, and English fallback | Choose under “More → Language”, or follow the system |
| Recording and export | Foreground change monitoring, permission comparisons, JSON viewing and export | Choose redacted or full data |

## Usage

Download an APK from [Releases](https://github.com/rnotlnglgq/AndroidNetProbe/releases). Choose `arm64-v8a` for most recent phones, `armeabi-v7a` for 32-bit ARM devices, or `universal` for all four supported architectures. Android 8 is the minimum version; Android 15 is the primary target. Basic checks have passed on Android 13, 14, and 15 emulators. Field availability depends on the Android version.

Browse by category using the top tabs. Interfaces and subscriptions have their own subtabs. “Show full values” lets you view and copy exact addresses; “More” provides JSON, permission comparisons, and public check settings. Section headers show runtime permission requirements. “Technical details” lists API sources and errors.

Results are mainly held in memory. Reader switches and endpoint settings are saved in private app preferences. JSON is saved through the system file picker, with addresses and carrier information redacted by default. A cloud storage provider may sync files saved to it.

Public network checks run after confirmation. The recipient sees your public IP and request time. See the [server guide](server/README.md) for hosting your own endpoint, and the [Schema](samples/schema-1.0.json) and [synthetic examples](samples/) for the export format.

## Making changes

Open the project in Android Studio. Release builds use the shared signing identity in the repository, allowing modified builds to update existing installations. Android accepts updates signed with the same identity; installers are responsible for checking their source. A local `release-signing.properties` file overrides the default signing configuration.

See the [maintainer guide](.github/RELEASING.md) for CI and version-tag releases.

## License

Licensed under [GNU GPL v3.0](LICENSE) (GPL-3.0-only).
