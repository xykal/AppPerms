/**
 * AppsPerms DL Worker - Resilient Download Worker (Anti-Suspend)
 * 
 * Tujuan: dl.appsperms.xyverse.my.id menjadi tahan banting walau GitHub suspend
 * 
 * Strategi:
 * 1. Coba serve dari R2 bucket (jika ada binding APK_BUCKET)
 * 2. Coba fetch dari Cloudflare Pages primary origin (appsperms.xyverse.my.id/apk/...)
 * 3. Fallback ke GitHub Releases aktif xykal/AppPerms (bukan xykalnotkel yang suspend)
 * 4. Fallback terakhir ke jsDelivr CDN mirror
 * 
 * Deploy: wrangler deploy
 * Custom domain: dl.appsperms.xyverse.my.id -> route ke worker ini
 */

const GITHUB_REPO = "xykal/AppPerms"; // AKUN AKTIF, bukan xykalnotkel yang suspend
const PRIMARY_ORIGIN = "https://appsperms.xyverse.my.id";
const VERSION = "v1.7.2";

// Mapping file requests
const FILE_MAP = {
  "AppsPerms-latest.apk": `AppsPerms-${VERSION.replace('v','')}-release.apk`,
  "AppsPerms-debug-latest.apk": `AppsPerms-${VERSION.replace('v','')}-debug.apk`,
};

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    let pathname = url.pathname.replace(/^\/+/, ''); // remove leading slash
    
    // Handle root -> redirect to main site
    if (!pathname || pathname === '/') {
      return Response.redirect('https://appsperms.xyverse.my.id/', 302);
    }

    // Normalize filename
    let filename = pathname;
    // If request is /dl/AppsPerms-latest.apk (from old _redirects) handle it
    if (filename.startsWith('dl/')) filename = filename.replace(/^dl\//, '');
    if (filename.startsWith('apk/')) filename = filename.replace(/^apk\//, '');

    // Build list of mirrors to try (in order)
    const mirrors = await buildMirrorList(filename, env);

    // Try each mirror sequentially with cache
    for (let i = 0; i < mirrors.length; i++) {
      const mirrorUrl = mirrors[i];
      try {
        // Try R2 first if available
        if (mirrorUrl.startsWith('r2://')) {
          if (env.APK_BUCKET) {
            const key = mirrorUrl.replace('r2://', '');
            const obj = await env.APK_BUCKET.get(key);
            if (obj) {
              return new Response(obj.body, {
                headers: {
                  'Content-Type': 'application/vnd.android.package-archive',
                  'Content-Disposition': `attachment; filename="${filename}"`,
                  'Cache-Control': 'public, max-age=3600, s-maxage=86400',
                  'X-Mirror': `r2:${key}`,
                  'X-Content-Type-Options': 'nosniff',
                  'Access-Control-Allow-Origin': '*',
                }
              });
            }
          }
          continue; // R2 miss, try next
        }

        // Fetch from HTTP mirror
        const resp = await fetch(mirrorUrl, {
          cf: {
            cacheTtl: 3600,
            cacheEverything: true,
          },
          headers: {
            'User-Agent': 'AppsPerms-DL-Worker/1.0 (+https://appsperms.xyverse.my.id)',
          }
        });

        if (resp.ok) {
          // Stream response with proper headers
          const headers = new Headers(resp.headers);
          headers.set('Content-Type', 'application/vnd.android.package-archive');
          headers.set('Content-Disposition', `attachment; filename="${filename}"`);
          headers.set('Cache-Control', 'public, max-age=3600, s-maxage=86400');
          headers.set('X-Mirror', mirrorUrl);
          headers.set('X-Content-Type-Options', 'nosniff');
          headers.set('Access-Control-Allow-Origin', '*');
          
          return new Response(resp.body, {
            status: 200,
            headers: headers
          });
        }
      } catch (e) {
        console.warn(`Mirror failed: ${mirrorUrl}`, e);
        continue;
      }
    }

    // All mirrors failed -> return helpful 404 page
    return new Response(JSON.stringify({
      error: "All mirrors failed",
      requested: filename,
      tried: mirrors,
      message: "Semua mirror gagal. Akun xykalnotkel memang ke-suspend, tapi seharusnya fallback ke xykal/AppPerms jalan. Cek https://github.com/xykal/AppPerms/releases",
      fix: "Pastikan APK sudah di-upload ke R2 atau ke Cloudflare Pages /apk/ folder, dan GitHub release di xykal/AppPerms ada."
    }, null, 2), {
      status: 404,
      headers: {
        'Content-Type': 'application/json; charset=utf-8',
        'Cache-Control': 'no-cache',
        'Access-Control-Allow-Origin': '*',
      }
    });
  }
};

async function buildMirrorList(filename, env) {
  // Normalize: AppsPerms-latest.apk -> AppsPerms-1.7.2-release.apk
  let normalized = FILE_MAP[filename] || filename;
  
  const list = [];

  // 1. R2 bucket (fastest, no GitHub dependency)
  list.push(`r2://${filename}`);
  list.push(`r2://${normalized}`);
  list.push(`r2://latest/${filename}`);

  // 2. Primary origin - Cloudflare Pages (appsperms.xyverse.my.id/apk/...)
  list.push(`${PRIMARY_ORIGIN}/apk/${filename}`);
  list.push(`${PRIMARY_ORIGIN}/apk/${normalized}`);
  list.push(`${PRIMARY_ORIGIN}/${filename}`);

  // 3. GitHub Releases - AKTIF repo xykal/AppPerms (bukan xykalnotkel yang suspend)
  list.push(`https://github.com/${GITHUB_REPO}/releases/latest/download/${filename}`);
  list.push(`https://github.com/${GITHUB_REPO}/releases/download/${VERSION}/${normalized}`);
  list.push(`https://github.com/${GITHUB_REPO}/releases/download/${VERSION}/${filename}`);

  // 4. jsDelivr CDN mirror of GitHub (bypass GitHub rate limit)
  list.push(`https://cdn.jsdelivr.net/gh/${GITHUB_REPO}@main/docs/apk/${filename}`);

  // 5. Direct GitHub raw via github.com (last resort)
  // Note: release assets are not on raw, but we try anyway

  return list;
}
