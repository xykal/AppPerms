#!/usr/bin/env bash
# Fast, dependency-free release metadata gate used by local development and CI.
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION=$(sed -n 's/^APP_VERSION_NAME=//p' gradle.properties)
VERSION_CODE=$(sed -n 's/^APP_VERSION_CODE=//p' gradle.properties)
if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || [[ ! "$VERSION_CODE" =~ ^[0-9]+$ ]]; then
  echo "::error::APP_VERSION_NAME or APP_VERSION_CODE is invalid in gradle.properties"
  exit 1
fi
printf 'Canonical version: %s (%s)\n' "$VERSION" "$VERSION_CODE"

PUBLISHED_VERSION=$(python3 -c 'import json; print(json.load(open("docs/data/release.json"))["version"])')
README_BADGE=$(grep -oP 'badge/version-\K[0-9]+\.[0-9]+\.[0-9]+' README.md | head -n1 || true)
if [[ -n "$README_BADGE" && "$README_BADGE" != "$PUBLISHED_VERSION" ]]; then
  echo "::error::README badge $README_BADGE does not match published release $PUBLISHED_VERSION"
  exit 1
fi

if [[ "${GITHUB_REF:-}" == refs/tags/v* ]]; then
  TAG=${GITHUB_REF#refs/tags/v}
  TAG_VERSION=${TAG%%-*}
  if [[ "$TAG_VERSION" != "$VERSION" ]]; then
    echo "::error::Release tag $TAG_VERSION does not match APP_VERSION_NAME=$VERSION"
    exit 1
  fi
fi

python3 scripts/check_release_integrity.py
