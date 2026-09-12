'use strict';

const fs = require('node:fs');
const dns = require('node:dns').promises;
const https = require('node:https');
const path = require('node:path');
const { exec } = require('node:child_process');
const { promisify } = require('node:util');

const execAsync = promisify(exec);

function event(name, data = {}) {
  process.stdout.write(`${JSON.stringify({ name, ...data })}\n`);
}

function request204() {
  return new Promise((resolve, reject) => {
    const request = https.get('https://www.google.com/generate_204', {
      headers: { 'user-agent': 'rava-android-runtime-probe/0.1' },
      timeout: 15000,
    }, (response) => {
      response.resume();
      response.on('end', () => {
        if (response.statusCode !== 204) {
          reject(new Error(`unexpected HTTPS status ${response.statusCode}`));
          return;
        }
        resolve(response.statusCode);
      });
    });
    request.on('timeout', () => request.destroy(new Error('HTTPS timeout')));
    request.on('error', reject);
  });
}

async function main() {
  const workDir = process.argv[2];
  if (!workDir) throw new Error('missing work directory argument');
  fs.mkdirSync(workDir, { recursive: true });

  event('runtime', {
    node: process.version,
    platform: process.platform,
    arch: process.arch,
    openssl: process.versions.openssl,
    icu: process.versions.icu,
  });

  const persian = 'سلام از راوا — ۱۲۳۴۵';
  const unicodeFile = path.join(workDir, 'unicode.txt');
  fs.writeFileSync(unicodeFile, persian, 'utf8');
  const roundTrip = fs.readFileSync(unicodeFile, 'utf8');
  if (roundTrip !== persian) throw new Error('Unicode file round-trip failed');
  event('file-io', { ok: true, value: roundTrip });

  const formatted = new Intl.NumberFormat('fa-IR').format(123456789);
  event('intl', { ok: formatted.length > 0, value: formatted });

  const markerFile = path.join(workDir, 'restart-count.txt');
  const previous = fs.existsSync(markerFile)
    ? Number.parseInt(fs.readFileSync(markerFile, 'utf8'), 10) || 0
    : 0;
  const restartCount = previous + 1;
  fs.writeFileSync(markerFile, String(restartCount), 'utf8');
  event('restart', { count: restartCount });

  const status = await request204();
  event('https', { ok: true, status });
  const addresses = await dns.resolve4('www.google.com');
  event('dns-resolve4', { ok: addresses.length > 0, addresses });
  const shell = await execAsync('printf rava-shell');
  if (shell.stdout !== 'rava-shell') throw new Error('default child-process shell failed');
  event('child-process-shell', { ok: true, value: shell.stdout });
  event('complete', { ok: true });
}

main().catch((error) => {
  process.stderr.write(`${error.stack || error.message}\n`);
  event('complete', { ok: false, error: error.message });
  process.exitCode = 1;
});
