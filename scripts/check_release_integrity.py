#!/usr/bin/env python3
"""Fail closed when app, site, redirects, checksums, and release metadata drift."""
from __future__ import annotations

import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
ERRORS: list[str] = []


def fail(message: str) -> None:
    ERRORS.append(message)


def properties(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            result[key.strip()] = value.strip()
    return result


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


version = properties(ROOT / "gradle.properties")
expected_version = version.get("APP_VERSION_NAME")
expected_code = version.get("APP_VERSION_CODE")
if not expected_version or not re.fullmatch(r"\d+\.\d+\.\d+", expected_version):
    fail("gradle.properties APP_VERSION_NAME is missing or invalid")
if not expected_code or not expected_code.isdigit():
    fail("gradle.properties APP_VERSION_CODE is missing or invalid")

manifest_path = DOCS / "data/release.json"
try:
    release = json.loads(manifest_path.read_text(encoding="utf-8"))
except (OSError, json.JSONDecodeError) as error:
    fail(f"cannot read release manifest: {error}")
    release = {}

if release.get("schema_version") != 1:
    fail("release.json schema_version must be 1")
def version_tuple(value: str) -> tuple[int, int, int]:
    try:
        parts = tuple(int(part) for part in value.split("."))
        if len(parts) != 3:
            raise ValueError
        return parts
    except (AttributeError, ValueError):
        fail(f"invalid semantic version: {value!r}")
        return (0, 0, 0)

published_version = release.get("version")
published_code = str(release.get("version_code", ""))
if release.get("channel") != "stable":
    fail("the public Pages fallback must describe only the stable channel")
if version_tuple(published_version) > version_tuple(expected_version):
    fail(f"published release {published_version} cannot be newer than app version {expected_version}")
if published_code.isdigit() and expected_code and int(published_code) > int(expected_code):
    fail(f"published version code {published_code} cannot be newer than app code {expected_code}")
if published_version != expected_version:
    print(f"::notice::App development version is {expected_version}; published site release remains {published_version}")
if release.get("tag") != f"v{published_version}":
    fail("release.json tag does not match its published version")

artifacts = release.get("artifacts", {})
if set(artifacts) != {"release"}:
    fail("public release.json must contain exactly one release artifact; debug builds are CI-only")
stable = artifacts.get("release", {})
if not isinstance(stable, dict):
    fail("release.json is missing artifacts.release")
    stable = {}
filename = stable.get("filename", "")
digest = stable.get("sha256", "")
size = stable.get("bytes")
expected_filename = f"AppsPerms-{published_version}-stable-universal-release.apk"
if filename != expected_filename:
    fail(f"release filename {filename!r} does not match {expected_filename!r}")
if not re.fullmatch(r"[a-f0-9]{64}", digest):
    fail("invalid release SHA-256")
if not isinstance(size, int) or size < 1:
    fail("invalid release size")
if stable.get("public") is not True or stable.get("package_id") != "app.appsperms":
    fail("release artifact must be public and use package app.appsperms")
expected_cert = (ROOT / "app/signing-certificate.sha256").read_text(encoding="utf-8").strip()
if not re.fullmatch(r"[a-f0-9]{64}", expected_cert):
    fail("app/signing-certificate.sha256 is invalid")
if stable.get("signing_certificate_sha256") != expected_cert:
    fail("release signing-certificate fingerprint does not match the pinned identity")
expected_r2_key = f"releases/{published_version}/{expected_filename}"
if stable.get("r2_key") != expected_r2_key:
    fail("release artifact R2 key is not the canonical immutable stable key")
urls = stable.get("urls", [])
canonical_url = (
    f"https://github.com/xykal/AppPerms/releases/download/"
    f"v{published_version}/{expected_filename}"
)
if urls != [canonical_url]:
    fail("release artifact must expose exactly the canonical immutable GitHub URL")

site_apks = sorted((DOCS / "apk").glob("*.apk"))
if site_apks:
    fail("APK binaries must live in R2/GitHub Releases, not the Pages source tree")
scan = release.get("virustotal", {})
if scan.get("sha256") != stable.get("sha256"):
    fail("VirusTotal SHA-256 does not match the release artifact")
if scan.get("status") == "complete":
    if scan.get("checked") is not True or not isinstance(scan.get("detected"), int) or not isinstance(scan.get("total"), int) or scan.get("total", 0) <= 0:
        fail("complete VirusTotal status lacks a completed count")
elif scan.get("checked") is not False or scan.get("detected") is not None or scan.get("total") is not None:
    fail("incomplete VirusTotal status must use checked=false and null counts")

try:
    legacy_scan = json.loads((DOCS / "data/virustotal.json").read_text(encoding="utf-8"))
except (OSError, json.JSONDecodeError) as error:
    fail(f"cannot read legacy VirusTotal compatibility metadata: {error}")
    legacy_scan = {}
legacy_artifact = legacy_scan.get("release", {})
legacy_result = legacy_scan.get("virustotal", {})
if legacy_scan.get("version") != published_version or legacy_scan.get("channel") != "stable":
    fail("legacy VirusTotal metadata version/channel does not match release.json")
if legacy_artifact.get("file") != filename or legacy_artifact.get("sha256") != digest:
    fail("legacy VirusTotal artifact identity does not match release.json")
if legacy_artifact.get("direct_url") != canonical_url:
    fail("legacy VirusTotal metadata does not use the canonical release URL")
for field in ("status", "checked", "detected", "total", "permalink"):
    if legacy_result.get(field) != scan.get(field):
        fail(f"legacy VirusTotal field {field!r} does not match release.json")

texts = {
    "README.md": (ROOT / "README.md").read_text(encoding="utf-8"),
    "docs/index.html": (DOCS / "index.html").read_text(encoding="utf-8"),
    "docs/thanks.html": (DOCS / "thanks.html").read_text(encoding="utf-8"),
    "app About layout": (ROOT / "app/src/main/res/layout/activity_about.xml").read_text(encoding="utf-8"),
    "Android strings": "\n".join(
        path.read_text(encoding="utf-8")
        for path in (ROOT / "app/src/main/res").glob("values*/strings.xml")
    ),
}
for name, text in texts.items():
    for false_claim in (
        "0/67 Clean",
        "0 / 67",
        "100% Clean",
        "100% Bersih",
        "100% Undetected",
        "VirusTotal-scanned APK",
        "APK dipindai VirusTotal",
    ):
        if false_claim.lower() in text.lower():
            fail(f"{name} contains unverified scan claim: {false_claim}")

index = texts["docs/index.html"]
thanks = texts["docs/thanks.html"]
for page_name, page in (("README", texts["README.md"]), ("index", index), ("thanks", thanks)):
    if "googletagmanager.com" in page or "gtag(" in page:
        fail(f"{page_name} loads Google Analytics despite the tracker-free policy")
    if "dl.appsperms.xyverse.my.id" in page:
        fail(f"{page_name} references the unavailable legacy download hostname")
if stable.get("filename") not in index or stable.get("sha256") not in index:
    fail("index.html does not contain the current release filename and SHA-256 fallback")
if stable.get("filename") not in thanks or stable.get("sha256") not in thanks:
    fail("thanks.html does not contain the current release filename and SHA-256 fallback")
if not re.search(r'<a id="btnRelease"[^>]+href="' + re.escape(canonical_url) + r'"', index):
    fail("index.html primary download fallback does not use the canonical release URL")
if not re.search(r'<a id="btnManualDl"[^>]+href="' + re.escape(canonical_url) + r'"', thanks):
    fail("thanks.html primary download fallback does not use the canonical release URL")
if canonical_url not in texts["README.md"]:
    fail("README.md does not link to the canonical published release")
release_page_url = f"https://github.com/xykal/AppPerms/releases/tag/v{published_version}"
if not re.search(r'<a id="btnMirror"[^>]+href="' + re.escape(release_page_url) + r'"', index):
    fail("index.html release-notes link does not match the published version")

for alias in ("AppsPerms-latest.apk", "AppsPerms-debug-latest.apk"):
    if (DOCS / "apk" / alias).exists():
        fail(f"mutable APK alias must be a redirect, not a cached file: {alias}")

redirects = (DOCS / "_redirects").read_text(encoding="utf-8")
if f"/apk/AppsPerms-latest.apk {canonical_url} 302" not in redirects:
    fail("stable alias redirect does not point to the canonical immutable release URL")
if "AppsPerms-debug-latest.apk" in redirects:
    fail("debug alias must not be published")
if (DOCS / "apk/SHA256SUMS").exists():
    fail("checked-in SHA256SUMS must not describe binaries that are no longer stored in Git")

try:
    sitemap = ET.parse(DOCS / "sitemap.xml")
    namespace = "{http://www.sitemaps.org/schemas/sitemap/0.9}"
    for node in sitemap.findall(f"{namespace}url/{namespace}loc"):
        parsed = urlparse(node.text or "")
        if parsed.netloc != "appsperms.xyverse.my.id" or parsed.fragment:
            fail(f"invalid sitemap URL: {node.text}")
except ET.ParseError as error:
    fail(f"sitemap.xml is invalid: {error}")

uploader = (ROOT / "scripts/upload-to-r2.js").read_text(encoding="utf-8")
if re.search(r"Authorization:\s*Bearer\s*\$\{", uploader):
    fail("R2 uploader prints or interpolates a bearer token")

if ERRORS:
    for message in ERRORS:
        print(f"::error::{message}")
    print(f"Release integrity check failed with {len(ERRORS)} error(s).")
    sys.exit(1)
print(f"Release integrity OK: published v{published_version} ({published_code}); app v{expected_version} ({expected_code}); canonical artifact is external and certificate-pinned.")
