# Publikasi ke Google Play

Panduan langkah demi langkah untuk merilis Cumi di Google Play. Semua bahan listing ada di repo ini:

| Bahan | Lokasi |
| --- | --- |
| Judul, deskripsi singkat & penuh (EN / ID) | `fastlane/metadata/android/{en-US,id}/*.txt` |
| Ikon 512×512, feature graphic 1024×500 | `fastlane/metadata/android/{en-US,id}/images/` (render ulang: `python3 fastlane/assets/render.py`) |
| Screenshot ponsel | `fastlane/metadata/android/{en-US,id}/images/phoneScreenshots/` (**perlu diisi**, lihat langkah 5) |
| Kebijakan privasi | `PRIVACY.md` → `https://github.com/n0z0/cumi/blob/main/PRIVACY.md` |
| File unggahan (AAB) | Lampiran setiap GitHub Release: `Cumi-vX.Y.Z.aab` |

## 1. Akun developer

1. Daftar di https://play.google.com/console (biaya sekali USD 25) dan selesaikan verifikasi identitas.
2. **Akun pribadi baru** wajib menjalankan **closed testing minimal 12 penguji selama 14 hari berturut-turut**
   sebelum bisa mengajukan akses produksi. Akun organisasi tidak terkena syarat ini.

## 2. Buat aplikasi

*Play Console → Create app*

- App name: `Cumi - SOCKS5 Proxy`
- Default language: `English (United States) – en-US`
- App or game: **App**, Free or paid: **Free**
- Centang pernyataan kebijakan → **Create app**

## 3. App signing

Pakai **Play App Signing** (bawaan). Google menyimpan kunci penandatangan aplikasi. Kunci `release.jks` milik kita
menjadi **upload key**, terdaftar otomatis saat AAB pertama diunggah.

> Konsekuensi: APK dari GitHub Releases ditandatangani `release.jks`, sedangkan versi dari Play ditandatangani kunci Google.
> Pengguna tidak bisa pindah dari versi GitHub ke versi Play (atau sebaliknya) tanpa uninstall dulu.

## 4. Unggahan pertama (manual)

API Play tidak bisa membuat rilis pertama, jadi unggahan pertama dilakukan lewat browser:

