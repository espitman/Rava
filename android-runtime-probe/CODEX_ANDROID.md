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
| Extracted executable SHA-256 | `0c2495cedd0e01fd6ba1e9d949b637f55ac283e6019b998024c010788da8c508` |
| Sigstore bundle SHA-256 | `928a3faf8b26af4d9d58bd8f93af206d85e96ab9ef13a9bd1e03a3ac7356940f` |
| Certificate identity | `https://github.com/openai/codex/.github/workflows/rust-release.yml@refs/tags/rust-v0.154.0` |
| OIDC issuer | `https://token.actions.githubusercontent.com` |

The Sigstore bundle signs the extracted executable rather than the compressed
archive. The preparation script checks the GitHub release archive hash, bundle
hash, single expected archive entry, extracted executable hash, certificate
identity, and issuer before installing any bytes in the APK source tree.

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
Gradle configuration preserves it byte-for-byte during native-library packaging,
so the executable extracted from the APK has the same SHA-256 as the Sigstore-
verified release input. Android installs it in the app's read-only executable
native library directory.

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

## Source-build experiment and remaining work

`scripts/build-codex-android.sh` remains available for a true
`aarch64-linux-android` source build with Rust 1.95.0, NDK 27.2.12479018, API 26,
`cargo --locked`, bounded job count, and an isolated work directory. The first
full attempt was stopped before compilation because the host had only about
4.1 GiB free; a clean build requires at least 12 GiB. Smaller cross-checks did
compile `codex-process-hardening`, `codex-utils-pty`, and
`codex-keyring-store` for the Android target.

The following runtime areas still need dedicated device tests:

- Android credential persistence: the selected keyring dependency has no Android
  backend and falls back to memory unless Codex uses its protected file store.
- SQLite state, WAL behavior, and file locks in app-private storage.
- PTY, process-group, shell, MCP subprocess, and command cancellation behavior.
- The bundled bubblewrap path and the policy for disabling unavailable tools in a
  chat-only Android profile.
- ChatGPT device-code sign-in, credential refresh, and a real multi-turn response.

Passing `initialize` establishes app-UID startup and protocol viability. It does
not establish authentication, conversation flow, or safe tool execution.
