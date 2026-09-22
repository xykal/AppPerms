# PROGRESS LOG - Template XyVerse

> Copy file ini jadi PROGRESS.md di setiap repo. Wajib diupdate tiap kerja.

## Format Wajib

### YYYY-MM-DD - Judul Task
**Status:** Done / In Progress / Blocked
**Dikerjain oleh:** AI Agent + xykalnotkel

**Yang dikerjain:**
- ...

**File yang diubah:**
- `path/file.ts` -> alasan: ...
- `path/file2.dart` -> alasan: ...

**Kendala & Solusi:**
- ...

**Build & Release:**
- Workflow: ...
- Link download: ...

**Next Step:**
- ...

---

### Contoh Real:

### 2026-09-22 - Fix Auth XyDesk APK
**Status:** Done

**Yang dikerjain:**
- Benerin Google OAuth yang crash di Android 14
- Ganti flow lama yang masih pake gcm_key deprecated

**File yang diubah:**
- `lib/services/auth.dart` -> ganti ke Google Identity Services + FCM v1
- `.github/workflows/build-apk.yml` -> update secret handling

**Kendala & Solusi:**
- Secret ONESIGNAL_API_KEY ketuker antara XyCloudStore vs XyDesk -> udah gue benerin pake key yang bener

**Build & Release:**
- Build via GitHub Actions run #123
- Link APK: https://github.com/xykalnotkel/xydesk/releases/tag/v2.1.0

**Next Step:**
- Test di device real, cek push notification
