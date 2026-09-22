# Paper-cut illustrations / Panduan integrasi

## Penempatan

| Aset | Website | Android |
|---|---|---|
| Download | Hero / unduhan APK | Tidak dibundel |
| Security report | Judul Audit Keamanan; data VirusTotal asli tetap terpisah | Tidak dibundel |
| FAQ | Bagian FAQ dengan `details` / `summary` native | Tombol Panduan & FAQ di Tentang |
| Versions | Judul Semua Versi | Riwayat kosong dan daftar belum berisi data |
| Permissions | Judul Fitur | Belum tersambung / belum ada overlay eksplisit; kartu privasi di Tentang |
| Update | Catatan pembaruan manual di Semua Versi | Tidak dibundel |
| Search | Kolom pencarian FAQ | Hasil pencarian / filter kosong |
| Empty / 404 | FAQ tanpa hasil dan halaman HTTP 404 | Tidak dibundel |

FAQ mencari teks Indonesia dan Inggris di sisi klien. Tanpa JavaScript, semua jawaban tetap tersedia dengan elemen `details` native; kontrol pencarian disembunyikan. Tombol reset mengembalikan fokus ke kolom pencarian. FAQ baru memakai pola `.lang-id` / `.lang-en` yang sudah ada.

## Ukuran dan loading

- Master transparan 1024 px: `design/illustrations/*.png`, tidak masuk APK.
- WebP responsif 320 / 640 / 960 px, quality 86 dan alpha lossless.
- PNG transparan 640 px menjadi fallback pada `<picture>`.
- Hero memakai `loading="eager"` dan `fetchpriority="high"`; gambar lain lazy.
- Semua `<img>` memiliki dimensi intrinsik, decoding async, dan ruang layout tetap.
- Empat WebP Android 384 px: `app/src/main/res/drawable-nodpi/illus_*.webp`.
- Total **berkas** WebP Android sekitar **100.5 KiB**; ini bukan hasil pengukuran selisih ukuran APK. APK final harus diukur dari build Actions.
- Budget tes: seluruh varian WebP web < 1400 KiB; empat aset Android < 128 KiB.

`drawable-nodpi` mencegah resampling implisit berdasarkan density resource. `ImageView` menggunakan bounds dp dan `fitCenter`. Ukuran empty-state 88 dp pada portrait dan 48 dp pada landscape. Tidak ada tint pada ilustrasi.

Aset bersifat dekoratif karena judul/penjelasan sudah menyampaikan maknanya: `alt=""` dan `aria-hidden` di web, `contentDescription="@null"` serta `importantForAccessibility="no"` di Android. TalkBack tidak perlu membacakan gambar yang menduplikasi pesan status.

Tidak ada izin INTERNET, image-loader dependency, WebView, animasi terus-menerus, atau perubahan alur AppOps/Shizuku yang ditambahkan. Tombol FAQ Android membuka browser eksternal dan menyebutkan `browser` pada label ID/EN.

## Regenerasi

```sh
python -m pip install -r tools/requirements-illustrations.txt
python tools/prepare_illustrations.py
```

Script membaca master lokal; tidak menghubungi layanan gambar atau mengunduh model. Manifest mencatat dimensi, ukuran dan SHA-256 seluruh hasil. Bila master diganti, jalankan ekspor lagi dan commit manifest bersama turunannya. Latar chroma-key hijau dan model rembg tidak dimasukkan ke repo.

## Preview dan tes

```sh
# Server lokal standar, termasuk HTTP 404 khusus; bukan server produksi.
python tools/serve_docs.py --host 0.0.0.0 --port 8080

# Di terminal terpisah:
python -m playwright install --with-deps chromium
python -m unittest discover -s tests -p 'test_illustrations*.py' -v
```

Opsional: set `ILLUSTRATION_SCREENSHOTS=out/illustration-smoke` untuk screenshot review. Test browser hanya memakai server lokal dan fixture scan; tidak mengunduh APK atau mengubah situs produksi.

Workflow `Illustration & web checks` memeriksa alpha, hash, size budget, referensi gambar, XML, lokalisasi label baru, ID/EN, FAQ/search/reset, tanpa JavaScript, fallback PNG, viewport 320–1440 px, tab versi, tautan unduhan, data scan dan HTTP 404 bertingkat. Workflow hanya membutuhkan `contents: read`, tanpa secrets.

**Batas verifikasi:** tes XML/resource bukan pengganti kompilasi Android. Build dan unit test Android tetap dijalankan oleh workflow `Build APK` yang sudah ada. Review perangkat nyata: Shizuku mati/izin ditolak, query tanpa hasil, tab overlay kosong, riwayat kosong/berisi, Tentang/FAQ, portrait/landscape, font besar dan TalkBack.

Halaman 404 memakai path root sesuai domain kustom di `CNAME`. Perubahan ini tidak mengubah `_redirects`, domain, metadata rilis, endpoint unduhan, data VirusTotal, signing atau workflow rilis.
