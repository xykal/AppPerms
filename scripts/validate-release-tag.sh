#!/usr/bin/env bash
# Validate the protected tag and monotonic Android release identity.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ "${GITHUB_REF:-}" != refs/tags/v* ]]; then
  echo "Release-tag validation skipped outside a tag workflow."
  exit 0
fi

TAG=${GITHUB_REF#refs/tags/}
RAW=${TAG#v}
VERSION=${RAW%%-*}
CHANNEL=stable
if [[ "$RAW" != "$VERSION" ]]; then CHANNEL=${RAW#*-}; fi
CANONICAL_VERSION=$(sed -n 's/^APP_VERSION_NAME=//p' gradle.properties)
CURRENT_CODE=$(sed -n 's/^APP_VERSION_CODE=//p' gradle.properties)

[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]
[[ "$CHANNEL" =~ ^[a-z][a-z0-9.-]*$ ]]
[[ "$CURRENT_CODE" =~ ^[0-9]+$ ]]
[[ "$VERSION" == "$CANONICAL_VERSION" ]]
EXPECTED_TAG="v$VERSION"
if [[ "$CHANNEL" != stable ]]; then EXPECTED_TAG="v$VERSION-$CHANNEL"; fi
[[ "$TAG" == "$EXPECTED_TAG" ]]

git rev-parse --verify --quiet "$TAG^{commit}" >/dev/null
TAG_COMMIT=$(git rev-parse "$TAG^{commit}")
if [[ "$TAG_COMMIT" != "$(git rev-parse HEAD)" ]]; then
  echo "::error::Checked-out commit is not the commit referenced by $TAG"
  exit 1
fi

MAIN_REF=refs/remotes/origin/main
if ! git show-ref --verify --quiet "$MAIN_REF" ||
    ! git merge-base --is-ancestor "$TAG_COMMIT" "$MAIN_REF"; then
  echo "::error::Release tag must reference a commit reachable from origin/main"
  exit 1
fi

MAX_PREVIOUS_CODE=0
LATEST_STABLE_VERSION=
while IFS= read -r previous_tag; do
  [[ "$previous_tag" == "$TAG" ]] && continue
  [[ "$previous_tag" =~ ^v([0-9]+\.[0-9]+\.[0-9]+)(-[a-z][a-z0-9.-]*)?$ ]] || continue
  previous_version=${BASH_REMATCH[1]}
  previous_suffix=${BASH_REMATCH[2]:-}
  previous_props=$(git show "$previous_tag:gradle.properties" 2>/dev/null || true)
  previous_code=$(sed -n 's/^APP_VERSION_CODE=//p' <<< "$previous_props")
  if [[ ! "$previous_code" =~ ^[0-9]+$ ]]; then
    previous_build=$(git show "$previous_tag:app/build.gradle.kts" 2>/dev/null || true)
    previous_code=$(sed -nE 's/.*(versionCodeFinal|versionCode)[^0-9]*([0-9]+).*/\2/p' <<< "$previous_build" | head -n1)
  fi
  if [[ "$previous_code" =~ ^[0-9]+$ ]] && (( previous_code > MAX_PREVIOUS_CODE )); then
    MAX_PREVIOUS_CODE=$previous_code
  fi
  if [[ -z "$previous_suffix" ]]; then
    if [[ -z "$LATEST_STABLE_VERSION" ]] || [[ "$(printf '%s\n%s\n' "$LATEST_STABLE_VERSION" "$previous_version" | sort -V | tail -n1)" == "$previous_version" ]]; then
      LATEST_STABLE_VERSION=$previous_version
    fi
  fi
done < <(git tag --list 'v*')

if (( CURRENT_CODE <= MAX_PREVIOUS_CODE )); then
  echo "::error::APP_VERSION_CODE=$CURRENT_CODE must be greater than previous maximum $MAX_PREVIOUS_CODE"
  exit 1
fi
if [[ -n "$LATEST_STABLE_VERSION" ]]; then
  highest=$(printf '%s\n%s\n' "$LATEST_STABLE_VERSION" "$VERSION" | sort -V | tail -n1)
  if [[ "$highest" != "$VERSION" || "$LATEST_STABLE_VERSION" == "$VERSION" ]]; then
    echo "::error::Release version $VERSION must be newer than stable $LATEST_STABLE_VERSION"
    exit 1
  fi
fi
VERSION_PATTERN=${VERSION//./\\.}
if ! grep -Eq "^## (\\[)?${VERSION_PATTERN}(\\])?( - |$)" CHANGELOG.md; then
  echo "::error::CHANGELOG.md has no release heading for $VERSION"
  exit 1
fi

printf 'Validated protected release tag %s at %s; version code %s > %s.\n' \
  "$TAG" "$TAG_COMMIT" "$CURRENT_CODE" "$MAX_PREVIOUS_CODE"
