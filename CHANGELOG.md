# Changelog

All notable AppsPerms releases are documented here. Release artifacts and hashes remain authoritative in each GitHub Release's `release.json`.

## [Unreleased]

### Added

- Device Care Lab with tiered reversible debloat, Shizuku/root-aware cache cleanup, and restorable Android/OEM feature controls.
- Integrity-aware download Worker with a SHA-256-verifying GitHub fallback and optional R2 primary.

### Security

- Hardened release, Pages, Worker, backup, certificate-pinning, and repository protection controls.
- Added bounded shell execution, strict target validation, protected packages, and reversible snapshots for Device Care operations.

### Changed

- Updated the Gradle wrapper and pinned CI/release actions after full validation.
- Advanced the development version to 1.9.0; the public website continues to advertise verified v1.8.0 until a signed 1.9.0 release is published.

## [1.8.0] - 2026-09-25

### Added

- Multi-user, work-profile, clone-profile, and emulator awareness.
- Built-in XyVerse branding and updated About presentation.

### Fixed

- Restored missing UI resources and multi-user backup parsing.
- Corrected release build and hosting behavior.

## [1.7.3] - 2026-09-25

### Fixed

- Simplified the Android build configuration after a Kotlin DSL failure.
- Published a minified universal release APK.

## [1.7.1] - 2026-09-22

- Corrected Material color and localization behavior.

## [1.7.0] - 2026-09-18

- Added the navigation drawer, live scan counters, and lazy icon loading.
