# About AppsPerms

AppsPerms adalah AppOps manager modern untuk Android via Shizuku — tanpa root permanen.

## Apa itu AppOps?

Android punya sistem izin tersembunyi bernama **AppOps** (App Operations) yang tidak terlihat di Settings biasa. Contoh:
- `SYSTEM_ALERT_WINDOW` (Display over other apps)
- `READ_CLIPBOARD` (akses clipboard di background)
- `CAMERA`, `RECORD_AUDIO` di background
- `RUN_IN_BACKGROUND`, `WAKE_LOCK`, dll.

AppsPerms membuka akses ke 19 AppOps krusial ini via Shizuku (UID 2000 shell) atau root (UID 0).

## Kenapa AppsPerms?

- **Ringan**: 1.9MB release, R8 minified
- **Cepat**: ~150ms scan, 0ms Optimistic UI, lazy icon loading
- **Privat**: 0 izin internet di AndroidManifest.xml — mustahil kirim data keluar
- **Aman**: Safe Tuning dengan auto-revert 15 detik, backup/restore via clipboard
- **Lengkap**: Terminal Shizuku, batch actions, tweaks resolusi & animasi

## Tech Stack

- Kotlin, Material 3, ViewBinding
- Shizuku API 13.1.5, HiddenApiBypass 4.3
- Coroutines, RecyclerView, DrawerLayout

## Branding

Built-in XyVerse by Kall (@xykal)

XyVerse adalah ekosistem aplikasi open-source: AppsPerms, XyDesk, XyCloudStore, XyStudio AI.

## Links

- Website: https://appsperms.xyverse.my.id/
- GitHub: https://github.com/xykal/AppPerms
- Releases: https://github.com/xykal/AppPerms/releases
- Discussions: https://github.com/xykal/AppPerms/discussions

## License

Apache License 2.0
