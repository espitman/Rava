# Codex app-server on Android

## Verified result

On 2026-09-12 the official Codex `0.154.0` Linux/musl ARM64 release completed a
JSON-RPC `initialize` exchange on a physical Xiaomi 2107113SG running Android 14
(API 34). The executable ran directly from the APK's extracted
`nativeLibraryDir` under the application UID. No Termux executable, prefix,
service, or writable executable directory participated in startup.

The response reported:

```json
{"id":1,"result":{"userAgent":"rava_runtime_probe/0.154.0 (Linux Unknown; aarch64) unknown (rava_runtime_probe; 0.1.0)","codexHome":"/data/user/0/ir.rava.runtimeprobe/files/runtime-home/.codex","platformFamily":"unix","platformOs":"linux"}}
```

The server exited with code 0 after stdin closed. It also emitted a nonfatal
warning that `bubblewrap` was not on `PATH` and it would use its bundled copy.
That warning does not affect initialization, but command/tool sandbox execution
is a separate gate and has not been validated on Android.

This direct app deployment did not reproduce the reported Termux startup-lock
failure. The result is specific to the pinned binary and initialization path; it
does not claim that all app-server features work on Android.

## Pinned official release

All values used by the scripts live in `codex-versions.env`:

| Input | Pinned value |
| --- | --- |
| Codex release | `rust-v0.154.0` / `0.154.0` |
| Upstream commit | `6b9826e3aa83b1a5947db50f4332cb9c65f1b340` |
| Release archive | `codex-app-server-aarch64-unknown-linux-musl.tar.gz` |
| Archive SHA-256 | `68d30a1cce7ce070d52e0855bcd1ec27d6c4d8e64ab9dfac5b1a09ec08de094f` |
| Sigstore-verified executable SHA-256 | `0c2495cedd0e01fd6ba1e9d949b637f55ac283e6019b998024c010788da8c508` |
| Android DNS-patched executable SHA-256 | `2442c82c9dc82903b6e50b1695a459d1441c56500852f14e3d03a344e3239d0a` |
| Sigstore bundle SHA-256 | `928a3faf8b26af4d9d58bd8f93af206d85e96ab9ef13a9bd1e03a3ac7356940f` |
| Certificate identity | `https://github.com/openai/codex/.github/workflows/rust-release.yml@refs/tags/rust-v0.154.0` |
| OIDC issuer | `https://token.actions.githubusercontent.com` |

The Sigstore bundle signs the extracted executable rather than the compressed
archive. The preparation script checks the GitHub release archive hash, bundle
hash, single expected archive entry, extracted executable hash, certificate
identity, and issuer before installing any bytes in the APK source tree.

The official static musl executable contains two `/etc/resolv.conf` resolver
paths, while Android applications do not have that file. After signature and
hash verification, the preparation script replaces exactly those two fixed-size
strings with the app-relative `resolv.conf` path plus NUL padding. The activity
writes that app-private file from Android's active `LinkProperties`. The script
also checks the exact patched-output hash before packaging it.

The Linux-targeted `rustls-native-certs` dependency also expects a PEM trust
bundle. Passing Android's certificate directory alone resolves names and opens
TCP port 443, but TLS still fails. Before starting an account session,
`CodexAuthStorage` deterministically concatenates Android's public system trust
anchors from `/system/etc/security/cacerts` into an owner-only file under
`codex-home` and sets `SSL_CERT_FILE` for app-server. No private key or account
credential is copied into this trust bundle.

The artifact is Apache-2.0 licensed. It is a static AArch64 ELF `ET_EXEC` with no
dynamic interpreter, RPATH, RUNPATH, or shared-library dependency. Its `PT_LOAD`
segments are aligned to 64 KiB, which satisfies the static 16 KiB ELF alignment
check. The test phone itself used 4 KiB pages; operation on a physical 16 KiB-page
device remains unverified.

## Reproduce packaging

Install `cosign`, then run:

```sh
cd android-runtime-probe
./tests/test-codex-build-scripts.sh
./scripts/fetch-codex-release.sh
../android-installer/gradlew -p . \
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The fetch script downloads only when its cache entries are absent. Set
`RAVA_DOWNLOAD_DIR` to use a disposable cache. It stages the verified executable
as:

```text
app/src/main/jniLibs/arm64-v8a/libcodex_app_server.so
```

The `.so` suffix is an APK packaging convention; this file is an executable. The
Gradle configuration preserves the patched file byte-for-byte during native-
library packaging. Android installs it in the app's read-only executable native
library directory; the official input remains independently reproducible and
Sigstore-verified before the documented Android compatibility patch is applied.

## Reproduce the device initialization probe

After installing the debug APK, the launch extra runs the Codex probe without UI
coordinate automation:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop ir.rava.runtimeprobe
adb shell am start --user 0 -W --es probe codex \
  -n ir.rava.runtimeprobe/.MainActivity
adb shell run-as ir.rava.runtimeprobe cat files/last-command.txt
```

The app starts:

```text
libcodex_app_server.so --listen stdio://
```

