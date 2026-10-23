# AppsPerms download Worker

The Worker is an integrity-aware gateway for the generated `release.json` manifest.
It serves R2 first when that optional backend is enabled. Without R2, it fetches
only the HTTPS URL allowlisted by the manifest, buffers the complete APK, and
verifies its SHA-256 before responding. Unknown filenames are rejected, so it is
not an open proxy or arbitrary storage browser.

## Deployment modes

The **Deploy download Worker** workflow provisions `dl.appsperms.xyverse.my.id`
as a Workers Custom Domain and requires a Cloudflare token with Workers Scripts
and Workers Routes edit access.

- `github-fallback` (default): no R2 account or billing setup is required. Full GET
  and HEAD requests work; byte-range requests fail closed with HTTP 503 because a
  partial mirror response cannot be verified against the full-file digest.
- `r2`: requires `appsperms-apk` and `appsperms-apk-preview`, plus Account →
  Workers R2 Storage → Edit. R2 becomes primary and supports verified byte ranges.

Set repository variable `R2_ENABLED=true` only after those buckets exist and the
production token can read/write them. Keep it unset or `false` otherwise. Confirm
`/health`, `/release.json`, GET, HEAD, conditional requests, and the expected
Range behavior after every deployment.

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
