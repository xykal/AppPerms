# OverlayOps

Aplikasi Android untuk mengelola **AppOps tersembunyi** — fokus utama: op
`SYSTEM_ALERT_WINDOW` (**Display over other apps**), plus 18 op lain (kamera, mikrofon,
clipboard, lokasi, wakelock, dst).

Terinspirasi dari [App Ops by Rikka](https://appops.rikka.app/). Bedanya: ini versi ringan
(~1.350 baris Kotlin, tanpa Hilt/Room/Compose) yang khusus membedah satu op secara mendalam
dan tetap pakai **Shizuku** supaya jalan tanpa root permanen.

---

## ⬇️ Download

| | |
|---|---|
| **APK langsung (paling cepat)** | **[OverlayOps-1.0.0-debug.apk](https://github.com/xykalnotkel/OverlayOps/releases/download/v1.0.0/OverlayOps-1.0.0-debug.apk)** |
| Halaman release | https://github.com/xykalnotkel/OverlayOps/releases/tag/v1.0.0 |
| Build log CI | https://github.com/xykalnotkel/OverlayOps/actions |
| sha256 | `065c0637b12892c3321c5f8885b388a1d1f355033e4053fa39733ce9d3fdc2c3` · 5,8 MB · debug-signed |

### Soal armeabi-v7a / arm64-v8a

APK ini **tidak mengandung native library (`.so`) sama sekali** — semuanya kode Java/Kotlin.
Buktinya di build log: `mergeDebugNativeLibs NO-SOURCE`.

Artinya **satu APK ini jalan di semua arsitektur**:

| ABI | status |
|---|---|
| `armeabi-v7a` (32-bit ARM) | ✔ jalan |
| `arm64-v8a` (64-bit ARM) | ✔ jalan |
| `x86` / `x86_64` (emulator) | ✔ jalan |

Jadi split per-ABI tidak ada gunanya di sini: file `-armeabi-v7a.apk` dan `-arm64-v8a.apk`
akan **identik byte-per-byte** dengan APK universal ini (beda nama saja), dan malah bikin
bingung saat install. Kalau nanti ada `.so` ditambahkan, workflow
(`.github/workflows/build.yml`) otomatis mendeteksi dan membuat varian per-ABI
lewat `zip -d lib/<abi-lain>/` + re-sign, tanpa perlu diubah.

> **Status:** build CI di GitHub Actions **hijau** (run #3 & #5). Belum diuji di perangkat
> nyata, jadi setelah install buka menu **⫶ → Laporan perangkat** untuk memastikan
> backend-nya `BINDER` dan kode op overlay ter-resolve (harusnya `24`).

---

## 1. Kenapa perlu Shizuku?

`SYSTEM_ALERT_WINDOW` (dan op-op lain) disimpan di `AppOpsService` sebagai "mode" per
(uid, package, op). Untuk membaca/mengubahnya app harus memegang
`MANAGE_APP_OPS_MODES` — permission yang **hanya dipegang shell (uid 2000) dan root**.
App biasa yang memanggil `AppOpsManager.setMode()` untuk paket lain akan kena
`SecurityException`.

Shizuku menyelesaikan ini dengan meminjamkan identitas shell/root lewat binder:
app kita tetap app biasa, tapi panggilan binder-nya dieksekusi sebagai shell.

```
┌──────────────┐   binder via Shizuku   ┌──────────────────┐   asInterface()   ┌─────────────────┐
│  OverlayOps  │ ─────────────────────► │  uid 2000 / 0    │ ────────────────► │  AppOpsService  │
│  (uid 10xxx) │  ShizukuBinderWrapper  │  Shizuku server  │  IAppOpsService   │  (system_server)│
└──────────────┘                        └──────────────────┘                   └─────────────────┘
```

Jadi tidak ada root, tidak ada Magisk, dan tidak ada ADB yang permanen.

## 2. Fitur

| Fitur | Keterangan |
|---|---|
| Tab **Overlay** | Daftar app yang punya op overlay eksplisit atau meminta izin overlay di manifest |
| Tab **Aplikasi** | Semua app terpasang (termasuk app sistem & yang dinonaktifkan) |
| Ubah mode per app | `Allow` / `Ignore` / `Deny` / `Default` / `Foreground` — ketuk chip status, atau tahan lama di baris |
| Mode **Ignore** | Bikin op "dianggap belum dijawab" alih-alih diblokir keras — lebih jarang bikin app crash |
| Detail app | 19 AppOp + status masing-masing, uid, targetSdk, badge app sistem |
| Cari & filter | Cari nama/paket, filter Diizinkan / Diblokir / Default / Sistem |
| Laporan perangkat | Versi Android, status Shizuku, backend aktif, kode op hasil resolusi — buat debug |
| Jalur cadangan | Kalau refleksi binder diblokir ROM, otomatis pindah ke perintah `appops` lewat shell Shizuku |

## 3. Cara pakai

1. **Install Shizuku** (`moe.shizuku.privileged.api`) dari Play Store atau GitHub.
2. **Mulai Shizuku**:
   - *Root*: buka Shizuku → "Start via root".
   - *Tanpa root (Android 11+)*: aktifkan **Wireless debugging**, pairing dari app Shizuku,
     lalu Start. Alternatif klasik lewat kabel:
     `adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh`
3. **Install OverlayOps**: `adb install -r apk/OverlayOps-1.0.0-debug.apk`
   (atau copy APK-nya ke HP dan pasang manual — debug-signed, aman di-sideload).
4. Buka app → kartu **"Sambungkan ke Shizuku"** → **Minta izin** → setujui.
5. Selesai. Ketuk app untuk lihat detail, ketuk chip status (atau tahan lama) untuk ubah mode.

> Catatan: kode op overlay = **24** (`OP_SYSTEM_ALERT_WINDOW`). Setara dengan
> `adb shell appops set --uid <paket> SYSTEM_ALERT_WINDOW allow`.

## 4. Struktur kode

```
app/src/main/java/app/overlayops/
├── core/
│   ├── AppOpsBridge.kt     # refleksi IAppOpsService lewat ShizukuBinderWrapper (jalur utama)
│   ├── ShizukuBridge.kt    # cek/izin Shizuku + shell `appops` (jalur cadangan)
│   ├── OpCatalog.kt        # daftar AppOp + kode AOSP-nya
│   └── OpStatus.kt         # mode 0..4 → ALLOWED/IGNORED/ERRORED/DEFAULT/FOREGROUND
├── data/AppsRepository.kt  # PackageManager + baca status overlay (bulk: `appops query-op`)
├── model/AppEntry.kt
└── ui/                     # MainActivity, MainViewModel, AppListAdapter, ChipStyle
```

Bagian paling menarik ada di `AppOpsBridge.kt`:

```kotlin
val raw: IBinder = SystemServiceHelper.getSystemService("appops")
val asInterface = Class.forName("android.app.AppOpsManager\$IAppOpsService\$Stub")
val svc = asInterface.getMethod("asInterface", IBinder::class.java)
    .invoke(null, ShizukuBinderWrapper(raw))
svc.checkOperation(op, uid, pkg)   // baca
svc.setMode(op, uid, pkg, mode)    // tulis
```

Kelas `IAppOpsService` itu hidden API, jadi sebelum dipakai app memasang
`HiddenApiBypass.addHiddenApiExemptions("Landroid/", ...)` (library LSPosed) —
persis seperti yang dilakukan App Ops aslinya.

## 5. Build sendiri

Butuh JDK 17 + Android SDK (platform 34, build-tools 34.0.0).

```bash
# cara cepat (Linux/mac, macOS pakai JDK 17 dari Homebrew)
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug          # atau: gradle assembleDebug
# hasil: app/build/outputs/apk/debug/app-debug.apk
```

Di `build.sh` sudah ada setup lengkap yang dipakai untuk membuat APK di folder `apk/`.
Kalau dibuka di **Android Studio** (Ladybug+), `local.properties` akan di-sesuaikan otomatis
ke SDK lokal kamu — file itu memang environment-specific.

Dependency: `dev.rikka.shizuku:api:13.1.5`, `:provider:13.1.5`,
`org.lsposed.hiddenapibypass:hiddenapibypass:4.3`, AndroidX + Material3.

## 6. Batasan & rencana lanjutan

- **`Shizuku.newProcess` di-private-kan di API 13** → jalur shell memanggilnya via refleksi.
  Kalau Shizuku mengubah implementasinya, jalur cadangan bisa mati (jalur utama binder tetap jalan).
  Solusi jangka panjang: pindah ke **Shizuku UserService**.
- Refleksi `IAppOpsService` bergantung pada nama method AOSP; ROM yang mengubahnya
  (beberapa ROM Cina) bisa gagal → UI akan menampilkan status "Tidak diketahui".
  Kode op punya fallback hardcoded (overlay = 24) supaya tetap jalan.
- Belum ada: template/batch (misal "blokir overlay semua app user"), backup-restore,
  pencatatan waktu akses terakhir (`noteOperation`) butuh thread watcher,
  dan multi-user (hanya user saat ini).
- Belum ada automated test; verifikasi dilakukan lewat menu Laporan perangkat di HP.

## 7. Privasi

Semua proses jalan lokal di HP. Tidak ada network permission di manifest, tidak ada
analytics, tidak ada data yang keluar dari perangkat.