It writes these newline-delimited protocol messages, waits briefly for the
response, closes stdin, and captures stdout and stderr in separate app-private
files:

```json
{"method":"initialize","id":1,"params":{"clientInfo":{"name":"rava_runtime_probe","title":"Rava Runtime Probe","version":"0.1.0"},"capabilities":{"experimentalApi":false}}}
{"method":"initialized"}
```

The protocol intentionally omits a JSON-RPC version field for this pinned app-
server version. Success requires a response with `id: 1`, a `result`, and exit
code 0.

## Account and device-code protocol

The probe implements the pinned `0.154.0` app-server schema as a long-lived,
line-delimited JSON session. After `initialize` and the `initialized`
notification, it can send `account/read` with `refreshToken: false` or
`account/login/start` with type `chatgptDeviceCode`. Request IDs are fixed and
correlated; protocol output is parsed only from stdout, while stderr remains
diagnostic output. The session handles `account/login/completed`,
`account/updated`, protocol errors, process exit, and the documented
`account/login/cancel` request.

The activity provides separate account-status, start-sign-in, open-page, and
cancel controls. It enables the page button only after app-server returns an
HTTPS URL whose host is exactly `auth.openai.com`, and shows the one-time code in
the app UI. It never accepts an API key or reads browser cookies.

Codex receives a dedicated `CODEX_HOME` at `files/codex-home`. The probe creates
that directory with owner-only access and writes
`cli_auth_credentials_store = "file"` to its owner-readable configuration. The
manifest disables backup and cleartext traffic. Transient device-code probe
state is app-private and is removed after automated boundary testing; the code
is not written to reports or command logs.

Host unit tests cover exact request shape, response/notification/error parsing,
verification-URL rejection, account storage permissions, the complete
initialize-to-device-code transcript, and formal cancellation. The physical
device returned `signed_out` with `requiresOpenaiAuth: true` for `account/read`.
With the resolver patch and app-private PEM bundle, the physical device returned
the official device-code response with a nonempty `loginId` and one-time code;
the supplied HTTPS host passed the exact `auth.openai.com` allowlist. The probe
did not print or persist the URL/code outside app-private transient state. It
immediately sent `account/login/cancel` with the returned `loginId`, received a
successful cancellation result, closed with exit code 0, and deleted transient
state before any browser or user interaction.

## Verified authenticated conversation and resume

The user then completed the app-server-managed ChatGPT device-code flow. A fresh
app-server process after an Android force-stop returned authenticated account
state, establishing credential persistence in the app-private Codex home. The
probe does not copy the account email or credential values into its UI log or
conversation-state file.

The installed Phase 3 APK had SHA-256
`8168c9ff02498a66e9ce2bb7ef80365b3f5fbedfd29ff5f7051611c9b8bda29c`.
Its conversation client performed `model/list`, selected the server's thread
default, and sent `thread/start` with `approvalPolicy: "never"`,
`sandbox: "read-only"`, a new empty app-private working directory, and a
chat-only instruction. It then sent `turn/start` with the text input `Reply with
exactly: سلام راوا`. The authoritative completed assistant item matched
`سلام راوا`, `turn/completed` reported `completed`, and app-server exited after
stdin closed.

Android then force-stopped the application. A new activity and app-server
process loaded the persisted nonsecret thread ID with `thread/resume` and sent a
second turn. The persisted thread ID remained identical, the second expected
response matched, the turn completed, and the process again exited cleanly.
Only `threadId`, `status`, and `responseMatched` are stored in the owner-only
conversation-state file; assistant text, account data, tokens, and diagnostics
are not stored there.

Assistant deltas, authoritative completed messages, turn completion/failure,
JSON-RPC errors, and stderr diagnostics have separate callbacks. Command and
file-change approval requests receive `decline`; unsupported server requests
receive a fail-closed JSON-RPC error. `turn/interrupt` is implemented and covered
by a host process test. A real interrupted model turn, expired authentication,
logout, quota errors, and any attempted tool execution remain unverified.

## Source-build experiment and remaining work

`scripts/build-codex-android.sh` remains available for a true
`aarch64-linux-android` source build with Rust 1.95.0, NDK 27.2.12479018, API 26,
`cargo --locked`, bounded job count, and an isolated work directory. The first
full attempt was stopped before compilation because the host had only about
4.1 GiB free; a clean build requires at least 12 GiB. Smaller cross-checks did
compile `codex-process-hardening`, `codex-utils-pty`, and
`codex-keyring-store` for the Android target.

The following runtime areas still need dedicated device tests:

- Keystore-backed wrapping and explicit logout cleanup for the verified
  app-private file credential store.
- SQLite state, WAL behavior, and file locks in app-private storage.
- PTY, process-group, shell, MCP subprocess, and command cancellation behavior.
- The bundled bubblewrap path and the policy for disabling unavailable tools in a
  chat-only Android profile.
- Credential refresh, logout, and provider/quota failures.
- A real interrupted model turn and intentional tool-attempt containment.

The verified turns establish authentication and persisted conversation flow.
They do not establish all failure handling or safe tool execution.
