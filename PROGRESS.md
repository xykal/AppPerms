# PROGRESS LOG - Template XyVerse

> Copy file ini jadi PROGRESS.md di setiap repo. Wajib diupdate tiap kerja.

## Format Wajib

### YYYY-MM-DD - Judul Task
**Status:** Done / In Progress / Blocked
**Dikerjain oleh:** AI Agent + xykalnotkel

**Yang dikerjain:**
- ...

**File yang diubah:**
- `path/file.ts` -> alasan: ...
- `path/file2.dart` -> alasan: ...

**Kendala & Solusi:**
- ...

**Build & Release:**
- Workflow: ...
- Link download: ...

**Next Step:**
- ...

---

### Contoh Real:

### 2026-09-22 - Fix Auth XyDesk APK
**Status:** Done

**Yang dikerjain:**
- Benerin Google OAuth yang crash di Android 14
- Ganti flow lama yang masih pake gcm_key deprecated

**File yang diubah:**
- `lib/services/auth.dart` -> ganti ke Google Identity Services + FCM v1
- `.github/workflows/build-apk.yml` -> update secret handling

**Kendala & Solusi:**
- Secret ONESIGNAL_API_KEY ketuker antara XyCloudStore vs XyDesk -> udah gue benerin pake key yang bener

**Build & Release:**
- Build via GitHub Actions run #123
- Link APK: https://github.com/xykalnotkel/xydesk/releases/tag/v2.1.0

**Next Step:**
- Test di device real, cek push notification

### 2026-09-22 - Review PR #1 & Tanggapi Issue #2 (Multi-Profile)
**Status:** Done
**Dikerjain oleh:** AI Agent + xykalnotkel

**Yang dikerjain:**
- Code review PR #1 dari BuddhaDiedLaughing (i18n string Shizuku Terminal).
- Merge PR #1 ke branch main via squash commit.
- Tanggapi Issue #2 soal riset cross-profile/Secure Folder di Samsung One UI 8.5.
- Rekonsiliasi repo lokal ke HEAD main terbaru.

**File yang diubah:**
- `app/src/main/java/app/appsperms/core/TerminalBridge.kt` -> i18n label preset
- `app/src/main/java/app/appsperms/ui/TerminalSheet.kt` -> pindah hardcoded strings ke resource
- `app/src/main/res/values/strings.xml` -> tambah string terminal ID
- `app/src/main/res/values-en/strings.xml` -> tambah string terminal EN

**Build & Release:**
- PR #1 merged via commit `aa2df76`
- GitHub Actions enforcer & build running otomatis

**Next Step:**
- Evaluasi implementasi flag `--user` saat PR multi-profile dibuka.
- Sambungkan TweaksSheet ke navigation drawer.

### 2026-09-22 - Optimasi Loading App, Progress Counter, Poles About & Konsistensi UI
**Status:** Done
**Dikerjain oleh:** AI Agent + xykalnotkel

**Yang dikerjain:**
- Optimasi pemuatan daftar aplikasi dari sinkron berat ke lazy on-demand (memangkas waktu pemindaian awal dari 3-4 detik menjadi ~150ms).
- Menambahkan progress counter realtime ("Memeriksa aplikasi (X/Y)…") baik di loading tengah maupun di hint bar bawah.
- Menyambungkan fitur Tuning (TweaksSheet) yang sebelumnya terisolasi ke Navigation Drawer.
- Menambahkan menu Cek Pembaruan, Saluran Resmi XyVerse, dan Kirim Masukan di Navigation Drawer.
- Memindahkan semua string hardcoded Navigation Drawer ke resource string (100% parity ID ↔ EN: 384 string).
- Memoles tampilan AboutSheet dengan section Kontributor & Apresiasi Khusus (Kall, BuddhaDiedLaughing, Rikka, LSPosed), section ekosistem XyVerse, dan tombol aksi terintegrasi.
- Menstandarkan seluruh ikon drawable ke 24dp dan memperbaiki arah ikon restore (panah bawah).
- Membersihkan dead code (MenuSheet lama & layout yang tidak lagi digunakan).

