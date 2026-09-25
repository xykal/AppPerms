# AppsPerms

[![Version](https://img.shields.io/badge/version-1.7.2-orange.svg?style=flat-square)](https://github.com/xykal/AppPerms/releases)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg?style=flat-square)](LICENSE)
[![VirusTotal](https://img.shields.io/badge/virustotal-0%2F72%20clean-brightgreen.svg?style=flat-square)](https://appsperms.xyverse.my.id/#keamanan)
[![Platform](https://img.shields.io/badge/platform-Shizuku%20%7C%20Root-informational.svg?style=flat-square)](https://shizuku.rikka.app/)
[![Privacy](https://img.shields.io/badge/privacy-0%20internet%20permission-success.svg?style=flat-square)](https://appsperms.xyverse.my.id/)
[![Build Status](https://img.shields.io/github/actions/workflow/status/xykal/AppPerms/build.yml?branch=main&style=flat-square)](https://github.com/xykal/AppPerms/actions)

Built-in XyVerse by Kall (@xykal)

[Bahasa Indonesia](#ringkasan-bahasa-indonesia) | [English Summary](#english-summary)

---

## Ringkasan (Bahasa Indonesia)

AppsPerms adalah aplikasi Android modern, super cepat, dan ringan (~1.9 MB) untuk mengelola izin sistem tersembunyi (**AppOps**) tanpa memerlukan root permanen, memanfaatkan hak akses shell Shizuku atau akses root langsung.

Fokus utama aplikasi ini adalah op `SYSTEM_ALERT_WINDOW` (*Display over other apps* / Menampilkan di atas aplikasi lain) serta 18 AppOps krusial lainnya (akses clipboard, kamera, mikrofon, lokasi latar belakang, wake lock, instalasi paket, dan lain-lain).

### Fitur Utama

- **Pemindaian Kilat (~150ms)**: Menggunakan lazy on-demand icon loading dengan status counter realtime ("Memeriksa aplikasi 48/312...").
- **0ms Optimistic UI**: Perubahan status langsung tercermin di antarmuka seketika tanpa jeda, dengan mekanisme rollback otomatis jika eksekusi ditolak oleh sistem.
- **Terminal Shizuku**: Menjalankan perintah shell langsung dari perangkat dengan UID 2000 (shell) atau UID 0 (root) tanpa memerlukan kabel data atau PC wireless debugging.
- **Safe Tuning Layar & Animasi**: Pengaturan resolusi dan DPI (`wm size` dan `wm density`) yang dilengkapi pengaman auto-revert 15 detik, serta skala animasi global (0x, 0.5x, 1x).
- **Aksi Massal (Batch)**: Mengubah status izin overlay untuk banyak aplikasi sekaligus dalam hitungan detik.
- **Backup & Restore**: Simpan status konfigurasi perizinan ke clipboard teks sederhana dan pulihkan kapan saja.
- **100% Offline & Privat**: Nol (0) izin internet di `AndroidManifest.xml`. Data mustahil meninggalkan perangkat Anda.

### Unduh APK Resmi

| Varian | Link Unduh Langsung | Mirror Cadangan | Catatan |
|---|---|---|---|
| Release APK (Rekomendasi) | [dl.appsperms.xyverse.my.id/AppsPerms-latest.apk](https://dl.appsperms.xyverse.my.id/AppsPerms-latest.apk) | [GitHub Releases](https://github.com/xykal/AppPerms/releases/latest) | Signed keystore resmi XyVerse, R8 minified (~1.98 MB) |
| Debug APK (Troubleshooting) | [dl.appsperms.xyverse.my.id/AppsPerms-debug-latest.apk](https://dl.appsperms.xyverse.my.id/AppsPerms-debug-latest.apk) | [GitHub Releases](https://github.com/xykal/AppPerms/releases/latest) | Logging logcat aktif, unstripped (~6.0 MB) |

### Website Resmi & Keamanan

Website Resmi: [https://appsperms.xyverse.my.id/](https://appsperms.xyverse.my.id/)

- Scan Antivirus VirusTotal: 0/72 Clean (100% Undetected).
- Audit Keamanan: 0 permission internet di AndroidManifest.xml.
- Keystore Resmi: Ditandatangani RSA 4096-bit resmi XyVerse.

```
SHA-256 : 82:A0:2C:AE:E2:7B:8B:9B:09:E7:00:B8:31:3D:D4:AD:E5:CF:9B:94:6B:01:BE:6C:54:EC:33:98:4A:B0:04:30
SHA-1   : 99:55:67:5C:19:0C:BB:4B:0F:D5:08:94:74:08:D2:B8:92:22:69:FF
MD5     : 86:4C:BF:D8:C3:9B:A3:F3:89:B8:5D:64:C2:A1:B7:D6
Alias   : overlayops (CN=OverlayOps, OU=Release, O=xykal, C=ID)
```

---

## English Summary

AppsPerms is a lightweight (~1.9 MB), high-performance Android utility to manage hidden system operations (**AppOps**) without requiring permanent root access, leveraging Shizuku's privileged shell (UID 2000) or direct root (UID 0).

It focuses on controlling `SYSTEM_ALERT_WINDOW` (*Display over other apps*) alongside 18 other critical AppOps (clipboard access, camera, microphone, background location, wake lock, package installation, etc.).

### Highlights

- **Sub-200ms App Scanning**: On-demand icon caching with a live progress indicator ("Checking apps 48/312...").
- **0ms Optimistic UI**: Instant UI feedback on permission changes with automatic rollback on ROM-level rejection.
- **Embedded Shizuku Terminal**: Run ADB shell commands directly on your device without a PC or wireless debugging setup.
- **Safe Screen & Animation Tuning**: Adjust resolution and DPI (`wm size` / `wm density`) backed by a 15-second safety revert timer.
- **Batch Actions**: Bulk allow, ignore, or reset overlay permissions across installed apps in seconds.
- **Backup & Restore**: Export and import your permission configurations via plain-text clipboard format.
- **Zero Internet Permissions**: No `android.permission.INTERNET` declared. Zero telemetry, zero tracking, 100% on-device.

### Official Downloads

| Variant | Direct Download Link | Mirror | Notes |
|---|---|---|---|
| Release APK (Recommended) | [dl.appsperms.xyverse.my.id/AppsPerms-latest.apk](https://dl.appsperms.xyverse.my.id/AppsPerms-latest.apk) | [GitHub Releases](https://github.com/xykal/AppPerms/releases/latest) | Signed by official XyVerse RSA-4096 keystore, R8 minified |
| Debug APK (Troubleshooting) | [dl.appsperms.xyverse.my.id/AppsPerms-debug-latest.apk](https://dl.appsperms.xyverse.my.id/AppsPerms-debug-latest.apk) | [GitHub Releases](https://github.com/xykal/AppPerms/releases/latest) | Full logcat logging, unstripped |

Official Portal: [https://appsperms.xyverse.my.id/](https://appsperms.xyverse.my.id/)

---

## Panduan Penggunaan / Quick Guide

1. **Jalankan Shizuku**: Unduh dan buka aplikasi Shizuku dari Google Play Store atau GitHub. Di Android 11+, gunakan opsi Wireless Debugging. Jika perangkat sudah di-root, jalankan melalui root.
2. **Pasang AppsPerms**: Unduh APK rilis dari link resmi di atas dan pasang pada perangkat Anda.
3. **Berikan Akses Shizuku**: Buka AppsPerms, ketuk tombol sambungan, lalu pilih "Izinkan selalu". Aplikasi langsung siap digunakan.

---

## Kontributor / Contributors

Proyek ini dibangun dan dikembangkan berkat kontribusi dari:

- **Kall ([@xykal](https://github.com/xykal))** — Founder & Lead Developer
- **[BuddhaDiedLaughing](https://github.com/BuddhaDiedLaughing)** — One UI 8.5 Research, Shizuku Terminal i18n & multi-profile plumbing
- **[Rikka](https://github.com/RikkaApps)** — Pencipta Shizuku & App Ops API
- **[LSPosed Developers](https://github.com/LSPosed)** — HiddenApiBypass library

---

## Ilustrasi UI / UI Illustrations

Aset papercut website dan Android, budget ukuran, regenerasi WebP/PNG serta tes UI didokumentasikan di [Panduan ilustrasi](docs/ILLUSTRATIONS.md). / Website and Android paper-cut assets, byte budgets, regeneration, and UI checks are documented in the [illustration guide](docs/ILLUSTRATIONS.md).

---

## Lisensi / License

Proyek ini dirilis di bawah lisensi terbuka [Apache License 2.0](LICENSE).  
Bebas digunakan, dimodifikasi, dan didistribusikan sesuai ketentuan lisensi.
