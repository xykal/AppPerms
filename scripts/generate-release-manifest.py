#!/usr/bin/env python3
"""Generate release.json from the exact signed APK bytes that will be published."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def digest(path: Path) -> str:
    sha = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            sha.update(chunk)
    return sha.hexdigest()


def gradle_properties() -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if raw.strip() and not raw.lstrip().startswith("#") and "=" in raw:
            key, value = raw.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def normalized_utc_timestamp(value: str | None) -> str:
    if value is None:
        timestamp = datetime.now(timezone.utc)
    else:
        try:
            timestamp = datetime.fromisoformat(value.replace("Z", "+00:00"))
        except ValueError as error:
            raise SystemExit(f"invalid --published-utc timestamp: {value!r}") from error
        if timestamp.tzinfo is None:
            raise SystemExit("--published-utc must include a timezone")
    return timestamp.astimezone(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--release-apk", required=True, type=Path)
    parser.add_argument("--channel", default="stable")
    parser.add_argument("--tag")
    parser.add_argument("--source-commit", default=os.environ.get("GITHUB_SHA", "unknown"))
    parser.add_argument(
        "--published-utc",
        help="Deterministic tag creation time (ISO 8601 with timezone); defaults to now for local use",
    )
    parser.add_argument("--github-repository", default=os.environ.get("GITHUB_REPOSITORY", "xykal/AppPerms"))
    parser.add_argument("--signing-certificate-sha256", required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    props = gradle_properties()
    version = props["APP_VERSION_NAME"]
    version_code = int(props["APP_VERSION_CODE"])
    if not re.fullmatch(r"[a-z][a-z0-9.-]*", args.channel):
        raise SystemExit(f"invalid release channel: {args.channel!r}")
    tag = args.tag or f"v{version}" + ("" if args.channel == "stable" else f"-{args.channel}")
    expected_tag = f"v{version}" + ("" if args.channel == "stable" else f"-{args.channel}")
    if tag != expected_tag:
        raise SystemExit(f"tag {tag!r} does not match version/channel {expected_tag!r}")
    expected_filename = f"AppsPerms-{version}-{args.channel}-universal-release.apk"
    if args.release_apk.name != expected_filename:
        raise SystemExit(
            f"release APK filename {args.release_apk.name!r} does not match {expected_filename!r}"
        )
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", args.github_repository):
        raise SystemExit("invalid GitHub repository name")
    if not re.fullmatch(r"[a-f0-9]{64}", args.signing_certificate_sha256):
        raise SystemExit("invalid signing-certificate SHA-256")
    if not args.release_apk.is_file():
        raise SystemExit("release APK does not exist")

    release_id = version if args.channel == "stable" else f"{version}-{args.channel}"
    urls = [
        f"https://github.com/{args.github_repository}/releases/download/{tag}/{args.release_apk.name}",
    ]

    release_artifact = {
        "public": True,
        "filename": args.release_apk.name,
        "package_id": "app.appsperms",
        "bytes": args.release_apk.stat().st_size,
        "sha256": digest(args.release_apk),
        "signing_certificate_sha256": args.signing_certificate_sha256,
        "r2_key": f"releases/{release_id}/{args.release_apk.name}",
        "urls": urls,
    }
    manifest = {
        "schema_version": 1,
        "version": version,
        "version_code": version_code,
        "channel": args.channel,
        "tag": tag,
        "published_utc": normalized_utc_timestamp(args.published_utc),
        "source_commit": args.source_commit,
        "android": {"min_sdk": 24, "minimum_version": "7.0", "target_sdk": 34},
        "artifacts": {"release": release_artifact},
        "virustotal": {
            "status": "pending",
            "checked": False,
            "detected": None,
            "total": None,
            "sha256": release_artifact["sha256"],
            "permalink": f"https://www.virustotal.com/gui/file/{release_artifact['sha256']}",
        },
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Generated {args.output}: {tag}, SHA-256 {release_artifact['sha256']}")


if __name__ == "__main__":
    main()
