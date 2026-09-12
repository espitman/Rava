# Gemini CLI Google authentication on Android

This probe uses Gemini CLI 0.59.0's own Google OAuth client, scopes, PKCE code
verifier, token exchange, refresh behavior, and credential cache. It does not use
an API key, copy browser cookies, or register a replacement OAuth client.

## Upstream flow selected

Gemini CLI contains two supported Google login paths:

1. Its normal desktop path binds an HTTP callback server on `127.0.0.1`, creates
   an authorization URL, and asks the `open` npm package to launch it. On Android,
   that package tries to execute `xdg-open`, which is not present in a standalone
   APK.
2. Its browser-suppressed path uses Authorization Code + PKCE with redirect URI
   `https://codeassist.google.com/authcode`, prints the Google URL, and reads the
   returned authorization code from stdin.

The probe uses the second path. `gemini-auth-probe.js` calls the exported
`getOauthClient` from the pinned upstream bundle and supplies only the config
methods needed to select that official path. It captures the generated URL into
an app-private state file. Android accepts only HTTPS URLs whose exact host is
`accounts.google.com`, then opens the system browser with `ACTION_VIEW`.

The bundle import is deliberately pinned to `chunk-YSBB75DZ.js` from 0.59.0.
`verifyRuntimeInputs` fails if that chunk is absent, so a Gemini update cannot
silently reuse an incompatible adapter.

## User interaction required

1. Tap **Start Gemini Google sign-in**.
2. In the system browser, select the intended Google account and approve the
   official Gemini CLI consent request.
3. Copy the authorization code displayed by `codeassist.google.com`.
4. Return to the probe, paste the code, and tap **Submit Gemini authorization
   code** within five minutes.

The app keeps a dedicated reference to the Gemini authorization process. The
submit field and button are enabled only while that exact process is alive and
the adapter has reported `authorization_required`. After one submission they
are disabled while the token exchange completes. The headless probe is disabled
during authorization and until an encrypted credential file exists, so it cannot
consume an authorization code in its own interactive prompt. Terminal success,
failure, and cancellation clear the code field.

Some Workspace, school, or organization accounts may also require a Google Cloud
project according to Gemini CLI's own authentication rules. The probe does not
change account eligibility or provider policy.

After a successful exchange, tap **Run authenticated Gemini headless probe**.
It runs the pinned CLI with `--output-format stream-json --prompt <text>` and
keeps stderr separate. The default prompt asks for the exact Persian response
`سلام راوا`; it can be edited before launch.

ADB can start the prepared paths without UI coordinate automation:

```sh
adb shell am start -n ir.rava.runtimeprobe/.MainActivity --es probe gemini-auth
adb shell am start -n ir.rava.runtimeprobe/.MainActivity --es probe gemini-headless
```

## Credential storage

`HOME` is `files/runtime-home`, so Gemini's global configuration is under
`files/runtime-home/.gemini`. The probe enables the CLI's encrypted file fallback
with `GEMINI_FORCE_ENCRYPTED_FILE_STORAGE=true` and
`GEMINI_FORCE_FILE_STORAGE=true`. The resulting
`gemini-credentials.json` is AES-256-GCM encrypted and written mode 0600 by the
upstream CLI. The Android application disables backup and excludes its root from
cloud backup and device transfer.

This fallback derives its encryption key from values available to the CLI, not
Android Keystore. It is acceptable for this feasibility probe's app-private
sandbox, but production integration should wrap or replace it with a
Keystore-backed bridge while keeping Gemini's refresh-token semantics.

The authorization state file contains only status, the short-lived authorization
URL, and booleans describing whether tokens exist. It never records access or
refresh token values or the pasted authorization code. Auth-process stdout and
stderr captures are temporary and deleted without being copied to the command
log or UI.

## Verification and remaining gate

Host unit tests cover the exact Google URL allowlist and reject HTTP, lookalike
hosts, and user-info URLs. JavaScript syntax, Android unit tests, lint, and
`assembleRuntimeProbe` pass. On the Android 14 arm64 phone, the adapter reached
`authorization_required`, generated a Google URL with PKCE S256 and the official
Code Assist redirect, and opened Chrome. The waiting process was then cancelled
before choosing an account.

