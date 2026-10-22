# Device Care Lab

Device Care Lab groups three privileged, offline tools behind Shizuku. Open it from the AppsPerms navigation drawer.

## 1. Reversible Debloat

### Tiers

- **Safe** — known optional packages. “Safe” means optional for most users, not zero impact for every device.
- **Advanced** — system packages not in the optional catalog and not recognized as sensitive.
- **Expert** — names associated with launchers, input, telephony, security, setup, WebView, connectivity, and similar components.
- **Protected** — immutable critical denylist or a current device role. AppsPerms refuses to mutate these packages.

Dynamic protection includes AppsPerms itself, the current Home app, default dialer, default SMS app, current IME, enabled accessibility services, WebView provider, and active device-admin packages.

### Actions

- **Disable-user** is the recommended default and is reversible with `pm enable`.
- **Uninstall for user** uses `pm uninstall -k --user`; it does not delete the system APK. Restore uses `install-existing` and then enables the package.
- The first mutation for each Android user automatically creates a full state snapshot.
- Manual snapshot and restore are also available.

AppsPerms never removes files from read-only system partitions and never modifies APEX packages.

## 2. Advanced Cache Cleaner

### Shizuku mode

The one-shot cleaner calls Android Package Manager’s cache trim operation. It:

- does not clear app data;
- does not kill background apps;
- does not run on a schedule;
- enforces a cooldown to avoid repeated I/O bursts;
- reports measured `/data` free-space change rather than promising a fixed result.

### Root-selective mode

When Shizuku is running as UID 0, a user can select one package. The app is force-stopped, then only these locations are cleaned where present:

- `cache`;
- `code_cache`;
- device-encrypted cache;
- external app cache.

Databases, files, shared preferences, accounts, and login state are not targeted. AppsPerms and protected packages are excluded.

## 3. Feature Lab

Feature Lab contains a bounded allowlist of legitimate Android system flags:

- freeform windows and forced-resizable activities;
- desktop mode on external displays;
- notification history;
- notification bubbles.

Support depends on Android version and OEM ROM. A successful `settings put` does not guarantee the ROM exposes a matching UI, so AppsPerms reads the value back and reports a failure if it is ignored. Flags that affect window management usually need a restart.

Before activation, AppsPerms records the exact original value (including “unset”) per Android user. Turning a managed switch off restores that original value; it does not blindly write `0`.

Feature Lab does **not** alter another app’s signature, license, subscription, premium entitlement, DRM, billing, or server response.

## Release QA checklist

1. Install a debug APK and connect Shizuku in shell mode.
2. Create a debloat snapshot for user 0.
3. Disable a harmless optional package; verify it disappears/stops; restore it.
4. Uninstall the same package for user 0; verify `install-existing` restores it.
5. Repeat on a work profile if available.
6. Confirm protected packages have no enabled action button.
7. Run 512 MB cache trim; verify no account/data loss and inspect the measured result.
8. With root Shizuku, clean a test app cache and confirm only cache is lost.
9. Toggle each Feature Lab flag, reboot where noted, then restore the original value.
10. Check logs for errors and attach the AppsPerms device report to any issue.
