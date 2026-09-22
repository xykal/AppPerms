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