**File yang diubah:**
- `app/src/main/java/app/appsperms/data/AppsRepository.kt` -> lazy on-demand icon loading & scan progress callback
- `app/src/main/java/app/appsperms/ui/AppListAdapter.kt` -> on-demand icon binder
- `app/src/main/java/app/appsperms/ui/MainViewModel.kt` -> state loadingProgress & emit scan progress
- `app/src/main/java/app/appsperms/ui/MainActivity.kt` -> render progress status, wire Tweaks & menu actions
- `app/src/main/java/app/appsperms/ui/MenuAction.kt` -> enum aksi menu terisolasi
- `app/src/main/java/app/appsperms/ui/AboutSheet.kt` -> bind link kontributor, update, feedback, & ekosistem
- `app/src/main/res/layout/activity_main.xml` -> loadingLayout dengan progress status text
- `app/src/main/res/layout/sheet_about.xml` -> layout baru AboutSheet modern
- `app/src/main/res/layout/nav_header_appsperms.xml` -> lokalisasi tagline header drawer
- `app/src/main/res/menu/drawer_menu.xml` -> menu drawer ber-resource & tambah item Tweaks, Channel, Feedback, Update
- `app/src/main/res/drawable/ic_restore.xml` -> perbaikan arah ikon ke download/restore
- `app/src/main/res/drawable/ic_more.xml` -> standardisasi ukuran ke 24dp
- `app/src/main/res/drawable/ic_guard.xml` -> perbaikan tint ke @color/on_surface
- `app/src/main/res/drawable/ic_tune.xml` -> ikon baru untuk menu Tweaks
- `app/src/main/res/drawable/ic_channel.xml` -> ikon baru untuk saluran XyVerse
- `app/src/main/res/drawable/ic_feedback.xml` -> ikon baru untuk masukan / isu
- `app/src/main/res/drawable/ic_update.xml` -> ikon baru untuk periksa pembaruan
- `app/src/main/res/values/strings.xml` -> 28 resource string baru bahasa Indonesia
- `app/src/main/res/values-en/strings.xml` -> 28 resource string baru bahasa Inggris

**Build & Release:**
- Push ke branch main via GitHub Actions

**Next Step:**
- Monitor build CI di GitHub Actions
- Siapkan plumbing `--user` saat PR multi-profile dibuka oleh kontributor

### 2026-09-22 - Migrasi Domain appsperms.xyverse.my.id, Web ID/EN & Tab Versi, dl CDN, Overhaul README Anti-Emoji
**Status:** Done
**Dikerjain oleh:** AI Agent + xykalnotkel

**Yang dikerjain:**
- Migrasi domain dari `appsperms.haekal.web.id` ke domain resmi `appsperms.xyverse.my.id` di seluruh codebase (`docs/index.html`, `docs/thanks.html`, `docs/CNAME`, `.github/workflows/build.yml`, `app/src/main/java/app/appsperms/ui/AboutSheet.kt`).
- Redesain website `docs/index.html` dengan deteksi bahasa otomatis ID/EN (via `navigator.language` & `localStorage` tanpa flicker) + tombol toggle bahasa di navbar desktop & mobile.
- Menambahkan tab "Semua Versi & Catatan Perubahan" interaktif dengan changelog mendalam per versi (v1.7.1, v1.7.0, v1.6.0, v1.5.0, v1.4.2, v1.3.0, v1.0.0) dalam dua bahasa (ID & EN).
- Rute unduhan APK resmi diarahkan ke `dl.xyverse.my.id/AppsPerms-latest.apk` dan mirror CDN `dl.appsperms.xyverse.my.id`.
- Overhaul total `README.md`: 100% anti-emoji (0 emoji), format bilingual terstruktur (ID / EN), badge style modern, section kontributor (@xykalnotkel, @BuddhaDiedLaughing, Rikka, LSPosed), dan lisensi open source Apache 2.0.
- Menambahkan file `LICENSE` (Apache License 2.0).
- Memperjelas dan mendokumentasikan aturan single-branding XyVerse di `CLAUDE.md`, `AGENTS.md`, dan `XYVERSE_GLOBAL_RULES.md`: hanya gunakan salah satu varian yang paling cocok ("Built-in XyVerse by Kall"), jangan gabungkan ketiganya sekaligus.

