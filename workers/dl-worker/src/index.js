const DEFAULT_MANIFEST_URL = "https://appsperms.xyverse.my.id/data/release.json";
const DEFAULT_TIMEOUT_MS = 8000;
const ALLOWED_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);
const FILENAME_PATTERN = /^AppsPerms-[A-Za-z0-9._-]+\.apk$/;
const PACKAGE_ID = "app.appsperms";
const SIGNING_CERTIFICATE_SHA256 = "76ffdc65e3f8c6aef0d3ff8b75099ede336381305cfb2f7dda8d29d831ccac05";
const MAX_APK_BYTES = 100 * 1024 * 1024;

export default {
  async fetch(request, env = {}, ctx = {}) {
    const url = new URL(request.url);

    if (!ALLOWED_METHODS.has(request.method)) {
      return jsonResponse({ error: "Method not allowed" }, 405, {
        Allow: "GET, HEAD, OPTIONS",
      });
    }
    if (request.method === "OPTIONS") return preflightResponse();
    if (url.pathname === "/" || url.pathname === "") {
      return new Response(null, {
        status: 302,
        headers: {
          Location: env.MAIN_SITE_URL || "https://appsperms.xyverse.my.id/",
          "Cache-Control": "no-store",
        },
      });
    }
    if (url.pathname === "/health") {
      return jsonResponse({ ok: true, service: "appsperms-downloads" }, 200, {
        "Cache-Control": "no-store",
      });
    }

    let manifest;
    try {
      manifest = await loadManifest(env);
    } catch (error) {
      console.error("release manifest unavailable", safeError(error));
      return jsonResponse({ error: "Release metadata unavailable" }, 503, {
        "Retry-After": "60",
        "Cache-Control": "no-store",
      });
    }

    if (url.pathname === "/release.json" || url.pathname === "/channels/stable.json") {
      return jsonResponse(manifest, 200, {
        "Cache-Control": "public, max-age=0, must-revalidate",
      });
    }

    const filename = normalizedFilename(url.pathname);
    if (!filename) return jsonResponse({ error: "Not found" }, 404);
    const resolved = artifactForFilename(manifest, filename);
    if (!resolved) return jsonResponse({ error: "Not found" }, 404);

    const { artifact, alias } = resolved;
    if (env.APK_BUCKET && artifact.r2_key) {
      try {
        const response = await serveR2(request, env.APK_BUCKET, artifact, filename, alias);
        if (response) return response;
      } catch (error) {
        console.warn("R2 read failed", safeError(error));
      }
    }

    const mirrorResponse = await serveMirror(request, artifact, filename, alias, env);
    if (mirrorResponse) return mirrorResponse;
    return jsonResponse({ error: "Download temporarily unavailable" }, 503, {
      "Retry-After": "60",
      "Cache-Control": "no-store",
    });
  },
};

async function loadManifest(env) {
  if (env.RELEASE_MANIFEST) {
    return validateManifest(
      typeof env.RELEASE_MANIFEST === "string"
        ? JSON.parse(env.RELEASE_MANIFEST)
        : env.RELEASE_MANIFEST,
    );
  }

  if (env.APK_BUCKET) {
    const object = await env.APK_BUCKET.get("channels/stable.json");
    if (object) return validateManifest(await object.json());
  }

  const manifestUrl = env.RELEASE_MANIFEST_URL || DEFAULT_MANIFEST_URL;
  const response = await fetchWithTimeout(
    manifestUrl,
    {
      method: "GET",
      headers: { Accept: "application/json" },
      cf: { cacheEverything: true, cacheTtl: 60 },
    },
    timeoutMs(env),
  );
  if (!response.ok) throw new Error(`manifest HTTP ${response.status}`);
  return validateManifest(await response.json());
}

