# AppsPerms download Worker

The Worker is an integrity-aware gateway for the generated `release.json` manifest.
It serves R2 first and only falls back to HTTPS URLs listed in that manifest.
Unknown filenames are rejected, so it is not an open proxy or arbitrary R2 browser.

## Prerequisites

1. Create R2 buckets:
   - `appsperms-apk`
   - `appsperms-apk-preview`
2. Give the production GitHub Environment a Cloudflare token scoped to:
   - Account → Workers Scripts → Edit
   - Account → Workers R2 Storage → Edit
   - Zone → Workers Routes → Edit for `xyverse.my.id`
3. Add the repository variable `R2_BUCKET=appsperms-apk`.
4. Run the **Deploy download Worker** workflow manually. Wrangler provisions
   `dl.appsperms.xyverse.my.id` as a Workers Custom Domain.
5. Confirm `/health`, `/release.json`, GET, HEAD, and Range requests before adding
   the Worker URL to public copy.

## Local checks

```bash
npm run check
npx --yes wrangler@4.152.0 dev
```

## HTTP contract

- Methods: GET, HEAD, OPTIONS
- Alias: `AppsPerms-latest.apk`; debug aliases are always rejected
- Versioned filenames: exactly one certificate-pinned universal release APK from `release.json`
- Range: one byte range with correct 206/416 and If-Range behavior from R2
- Mirror fallback: full GET bodies are buffered and SHA-256 checked; unverifiable fallback ranges fail closed
- Conditional: SHA-256 ETag / If-None-Match
- Mutable aliases: `Cache-Control: no-store`
- Versioned artifacts: one-year immutable caching

The Worker never claims a VirusTotal result and never chooses an artifact outside
of the published manifest.
