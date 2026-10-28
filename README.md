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

## Quickstart

1. Download the latest `KaosKaki-vX.Y.Z.apk` from [Releases](https://github.com/n0z0/kaoskaki/releases/latest) and install it on the phone that has the internet connection.
2. Open **KaosKaki** and tap the large **power button**. The status changes to **Running**.
3. Connect your laptop (or other device) to the same Wi-Fi network, or to the phone's hotspot.
4. On the laptop, set a **SOCKS5** proxy to the address shown under **Proxy address** (for example `192.168.43.1`, port `1080`).
5. Check that it works:

   ```sh
   curl --socks5-hostname 192.168.43.1:1080 https://ifconfig.me
   ```

   The IP printed should be the phone's public IP.

## User guide

### Installing

- Android 8.0 or newer is required.
- The APK is not from the Play Store, so Android asks for permission to install it. Allow
  **Install unknown apps** for the browser or file manager you opened the APK with.
- When updating, install the new APK over the existing app; settings are kept.
  (Version `v1.0.0` used a different signing key: uninstall it once before installing a newer version.)
- On first start, Android 13+ asks for notification permission. Allow it so you can see the proxy status and stop it from the notification.
  The proxy still works if you deny it.

### Sharing the connection

Pick how the other devices reach the phone:

| Method | When to use | Address shown as |
| --- | --- | --- |
| Same Wi-Fi network | Phone and laptop are on the same router | **Wi-Fi** (`wlan0`) |
| Phone hotspot | Share the phone's mobile data; turn the hotspot on first | **Hotspot** (`ap0`, `swlan0`, …) |
| USB tethering | Laptop connected by cable | **USB tethering** (`rndis0`, …) |
| Bluetooth tethering | No Wi-Fi available | **Bluetooth** (`bt-pan`) |

The **Proxy address** card lists every reachable address and refreshes automatically when networks change.
Tap the copy icon to copy `address:port`. Addresses marked **Mobile data** are your carrier's network and
usually cannot be reached from other devices; use one of the methods above instead.

### The app

**Dashboard**

- **Power button**: starts or stops the proxy. While it runs, a notification shows the address and the number of
  active connections, with a **Stop** button.
- **Proxy address**: where clients connect.
- **Credentials**: shown when authentication is on. Tap the eye icon to reveal the password, the copy icon to copy.
- **Statistics**: active and total connections, uptime, total data uploaded/downloaded and current speed.

**Activity**

A log of every connection: client IP, destination, data transferred and duration. Failed connections
(wrong password, unreachable host, …) are shown in amber, errors in red. The trash icon clears the log.

**Settings** (stop the proxy first to change them)

| Setting | Description |
| --- | --- |
| **Port** | Listening port, `1024`–`65535`. Default `1080`. |
| **Require username & password** | Clients must log in. Set both fields; the proxy will not start if one is empty. |
| **Keep connection awake** | Keeps the CPU and Wi-Fi active while the proxy runs so it stays fast with the screen off. Uses a little more battery. On by default. |

### Configuring clients

Use the address and port from the app. Wherever there is an option to resolve DNS through the proxy
("Proxy DNS", `socks5h`, "remote DNS"), turn it on.

**Firefox** (any OS): *Settings → General → Network Settings → Settings…* → **Manual proxy configuration** →
*SOCKS Host* = address, *Port* = port, select **SOCKS v5**, tick **Proxy DNS when using SOCKS v5**.

**Chrome / Edge**: start the browser with

```sh
chrome --proxy-server="socks5://192.168.43.1:1080"
```

**macOS**: *System Settings → Network → (your network) → Details… → Proxies* → enable **SOCKS proxy** and enter address and port.

**Linux (GNOME)**: *Settings → Network → Network Proxy* → **Manual** → *Socks Host* = address and port.

**Windows**: the built-in proxy settings only support HTTP proxies. Use Firefox (above), Chrome with the flag above,
or a tool such as Proxifier to send other apps through SOCKS5.

**Telegram**: *Settings → Data and Storage → Proxy → Add Proxy* → **SOCKS5**, enter address, port and credentials if set.

**Command line**:

```sh
# curl
curl --socks5-hostname user:pass@192.168.43.1:1080 https://example.com

# git over HTTPS
git config --global http.proxy socks5h://192.168.43.1:1080

# ssh
ssh -o ProxyCommand="nc -X 5 -x 192.168.43.1:1080 %h %p" user@server
```

**Other Android or iOS devices**: the system Wi-Fi proxy setting only supports HTTP proxies. Use apps with
built-in SOCKS5 support (for example Telegram or Firefox).

> **Authentication support varies.** Chrome and Firefox cannot log in to a SOCKS5 proxy. If you need those
> browsers, leave authentication off and only run the proxy on networks you trust. curl, Telegram and Proxifier support it.

### Keeping it running in the background

Some phones (Xiaomi, Oppo, Vivo, Samsung, Huawei, …) close background apps aggressively. If the proxy stops on its own:

1. *Settings → Apps → KaosKaki → Battery* → set to **Unrestricted** (or "No restrictions" / "Don't optimise").
2. On Xiaomi/MIUI, also enable **Autostart** for KaosKaki.
3. Keep **Keep connection awake** on in KaosKaki settings.

### Troubleshooting

| Problem | What to try |
| --- | --- |
| Client cannot connect / times out | Check both devices are on the same network and the address matches the app. Public and office Wi-Fi often block devices from talking to each other (client isolation); use the phone's hotspot instead. |
| "Cannot listen on port …" | Another app is using that port. Choose a different port in Settings. |
| "Authentication is on but the username or password is empty" | Fill in both fields in Settings, or turn authentication off. |
| Connected but websites do not load | Enable "Proxy DNS" / use `socks5h://`. Check that the phone itself has internet. |
| Activity shows "Authentication failed" | The client sent the wrong username or password. |
| Activity shows "Client did not offer username/password authentication" | The client does not support SOCKS5 login (e.g. Chrome). Turn authentication off or use another client. |
| Proxy stops after a while | See [Keeping it running in the background](#keeping-it-running-in-the-background). |
| Some apps or games still do not work | Only TCP is proxied. Apps that need UDP (voice calls, many games) will not work through the proxy. |

### Security notes

- SOCKS5 does not encrypt traffic between the client and the phone. Websites using HTTPS stay encrypted end to end.
- Without authentication, **anyone on the same network can use your connection**. Turn authentication on, or only
  run the proxy on your own hotspot or home network, and stop it when you are done.
- Traffic leaves through the phone, so it counts against your mobile data quota when you use mobile data.

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
