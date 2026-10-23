"""Release manifest and immutable R2 uploader regression tests."""
from __future__ import annotations

import json
import os
from pathlib import Path
import stat
import subprocess
import tempfile
import tomllib
import unittest

ROOT = Path(__file__).resolve().parents[1]
CERT = "76ffdc65e3f8c6aef0d3ff8b75099ede336381305cfb2f7dda8d29d831ccac05"
APP_VERSION = next(
    line.split("=", 1)[1]
    for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines()
    if line.startswith("APP_VERSION_NAME=")
)


class ReleaseTools(unittest.TestCase):
    def test_manifest_generator_emits_one_certificate_pinned_release(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / f"AppsPerms-{APP_VERSION}-stable-universal-release.apk"
            apk.write_bytes(b"PK\x03\x04test-release")
            output = root / "release.json"
            subprocess.run(
                [
                    "python3",
                    str(ROOT / "scripts/generate-release-manifest.py"),
                    "--release-apk",
                    str(apk),
                    "--channel",
                    "stable",
                    "--tag",
                    f"v{APP_VERSION}",
                    "--source-commit",
                    "a" * 40,
                    "--published-utc",
                    "2026-10-23T01:02:03+07:00",
                    "--signing-certificate-sha256",
                    CERT,
                    "--output",
                    str(output),
                ],
                cwd=ROOT,
                check=True,
                capture_output=True,
                text=True,
            )
            manifest = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(manifest["published_utc"], "2026-10-22T18:02:03Z")
            self.assertEqual(set(manifest["artifacts"]), {"release"})
            artifact = manifest["artifacts"]["release"]
            self.assertTrue(artifact["public"])
            self.assertEqual(artifact["package_id"], "app.appsperms")
            self.assertEqual(artifact["signing_certificate_sha256"], CERT)
            self.assertEqual(
                artifact["r2_key"],
                f"releases/{APP_VERSION}/"
                f"AppsPerms-{APP_VERSION}-stable-universal-release.apk",
            )
            self.assertEqual(
                artifact["urls"],
                [
                    "https://github.com/xykal/AppPerms/releases/download/"
                    f"v{APP_VERSION}/"
                    f"AppsPerms-{APP_VERSION}-stable-universal-release.apk"
                ],
            )

    def test_spdx_normalizer_replaces_volatile_metadata_deterministically(self):
        with tempfile.TemporaryDirectory() as directory:
            sbom = Path(directory) / "sbom.spdx.json"
            sbom.write_text(
                json.dumps(
                    {
                        "spdxVersion": "SPDX-2.3",
                        "documentNamespace": "https://anchore.example/random-uuid",
                        "creationInfo": {
                            "created": "2099-01-01T00:00:00Z",
                            "creators": ["Tool: syft"],
                        },
                        "packages": [],
                    }
                ),
                encoding="utf-8",
            )
            command = [
                "python3",
                str(ROOT / "scripts/normalize-spdx.py"),
                "--sbom",
                str(sbom),
                "--subject-sha256",
                "b" * 64,
                "--published-utc",
                "2026-10-23T01:02:03+07:00",
            ]
            subprocess.run(command, cwd=ROOT, check=True, capture_output=True, text=True)
            first = sbom.read_bytes()
            subprocess.run(command, cwd=ROOT, check=True, capture_output=True, text=True)
            self.assertEqual(sbom.read_bytes(), first)
            document = json.loads(first)
            self.assertEqual(document["creationInfo"]["created"], "2026-10-22T18:02:03Z")
            self.assertEqual(
                document["documentNamespace"],
                "https://spdx.org/spdxdocs/appsperms-" + "b" * 64,
            )

    def test_worker_has_explicit_fallback_and_opt_in_r2_modes(self):
        config = tomllib.loads(
            (ROOT / "workers/dl-worker/wrangler.toml").read_text(encoding="utf-8")
        )
        self.assertNotIn("r2_buckets", config)
        fallback = config["env"]["production"]
        r2 = config["env"]["production_r2"]
        self.assertNotIn("r2_buckets", fallback)
        self.assertEqual(r2["r2_buckets"][0]["binding"], "APK_BUCKET")
        self.assertEqual(fallback["name"], r2["name"])
        self.assertEqual(fallback["routes"], r2["routes"])

        worker_workflow = (ROOT / ".github/workflows/worker.yml").read_text(
            encoding="utf-8"
        )
        self.assertIn("default: github-fallback", worker_workflow)
        self.assertIn("r2) WRANGLER_ENV=production_r2", worker_workflow)

        release_workflow = (ROOT / ".github/workflows/release.yml").read_text(
            encoding="utf-8"
        )
        self.assertEqual(release_workflow.count("vars.R2_ENABLED == 'true'"), 2)

    def test_r2_uploader_is_idempotent_and_refuses_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            fake_bin = root / "bin"
            fake_bin.mkdir()
            fake_npx = fake_bin / "npx"
            fake_npx.write_text(
                """#!/usr/bin/env bash
set -euo pipefail
operation="${5:-}"
file=""
for arg in "$@"; do
  case "$arg" in --file=*) file=${arg#--file=};; esac
done
if [[ "$operation" == get ]]; then
  if [[ ! -f "$FAKE_R2_STATE" ]]; then
    echo 'The specified key does not exist.' >&2
    exit 1
  fi
  cp "$FAKE_R2_STATE" "$file"
elif [[ "$operation" == put ]]; then
  cp "$file" "$FAKE_R2_STATE"
else
  echo "unexpected fake npx arguments: $*" >&2
  exit 2
fi
""",
                encoding="utf-8",
            )
            fake_npx.chmod(fake_npx.stat().st_mode | stat.S_IXUSR)
            apk = root / "AppsPerms-1.8.0-stable-universal-release.apk"
            apk.write_bytes(b"PK\x03\x04canonical-bytes")
            state = root / "r2-object.apk"
            command = [
                "node",
                str(ROOT / "scripts/upload-to-r2.js"),
                "--file",
                str(apk),
                "--key",
                "releases/1.8.0/AppsPerms-1.8.0-stable-universal-release.apk",
                "--bucket",
                "test-bucket",
            ]
            env = {
                **os.environ,
                "PATH": f"{fake_bin}{os.pathsep}{os.environ['PATH']}",
                "FAKE_R2_STATE": str(state),
                "CLOUDFLARE_API_TOKEN": "test-only",
                "CLOUDFLARE_ACCOUNT_ID": "test-only",
            }

            invalid_command = command.copy()
            invalid_command[invalid_command.index("--key") + 1] = "releases/latest/AppsPerms-latest.apk"
            invalid = subprocess.run(
                invalid_command, cwd=ROOT, env=env, capture_output=True, text=True
            )
            self.assertNotEqual(invalid.returncode, 0)
            self.assertIn("immutable universal release APK", invalid.stderr)
            self.assertFalse(state.exists())

            first = subprocess.run(command, cwd=ROOT, env=env, capture_output=True, text=True)
            self.assertEqual(first.returncode, 0, first.stderr)
            self.assertEqual(state.read_bytes(), apk.read_bytes())
            self.assertIn("Verified R2 object SHA-256", first.stdout)

            second = subprocess.run(command, cwd=ROOT, env=env, capture_output=True, text=True)
            self.assertEqual(second.returncode, 0, second.stderr)
            self.assertIn("already matches SHA-256", second.stdout)

            original = state.read_bytes()
            apk.write_bytes(b"PK\x03\x04different-release")
            conflict = subprocess.run(command, cwd=ROOT, env=env, capture_output=True, text=True)
            self.assertNotEqual(conflict.returncode, 0)
            self.assertIn("immutable R2 key already exists", conflict.stderr)
            self.assertEqual(state.read_bytes(), original)


if __name__ == "__main__":
    unittest.main()
