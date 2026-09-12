'use strict';

const fs = require('node:fs');

const statePath = process.argv[2];
if (!statePath) throw new Error('missing auth state path');

function saveState(state) {
  fs.writeFileSync(statePath, `${JSON.stringify(state)}\n`, { mode: 0o600 });
}

let captured = '';
let authorizationUrl;
const originalWrite = process.stdout.write.bind(process.stdout);
process.stdout.write = (chunk, encoding, callback) => {
  const text = Buffer.isBuffer(chunk) ? chunk.toString(encoding || 'utf8') : String(chunk);
  captured = (captured + text).slice(-32768);
  const match = captured.match(/https:\/\/accounts\.google\.com\/[^\s\u001b]+/);
  if (match && match[0] !== authorizationUrl) {
    authorizationUrl = match[0];
    saveState({ status: 'authorization_required', authorizationUrl });
  }
  return originalWrite(chunk, encoding, callback);
};

async function main() {
  saveState({ status: 'starting' });
  const { getOauthClient } = await import('./gemini/chunk-YSBB75DZ.js');
  const config = {
    getProxy: () => undefined,
    isBrowserLaunchSuppressed: () => true,
    isInteractive: () => true,
  };
  const client = await getOauthClient('oauth-personal', config);
  saveState({
    status: 'authenticated',
    hasAccessToken: Boolean(client.credentials?.access_token),
    hasRefreshToken: Boolean(client.credentials?.refresh_token),
    expiryDate: client.credentials?.expiry_date || null,
  });
  process.stdout.write(`${JSON.stringify({ name: 'gemini-auth', ok: true })}\n`);
}

main().catch((error) => {
  // Keep authorization codes and provider error payloads out of persistent state.
  saveState({ status: 'failed' });
  process.stderr.write(`${error?.stack || error}\n`);
  process.exitCode = 1;
});
