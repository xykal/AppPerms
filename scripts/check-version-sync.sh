#!/usr/bin/env bash
# Cegah drift versi seperti yang terjadi sebelum 2026-10-03:
# app/build.gradle.kts sudah di v1.8.0, tapi README.md/ABOUT.md masih
# menampilkan badge/klaim v1.7.2 (ketinggalan karena tidak ada yang mengecek).
#
# Script ini MURNI teks (grep/sed), tidak butuh JDK atau Android SDK,
# jadi bisa jalan cepat di CI sebelum tahap build Gradle yang berat,
# atau dipanggil manual: ./scripts/check-version-sync.sh
set -euo pipefail
cd "$(dirname "$0")/.."

fail=0

# 1) Ambil versi default dari app/build.gradle.kts, contoh:
#    } ?: "1.8.0"
GRADLE_VERSION=$(grep -oP '\}\s*\?:\s*"\K[0-9]+\.[0-9]+\.[0-9]+' app/build.gradle.kts | head -n1 || true)
if [ -z "$GRADLE_VERSION" ]; then
  echo "::error::Tidak bisa membaca versionNameFinal default dari app/build.gradle.kts"
  exit 1
fi
echo "Versi sumber kebenaran (app/build.gradle.kts): $GRADLE_VERSION"

# 2) Badge versi di README.md: img.shields.io/badge/version-X.Y.Z-...
README_BADGE=$(grep -oP 'badge/version-\K[0-9]+\.[0-9]+\.[0-9]+' README.md | head -n1 || true)
if [ -z "$README_BADGE" ]; then
  echo "::warning::Tidak menemukan badge versi di README.md, lewati cek ini"
elif [ "$README_BADGE" != "$GRADLE_VERSION" ]; then
  echo "::error::README.md badge versi ($README_BADGE) tidak sama dengan app/build.gradle.kts ($GRADLE_VERSION)"
  fail=1
else
  echo "README.md badge versi OK ($README_BADGE)"
fi

# 3) Tag git terbaru (kalau ada, dan HEAD memang di tag itu) juga harus cocok.
#    Dibuat non-fatal kalau tidak ada tag (mis. clone dangkal / branch kerja).
LATEST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || true)
if [ -n "$LATEST_TAG" ]; then
  TAG_VERSION="${LATEST_TAG#v}"
  TAG_VERSION="${TAG_VERSION%%-*}"
  if [ "$TAG_VERSION" != "$GRADLE_VERSION" ]; then
    echo "::warning::Tag git terbaru ($LATEST_TAG) berbeda dari app/build.gradle.kts ($GRADLE_VERSION) - wajar kalau belum sempat dirilis/ditag"
  else
    echo "Tag git terbaru OK ($LATEST_TAG)"
  fi
fi

if [ "$fail" -ne 0 ]; then
  echo ""
  echo "Perbaiki dengan menyamakan badge/klaim versi di README.md ke $GRADLE_VERSION,"
  echo "atau perbarui app/build.gradle.kts kalau README sudah benar."
  exit 1
fi

echo "Semua cek versi lulus."
