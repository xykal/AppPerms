import test from "node:test";
import assert from "node:assert/strict";
import worker, {
  artifactForFilename,
  normalizedFilename,
  parseRange,
  validateManifest,
} from "../src/index.js";

const SHA = "84d89877f0d4041efb6bf91a16f0248f2fd573e6af05c19f96bedb9f882f7882";
const CERT_SHA = "76ffdc65e3f8c6aef0d3ff8b75099ede336381305cfb2f7dda8d29d831ccac05";
const manifest = {
  schema_version: 1,
  version: "9.9.9",
  channel: "stable",
  tag: "v9.9.9",
  artifacts: {
    release: {
      public: true,
      package_id: "app.appsperms",
      signing_certificate_sha256: CERT_SHA,
      filename: "AppsPerms-9.9.9-stable-universal-release.apk",
      sha256: SHA,
      bytes: 10,
      r2_key: "releases/9.9.9/AppsPerms-9.9.9-stable-universal-release.apk",
      urls: [
        "https://github.com/xykal/AppPerms/releases/download/v9.9.9/AppsPerms-9.9.9-stable-universal-release.apk",
      ],
    },
  },
};

function env(extra = {}) {
  return { RELEASE_MANIFEST: JSON.stringify(manifest), ...extra };
}

function bucket() {
  let gets = 0;
  return {
    get calls() { return gets; },
    async head(key) {
      assert.equal(key, "releases/9.9.9/AppsPerms-9.9.9-stable-universal-release.apk");
      return { size: 10, httpEtag: '"r2-etag"', uploaded: new Date("2026-01-01T00:00:00Z") };
    },
    async get(key, options) {
      gets += 1;
      assert.equal(key, "releases/9.9.9/AppsPerms-9.9.9-stable-universal-release.apk");
      const bytes = new TextEncoder().encode("0123456789");
      const range = options?.range;
      const body = range ? bytes.slice(range.offset, range.offset + range.length) : bytes;
      return { body };
    },
  };
}

test("manifest validation and exact allowlist", () => {
  assert.equal(validateManifest(structuredClone(manifest)).version, "9.9.9");
  assert.equal(artifactForFilename(manifest, "AppsPerms-latest.apk").alias, true);
  assert.equal(artifactForFilename(manifest, manifest.artifacts.release.filename).alias, false);
  assert.equal(artifactForFilename(manifest, "AppsPerms-random.apk"), null);
  assert.equal(artifactForFilename(manifest, "AppsPerms-debug-latest.apk"), null);
  assert.throws(() => validateManifest({ ...manifest, schema_version: 2 }));
  const unexpectedDebug = structuredClone(manifest);
  unexpectedDebug.artifacts.debug = { ...unexpectedDebug.artifacts.release, public: false };
  assert.throws(() => validateManifest(unexpectedDebug));
  const untrustedSigner = structuredClone(manifest);
  untrustedSigner.artifacts.release.signing_certificate_sha256 = "b".repeat(64);
  assert.throws(() => validateManifest(untrustedSigner));
});

test("path normalization rejects nested and encoded traversal paths", () => {
  assert.equal(normalizedFilename("/apk/AppsPerms-latest.apk"), "AppsPerms-latest.apk");
  assert.equal(normalizedFilename("/AppsPerms-latest.apk"), "AppsPerms-latest.apk");
  assert.equal(normalizedFilename("/apk/nested/AppsPerms-latest.apk"), null);
  assert.equal(normalizedFilename("/apk/%2e%2e%2fsecret.apk"), null);
  assert.equal(normalizedFilename("/not-an-apk.txt"), null);
});

test("single byte ranges are parsed and invalid ranges are rejected", () => {
  assert.deepEqual(parseRange("bytes=0-3", 10), { offset: 0, length: 4 });
  assert.deepEqual(parseRange("bytes=5-", 10), { offset: 5, length: 5 });
  assert.deepEqual(parseRange("bytes=-3", 10), { offset: 7, length: 3 });
  assert.equal(parseRange("bytes=10-11", 10), false);
  assert.equal(parseRange("bytes=0-1,4-5", 10), false);
});

test("HEAD uses R2 metadata without reading the body", async () => {
  const store = bucket();
  const response = await worker.fetch(new Request("https://dl.example/AppsPerms-latest.apk", { method: "HEAD" }), env({ APK_BUCKET: store }));
  assert.equal(response.status, 200);
  assert.equal(store.calls, 0);
  assert.equal(response.headers.get("content-length"), "10");
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.equal(response.headers.get("accept-ranges"), "bytes");
});

