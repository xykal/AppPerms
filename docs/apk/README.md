# APK storage policy

APK binaries are intentionally not stored in Git or deployed with Cloudflare Pages.

- The canonical stable artifact is identified by `docs/data/release.json`.
- Historical binaries remain immutable GitHub Release assets.
- New signed releases are uploaded once to an immutable R2 key and the matching GitHub Release.
- `AppsPerms-latest.apk` is a short-lived redirect in `docs/_redirects`, not a mutable binary.
- Debug builds are CI-only. They are not public release artifacts and never substitute for release APKs.
- The release workflow generates `SHA256SUMS`, an SPDX SBOM, and provenance for the exact signed APK.

Verify checked-in metadata with:

```bash
bash scripts/check-version-sync.sh
```
