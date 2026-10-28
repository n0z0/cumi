# KaosKaki

Native Android app (Kotlin + Jetpack Compose) that turns your phone into a **SOCKS5 proxy server**,
so other devices on the same Wi-Fi network, hotspot, or USB tether can use the phone's internet connection.

## Features

- SOCKS5 server (RFC 1928): TCP `CONNECT` for IPv4, IPv6 and domain names
- Optional username/password authentication (RFC 1929)
- Runs as a foreground service with a live notification (address and active connections) and a Stop action
- Lists every address the proxy can be reached on (Wi-Fi, hotspot, USB, Bluetooth, …) with one-tap copy
- Live stats: active/total connections, bytes uploaded/downloaded, throughput, uptime
- Activity log of every connection (target, traffic, duration, errors)
- Optional CPU and Wi-Fi wake locks so the proxy stays responsive with the screen off
- Material 3 UI with light/dark themes, in English and Bahasa Indonesia

UDP `ASSOCIATE` and `BIND` are not supported; clients receive the standard "command not supported" reply.

## Usage

1. Open KaosKaki and tap the power button.
2. Connect the other device to the same network (or to the phone's hotspot).
3. Set its proxy to **SOCKS5** with the address shown in the app (default port `1080`).

Quick test from a computer:

```sh
curl --socks5-hostname 192.168.1.20:1080 https://ifconfig.me
# with authentication
curl --socks5-hostname user:pass@192.168.1.20:1080 https://ifconfig.me
```

## Building

Requires JDK 17 and the Android SDK (API 35).

```sh
./gradlew assembleDebug     # app/build/outputs/apk/debug/
./gradlew assembleRelease   # app/build/outputs/apk/release/
```

## Releases (GitHub Actions)

Every push to `main` runs `.github/workflows/release.yml`, which:

1. Calculates the next version from the latest `vX.Y.Z` tag (the first release is `v1.0.0`):
   - `#major` or `BREAKING CHANGE` in the commit message bumps the major version
   - `#minor` or a `feat:` commit bumps the minor version
   - anything else bumps the patch version
2. Builds a signed release APK (`versionCode = major*1000000 + minor*1000 + patch`).
3. Creates the tag and a GitHub Release with the APK and its SHA-256 checksum attached.

Changes that only touch Markdown files, `LICENSE` or `.gitignore` do not trigger a release.
Pull requests and other branches only build a debug APK (`build.yml`).

### Signing

Release builds are signed with `release.jks`, described by `keystore.properties` in the project root.
Both files are git-ignored. **Back them up somewhere safe**: if the key is lost, installed copies can no
longer be updated and users must uninstall first.

`keystore.properties` format:

```properties
storeFile=release.jks
storePassword=...
keyAlias=kaoskaki
keyPassword=...
```

To create a new key instead:

```sh
keytool -genkeypair -v -storetype PKCS12 -keystore release.jks -alias kaoskaki -keyalg RSA -keysize 4096 -validity 10000
```

CI reads the same key from repository secrets. Upload them with the GitHub CLI, logged in as an account
with admin access to the repository:

```sh
./scripts/set-signing-secrets.sh            # defaults to n0z0/kaoskaki
```

| Secret | Value |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | base64 of `release.jks` |
| `SIGNING_STORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | key alias (`kaoskaki`) |
| `SIGNING_KEY_PASSWORD` | key password |

Without these secrets CI falls back to a throwaway debug key.

## License

GPL-3.0. See [LICENSE](LICENSE).