test("GET range returns 206 and only the requested bytes", async () => {
  const store = bucket();
  const request = new Request(`https://dl.example/${manifest.artifacts.release.filename}`, {
    headers: { Range: "bytes=2-5" },
  });
  const response = await worker.fetch(request, env({ APK_BUCKET: store }));
  assert.equal(response.status, 206);
  assert.equal(response.headers.get("content-range"), "bytes 2-5/10");
  assert.equal(response.headers.get("content-length"), "4");
  assert.equal(response.headers.get("cache-control"), "public, max-age=31536000, immutable");
  assert.equal(await response.text(), "2345");
});

test("If-Range mismatch returns the complete R2 object", async () => {
  const response = await worker.fetch(
    new Request("https://dl.example/AppsPerms-latest.apk", {
      headers: { Range: "bytes=2-5", "If-Range": '"different"' },
    }),
    env({ APK_BUCKET: bucket() }),
  );
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("content-range"), null);
  assert.equal(response.headers.get("content-length"), "10");
  assert.equal(await response.text(), "0123456789");
});

test("unsatisfiable range returns 416", async () => {
  const response = await worker.fetch(
    new Request("https://dl.example/AppsPerms-latest.apk", { headers: { Range: "bytes=99-" } }),
    env({ APK_BUCKET: bucket() }),
  );
  assert.equal(response.status, 416);
  assert.equal(response.headers.get("content-range"), "bytes */10");
});

test("conditional request returns 304", async () => {
  const response = await worker.fetch(
    new Request("https://dl.example/AppsPerms-latest.apk", { headers: { "If-None-Match": `"sha256-${SHA}"` } }),
    env({ APK_BUCKET: bucket() }),
  );
  assert.equal(response.status, 304);
});

test("mirror fallback serves only a full body matching the manifest digest", async () => {
  const originalFetch = globalThis.fetch;
  try {
    globalThis.fetch = async () => new Response("0123456789", {
      status: 200,
      headers: { "Content-Length": "10" },
    });
    const verified = await worker.fetch(
      new Request(`https://dl.example/${manifest.artifacts.release.filename}`),
      env(),
    );
    assert.equal(verified.status, 200);
    assert.equal(await verified.text(), "0123456789");
    assert.equal(verified.headers.get("etag"), `"sha256-${SHA}"`);

    globalThis.fetch = async () => new Response("abcdefghij", {
      status: 200,
      headers: { "Content-Length": "10" },
    });
    const mismatch = await worker.fetch(
      new Request(`https://dl.example/${manifest.artifacts.release.filename}`),
      env(),
    );
    assert.equal(mismatch.status, 503);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("mirror fallback is bounded by the configured timeout", async () => {
  const originalFetch = globalThis.fetch;
  try {
    globalThis.fetch = async (_url, init) => new Promise((_resolve, reject) => {
      init.signal.addEventListener("abort", () => reject(new Error("aborted")), { once: true });
    });
    const started = Date.now();
    const response = await worker.fetch(
      new Request("https://dl.example/AppsPerms-latest.apk"),
      env({ MIRROR_TIMEOUT_MS: "1000" }),
    );
    assert.equal(response.status, 503);
    assert.ok(Date.now() - started >= 900);
    assert.ok(Date.now() - started < 3000);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("mirror fallback refuses unverifiable range responses", async () => {
  const originalFetch = globalThis.fetch;
  let calls = 0;
  try {
    globalThis.fetch = async () => {
      calls += 1;
      return new Response("23", { status: 206 });
    };
    const response = await worker.fetch(
      new Request("https://dl.example/AppsPerms-latest.apk", { headers: { Range: "bytes=2-3" } }),
      env(),
    );
    assert.equal(response.status, 503);
    assert.equal(calls, 0);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("unknown files and unsupported methods fail closed", async () => {
  const missing = await worker.fetch(new Request("https://dl.example/AppsPerms-random.apk"), env());
  assert.equal(missing.status, 404);
  const post = await worker.fetch(new Request("https://dl.example/AppsPerms-latest.apk", { method: "POST" }), env());
  assert.equal(post.status, 405);
  assert.equal(post.headers.get("allow"), "GET, HEAD, OPTIONS");
});