1. Unduh `Cumi-vX.Y.Z.aab` dari [Releases](https://github.com/n0z0/cumi/releases/latest).
2. *Test and release → Testing → Internal testing → Create new release* → unggah AAB → isi catatan rilis → **Save** → **Review release** → **Start rollout**.
3. Tambahkan email penguji di tab *Testers*.

## 5. Store listing

*Grow users → Store presence → Main store listing*

- Salin judul dan deskripsi dari `fastlane/metadata/android/en-US/`.
- Tambah terjemahan: *Manage translations → Add your own translation → Indonesian – id*, salin dari `fastlane/metadata/android/id/`.
- **App icon**: `images/icon.png`. **Feature graphic**: `images/featureGraphic.png`.
- **Phone screenshots** (minimal 2, disarankan 4–8, rasio 9:16, 1080×1920 atau lebih). Ambil dari HP dengan aplikasi rilis:
  1. Dasbor saat berhenti
  2. Dasbor saat berjalan, dengan statistik dan alamat terisi (sambungkan satu perangkat dulu)
  3. Tab Aktivitas berisi beberapa koneksi
  4. Tab Pengaturan dengan autentikasi aktif
  5. (Opsional) notifikasi proxy berjalan, dan versi tema gelap

  Simpan juga ke `fastlane/metadata/android/<locale>/images/phoneScreenshots/` agar terarsip.

*Store settings*:

- App category: **Tools**
- Contact details: email developer (wajib), website `https://github.com/n0z0/cumi`

## 6. App content (Policy → App content)

Jawaban yang sesuai dengan perilaku aplikasi:

| Bagian | Jawaban |
| --- | --- |
| **Privacy policy** | `https://github.com/n0z0/cumi/blob/main/PRIVACY.md` |
| **Ads** | No, my app does not contain ads |
| **App access** | All functionality is available without special access |
| **Content rating** | Kategori *All Other App Types*. Jawab **No** untuk semua pertanyaan konten (kekerasan, seksual, judi, dll.) dan interaksi pengguna. Hasil yang diharapkan: semua umur / PEGI 3 |
| **Target audience** | **18 and over** (menghindari persyaratan program Families) |
| **News app** | No |
| **Data safety** | *Does your app collect or share any of the required user data types?* → **No**. Tidak ada data yang dikirim keluar perangkat (lihat `PRIVACY.md`) |
| **Government / Financial / Health apps** | Not applicable / None |
| **Advertising ID** | No (aplikasi tidak memakai Advertising ID) |

### Foreground service

Cumi memakai `FOREGROUND_SERVICE_SPECIAL_USE`, jadi Play meminta deklarasi. Isi di *App content → Foreground service permissions*:

- Pilih **Special use** dan centang bahwa fitur ini dimulai oleh pengguna.
- **Description** (salin):

  > Cumi is a SOCKS5 proxy server that lets the user share the phone's internet connection with their other devices
  > (laptop, tablet) on the same Wi-Fi network, hotspot or USB tether. The user starts the proxy with the power button
  > and it must keep accepting and relaying network connections from those devices while the app is in the background
  > or the screen is off. Stopping the service would immediately drop every connected device's traffic. The service
  > runs only while the user has the proxy turned on, shows an ongoing notification with the proxy address and a Stop
  > action, and stops as soon as the user taps Stop. None of the other foreground service types (dataSync, connectedDevice,
  > etc.) describe a long-running local network server.

- **Video**: rekam layar (unggah ke YouTube sebagai *Unlisted*) yang menunjukkan: membuka aplikasi → menekan tombol daya →
  notifikasi muncul → perangkat lain browsing lewat proxy dengan layar HP mati → menekan Stop di notifikasi.

## 7. Closed testing → produksi

1. *Testing → Closed testing → Create track* → unggah AAB yang sama (atau promosikan dari Internal) → tambahkan minimal 12 penguji (Google Group atau daftar email).
2. Penguji membuka link opt-in, memasang dari Play, dan **tetap ikut serta 14 hari**.
3. Setelah 14 hari: *Dashboard → Apply for production* → isi kuesioner → tunggu persetujuan.
4. *Production → Create new release* → promosikan build → kirim untuk review (biasanya 1–7 hari untuk aplikasi baru).

## 8. Otomatisasi unggahan dari GitHub Actions

Setelah unggahan pertama selesai, setiap push ke `main` bisa langsung mengunggah AAB ke Play.

1. **Google Cloud**: buat project (atau pakai yang ada) → aktifkan **Google Play Android Developer API** →
   *IAM & Admin → Service Accounts → Create* → buat key **JSON** dan unduh.
2. **Play Console**: *Users and permissions → Invite new users* → masukkan email service account →
   *App permissions* → pilih Cumi → beri izin **Release apps to testing tracks** (dan **Release to production** bila perlu) → kirim undangan.
3. **GitHub** (login `gh` sebagai `awangga`):

   ```sh
   gh secret set PLAY_SERVICE_ACCOUNT_JSON -R n0z0/cumi < play-service-account.json
   # Selama aplikasi belum pernah dipublikasikan, rilis harus berstatus draft:
   gh variable set PLAY_RELEASE_STATUS -R n0z0/cumi --body draft
   # Track tujuan (bawaan: internal). Pilihan: internal, alpha (closed), beta (open), production
   gh variable set PLAY_TRACK -R n0z0/cumi --body internal
   ```

   Hapus file JSON dari laptop setelahnya (atau simpan di password manager).
4. Setelah aplikasi lolos review pertama, hapus variabel draft agar rilis langsung aktif:
   `gh variable delete PLAY_RELEASE_STATUS -R n0z0/cumi`

Pastikan terjemahan Indonesia (langkah 5) sudah ditambahkan, karena workflow juga mengirim "What's new" untuk locale `id`.

Catatan "What's new" di Play dibuat otomatis dari judul commit sejak tag sebelumnya, jadi tulis judul commit yang jelas.

## Catatan kebijakan

- Deskripsi sengaja netral. Jangan menambahkan klaim seperti "melewati batas tethering operator" atau "internet gratis".
  Klaim semacam itu bisa melanggar kebijakan *Device and Network Abuse* dan membuat aplikasi ditolak.
- Setiap Agustus Google menaikkan syarat target API. Saat ini (2026) wajib `targetSdk = 36`. Perbarui di `app/build.gradle.kts` tiap tahun.