function validateManifest(manifest) {
  if (!manifest || manifest.schema_version !== 1 || !manifest.artifacts ||
      !/^\d+\.\d+\.\d+$/.test(manifest.version || "")) {
    throw new Error("unsupported release manifest");
  }
  const channel = manifest.channel || "stable";
  if (!/^[a-z][a-z0-9.-]*$/.test(channel)) throw new Error("invalid release channel");
  const expectedTag = `v${manifest.version}${channel === "stable" ? "" : `-${channel}`}`;
  if (manifest.tag !== expectedTag) throw new Error("invalid release tag");
  if (Object.keys(manifest.artifacts).length !== 1 || !manifest.artifacts.release) {
    throw new Error("manifest must expose exactly one release artifact");
  }
  const artifact = manifest.artifacts.release;
  const releaseId = channel === "stable" ? manifest.version : `${manifest.version}-${channel}`;
  const expectedFilename = `AppsPerms-${manifest.version}-${channel}-universal-release.apk`;
  if (artifact.filename !== expectedFilename || !FILENAME_PATTERN.test(artifact.filename)) {
    throw new Error("invalid artifact filename");
  }
  if (!/^[a-f0-9]{64}$/.test(artifact.sha256 || "")) throw new Error("invalid artifact digest");
  if (!Number.isSafeInteger(artifact.bytes) || artifact.bytes < 1 || artifact.bytes > MAX_APK_BYTES) {
    throw new Error("invalid artifact size");
  }
  if (artifact.public !== true || artifact.package_id !== PACKAGE_ID) {
    throw new Error("invalid public package identity");
  }
  if (artifact.signing_certificate_sha256 !== SIGNING_CERTIFICATE_SHA256) {
    throw new Error("untrusted release signing certificate");
  }
  if (artifact.r2_key !== `releases/${releaseId}/${expectedFilename}`) {
    throw new Error("invalid immutable R2 key");
  }
  if (!Array.isArray(artifact.urls) || artifact.urls.length !== 1 ||
      !isSafeMirror(artifact.urls[0], artifact.filename, expectedTag)) {
    throw new Error("invalid artifact URL");
  }
  return manifest;
}

function normalizedFilename(pathname) {
  let decoded;
  try {
    decoded = decodeURIComponent(pathname);
  } catch {
    return null;
  }
  decoded = decoded.replace(/^\/+/, "").replace(/^(?:dl|apk)\//, "");
  if (!decoded || decoded.includes("/") || decoded.includes("\\") || !FILENAME_PATTERN.test(decoded)) {
    return null;
  }
  return decoded;
}

function artifactForFilename(manifest, filename) {
  const release = manifest.artifacts.release;
  if (filename === "AppsPerms-latest.apk" && release?.public === true) {
    return { artifact: release, alias: true };
  }
  if (release?.public === true && filename === release.filename) {
    return { artifact: release, alias: false };
  }
  return null;
}

async function serveR2(request, bucket, artifact, requestedFilename, alias) {
  const metadata = await bucket.head(artifact.r2_key);
  if (!metadata) return null;
  if (metadata.size !== artifact.bytes) throw new Error("R2 object size does not match release manifest");

  const etag = `"sha256-${artifact.sha256}"`;
  if (etagMatches(request.headers.get("If-None-Match"), etag)) {
    return new Response(null, { status: 304, headers: downloadHeaders(artifact, requestedFilename, alias, etag) });
  }

  const rangeHeader = request.headers.get("Range");
  const range = rangeHeader && shouldHonorRange(request.headers.get("If-Range"), etag, metadata.uploaded)
    ? parseRange(rangeHeader, metadata.size)
    : null;
  if (range === false) {
    const headers = downloadHeaders(artifact, requestedFilename, alias, etag);
    headers.set("Content-Range", `bytes */${metadata.size}`);
    return new Response(null, { status: 416, headers });
  }

  const headers = downloadHeaders(artifact, requestedFilename, alias, etag);
  if (metadata.uploaded) headers.set("Last-Modified", new Date(metadata.uploaded).toUTCString());
  if (range) {
    headers.set("Content-Range", `bytes ${range.offset}-${range.offset + range.length - 1}/${metadata.size}`);
    headers.set("Content-Length", String(range.length));
  } else {
    headers.set("Content-Length", String(metadata.size));
  }

  if (request.method === "HEAD") {
    return new Response(null, { status: range ? 206 : 200, headers });
  }

  const object = await bucket.get(artifact.r2_key, range ? { range } : undefined);
  if (!object) return null;
  return new Response(object.body, { status: range ? 206 : 200, headers });
}

async function serveMirror(request, artifact, requestedFilename, alias, env) {
  // A partial mirror response cannot be checked against the full-file digest.
  // Range requests therefore fail closed when R2 is unavailable.
  if (request.headers.has("Range")) return null;

  const etag = `"sha256-${artifact.sha256}"`;
  if (etagMatches(request.headers.get("If-None-Match"), etag)) {
    return new Response(null, {
      status: 304,
      headers: downloadHeaders(artifact, requestedFilename, alias, etag),
    });
  }

  for (const mirrorUrl of artifact.urls) {
    try {
      const source = new URL(mirrorUrl);
      if (source.hostname === new URL(request.url).hostname) continue;
      const response = await fetchWithTimeout(
        source.toString(),
        {
          method: request.method,
          headers: { "User-Agent": "AppsPerms-Download-Worker/2" },
          redirect: "follow",
        },
        timeoutMs(env),
      );
      if (response.status !== 200) continue;
      const declaredLength = Number(response.headers.get("Content-Length"));
      if (Number.isFinite(declaredLength) && declaredLength !== artifact.bytes) continue;

      const headers = downloadHeaders(artifact, requestedFilename, alias, etag);
      headers.set("Content-Length", String(artifact.bytes));
      const modified = response.headers.get("Last-Modified");
      if (modified) headers.set("Last-Modified", modified);
      if (request.method === "HEAD") return new Response(null, { status: 200, headers });

      const bytes = await response.arrayBuffer();
      if (bytes.byteLength !== artifact.bytes) continue;
      const actual = await sha256Hex(bytes);
      if (actual !== artifact.sha256) {
        console.warn("download mirror digest mismatch", source.hostname);
        continue;
      }
      return new Response(bytes, { status: 200, headers });
    } catch (error) {
      console.warn("download mirror failed", safeError(error));
    }
  }
  return null;
}

function downloadHeaders(artifact, requestedFilename, alias, etag) {
  return new Headers({
    "Content-Type": "application/vnd.android.package-archive",
    "Content-Disposition": `attachment; filename="${requestedFilename}"`,
    "Cache-Control": alias ? "no-store" : "public, max-age=31536000, immutable",
    "Accept-Ranges": "bytes",
    ETag: etag,
    Digest: `sha-256=${hexToBase64(artifact.sha256)}`,
    "X-Content-Type-Options": "nosniff",
    "Access-Control-Allow-Origin": "*",
    "Cross-Origin-Resource-Policy": "cross-origin",
  });
}

function parseRange(value, size) {
  if (!value) return null;
  const match = /^bytes=(\d*)-(\d*)$/.exec(value.trim());
  if (!match || (match[1] === "" && match[2] === "")) return false;
  let start;
  let end;
  if (match[1] === "") {
    const suffix = Number(match[2]);
    if (!Number.isSafeInteger(suffix) || suffix <= 0) return false;
    start = Math.max(0, size - suffix);
    end = size - 1;
  } else {
    start = Number(match[1]);
    end = match[2] === "" ? size - 1 : Number(match[2]);
    if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start > end || start >= size) return false;
    end = Math.min(end, size - 1);
  }
  return { offset: start, length: end - start + 1 };
}

