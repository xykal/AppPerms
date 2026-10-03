# Security & Code Review - Core Bridges

> Review oleh Arena Agent, 2026-10-03, fokus pada `app/src/main/java/app/appsperms/core/`
> (ShizukuBridge, AppOpsBridge, TerminalBridge, TweaksBridge, GhostGuard) karena area ini
> yang paling sensitif: eksekusi shell dengan hak Shizuku (UID 2000) / root (UID 0) dan
> reflection ke API tersembunyi Android.
>
> Catatan metodologi: sandbox review ini tidak punya JDK/Android SDK maupun akses internet
> umum (hanya proxy git/gh ke GitHub), jadi analisis murni pembacaan kode statis + penelusuran
> histori commit asli (`git fetch` dari GitHub), BUKAN hasil compile/run di device/emulator.
> Semua temuan di bawah sudah dicocokkan dengan 5 file unit test yang sudah ada
> (`AppOpsParserTest`, `WmParserTest`, `OpStatusTest`, `HistoryCodecTest`, `GhostGuardTest`).

## Ringkasan: Tidak ada temuan kritis

Kode di area ini jauh lebih defensif dari rata-rata proyek Shizuku hobi. Tidak ditemukan
command-injection, privilege-escalation, atau kebocoran data yang nyata. Yang ditemukan
cuma observasi/peningkatan minor di bawah.

## 1. ShizukuBridge.shell() - interpolasi string ke `sh -c`

`shell(command: String)` menjalankan `sh -c command` lewat `Shizuku.newProcess()` (reflection).
Fungsi-fungsi seperti `shellSetOp(pkg, shellOp, status)` menyusun command dengan string
interpolation langsung: `"appops set --uid $pkg $shellOp ${OpStatus.shellName(status)}"`.

**Kenapa ini bukan celah (saat ini):**
- `pkg` selalu berasal dari `PackageManager` (daftar paket terpasang di OS), dan nama paket
  Android dibatasi sistem ke `[a-zA-Z0-9_.]` — tidak mungkin memuat metacharacter shell
  (spasi, `;`, backtick, dll). Tidak ada jalur di kode ini yang mengisi `pkg` dari input
  user bebas teks.
- `shellOp` selalu berasal dari `OpCatalog` (konstanta internal tetap), bukan input dinamis.
- Fitur Terminal (`TerminalBridge`) memang **sengaja** menjalankan command bebas dari user —
  itu fitur intinya (seperti LADB/Brevent), bukan bug. Risikonya sudah inheren pada hak
  Shizuku shell/root itu sendiri, bukan pada cara AppsPerms menyusun command.

**Saran non-blocking:** kalau ke depan ada fitur yang menerima nama paket dari sumber yang
tidak dijamin OS (misal import dari file/clipboard backup, lihat poin 3), sebaiknya validasi
format paket (regex `^[a-zA-Z0-9_.]+$`) sebelum dipakai di `shell()` — defense in depth,
bukan karena ada bug yang ditemukan sekarang.

## 2. AppOpsBridge - reflection ke `IAppOpsService`

Reflection dipakai dengan baik: ada daftar `STUB_CANDIDATES` untuk variasi ROM, `findMethod`
toleran terhadap parameter tambahan antar versi Android, dan semua kegagalan ditangkap lewat
`runCatching`/`try-catch` tanpa pernah crash ke UI thread. `HiddenApiBypass` dipasang sedini
mungkin di `Application.onCreate()` — ini penting dan sudah benar, karena pembatasan hidden
API di Android 9+ hanya bisa dibuka sebelum method hidden pertama kali diresolusi.

Tidak ada temuan. Satu catatan kerapuhan yang wajar untuk pendekatan reflection: kalau Google
mengubah signature AIDL `IAppOpsService` secara drastis di rilis Android mendatang, app ini
akan fallback ke "Mode shell" (lewat `ShizukuBridge.shell`) alih-alih binder — sudah ada
fallback-nya, jadi bukan single point of failure.

## 3. HistoryStore / Backup-Restore (via clipboard)

Format backup berbasis teks sederhana (`epochMillis|paket|android:op|DARI|KE`), parser
(`HistoryCodec.decode`) menolak baris yang tidak punya tepat 5 kolom atau enum tidak valid,
dan selalu mengembalikan `null` (skip) alih-alih throw. Ini sudah tepat untuk data yang bisa
"half-write" kalau proses mati mendadak.

