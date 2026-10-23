# Security policy

## Supported releases

Security fixes target the latest stable AppsPerms release. Older APKs remain available only for historical verification and may not receive fixes.

## Reporting a vulnerability

Please use GitHub's **Report a vulnerability** / private security advisory flow for this repository. If that control is not visible, open a minimal issue requesting a private contact channel without including exploit details. Do not publish credentials, signing material, a working privilege-escalation proof, or sensitive user/device data.

Include, when possible:

- affected version and Android/OEM build;
- whether Shizuku or root was active;
- minimal reproduction steps;
- security impact;
- relevant logs with package names or personal data removed.

## Release integrity

Official release metadata is published in `release.json`. Verify the APK SHA-256 and the pinned signing-certificate SHA-256 before installation:

`76ffdc65e3f8c6aef0d3ff8b75099ede336381305cfb2f7dda8d29d831ccac05`

The same fingerprint is stored in `app/signing-certificate.sha256` and enforced by the protected release workflow. A VirusTotal count is shown only when a completed result matches the exact release SHA-256.

The Android app intentionally has no Internet permission. The website and download infrastructure are separate systems and are covered by the public privacy notice.
