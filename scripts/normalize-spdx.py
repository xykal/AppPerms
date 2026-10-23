#!/usr/bin/env python3
"""Remove volatile Syft fields so release SBOM assets are immutable across reruns."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re


def utc_timestamp(value: str) -> str:
    try:
        timestamp = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise SystemExit(f"invalid --published-utc timestamp: {value!r}") from error
    if timestamp.tzinfo is None:
        raise SystemExit("--published-utc must include a timezone")
    return timestamp.astimezone(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sbom", required=True, type=Path)
    parser.add_argument("--subject-sha256", required=True)
    parser.add_argument("--published-utc", required=True)
    args = parser.parse_args()

    if not re.fullmatch(r"[a-f0-9]{64}", args.subject_sha256):
        raise SystemExit("invalid subject SHA-256")
    try:
        document = json.loads(args.sbom.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise SystemExit(f"cannot read SPDX JSON: {error}") from error
    if document.get("spdxVersion") != "SPDX-2.3":
        raise SystemExit("expected an SPDX-2.3 JSON document")
    creation = document.get("creationInfo")
    if not isinstance(creation, dict):
        raise SystemExit("SPDX document has no creationInfo object")

    document["documentNamespace"] = (
        "https://spdx.org/spdxdocs/appsperms-" + args.subject_sha256
    )
    creation["created"] = utc_timestamp(args.published_utc)
    args.sbom.write_text(
        json.dumps(document, indent=2, ensure_ascii=False, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(f"Normalized immutable SPDX metadata in {args.sbom}")


if __name__ == "__main__":
    main()
