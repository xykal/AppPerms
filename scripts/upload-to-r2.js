#!/usr/bin/env node
/**
 * Upload one immutable release artifact to Cloudflare R2 and verify it by
 * downloading it again. Credentials are read by Wrangler from the environment;
 * this program never prints or interpolates a bearer token.
 *
 * Usage:
 *   CLOUDFLARE_API_TOKEN=... CLOUDFLARE_ACCOUNT_ID=... \
 *   node scripts/upload-to-r2.js \
 *     --file out/AppsPerms-1.8.1-stable-universal-release.apk \
 *     --key releases/1.8.1/AppsPerms-1.8.1-stable-universal-release.apk
 */
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const crypto = require("node:crypto");
const { spawnSync } = require("node:child_process");

const WRANGLER_VERSION = "4.152.0";
const MAX_BYTES = 100 * 1024 * 1024;

function argument(name) {
  const args = process.argv.slice(2);
  const equals = args.find((value) => value.startsWith(`${name}=`));
  if (equals) return equals.slice(name.length + 1);
  const index = args.indexOf(name);
  return index >= 0 ? args[index + 1] : undefined;
}

function digest(file) {
  return crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function wranglerCommand(args, stdio = "inherit") {
  const result = spawnSync(
    "npx",
    ["--yes", `wrangler@${WRANGLER_VERSION}`, ...args],
    {
      stdio,
      encoding: stdio === "pipe" ? "utf8" : undefined,
      env: process.env,
      shell: false,
    },
  );
  if (result.error) throw result.error;
  return result;
}

function runWrangler(args) {
  const result = wranglerCommand(args);
  if (result.status !== 0) throw new Error(`Wrangler exited with status ${result.status}`);
}

function main() {
  const source = path.resolve(argument("--file") || "");
  const key = argument("--key");
  const bucket = argument("--bucket") || process.env.R2_BUCKET || "appsperms-apk";

  if (!argument("--file") || !key) {
    throw new Error("Usage: upload-to-r2.js --file <apk> --key releases/<version>/<filename> [--bucket <name>]");
  }
  if (!process.env.CLOUDFLARE_API_TOKEN || !process.env.CLOUDFLARE_ACCOUNT_ID) {
    throw new Error("CLOUDFLARE_API_TOKEN and CLOUDFLARE_ACCOUNT_ID are required");
  }
  if (!fs.existsSync(source) || !fs.statSync(source).isFile()) throw new Error("APK file not found");
  if (path.extname(source).toLowerCase() !== ".apk") throw new Error("Only .apk files are accepted");
  const keyMatch = key.match(
    /^releases\/(\d+\.\d+\.\d+(?:-[a-z][a-z0-9.-]*)?)\/(AppsPerms-(\d+\.\d+\.\d+)-([a-z][a-z0-9.-]*)-universal-release\.apk)$/,
  );
  if (!keyMatch) {
    throw new Error("R2 key must identify one immutable universal release APK");
  }
  const [, releaseId, filename, version, channel] = keyMatch;
  const expectedReleaseId = channel === "stable" ? version : `${version}-${channel}`;
  if (releaseId !== expectedReleaseId || path.basename(source) !== filename) {
    throw new Error("R2 key version, channel, and filename must match the local release APK");
  }
  if (!/^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$/.test(bucket)) {
    throw new Error("invalid R2 bucket name");
  }

  const size = fs.statSync(source).size;
  if (size < 4 || size > MAX_BYTES) throw new Error("APK size is outside the allowed range");
  const prefix = fs.readFileSync(source).subarray(0, 2).toString("binary");
  if (prefix !== "PK") throw new Error("APK does not have a ZIP signature");
  const expected = digest(source);
  const objectPath = `${bucket}/${key}`;
  const tempDirectory = fs.mkdtempSync(path.join(os.tmpdir(), "appsperms-r2-"));
  const downloaded = path.join(tempDirectory, path.basename(source));
  try {
    // Versioned release keys are immutable. Permit an idempotent retry only when
    // the already-published bytes have the exact same digest.
    const probe = wranglerCommand(
      ["r2", "object", "get", objectPath, `--file=${downloaded}`],
      "pipe",
    );
    if (probe.status === 0) {
      const existing = digest(downloaded);
      if (existing !== expected) {
        throw new Error(`immutable R2 key already exists with SHA-256 ${existing}`);
      }
      console.log(`Immutable R2 object already matches SHA-256: ${existing}`);
      return;
    }
    const probeMessage = `${probe.stdout || ""}\n${probe.stderr || ""}`;
    if (!/(not found|does not exist|NoSuchKey|10007)/i.test(probeMessage)) {
      throw new Error(`could not determine whether immutable R2 key exists (Wrangler status ${probe.status})`);
    }
    fs.rmSync(downloaded, { force: true });

    console.log(`Uploading ${path.basename(source)} (${size} bytes, SHA-256 ${expected}) to ${objectPath}`);
    runWrangler([
      "r2", "object", "put", objectPath,
      `--file=${source}`,
      "--content-type=application/vnd.android.package-archive",
    ]);
    runWrangler(["r2", "object", "get", objectPath, `--file=${downloaded}`]);
    const actual = digest(downloaded);
    if (actual !== expected) throw new Error(`R2 verification failed: expected ${expected}, received ${actual}`);
    console.log(`Verified R2 object SHA-256: ${actual}`);
  } finally {
    fs.rmSync(tempDirectory, { recursive: true, force: true });
  }
}

try {
  main();
} catch (error) {
  console.error(`R2 upload failed: ${error instanceof Error ? error.message : String(error)}`);
  process.exit(1);
}
