# AppsPerms

[![Version](https://img.shields.io/badge/version-1.8.0-orange.svg?style=flat-square)](https://github.com/xykal/AppPerms/releases)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg?style=flat-square)](LICENSE)
[![Release integrity](https://img.shields.io/badge/integrity-SHA--256%20published-blue.svg?style=flat-square)](https://appsperms.xyverse.my.id/#keamanan)
[![Platform](https://img.shields.io/badge/platform-Shizuku%20%7C%20Root-informational.svg?style=flat-square)](https://shizuku.rikka.app/)
[![Privacy](https://img.shields.io/badge/privacy-0%20internet%20permission-success.svg?style=flat-square)](https://appsperms.xyverse.my.id/)
[![Build Status](https://img.shields.io/github/actions/workflow/status/xykal/AppPerms/build.yml?branch=main&style=flat-square)](https://github.com/xykal/AppPerms/actions)

Built-in XyVerse by Kall (@xykal)

[Bahasa Indonesia](#ringkasan-bahasa-indonesia) | [English Summary](#english-summary)

---

## Ringkasan (Bahasa Indonesia)

AppsPerms adalah aplikasi Android modern, super cepat, dan ringan (~2.2 MB) untuk mengelola izin sistem tersembunyi (**AppOps**) tanpa memerlukan root permanen, memanfaatkan hak akses shell Shizuku atau akses root langsung.

Fokus utama aplikasi ini adalah op `SYSTEM_ALERT_WINDOW` (*Display over other apps* / Menampilkan di atas aplikasi lain) serta 18 AppOps krusial lainnya (akses clipboard, kamera, mikrofon, lokasi latar belakang, wake lock, instalasi paket, dan lain-lain).

### Fitur Utama

- **Pemindaian Lokal yang Cepat**: Menggunakan lazy on-demand icon loading dengan status counter realtime ("Memeriksa aplikasi 48/312...").
- **Optimistic UI**: Perubahan status langsung tercermin di antarmuka seketika tanpa jeda, dengan mekanisme rollback otomatis jika eksekusi ditolak oleh sistem.
- **Terminal Shizuku**: Menjalankan perintah shell langsung dari perangkat dengan UID 2000 (shell) atau UID 0 (root) tanpa memerlukan kabel data atau PC wireless debugging.
- **Safe Tuning Layar & Animasi**: Pengaturan resolusi dan DPI (`wm size` dan `wm density`) yang dilengkapi pengaman auto-revert 15 detik, serta skala animasi global (0x, 0.5x, 1x).
- **Aksi Massal (Batch)**: Mengubah status izin overlay untuk banyak aplikasi sekaligus dalam hitungan detik.
- **Backup & Restore**: Simpan status konfigurasi perizinan ke clipboard teks sederhana dan pulihkan kapan saja.
- **Device Care Lab**: Debloat bertingkat yang reversible, cache trim resmi Android, pembersihan cache selektif dengan root, serta Feature Lab untuk flag sistem Android yang legal. Paket kritis dilindungi dan nilai awal dapat dipulihkan.
- **Lokal & Privat secara default**: Tidak mendeklarasikan `android.permission.INTERNET` dan tidak memuat iklan atau tracker. Data tetap lokal kecuali Anda sendiri menyalin atau mengekspornya.

### Unduh APK Resmi

| Artefak | Unduhan immutable | Catatan |
|---|---|---|
| Release APK (Rekomendasi) | [AppsPerms v1.8.0 universal](https://github.com/xykal/AppPerms/releases/download/v1.8.0/AppsPerms-1.8.0-stable-universal-release.apk) | Paket `app.appsperms`, ditandatangani sertifikat rilis resmi, R8 minified (~2.2 MB) |

Build debug hanya artefak CI untuk pengujian dan tidak dipromosikan sebagai rilis publik.

### Website Resmi & Keamanan

Website Resmi: [https://appsperms.xyverse.my.id/](https://appsperms.xyverse.my.id/)

- Status VirusTotal ditampilkan hanya jika pemindaian rilis telah selesai dan SHA-256 cocok; jika belum, statusnya **Pending / Not scanned**.
- Audit Keamanan: 0 permission internet di AndroidManifest.xml.
- Keystore Resmi: Ditandatangani RSA 4096-bit resmi XyVerse.

```text
Signing certificate SHA-256: 76:FF:DC:65:E3:F8:C6:AE:F0:D3:FF:8B:75:09:9E:DE:33:63:81:30:5C:FB:2F:7D:DA:8D:29:D8:31:CC:AC:05
Signing certificate SHA-1:   56:A5:36:1D:DC:B0:2A:54:22:81:8C:CD:84:73:AD:05:F4:7A:D6:32
Signing certificate MD5:     3D:1B:7B:5A:49:0B:C6:A5:F4:D5:9A:68:22:5B:D5:48
Subject: CN=OverlayOps, OU=Release, O=xykal, C=ID
```

---

## English Summary

AppsPerms is a lightweight (~2.2 MB), high-performance Android utility to manage hidden system operations (**AppOps**) without requiring permanent root access, leveraging Shizuku's privileged shell (UID 2000) or direct root (UID 0).

It focuses on controlling `SYSTEM_ALERT_WINDOW` (*Display over other apps*) alongside 18 other critical AppOps (clipboard access, camera, microphone, background location, wake lock, package installation, etc.).

### Highlights

- **Fast Local App Scanning**: On-demand icon caching with a live progress indicator ("Checking apps 48/312...").
- **Optimistic UI**: Instant UI feedback on permission changes with automatic rollback on ROM-level rejection.
- **Embedded Shizuku Terminal**: Run ADB shell commands directly on your device without a PC or wireless debugging setup.
- **Safe Screen & Animation Tuning**: Adjust resolution and DPI (`wm size` / `wm density`) backed by a 15-second safety revert timer.
- **Batch Actions**: Bulk allow, ignore, or reset overlay permissions across installed apps in seconds.
- **Backup & Restore**: Export and import your permission configurations via plain-text clipboard format.
- **Device Care Lab**: Tiered reversible debloat, Android's official cache trim, selective root cache cleanup, and a bounded Feature Lab for legitimate Android system flags. Critical packages are protected and original values can be restored.
- **Local & private by default**: No `android.permission.INTERNET` declaration and no built-in telemetry, advertising, or trackers. Data stays on-device unless you copy or export it.

### Official Downloads

| Artifact | Immutable download | Notes |
|---|---|---|
| Release APK (Recommended) | [AppsPerms v1.8.0 universal](https://github.com/xykal/AppPerms/releases/download/v1.8.0/AppsPerms-1.8.0-stable-universal-release.apk) | Package `app.appsperms`, official release certificate, R8 minified (~2.2 MB) |

Debug builds are CI-only test artifacts and are not promoted as public releases.

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