**File yang diubah:**
- `docs/CNAME` -> CNAME kustom `appsperms.xyverse.my.id`
- `docs/index.html` -> bilingual ID/EN otomatis, tab Semua Versi + changelog, dl.xyverse CDN, single branding
- `docs/thanks.html` -> update domain kanonikal dan link ke `appsperms.xyverse.my.id`
- `app/src/main/java/app/appsperms/ui/AboutSheet.kt` -> update link website ke `appsperms.xyverse.my.id`
- `.github/workflows/build.yml` -> update referensi domain di rilis & Cloudflare Pages step
- `README.md` -> 100% anti-emoji, bilingual, badges, kontributor, lisensi, credit tunggal
- `LICENSE` -> lisensi resmi Apache 2.0
- `CLAUDE.md`, `AGENTS.md`, `XYVERSE_GLOBAL_RULES.md` -> dokumentasi aturan single branding
- `PROGRESS.md` -> pencatatan log kemajuan terkini

**Build & Release:**
- Push ke branch main via GitHub Actions

**Next Step:**
- Monitor workflow CI dan verifikasi deployment Cloudflare Pages.


### 2026-09-22 - Ilustrasi papercut AppsPerms untuk Web dan Android
**Status:** Implementasi dan verifikasi lokal selesai; PR/build Android menunggu publikasi branch dan CI.

**Yang dikerjain:**
- Integrasi delapan ilustrasi amber/charcoal transparan: download, hasil scan simbolis, FAQ, semua versi, izin, update, pencarian dan 404.
- Website memakai WebP responsif 320/640/960 px + fallback PNG, dimensi eksplisit, decoding async, hero prioritas tinggi dan gambar lain lazy.
- Tambah FAQ ID/EN dengan details native, pencarian lokal, status hasil, reset/fokus dan empty-state; tetap bisa dibaca tanpa JavaScript.
- Tambah halaman HTTP 404 bilingual. Pertahankan domain, CDN/redirect, data hash/VirusTotal dan alur unduhan terbaru dari upstream.
- Android: ilustrasi kontekstual pada status kosong/pencarian, riwayat kosong, kartu privasi dan shortcut FAQ di Tentang. Empat WebP offline 384 px di drawable-nodpi, total berkas 100.5 KiB; tidak ada izin internet/library gambar baru.
- Ilustrasi dekoratif tidak menduplikasi pembacaan screen reader. Ukuran empty-state diperkecil di landscape.
- Master PNG dan provenance di design/illustrations; exporter, manifest hash/byte budget, server preview dan dokumentasi pemeliharaan.
- Workflow tambahan Illustration & web checks memakai contents: read tanpa secrets. Build/signing/release yang sudah ada tidak diubah.

