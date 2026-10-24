# Release process

## Security model

Pull-request CI never receives the release keystore, Cloudflare credentials, or
write permissions. Signed releases are produced only from a protected `v*` tag
inside the GitHub `production` Environment.

## Required repository controls

Configure these controls before the first hardened release:

- protect `main` with required Android, web/browser, metadata, and Worker checks;
- use a tag ruleset for `v*` that restricts creation and blocks updates/deletions;
- restrict the `production` Environment to `main` and protected `v*` tags and require a reviewer; use an independent reviewer when a second trusted collaborator is available;
- store signing and Cloudflare credentials only as `production` Environment secrets;
- enable GitHub immutable releases so assets cannot be changed after a draft is published.

## Before tagging

1. Merge only after Android CI, release metadata, web/browser, and Worker checks pass.
2. Increase `APP_VERSION_NAME` and `APP_VERSION_CODE` in `gradle.properties`.
3. Add the release heading to `CHANGELOG.md`. Keep the checked-in website fallback on the last verified public release until the new signed bytes exist.
4. Run:

   ```bash
   bash scripts/check-version-sync.sh
   ./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
   (cd workers/dl-worker && npm run check)
   ```

5. Complete the real-device/OEM test matrix.
6. Create a signed, protected tag such as `v1.8.1` or `v1.8.1-beta`.

## Signed draft candidate for device QA

When real-device QA needs production signing before a public tag exists, run
**Build signed draft candidate** against protected `main`. The workflow requires
an existing unique draft whose tag name matches `APP_VERSION_NAME`, whose target
is the exact checked-out commit, and for which no Git tag exists. It builds once
inside the reviewed `production` Environment, verifies package/version,
permissions, backup flags, v2/v3 signatures, and the pinned certificate, then
attaches and re-downloads the canonical APK without publishing the draft.

A debug QA asset must be removed before the protected tag is created. The normal
release workflow remains authoritative and refuses unexpected or mismatched
assets when publication later begins.

## Automated publication

`.github/workflows/release.yml` then:

1. validates tag/version/channel consistency;
2. runs tests and lint;
3. builds one universal release APK (debug builds remain CI-only);
4. verifies package identity, version code/name, permissions, backup flags, v2/v3 signatures, and the pinned certificate digest in `app/signing-certificate.sha256`;
5. generates checksums, `release.json`, and an SPDX SBOM from the exact signed bytes;
6. when repository variable `R2_ENABLED=true`, uploads and downloads the immutable APK and release manifest from R2 to verify them;
7. creates or resumes a draft GitHub Release, refuses to replace any mismatched asset, and downloads every asset again to compare bytes;
8. records GitHub artifact/SBOM attestations and publishes the draft only after all assets verify;
9. when R2 is enabled, promotes `channels/stable.json` only after the complete GitHub Release is public.

The job never writes directly to `main` and never deploys the Pages site.
It fails closed if the signing certificate changes. Planned key rotation requires a reviewed update to the pinned fingerprint and explicit migration communication before tagging.

## Website metadata

The checked-in `docs/data/release.json` is the safe static Worker manifest. When
R2 is enabled, `channels/stable.json` is the promoted primary channel record. After publication,
download `release.json` from the retained workflow bundle, verify it against the
GitHub asset, and use it in a reviewed PR that updates the static download links,
release notes, and compatibility metadata. Never invent or edit hashes by hand.

## VirusTotal

A missing, pending, failed, or mismatched scan must render as **Not scanned** or
**Pending**. Counts are displayed only when:

- status is `complete`;
- `checked` is true;
- `detected` and `total` are valid completed counts;
- the result SHA-256 exactly matches the release artifact.

“100% safe/clean” is not valid release language; use factual detection counts.

## Cloudflare cache policy

- immutable versioned APK: one year + `immutable`;
- mutable alias/manifest: `no-store` or mandatory revalidation;
- HTML: mandatory revalidation.

After changing an existing public alias, verify the unmodified URL from more than
one network/POP. A query-string check is not sufficient.