A real user approval and OAuth token exchange completed on the Android phone.
The first authenticated headless request then reached Google but was rejected by
the service with `IneligibleTierError` / `UNSUPPORTED_CLIENT`; no model response
was returned. Encrypted credential-file inspection and refresh after process
restart remain unverified. No Phase 2 checklist item is complete yet.

## Consumer OAuth retirement and migration check (2026-09-12)

This rejection is a server-side product retirement, not an Android transport or
OAuth callback defect. Google's [consumer-account deprecation
page](https://developers.google.com/gemini-code-assist/docs/deprecations/code-assist-individuals)
states that, starting 2026-06-18, Gemini CLI stopped serving Gemini Code Assist
for individuals, Google AI Pro, and Google AI Ultra, and that **Login with
Google can no longer be used** for those accounts. The Gemini CLI team's
[official announcement](https://github.com/google-gemini/gemini-cli/discussions/28017)
directs individual users to Antigravity CLI. Gemini Code Assist Standard and
Enterprise subscriptions and API-key authentication are explicitly outside
that shutdown.

The npm `latest` tag still resolves to `0.59.0`, the version already bundled in
this probe. The newer `preview` tag is `0.60.0-preview.0` and the current nightly
is `0.61.0-nightly.20260912.g9c1b0a610`. A bounded preview experiment downloaded
the official 20,753,165-byte npm tarball, verified SHA-1
`571fc88de7e6c841f46aeefa902bfffcfa184a6f`, unpacked it temporarily, and ran
`gemini --version` successfully on host Node 20. Its Apache-2.0 package still has
the same `oauth-personal` route. Compared with `0.59.0`, its complete
`authWithUserCode`, `setupUser`, and `throwIneligibleOrProjectIdError` function
bodies were byte-identical; its OAuth client-ID set and Code Assist endpoint set
were also identical. Upgrading the APK to preview would therefore retain the
same retired consumer backend and cannot fix `UNSUPPORTED_CLIENT`. The temporary
npm files were removed and the installed credential was not read or copied.

Google's [Antigravity migration
guide](https://antigravity.google/docs/cli/gcli-migration/) and [installation
documentation](https://antigravity.google/docs/cli/install/) define `agy` as the
replacement. The official installer currently offers macOS, Linux, and Windows,
with amd64 and arm64 detection. Its 2026-09-12 `linux_arm64` manifest points to
Antigravity CLI `1.2.2` with SHA-512
`a1645a30f36b767c7534c2f6a53e99a9bfade993267efcca715f7a45d797d47d6561df787e9d4a51a3bdfc9be855d49d23fa3f4b91b2c661fd17314050836048`.
The payload is a 202,985,704-byte dynamically linked GNU/Linux AArch64 ELF whose
interpreter is `/lib/ld-linux-aarch64.so.1`; it needs glibc libraries including
`libresolv.so.2`, `libpthread.so.0`, `libm.so.6`, `libdl.so.2`, `librt.so.1`, and
`libc.so.6`, and has no RPATH/RUNPATH. Android uses Bionic and does not provide
that loader. A direct execution check on the connected Android 14 arm64 phone
failed before program startup with exit 126. The installer's
`linux_arm64_musl` manifest returned HTTP 404. Device and host scratch payloads
were removed after inspection.

Consequently, the current standalone APK cannot restore personal Google OAuth
by changing Gemini CLI versions, and Google's replacement binary cannot be
dropped into `nativeLibraryDir` unchanged. Gemini CLI itself can still be
evaluated with an eligible Standard/Enterprise account, Vertex/ADC, or an API
key, but those are different authentication/product paths.

### Termux compatibility experiment and official SSH login boundary

The owner approved a personal-use Termux fallback after an Astra risk review.
The review found a real ambiguity rather than zero policy risk: Google's
[official Antigravity FAQ](https://www.antigravity.google/docs/faq/) warns that
third-party tools or services may cause account suspension, while Google's
[official headless documentation](https://antigravity.google/docs/cli/headless/)
explicitly teaches applications to run the official `agy` subprocess and drive
its stdin/stdout. Neither page specifically discusses Android plus Termux. The
approved boundary therefore permits only Google's unmodified executable and its
documented CLI interfaces. Rava must not read, export, or reuse OAuth tokens,
call private Google endpoints itself, alter the binary or OAuth client identity,
or bypass quota and safety controls.

The connected arm64 phone has Termux `0.119.0-beta.3` and
`allow-external-apps=true`. Before this run, `HOME` was 2,150,401 KiB and
`PREFIX` was 2,821,308 KiB; neither `agy` nor a glibc runner was present. The
previously audited install was repeated without upgrading existing packages:
`glibc-repo 1.0`, `glibc-runner 2.0-3`, `glibc 2.44`, and the same 40 automatic
glibc dependencies. Apt still estimates 72,177,244 download bytes and 416,964
installed KiB for the runner transaction. The measured post-install `PREFIX`
was 3,235,834 KiB, a 414,526 KiB increase. Apt dependency and dpkg audits pass;
the only newly manual packages are `glibc-repo` and `glibc-runner`.

The `linux_arm64` manifest was fetched again from Google's updater and still
identified version `1.2.2`, archive URL
`https://storage.googleapis.com/antigravity-public/antigravity-cli/1.2.2-6061403484848128/linux-arm/cli_linux_arm64.tar.gz`, content length
54,016,481 bytes, and SHA-512
`a1645a30f36b767c7534c2f6a53e99a9bfade993267efcca715f7a45d797d47d6561df787e9d4a51a3bdfc9be855d49d23fa3f4b91b2c661fd17314050836048`.
The downloaded archive passed that digest and contained one executable named
`antigravity`. It was moved byte-for-byte to Termux `~/.local/bin/agy`; the
archive and staging directory were removed. The retained executable is
202,985,704 bytes with SHA-256
`0735841949aaedc0ba4121a84b47b83b067b71af03d76897850077a4ee86bcdc`.
It is not committed to or repackaged by this repository.

`grun` was used strictly as the runtime loader; its `--configure`/`-c` binary
patch option was never used. `grun ~/.local/bin/agy --version` returned `1.2.2`
with exit 0 in 286 ms. `--help` returned exit 0 in 270 ms and documented print,
JSON, and streaming stdin/stdout modes. The executable's SHA-256 stayed unchanged
after both runs. After the first TUI launch, `HOME` measured 2,364,593 KiB, an
increase of 214,192 KiB including the 202,985,704-byte executable and
CLI-generated caches/state. No credential file content was inspected.

The first local Google OAuth selection exited with code 2. A redacted diagnostic
showed the exact cause: Android seccomp delivered `SIGSYS` when Go's
`os/exec.LookPath` called Linux `faccessat2` while `auth.OpenBrowser` tried to
launch a desktop browser. The crash occurs before any `xdg-open` executable
could run, so a Termux browser wrapper does not solve it. The first manual
Remote SSH token exchange exposed a separate transport issue: Go's pure resolver
looked for Android's absent `/etc/resolv.conf`, fell back to `[::1]:53`, and
failed to resolve the OAuth host. The redacted diagnostic contained no TLS or
certificate failure. Independently, the glibc runtime resolved `example.com`
and completed a verified TLS 1.3 connection with its packaged CA bundle.

The minimal fix adds official Termux `proot 5.1.107.92` and its required
`libtalloc 2.4.3`; no distribution is installed. Apt downloaded 131 kB, completed
in 3 seconds, upgraded no existing package, and increased `PREFIX` by 414 KiB.
Two owner-only files provide a 48-byte hosts map and 38-byte resolver map under
`~/.local/share/rava-antigravity/etc`; `proot` binds them at `/etc/hosts` and
`/etc/resolv.conf` for the child. Their SHA-256 values are
`00e987d761e447af9e2d73ff09ccc896273948c756a71d5bfb8db3a73dc2eb54` and
`7e8ad76e0d200e93918ca2e93c99ff8ecd02071953bf1479819db3ac0dbb6d07`.
With `GODEBUG=netdns=go+1` and
`SSL_CERT_FILE=$PREFIX/glibc/etc/ssl/certs/ca-certificates.crt`, a bounded
unauthenticated `agy --print` run started both listeners, logged zero DNS
failures, and exited with the expected `authentication required` diagnostic.

The official [installation and authentication
guide](https://antigravity.google/docs/cli/install/) provides a Remote SSH OAuth
flow that prints a URL and accepts the browser-returned authorization code.
Starting the same unmodified executable through the private `proot` mounts under
standard `SSH_CONNECTION` and `SSH_TTY` variables was confirmed in its redacted
log as `ssh=true`; the process remained alive at the login-method screen with no
DNS error. The first token exchange then timed out while connecting to
`oauth2.googleapis.com:443`. After the owner enabled the phone's VPN, independent
Bionic curl and glibc OpenSSL checks both resolved that host and completed a
verified TLS connection. A fresh official SSH-mode OAuth run then completed
successfully. No credential or token file was opened, copied, or logged.

`agy models` subsequently completed with exit 0 in 5.82 seconds through the same
wrapper and listed these official model slugs:

```text
gemini-3.8-flash-high
gemini-3.8-flash-medium
gemini-3.8-flash-low
gemini-3.7-flash-high
gemini-3.7-flash-medium
gemini-3.7-flash-low
gemini-3.6-flash-high
gemini-3.6-flash-medium
gemini-3.6-flash-low
gemini-3.1-pro-high
gemini-3.1-pro-low
claude-sonnet-4-6
claude-opus-4-6-thinking
gpt-oss-120b-medium
```

A documented NDJSON stdin run selected `gemini-3.8-flash-low`, used stream-JSON
input and output, disabled slash commands, selected plan/read-only behavior, and
closed stdin after one user message. It emitted `init`, three `step_update`
events, and one terminal `result`; no tool event occurred. The result had status
`SUCCESS`, exit 0, and text exactly `سلام راوا\n`. Wall time was 8.913 seconds;
provider duration was 1.915825936 seconds and reported usage was 14,019 input,
3 output, 0 thinking, 0 cache, and 14,022 total tokens.

After that process exited, a new process resumed conversation
`32304ca1-3736-47d7-a175-c755c6646dd2` with the documented `--conversation`
argument. It returned exactly `ادامه راوا\n`, status `SUCCESS`, `num_turns: 2`,
exit 0, and the same conversation ID in 8.186 seconds, again with no tool event.
The CLI's cumulative usage became 28,243 input, 7 output, and 28,250 total, so
the attributable second-turn delta was 14,224 input, 4 output, and 14,228 total.
Stdout remained machine-readable and stderr contained only the benign warning
that plan mode has no effect while slash-command expansion is disabled.

Termux `glibc-runner 2.0-3` expands `$@` without quotes. Two pre-network attempts
therefore split prompts containing spaces and exited 2. The retained runner in
this repository invokes the same packaged glibc loader directly and forwards
`"$@"` correctly; a quoted-path version check passed without changing the
official binary. `antigravity-termux.env` pins every artifact/version, the
installer verifies size and hashes before installation, and the runner preserves
the private resolver, pure-Go DNS, and CA environment used in the device proof.

The older `android-installer` module already declares
`com.termux.permission.RUN_COMMAND` and its `TermuxBridge` targets Termux's
documented `RunCommandService`. Termux's [official RUN_COMMAND
documentation](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent)
requires `allow-external-apps=true` plus an explicit user grant and can return
stdout, stderr, and exit status through a `PendingIntent`. The standalone
`android-runtime-probe` intentionally has no Termux permission or bridge, and no
Rava-to-Termux Antigravity command has been sent. Even if technical IPC is later
verified, the policy ambiguity remains and should be disclosed rather than
described as risk-free.

The Gemini CLI personal-account path remains blocked by provider retirement.
The Antigravity/Termux fallback has now proven official OAuth, model discovery,
a real streamed response, and process-restart conversation resume. Integration
through Termux's documented `RunCommandService`, cancellation, logout, refresh,
network-loss behavior, and policy acceptance for anything beyond the owner's
approved personal use remain open.
