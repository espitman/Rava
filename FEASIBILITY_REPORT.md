# Rava Standalone Android Feasibility Report

Status: the shared native APK probe and the Codex app-server initialization gate
passed on a physical arm64 phone. Node and Gemini have separate evidence.

## Physical-device run

Tested at `2026-09-12T13:30:47+03:30` over USB ADB with the installed debug APK.

| Property | Measured value |
| --- | --- |
| Device | Xiaomi 2107113SG (`vili_global`) |
| Android | 14, API 34, security patch 2025-08-01 |
| Build fingerprint | `Xiaomi/vili_global/vili:14/UKQ1.231207.002/V816.0.22.0.UKDMIXM:user/release-keys` |
| Supported ABIs | `arm64-v8a, armeabi-v7a, armeabi` |
| App process | 64-bit; primary ABI `arm64-v8a` |
| Kernel page size | 4096 bytes from Android and native `sysconf` |
| App-reported available storage | 37,819,326,464 bytes at activity startup |
| ADB `/data` available storage | 36,932,352 KiB / 37,818,728,448 bytes / 35.22 GiB |
| App ID and version | `ir.rava.runtimeprobe`, version 0.1.0 (code 1) |
| Installed/local APK SHA-256 | `2fa1a0d81e243367cf9b5d62f0ab8266585183e018ed74d0821153ab279bb176` |

The matching installed and local hashes confirm that the inspected APK is the
one built at `android-runtime-probe/app/build/outputs/apk/debug/app-debug.apk`.

## Embedded native execution

The installed executable was present and executable at:

```text
/data/app/~~YHzM2CN94fhzL0In2GcO1g==/ir.rava.runtimeprobe-WUR8E0jPeRdnpTzd9SVRzA==/lib/arm64/librava_probe_exec.so
-rwxr-xr-x 1 system system 6888 1981-01-01 01:01
```

It was launched directly from that `nativeLibraryDir` path under the app UID
(`u0_a538`, numeric UID/GID 10538) using `run-as`. It was not copied to or
executed from writable app storage. ADB returned process exit code 0 and stdout:

```json
{"probe":"rava-native","version":1,"abi":"arm64-v8a","pageSizeBytes":4096,"pid":15105,"uid":10538,"gid":10538,"cwd":"/data/user/0/ir.rava.runtimeprobe","locale":"C.UTF-8","unicode":"فارسی ✓","kernel":"5.4.289-qgki-g73b3c5bfb605","argc":2,"argv":["/data/app/~~YHzM2CN94fhzL0In2GcO1g==/ir.rava.runtimeprobe-WUR8E0jPeRdnpTzd9SVRzA==/lib/arm64/librava_probe_exec.so","--json"]}
```

This confirms Android-native process startup, arm64 selection, agreement on the
4 KiB page size, app-UID execution, UTF-8/Farsi output, and a clean exit. The APK
uses Android's extracted native-library arrangement; no Termux path or external
runtime participated in this probe.

## Codex app-server initialization

A clean combined build packaged Node, the Gemini bundle, and Codex together. The
resulting APK was 140,627,977 bytes with SHA-256
`5c5a0a3220b3fb0608f090efe140336d358867a4891f677fe1d2e8fa5b9eadc4`;
the installed `base.apk` returned the same hash. APK v2 signature verification
and `zipalign -c -P 16 4` both passed. From that one installation, the probes ran
sequentially with these results:

| Probe | Result |
| --- | --- |
| Node | Node `24.18.0`, Android/arm64, Farsi file I/O, Persian `Intl`, HTTPS 204, DNS, child shell, restart persistence, and exit 0 |
| Gemini | `gemini.js --version` printed `0.59.0` on stdout with empty stderr and exit 0 |
| Codex | JSON-RPC `initialize` returned the expected result and exited 0 after stdin closed |

Two Node attempts immediately before the successful sequence saw transient
network failures (`ECONNABORTED` and an HTTPS timeout) while the phone reported a
partially connected but validated Wi-Fi network. The unchanged APK then completed
HTTPS 204 and DNS successfully. This is recorded as network variability rather
than a runtime or packaging failure.

The official OpenAI `rust-v0.154.0`
`codex-app-server-aarch64-unknown-linux-musl` release was downloaded with its
Sigstore bundle. The archive SHA-256 matched
`68d30a1cce7ce070d52e0855bcd1ec27d6c4d8e64ab9dfac5b1a09ec08de094f`.
The bundle SHA-256 matched
`928a3faf8b26af4d9d58bd8f93af206d85e96ab9ef13a9bd1e03a3ac7356940f`,
and `cosign verify-blob` accepted the OpenAI release workflow identity for the
exact tag. The extracted executable SHA-256 was
`0c2495cedd0e01fd6ba1e9d949b637f55ac283e6019b998024c010788da8c508`.

The APK preserved that executable byte-for-byte as
`lib/arm64-v8a/libcodex_app_server.so`. Android installed it into the app's
read-only `nativeLibraryDir`; the probe launched it under app UID `u0_a538` as:

```text
libcodex_app_server.so --listen stdio://
```

After the app wrote the pinned newline-delimited `initialize` and `initialized`
messages over stdin, stdout returned:

```json
{"id":1,"result":{"userAgent":"rava_runtime_probe/0.154.0 (Linux Unknown; aarch64) unknown (rava_runtime_probe; 0.1.0)","codexHome":"/data/user/0/ir.rava.runtimeprobe/files/runtime-home/.codex","platformFamily":"unix","platformOs":"linux"}}
```

Closing stdin produced exit code 0. No Termux executable, service, prefix, or
writable executable directory participated, and the reported Termux startup-lock
failure did not occur in this deployment path. Stderr contained only a nonfatal
warning that `bubblewrap` was absent from `PATH`; app-server said it would use its
bundled copy. Tool execution and the bundled sandbox helper remain untested.

The official binary is a static AArch64 `ET_EXEC` with no interpreter or dynamic
dependencies and 64 KiB `PT_LOAD` alignment. It passed host ELF checks and ran on
this 4 KiB-page device. A physical 16 KiB-page device is still needed before
claiming 16 KiB runtime compatibility. Reproduction details and the remaining
Codex risks are in `android-runtime-probe/CODEX_ANDROID.md`.

## Remaining gate

Device characterization, generic native execution, and Codex JSON-RPC
initialization are verified. Codex authentication, a real conversation, SQLite
state/locking, subprocess tools, and sandbox behavior remain open. The physical
16 KiB-page gate also remains open.
