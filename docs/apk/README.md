# APK Hosting - Fixed System (Anti-Suspend)

Folder ini adalah **primary hosting** untuk APK AppsPerms, tidak lagi bergantung ke GitHub Releases dari akun yang ke-suspend.

## Kenapa perlu fix?

Sebelumnya:
- `_redirects` mengarah ke `https://github.com/xykalnotkel/OverlayOps/releases/...`
- Akun `xykalnotkel` ke-suspend / 404
- Hasil: `dl.appsperms.xyverse.my.id/AppsPerms-latest.apk` -> 404

Sekarang:
- APK ditaruh langsung di `docs/apk/` 
- Cloudflare Pages akan serve file ini secara langsung dari edge (global CDN)
- GitHub Releases di `xykal/AppPerms` jadi **mirror cadangan**, bukan primary

## Cara pakai

1. Setelah build APK (via GitHub Actions), copy APK ke folder ini:

```bash
cp out/AppsPerms-1.7.2-release.apk docs/apk/
cp out/AppsPerms-1.7.2-debug.apk docs/apk/
cp out/AppsPerms-1.7.2-release.apk docs/apk/AppsPerms-latest.apk
cp out/AppsPerms-1.7.2-debug.apk docs/apk/AppsPerms-debug-latest.apk
```

2. Commit & push ke `main` -> otomatis deploy ke Cloudflare Pages

3. File akan tersedia di:
- `https://appsperms.xyverse.my.id/apk/AppsPerms-latest.apk`
- `https://appsperms.xyverse.my.id/apk/AppsPerms-1.7.2-release.apk`
- `https://dl.appsperms.xyverse.my.id/AppsPerms-latest.apk` (via _redirects 200, bukan 302 ke GitHub)

## Keuntungan

- **Tidak bergantung GitHub**: Kalau GitHub suspend lagi, download tetap jalan dari Cloudflare edge
- **Lebih cepat**: Cloudflare Pages punya cache global, user Indonesia dapat edge Singapura
- **Fallback otomatis**: JS di index.html coba mirror lokal dulu, kalau gagal baru ke GitHub
- **R2 Ready**: Bisa juga upload ke R2 bucket untuk file >25MB (kalau nanti ada expansion files)

## Ukuran limit

Cloudflare Pages: max 25MB per file, APK AppsPerms ~2MB jadi aman.

Kalau mau pakai R2 untuk unlimited:
- Buat R2 bucket `appsperms-apk`
- Upload via `wrangler r2 object put`
- Set custom domain `dl.appsperms.xyverse.my.id` ke R2 bucket

Lihat `workers/dl-worker/` untuk Worker yang serve dari R2 + fallback.
