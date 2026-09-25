#!/bin/bash
# Fix AppsPerms website - Anti Suspend
# Jalankan dari root repo xykal/AppPerms

set -e

echo "=== AppsPerms Website Fix - Anti Suspend ==="
echo "Repo: xykal/AppPerms (akun aktif, bukan xykalnotkel yang suspend)"
echo ""

# 1. Backup old files
echo "1. Backup _redirects lama..."
cp docs/_redirects docs/_redirects.bak.$(date +%Y%m%d) 2>/dev/null || true

# 2. Copy fixed files dari AppPerms-Fixed
FIXED_DIR="/home/user/AppPerms-Fixed"

echo "2. Copy fixed _redirects, _headers, index.html, thanks.html..."
cp "$FIXED_DIR/docs/_redirects" docs/_redirects
cp "$FIXED_DIR/docs/_headers" docs/_headers
cp "$FIXED_DIR/docs/index.html" docs/index.html
cp "$FIXED_DIR/docs/thanks.html" docs/thanks.html

echo "3. Buat folder docs/apk/ untuk direct hosting..."
mkdir -p docs/apk

echo "4. Copy APK terbaru ke docs/apk/ kalau ada di out/"
if [ -d "out" ]; then
  cp out/*.apk docs/apk/ 2>/dev/null || echo "  out/*.apk tidak ada, skip"
  # Buat alias latest
  VERSION=$(grep -oP 'versionName\s*=\s*"\K[^"]+' app/build.gradle.kts)
  if [ -f "docs/apk/AppsPerms-$VERSION-release.apk" ]; then
    cp "docs/apk/AppsPerms-$VERSION-release.apk" docs/apk/AppsPerms-latest.apk
    echo "  ✓ Created docs/apk/AppsPerms-latest.apk from v$VERSION"
  fi
  if [ -f "docs/apk/AppsPerms-$VERSION-debug.apk" ]; then
    cp "docs/apk/AppsPerms-$VERSION-debug.apk" docs/apk/AppsPerms-debug-latest.apk
  fi
else
  echo "  Folder out/ tidak ada. Build dulu via ./gradlew :app:assembleRelease"
  echo "  Atau download APK dari release terakhir yang masih bisa diakses"
fi

echo "5. Update virustotal.json dengan info hosting baru..."
# Tidak overwrite hash, cuma tambah field hosting
if [ -f "docs/data/virustotal.json" ]; then
  python3 << 'PY'
import json, pathlib
p = pathlib.Path('docs/data/virustotal.json')
data = json.loads(p.read_text())
data['hosting'] = {
  'primary': 'Cloudflare Pages /apk/',
  'fallback': 'GitHub xykal/AppPerms',
  'anti_suspend': True,
  'fixed_date': '2026-09-25'
}
if 'release' in data and 'direct_url' not in data['release']:
  data['release']['direct_url'] = '/apk/AppsPerms-latest.apk'
if 'debug' in data and 'direct_url' not in data['debug']:
  data['debug']['direct_url'] = '/apk/AppsPerms-debug-latest.apk'
p.write_text(json.dumps(data, indent=2))
print("  ✓ Updated virustotal.json with hosting info")
PY
fi

echo ""
echo "6. Cek hasil..."
ls -lh docs/_redirects docs/_headers docs/apk/ 2>&1 | head -n 20
echo ""
echo "=== FIX SELESAI ==="
echo ""
echo "Langkah selanjutnya:"
echo "1. git add docs/_redirects docs/_headers docs/index.html docs/thanks.html docs/apk/ docs/data/virustotal.json"
echo "2. git commit -m 'fix(web): anti-suspend hosting, APK direct di /apk/ + fallback ke xykal/AppPerms (bukan xykalnotkel)'"
echo "3. git push origin main"
echo "4. Tunggu Cloudflare Pages deploy (atau manual: npx wrangler pages deploy docs --project-name=appsperms)"
echo "5. Test download: https://appsperms.xyverse.my.id/apk/AppsPerms-latest.apk"
echo "6. (Opsional) Setup R2 bucket + Worker di workers/dl-worker/ untuk dl.appsperms.xyverse.my.id"
echo ""
echo "Jika akun xykal juga ke-suspend nanti, hosting di Cloudflare Pages tetap jalan karena APK ada di /apk/ folder"
