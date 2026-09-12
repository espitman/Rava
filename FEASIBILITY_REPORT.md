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

The Phase 3 pre-authentication build was 218,479,045 bytes with SHA-256
`d99c388445570ec5d3e3d2763a4ad139b6795a98ef1519a7bde542faf5863859`.
It packages the Android DNS-patched Codex executable with SHA-256
`2442c82c9dc82903b6e50b1695a459d1441c56500852f14e3d03a344e3239d0a`.
The patch changes only the two pinned musl `/etc/resolv.conf` strings after the
official input passes hash and Sigstore checks. The app writes Android's active
DNS configuration and a deterministic PEM bundle of public system trust anchors
to app-private storage; Codex receives them through its working directory and
`SSL_CERT_FILE`.

On the physical device, `account/read` returned signed-out state with OpenAI
authentication required. The integrated UI then received a device-code response
with a nonempty code and `loginId`; the verification URL passed an exact HTTPS
`auth.openai.com` allowlist. Automation recorded only those boolean checks, then
closed the process and deleted transient state before opening a browser or
performing user interaction. A separate device session sent the documented
`account/login/cancel` request with the real `loginId`, received success, and
exited 0. `codex-home`, `config.toml`, and the generated CA bundle were owner-only;
credential persistence is pinned to Codex's file store. No API key or browser
cookie path is present.

## Codex authenticated conversation

The user completed the documented app-server device-code flow, and
`account/read` remained authenticated after the Android app and app-server were
restarted. The Phase 3 APK SHA-256 was
`8168c9ff02498a66e9ce2bb7ef80365b3f5fbedfd29ff5f7051611c9b8bda29c`.

On the physical phone, the installed client completed `model/list`,
`thread/start`, and `turn/start`. The authoritative final assistant item exactly
matched the requested `سلام راوا`, the turn status was `completed`, and the
app-server closed cleanly. After an Android force-stop, a new app-server process
resumed the same persisted thread ID and completed a second matching turn. The
owner-only state file contains only `threadId`, `status`, and `responseMatched`.

The thread uses the strongest stable chat-only restrictions exposed by the
pinned schema: an empty app-private working directory, read-only sandbox,
approval policy `never`, explicit no-tool instructions, automatic decline for
command/file approval callbacks, and fail-closed responses for unsupported
server requests. This does not yet prove that every tool path is unavailable;
an intentional tool attempt and Android sandbox behavior remain separate gates.

## Gemini personal-account blocker

The APK completed Google's OAuth approval and token exchange, but its first real
Gemini CLI headless request was rejected by the service with
`IneligibleTierError` / `UNSUPPORTED_CLIENT`. Google's official consumer-account
deprecation says Gemini CLI stopped serving individual, Google AI Pro, and Google
AI Ultra accounts on 2026-06-18 and no longer permits Login with Google for that
product. npm stable remains `0.59.0`; a bounded inspection of official
`0.60.0-preview.0` found byte-identical manual OAuth, setup, and ineligible-tier
functions and identical OAuth client/backend sets. A package upgrade cannot
restore the retired entitlement.

Google names Antigravity CLI as the migration target, but its official arm64
Linux `1.2.2` executable requires glibc and does not launch directly on Android.
After an Astra risk review and explicit personal-use approval, the Termux test
continued within a strict boundary: only Google's unmodified `agy`, only its
documented CLI interfaces, and no token inspection/export, private endpoint
calls, client-identity changes, or quota/safety bypass. This is not a zero-risk
policy conclusion. Google's FAQ warns about third-party access while its
headless guide expressly documents an application driving `agy` over
stdin/stdout; neither explicitly addresses an Android/Termux launcher.

Termux `glibc-runner 2.0-3` plus 40 automatic dependencies increased `PREFIX`
by 414,526 KiB. Google's 54,016,481-byte archive passed its published SHA-512;
the retained 202,985,704-byte `agy` has SHA-256
`0735841949aaedc0ba4121a84b47b83b067b71af03d76897850077a4ee86bcdc`.
`--version` returned `1.2.2` and `--help` passed on the phone without changing
the binary. Local OAuth then hit an Android seccomp `SIGSYS` on `faccessat2` in
Go's browser-launch lookup and exited 2. The documented Remote SSH mode avoids
that browser launcher. Its first code exchange exposed an absent Android
`/etc/resolv.conf`: Go fell back to `[::1]:53`, so the OAuth hostname did not
resolve. Official Termux `proot` plus `libtalloc` added only 414 KiB measured and
binds private hosts/resolver files without changing `agy`. A bounded
unauthenticated run then started its listeners with zero DNS failures and exited
with the expected authentication-required result. The fresh SSH-mode process
reported `ssh=true`. Its first token-exchange TCP connection timed out until the
owner enabled the phone's VPN; public DNS/TCP/TLS checks then passed from both
Bionic and the proot/glibc environment. The next official OAuth run succeeded.
No credential or token contents were inspected.

Under that exact environment, `agy models` returned 14 model slugs with exit 0.
A documented stream-JSON request to `gemini-3.8-flash-low` returned exactly
`سلام راوا`, status `SUCCESS`, no tool events, and exit 0 in 8.913 seconds. A
new process resumed the returned conversation ID and answered exactly
`ادامه راوا`, with `num_turns: 2`, no tool events, and exit 0 in 8.186 seconds.
Stdout and stderr stayed separate. The unmodified executable retained SHA-256
`0735841949aaedc0ba4121a84b47b83b067b71af03d76897850077a4ee86bcdc`.
Full measurements and primary links are in
`android-runtime-probe/GEMINI_AUTH.md`.

## Remaining gate

Device characterization, generic native execution, Codex JSON-RPC initialization,
account state, device-code sign-in, a real turn, and persisted thread resume are
verified.
The Gemini personal-account flow is blocked by provider retirement. Its
Antigravity replacement now passes official OAuth, model listing, a streamed
real turn, and process-restart resume through Termux under the disclosed policy
ambiguity. Rava-to-Termux service integration, Antigravity cancellation/logout/
refresh/network-loss behavior, and an APK-independent product decision remain
open. Codex credential refresh/logout, quota failures,
SQLite state/locking under contention, subprocess tools, and sandbox behavior
remain open. The physical 16 KiB-page gate also remains open.