function etagMatches(value, etag) {
  if (!value) return false;
  return value.split(",").map((item) => item.trim()).some((item) => item === "*" || item === etag);
}

function shouldHonorRange(value, etag, uploaded) {
  if (!value) return true;
  const trimmed = value.trim();
  if (trimmed.startsWith('"') || trimmed.startsWith("W/")) return trimmed === etag;
  const conditionDate = Date.parse(trimmed);
  const uploadedDate = uploaded ? new Date(uploaded).getTime() : Number.NaN;
  return Number.isFinite(conditionDate) && Number.isFinite(uploadedDate) && uploadedDate <= conditionDate;
}

function isSafeMirror(value, filename, tag) {
  try {
    const url = new URL(value);
    return url.origin === "https://github.com" &&
      !url.username && !url.password && !url.search && !url.hash &&
      url.pathname === `/xykal/AppPerms/releases/download/${tag}/${filename}`;
  } catch {
    return false;
  }
}

function timeoutMs(env) {
  const value = Number(env.MIRROR_TIMEOUT_MS || DEFAULT_TIMEOUT_MS);
  return Number.isFinite(value) && value >= 1000 && value <= 30000 ? value : DEFAULT_TIMEOUT_MS;
}

async function fetchWithTimeout(url, init, milliseconds) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort("timeout"), milliseconds);
  try {
    return await fetch(url, { ...init, signal: controller.signal });
  } finally {
    clearTimeout(timeout);
  }
}

function preflightResponse() {
  return new Response(null, {
    status: 204,
    headers: {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, HEAD, OPTIONS",
      "Access-Control-Allow-Headers": "Range, If-Range, If-None-Match, If-Modified-Since",
      "Access-Control-Max-Age": "86400",
    },
  });
}

function jsonResponse(value, status, extra = {}) {
  return new Response(JSON.stringify(value), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "X-Content-Type-Options": "nosniff",
      "Access-Control-Allow-Origin": "*",
      "Cache-Control": status === 200 ? "public, max-age=60" : "no-store",
      ...extra,
    },
  });
}

function hexToBase64(hex) {
  let binary = "";
  for (let index = 0; index < hex.length; index += 2) {
    binary += String.fromCharCode(Number.parseInt(hex.slice(index, index + 2), 16));
  }
  return btoa(binary);
}

async function sha256Hex(bytes) {
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

function safeError(error) {
  return error instanceof Error ? error.message : String(error);
}

export {
  artifactForFilename,
  normalizedFilename,
  parseRange,
  serveR2,
  validateManifest,
};
