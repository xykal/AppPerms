# OverlayOps

Aplikasi Android untuk mengelola **AppOps tersembunyi** — fokus utama op
`SYSTEM_ALERT_WINDOW` (**Display over other apps**), plus 18 op lain (kamera, mikrofon,
clipboard, lokasi, wakelock, dst).

Terinspirasi dari [App Ops by Rikka](https://appops.rikka.app/). Bedanya: versi ringan
(~1.700 baris Kotlin, tanpa Hilt/Room/Compose) yang khusus membedah overlay secara mendalam,
dan **tanpa root permanen** karena memakai Shizuku.

---

## ⬇️ Download

| | |
|---|---|
| **Release APK (disarankan)** | **[OverlayOps-1.1.0-release.apk](https://github.com/xykalnotkel/OverlayOps/releases/download/v1.1.0/OverlayOps-1.1.0-release.apk)** |
| Debug APK (troubleshooting) | [OverlayOps-1.1.0-debug.apk](https://github.com/xykalnotkel/OverlayOps/releases/download/v1.1.0/OverlayOps-1.1.0-debug.apk) |
| Halaman release | https://github.com/xykalnotkel/OverlayOps/releases |
| Build log CI | https://github.com/xykalnotkel/OverlayOps/actions |

**Versi release** sudah di-minify R8 + resource shrink (**1,9 MB**, dari 5,8 MB versi debug)
dan ditandatangani keystore resmi:

```
SHA-256 : 82:A0:2C:AE:E2:7B:8B:9B:09:E7:00:B8:31:3D:D4:AD:E5:CF:9B:94:6B:01:BE:6C:54:EC:33:98:4A:B0:04:30
SHA-1   : 99:55:67:5C:19:0C:BB:4B:0F:D5:08:94:74:08:D2:B8:92:22:69:FF
```

Pakai SHA-1 di atas kalau perlu daftar di Google Cloud Console (OAuth client Android /
Firebase) — **bukan** SHA debug lagi.

> ⚠️ Signature release berbeda dari build debug sebelumnya → **uninstall dulu versi debug**,
> baru pasang versi release. Setelah install ulang, kasih izin Shizuku sekali lagi
> (dibuka otomatis lewat dialog saat pertama jalan).

### Soal armeabi-v7a / arm64-v8a

APK ini **tidak punya native library (`.so`)** — semua kode Java/Kotlin
(bukti di build log: `mergeReleaseNativeLibs NO-SOURCE`). Artinya satu APK jalan di
`armeabi-v7a`, `arm64-v8a`, dan `x86/x86_64`. Varian per-ABI akan identik byte-per-byte,
jadi tidak dibuatkan. Workflow otomatis bikin split per-ABI kalau nanti ada `.so`.

---

## 🆕 Yang baru di 1.1.0

**Perbaikan penting — pesan status yang salah**
Sebelumnya app sering bilang *"Izin Shizuku belum diberikan"* padahal izinnya sudah kamu kasih.
Penyebabnya: kalau refleksi ke `IAppOpsService` gagal (dibatasi ROM/Android versi tertentu),
app diam-diam jalan lewat jalur shell tapi UI-nya menyalahkan izin. Sekarang status dipisah jelas
dan pesannya jujur:

| Chip | Artinya |
|---|---|
| 🔴 `Shizuku mati` | Shizuku belum dijalankan |
| 🔴 `Izin belum` | Shizuku jalan, app belum diizinkan |
| 🟠 `Mode shell` | Izin ada, binder gagal → otomatis pakai perintah `appops` (tetap jalan!) |
| 🟢 `Shizuku · shell` / `· root` | Semua lancar lewat binder |

Ditambah: status diperiksa ulang tiap app dibuka kembali (`onResume`), jadi kalau izin diberikan
dari app Shizuku (di background) chip-nya langsung berubah tanpa restart. Menu **⫶ → Laporan
perangkat** kini menampilkan jalur mana yang aktif + error persisnya.

**Arsitektur jadi shell-first**
Baca/tulis kini default lewat perintah resmi `appops` (stabil di semua ROM), binder dipakai
kalau refleksi berhasil. Efeknya:
- daftar app dimuat lewat 4 perintah (`appops query-op`) untuk semua paket — bukan 1 perintah per app
- detail app = **satu** perintah (`appops get <pkg>`) untuk semua op, dulu 19 panggilan
- tulis pakai `appops set --uid <pkg> <OP> <mode>`

**Filter & pemisah (request kamu)**
- Chip filter **tipe**: `Semua` / `Terinstall` / `Sistem`
- Chip filter **status**: `Semua status` / `Diizinkan` / `Diblokir` / `Default`
- **Pemisah section** "APP TERINSTALL (n)" dan "APP SISTEM (n)" dengan pembatas + jumlah
- Menu **⫶ → Urutkan berdasarkan**: Nama ⇄ Status (yang eksplisit naik ke atas)

**UX**
- **Bottom sheet** ubah mode dengan penjelasan tiap opsi (allow/ignore/deny/default/foreground)
  dan peringatan khusus kalau app-nya memang minta izin overlay
- **Snackbar + tombol Batal (undo)** untuk setiap perubahan — termasuk undo aksi massal
- Tab menampilkan jumlah: `Overlay · 12` / `Semua app · 156`
- Hint bar dinamis: total app · overlay · diizinkan · diblokir, dan progress saat aksi massal
- Empty state yang spesifik (belum tersambung / belum ada data / tidak ada hasil)

**Fitur baru**
- **Aksi massal**: blokir overlay semua app user · izinkan semua yang minta overlay · reset ke default
- **Backup & restore** via clipboard (format `namapaket=status`), restore melewati app yang tak terpasang
- Detail app: copy paket, buka App Info, buka Settings overlay, lintas 19 AppOp

**Optimasi**
- Release di-minify R8 + shrink resources: **5,8 MB → 1,9 MB**
- Ikon app di-cache (`LruCache`) — refresh tidak lagi load ulang ratusan ikon
- `itemAnimator` dimatikan + `setHasFixedSize`, binder call dihemat

---

## 🔧 Kenapa perlu Shizuku?

`SYSTEM_ALERT_WINDOW` (dan op lain) disimpan di `AppOpsService` sebagai mode per (uid, package, op).
Untuk membacanya app harus memegang `MANAGE_APP_OPS_MODES` — permission yang **hanya dipegang
shell (uid 2000) dan root**. Shizuku meminjamkan identitas itu:

```
┌──────────────┐    perintah `appops` / binder    ┌──────────────────┐        ┌─────────────────┐
│  OverlayOps  │ ───────────────────────────────► │  uid 2000 / 0    │ ─────► │  AppOpsService  │
│  (uid 10xxx) │        via Shizuku server        │  Shizuku server  │        │  (system_server)│
└──────────────┘                                  └──────────────────┘        └─────────────────┘
```

Tanpa root, tanpa Magisk, tanpa ADB permanen. Jalur tulis setara dengan:
`adb shell appops set --uid <paket> SYSTEM_ALERT_WINDOW allow|ignore|deny|default|foreground`

## 🚀 Cara pakai

1. Install **Shizuku** (`moe.shizuku.privileged.api`) — Play Store atau GitHub.
2. Mulai Shizuku:
   - *root*: buka Shizuku → "Start via root"
   - *tanpa root (Android 11+)*: aktifkan **Wireless debugging** → pairing dari app Shizuku → Start
   - *lewat kabel*: `adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh`
3. Install OverlayOps (lihat tabel Download).
4. Buka app → kalau muncul kartu **Sambungkan ke Shizuku** → **Minta izin** → setujui.
5. Ketuk app untuk detail · ketuk chip status atau tahan lama di baris untuk ubah mode.
6. Cek **⫶ → Laporan perangkat** buat memastikan jalur aktif (`binder` atau `shell`).

## 🔍 Troubleshooting

| Gejala | Penyebab & solusi |
|---|---|
| Chip merah **Izin belum**, dialog tidak muncul | Buka app Shizuku → **Authorized applications** → aktifkan OverlayOps. Setelah itu balik ke app (status auto-refresh). |
| Chip oranye **Mode shell** | Refleksi binder diblokir ROM. Semua fitur tetap jalan lewat `appops`; tak perlu diapa-apakan. |
| Chip merah **Akses gagal** | Izin ada tapi binder & shell dua-duanya gagal. Kirim isi **Laporan perangkat** untuk ditelusuri. |
| Perubahan tidak terasa | Beberapa app mewajibkan restart agar override overlay berlaku. Beberapa ROM juga punya "Restricted settings" — keluarkan app-nya dari situ. |
| Status `Tidak diketahui` | Op tidak tersedia di ROM tersebut; kode op di-resolve lewat 3 cara (refleksi `strOpToOp`, peta `opToName`, fallback hardcoded). |

## 🧱 Struktur kode

```
app/src/main/java/app/overlayops/
├── OverlayOpsApp.kt          # pasang HiddenApiBypass sedini mungkin
├── core/
│   ├── AppOpsBridge.kt       # refleksi IAppOpsService via ShizukuBinderWrapper (opsional)
│   ├── ShizukuBridge.kt      # probe status izin + jalur shell `appops` (utama)
│   ├── AccessState.kt        # state koneksi + filter tipe + mode urut
│   ├── OpCatalog.kt          # 19 AppOp + kode AOSP
│   └── OpStatus.kt           # mode 0..4 → ALLOWED/IGNORED/ERRORED/DEFAULT/FOREGROUND
├── data/AppsRepository.kt    # PackageManager, cache ikon, baca/tulis/batch, backup
├── model/AppEntry.kt
└── ui/                       # MainActivity, MainViewModel, AppListAdapter, ModeSheet, ListItems
```

## 🛠️ Build sendiri

Butuh JDK 17 + Android SDK (platform 34, build-tools 34.0.0).

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
bash ./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
```

Build release yang ditandatangani (tanpa menaruh keystore di repo):

```bash
export KEYSTORE_PATH=/path/keystore.jks
export KEYSTORE_PASSWORD=...
export KEY_PASSWORD=...      # PKCS12: sama dengan KEYSTORE_PASSWORD
export KEY_ALIAS=...
bash ./gradlew assembleRelease
```

CI (`.github/workflows/build.yml`) membaca keystore dari GitHub Secrets:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`. Kalau secret belum ada,
build release dilewati dan hanya debug yang dibuat — jadi fork tetap bisa build.

Semua proses jalan lokal di HP: **tidak ada permission internet**, tidak ada analytics.

## 📋 Rencana lanjutan

- Template/batch bernama (mis. "mode hemat baterai": blokir overlay + wakelock sekaligus)
- Riwayat perubahan + waktu akses terakhir per op (`noteOperation` butuh watcher)
- Dukungan multi-user / work profile
- Migrasi binder ke Shizuku UserService (agar tidak bergantung pada refleksi `newProcess`)
- Patch `targetSdk` ke 35/36 untuk Android 15/16