**Observasi:** backup/restore ini jadi satu-satunya jalur di mana nama paket *tidak*
dijamin berasal dari `PackageManager` saat ini (user bisa tempel teks backup apa saja ke
clipboard). Sejauh ini nama paket dari situ dipakai untuk *query* AppOps (baca/tulis lewat
`appops set/get`), bukan dieksekusi sebagai command terpisah, jadi tetap aman. Tapi ini
alasan bagus untuk menambahkan validasi format paket di langkah 1 di atas sebagai
pencegahan, bukan perbaikan darurat.

## 4. TweaksBridge - `wm size` / `wm density` / animasi

Semua command dijalankan serial lewat satu executor (mencegah race antara "set" dan
auto-revert), dibatasi rentang aman (`MIN_DENSITY..MAX_DENSITY`, `MIN_DIMENSION`, maksimal
2x resolusi fisik — lihat `WmParser.validateSize/validateDensity`), dan safety-net
auto-revert 15 detik diimplementasikan lewat `CountDownTimer` yang disimpan di objek
singleton (`TweaksSheet.pendingConfirm`), sehingga tetap jalan walau `Activity` di-recreate
akibat perubahan resolusi itu sendiri. Ini solusi yang tepat untuk masalah nyata (banyak
tool sejenis bikin user terkunci di resolusi aneh tanpa cara mundur).

Tidak ada temuan.

## 5. GhostGuard / GhostGuardService / GhostGuardReceiver - BUKAN dead code

Sekilas terlihat seperti kode mati (service `enabled=false` di manifest, semua method di
`Settings.kt` yang berhubungan "guard" sudah dipaksa jadi no-op/`false`). Setelah ditelusuri,
ini **bukan sisa yang lupa dibersihkan**, tapi migrasi yang disengaja:

- `AppsPermsApp.onCreate()` secara eksplisit memanggil `GhostGuardService.syncFromSettings()`
  dan `stopService()` setiap kali app start, untuk memastikan user yang upgrade dari versi
  pre-v1.5 (saat fitur overlay "anti ghost-touch" masih aktif) tidak punya service/overlay
  nyangkut yang bikin HP lag/panas.
- `GhostGuard.kt` (objek logika murni band/geometri) masih diuji oleh `GhostGuardTest.kt` dan
  masih dipakai `Settings.kt` untuk membaca preferensi lama (`Side` enum) demi kompatibilitas
  migrasi, walau nilainya sekarang selalu konstan/aman.
- Komponen manifest (`GhostGuardService`, `GhostGuardReceiver`) tetap dideklarasikan
  (`enabled=false`) supaya PendingIntent/alarm lama dari instalasi sebelum upgrade tidak
  menyebabkan `ComponentNotFoundException`.

**Rekomendasi:** jangan dihapus sampai cukup yakin tidak ada lagi populasi user yang upgrade
dari versi pre-v1.5 (bisa dicek dari telemetry kalau ada, atau cukup tunggu beberapa rilis
mayor lagi). Kalau nanti dihapus, hapus sekaligus: `GhostGuard.kt`, `GhostGuardService.kt`,
`GhostGuardReceiver.kt`, entri manifest terkait, `GhostGuardTest.kt`, dan bagian
"DEPRECATED"/no-op di `Settings.kt` — supaya tidak ada sisa setengah-setengah.

## 6. Parser murni (AppOpsParser, WmParser, HistoryCodec)

Ketiganya sengaja dipisah tanpa import Android (bisa diuji JVM biasa) dan sudah ditest cukup
baik: toleran format ROM yang beda-beda, tidak pernah throw ke pemanggil (selalu
null/skip/default untuk input tak terduga), dan punya batas aman eksplisit (regex ketat
`OP_NAME`, rentang density/dimension). Tidak ada temuan.

## Kesimpulan

Tidak ada perbaikan kode yang mendesak dari review ini — kualitas defensive coding di core
bridge sudah di atas rata-rata untuk ukuran proyek hobi. Dua tindak lanjut konkret yang
sudah dikerjakan di sesi yang sama (lihat `PROGRESS.md`):

1. `scripts/check-version-sync.sh` + `.github/workflows/version-sync.yml` — cegah drift
   versi README/ABOUT vs `app/build.gradle.kts` terulang (akar masalah dari temuan
   dokumentasi sebelumnya, lihat commit `docs: sync README/ABOUT ...`).
2. Dokumen ini — supaya alasan "GhostGuard tidak dihapus" terdokumentasi dan tidak
   disalahartikan sebagai dead code oleh kontributor baru di masa depan.