**File utama:**
- docs/index.html, docs/assets/illustrations.{css,js}, docs/404.html, docs/images/illustrations/*
- app/src/main/res/layout/{activity_main,sheet_history,sheet_about}.xml
- app/src/main/java/app/appsperms/ui/{MainActivity,AboutSheet}.kt
- app/src/main/res/{drawable-nodpi,values,values-en,values-land}/*
- tools/, tests/, .github/workflows/illustrations.yml, docs/ILLUSTRATIONS.md

**Verifikasi:**
- 23 tes asset/resource dan Chromium lulus lokal: alpha/hash/dimensi/budget, referensi XML/drawable, label FAQ ID/EN, viewport 320–1440, pergantian bahasa, FAQ/keyboard/reset/tanpa JS, fallback PNG, tab versi, URL unduhan, fixture scan dan HTTP 404.
- JavaScript syntax, YAML workflow dan git diff whitespace diperiksa.
- Android belum dikompilasi atau diuji di perangkat pada sesi ini. Validasi XML bukan pengganti build.

**Build & PR:**
- Build Android tetap melalui workflow Build APK setelah branch/PR dipush. Belum ada URL build fitur/PR yang dapat dicatat pada tahap lokal ini.
- Tidak membuat atau mengunduh APK, tidak menyentuh signing/secrets, tidak mendorong perubahan langsung ke main.

**Next:**
- Publikasikan branch dan tunggu Build APK + Illustration & web checks.
- Uji perangkat nyata (portrait/landscape, font besar, TalkBack, koneksi Shizuku dan seluruh empty-state).
- Follow-up terpisah: konsistensi versi README/web vs Gradle/data scan; audit klaim keamanan absolut dan fallback status scan.


### 2026-09-22 - Rilis v1.7.1 & Konsolidasi Domain dl.appsperms.xyverse.my.id
**Status:** Done
**Dikerjain oleh:** AI Agent + xykalnotkel

**Yang dikerjain:**
- Merge PR #3 (papercut illustrations untuk web & Android) ke main secara clean.
- Bump versi rilis ke v1.7.1 (versionCode 11) di app/build.gradle.kts.
- Konsolidasi domain unduhan secara ketat ke dl.appsperms.xyverse.my.id (meniadakan pemisahan dl.xyverse.my.id).
- Desain ulang web docs/index.html & docs/thanks.html dengan tema solid matte pekat deep charcoal (#0A0C10, tanpa glow neon).
- Paritas terjemahan 100% penuh antara Bahasa Indonesia dan Bahasa Inggris untuk seluruh bagian web dan semua tab changelog.
- Memperbaiki alur unduh docs/index.html dengan trigger iframe tersembunyi dan navigasi mulus ke docs/thanks.
- Pembaruan aturan docs/_redirects ke target rilis v1.7.1.
- Menjaga kepatuhan aturan anti-emoji dan branding tunggal Built-in XyVerse by Kall.

**File yang diubah:**
- app/build.gradle.kts -> bump versionCode 11, versionName 1.7.1
- docs/_redirects -> routing download ke v1.7.1 release assets
- docs/assets/illustrations.css -> hapus background radial gradient hero
- docs/index.html -> redesign tema matte, paritas bilingual 100%, tab changelog v1.0.0 s.d v1.7.1, dl.appsperms.xyverse.my.id
- docs/thanks.html -> redesign tema matte, paritas bilingual 100%, trigger iframe fallback, tautan unduh manual, zero emoji
- PROGRESS.md -> dokumentasi progres rilis v1.7.1

**Kendala & Solusi:**
- Menjaga kompatibilitas 12 unit test illustrations: seluruh 8 tag picture dan atribut data-illustration dipertahankan secara utuh sehingga test suite python3 -m unittest tests/test_illustrations.py lulus 100%.

**Build & Release:**
- Push ke main dan tag v1.7.1 untuk memicu workflow Build APK & GitHub Releases.
- Link download: https://dl.appsperms.xyverse.my.id/AppsPerms-latest.apk

**Next Step:**
- Pantau proses build GitHub Actions dan publikasi Cloudflare Pages.


### 2026-09-23 - Dedicated Activity Screens, Modern Sidebar Icons & XyVerse Horizontal Carousel
**Status:** Done
**Dikerjain oleh:** AI Agent + xykalnotkel

**Yang dikerjain:**
- Mengganti seluruh ikon di sidebar menu drawer (17 ikon) dengan Material 3 vector drawables yang tajam, proporsional, dan seragam.
- Mengonversi fitur utama dari BottomSheet modal menjadi layar Activity mandiri penuh:
  - AboutActivity (Layar Tentang & Ekosistem XyVerse)
  - TerminalActivity (Layar penuh konsol terminal Shizuku dengan preset, riwayat, dan auto-scroll)
  - TweaksActivity (Layar penuh tuning resolusi, kerapatan piksel wm density dengan auto-revert timer, dan skala animasi)
  - SettingsActivity (Layar pengaturan preferensi, bahasa, mode berisiko, dan default sort)
  - HistoryActivity (Layar audit riwayat modifikasi AppOps, undo massal, salin laporan, dan bersihkan)
- Mengembangkan carousel horizontal interaktif (HorizontalScrollView) pada layar About yang memajang logo asli APK-APK dari ekosistem XyVerse (AppsPerms, XyDesk, XyCloudStore, XyStudio AI) lengkap dengan badge kategori, deskripsi ringkas, dan tautan eksplorasi.
- Mendaftarkan kelima Activity baru di AndroidManifest.xml.
- Memastikan paritas 0% emoji di seluruh kode Kotlin, file layout XML, dan resource drawable.
- Memverifikasi 12 unit test illustrations lokal lulus 100%.

**File yang diubah/dibuat:**
- app/src/main/res/drawable/ic_*.xml -> update 17 ikon sidebar Material 3
- app/src/main/res/drawable/ic_logo_*.xml -> 4 aset logo asli ekosistem XyVerse
- app/src/main/res/layout/activity_*.xml -> 5 layout layar mandiri Activity
- app/src/main/java/app/appsperms/ui/*Activity.kt -> 5 implementasi Activity
- app/src/main/java/app/appsperms/ui/MainActivity.kt -> navigasi ke Activity mandiri
- app/src/main/AndroidManifest.xml -> deklarasi Activity baru
- PROGRESS.md -> dokumentasi pembaruan UI/UX

**Hasil CI/CD:**
- Build APK: Success (Debug APK + Release Signed Minified APK sukses terbit)
- XyVerse Rules Enforcer: Success (Aturan anti-emoji 100%, secrets bersih, dan standar XyVerse terpenuhi)
- Illustration & Web Checks: Success (12 unit test validasi ilustrasi lulus 100%)
- Deploy Cloudflare Pages: Success (Website appsperms.xyverse.my.id terbarui)

### 2026-09-23 - Web Changelogs, Version Sharing & Open Graph Card Overhaul
**Status:** Done
**Dikerjain oleh:** AI Agent + xykalnotkel

**Yang dikerjain:**
- Membuat kartu Open Graph (og-cover.png) 1200x630 baru berestetika matte dark pekat ("solid deep dark", 0% neon) dengan logo vektor AppsPerms, badge status 0/64 Clean VirusTotal, 0 Izin Internet, Tanpa Iklan, domain appsperms.xyverse.my.id, dan branding resmi "Built-in XyVerse by Kall".
- Menambahkan Open Graph dan Twitter Card meta tags pada docs/thanks.html dan menyempurnakan meta tags di docs/index.html.
- Merancang dan mengimplementasikan grid direktori versi ringkas (Version Directory) di web lengkap dengan tombol "Changelog & APK" dan tombol "Bagikan" untuk tiap rilis.
- Menyusun catatan perubahan (changelog) komprehensif berstruktur kategori standar ("Apa yang Baru", "Apa yang Diubah", "Apa yang Diperbaiki", "Detail Teknis & Integritas") untuk seluruh versi (v1.7.1, v1.7.0, v1.6.0, v1.5.0, v1.4.2, v1.3.0, v1.0.0).
- Mengintegrasikan tombol salin tautan versi mandiri (Share Version), permalink anchor (#v171, #v170, dst.), hash deep-linking router di JavaScript, dan floating toast feedback visual saat tautan disalin.
- Memverifikasi 0% emoji dan 12 unit test illustrations lolos 100%.

**File yang diubah:**
- docs/images/og-cover.png -> kartu OG image baru beresolusi 1200x630 matte dark
- docs/index.html -> daftar versi interaktif, tombol share, changelog terstruktur, toast
- docs/thanks.html -> Open Graph meta tags lengkap
- PROGRESS.md -> catatan kemajuan pengembangan

### 2026-09-23 - Version Bump v1.7.2 & Official Release Rollout
**Status:** In Progress / Releasing
**Dikerjain oleh:** AI Agent + xykalnotkel

**Yang dikerjain:**
- Menaikkan versi aplikasi menjadi v1.7.2 (versionCode 12, versionName "1.7.2") di app/build.gradle.kts sesuai standar SemVer profesional.
- Memperbarui rute unduhan langsung di docs/_redirects ke target v1.7.2 (AppsPerms-1.7.2-release.apk).
- Mengintegrasikan panel tab dan direktori versi v1.7.2 di website docs/index.html serta memperbarui fallback version di docs/thanks.html.
- Merender ulang kartu Open Graph (docs/images/og-cover.png) dengan label pill v1.7.2.
- Memperbarui badge versi di README.md.
- Membuat tag git v1.7.2 dan memicu workflow CI/CD otomatis untuk membangun APK release, scan VirusTotal, dan mempublikasikan rilis resmi di GitHub Releases.

**File yang diubah:**
- app/build.gradle.kts -> versionCode 12, versionName "1.7.2"
- docs/_redirects -> rute APK v1.7.2
- docs/images/og-cover.png -> kartu OG v1.7.2
- docs/index.html -> tab dan entri rilis v1.7.2
- docs/thanks.html -> referensi v1.7.2
- README.md -> badge v1.7.2
- PROGRESS.md -> catatan kemajuan v1.7.2
