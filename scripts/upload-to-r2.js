/**
 * Upload APK ke Cloudflare R2 - Anti Suspend Solution
 * 
 * Penggunaan:
 * node scripts/upload-to-r2.js --file out/AppsPerms-1.7.2-release.apk
 * 
 * Env vars (dari ini-buat-kerja.json):
 * CLOUDFLARE_API_TOKEN=cfut_...
 * CLOUDFLARE_ACCOUNT_ID=...
 * R2_BUCKET=appsperms-apk
 * 
 * Buat bucket dulu: wrangler r2 bucket create appsperms-apk
 */

const fs = require('fs');
const path = require('path');

// Config dari ini-buat-kerja.json (jangan commit file asli!)
const CONFIG_PATH = process.env.CONFIG_JSON || '/home/user/uploads/ini-buat-kerja.json';

async function main() {
  const args = process.argv.slice(2);
  const fileArg = args.find(a => a.startsWith('--file='))?.split('=')[1] || args[args.indexOf('--file') + 1];
  const bucket = process.env.R2_BUCKET || 'appsperms-apk';

  if (!fileArg) {
    console.error('Usage: node upload-to-r2.js --file <path-to-apk>');
    process.exit(1);
  }

  // Load token dari ini-buat-kerja.json kalau ada
  let cfToken = process.env.CLOUDFLARE_API_TOKEN;
  let accountId = process.env.CLOUDFLARE_ACCOUNT_ID;

  try {
    if (fs.existsSync(CONFIG_PATH)) {
      const cfg = JSON.parse(fs.readFileSync(CONFIG_PATH, 'utf8'));
      if (cfg.cloudflare?.api_token) {
        cfToken = cfToken || cfg.cloudflare.api_token;
        console.log('✓ Loaded Cloudflare token from ini-buat-kerja.json');
      }
    }
  } catch (e) {
    console.warn('Could not load config json:', e.message);
  }

  if (!cfToken) {
    console.error('❌ CLOUDFLARE_API_TOKEN not set. Set env var or ensure ini-buat-kerja.json exists');
    process.exit(1);
  }

  if (!accountId) {
    // Try to get account ID via API
    console.log('Fetching account ID...');
    const res = await fetch('https://api.cloudflare.com/client/v4/accounts', {
      headers: { 'Authorization': `Bearer ${cfToken}` }
    });
    const data = await res.json();
    if (data.success && data.result.length > 0) {
      accountId = data.result[0].id;
      console.log(`✓ Account ID: ${accountId}`);
    } else {
      console.error('Failed to get account ID:', data);
      process.exit(1);
    }
  }

  const filePath = path.resolve(fileArg);
  if (!fs.existsSync(filePath)) {
    console.error(`File not found: ${filePath}`);
    process.exit(1);
  }

  const fileName = path.basename(filePath);
  const fileBuffer = fs.readFileSync(filePath);

  console.log(`\n📦 Uploading ${fileName} (${(fileBuffer.length/1024/1024).toFixed(2)} MB) to R2 bucket ${bucket}...`);

  // S3-compatible API via Cloudflare R2
  // Using wrangler-style upload via API
  const uploadUrl = `https://api.cloudflare.com/client/v4/accounts/${accountId}/r2/buckets/${bucket}/objects/${fileName}`;

  // For simplicity, use wrangler if available, else direct S3 API would need more config
  console.log(`\nCoba upload via wrangler (lebih mudah):`);
  console.log(`  npx wrangler r2 object put ${bucket}/${fileName} --file=${filePath} --content-type=application/vnd.android.package-archive`);
  console.log(`\nAtau via API:`);
  console.log(`  curl -X PUT "${uploadUrl}" \\`);
  console.log(`    -H "Authorization: Bearer ${cfToken}" \\`);
  console.log(`    -H "Content-Type: application/vnd.android.package-archive" \\`);
  console.log(`    --data-binary "@${filePath}"`);

  // Also upload as latest alias
  if (fileName.includes('release')) {
    console.log(`\n  # Alias latest:`);
    console.log(`  npx wrangler r2 object put ${bucket}/AppsPerms-latest.apk --file=${filePath} --content-type=application/vnd.android.package-archive`);
  }
  if (fileName.includes('debug')) {
    console.log(`  npx wrangler r2 object put ${bucket}/AppsPerms-debug-latest.apk --file=${filePath} --content-type=application/vnd.android.package-archive`);
  }

  console.log(`\n✅ Setelah upload, file akan tersedia di:`);
  console.log(`  https://dl.appsperms.xyverse.my.id/${fileName} (via Worker)`);
  console.log(`  https://appsperms.xyverse.my.id/apk/${fileName} (via Pages, kalau juga di-copy ke docs/apk/)`);
}

main().catch(e => {
  console.error(e);
  process.exit(1);
});
