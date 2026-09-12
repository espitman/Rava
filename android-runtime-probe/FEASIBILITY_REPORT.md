# Node/Gemini Android feasibility report

Date: 2026-09-12 (Asia/Tehran)

The bounded official-Termux binary path passes the Node runtime and Gemini
`--version` gates on a physical arm64 Android phone. It does not require the
Termux application at runtime.

## Verified artifact and device

| Item | Result |
| --- | --- |
| Device | Xiaomi 2107113SG, Android 14 / API 34, `arm64-v8a`, 4096-byte kernel page |
| Node | Official Termux `nodejs-lts` `24.18.0-1`; reports Node `v24.18.0`, `platform=android`, `arch=arm64` |
| Gemini CLI | npm bundle `0.59.0`, 448 files, 97,990,558 uncompressed asset bytes |
| Debug APK | 140,627,977 bytes; SHA-256 `5c5a0a3220b3fb0608f090efe140336d358867a4891f677fe1d2e8fa5b9eadc4` |
| Install identity | SHA-256 of the device's installed `base.apk` exactly matched the local APK |
| APK checks | v2 signature verified; `zipalign -c -P 16 4` passed |
| Termux independence | Both probes passed while Android package `com.termux` was disabled; it was re-enabled after the test |

The on-device Node run passed UTF-8/Farsi file round-trip, Persian `Intl`, a
persistent restart counter (observed count 12), HTTPS with status 204, c-ares
`resolve4`, the default `child_process` shell, clean stdout/stderr capture, and
exit code 0. The Gemini run printed exactly `0.59.0`, had empty stderr, and exited
0. The executable command path was inside this APK's installed
`nativeLibraryDir`; JavaScript came from this app's private `filesDir`.
Two immediately preceding runs on the same unchanged APK hit transient Wi-Fi
errors (`ECONNABORTED`, then HTTPS timeout); the successful retry establishes the
runtime path but does not eliminate ordinary network variability.

## Official Termux inputs

The repository snapshot was discovered from the official aarch64 `Packages.gz`
index dated 2026-09-12; its SHA-256 was
`66fe1bc1214cca67f221c09db37968e3f097a8eb6048b50858efdecda5acd6a4`.
`termux-runtime-packages.tsv` pins every exact package version, byte size,
repository-relative URL, and SHA-256. The downloaded set is bounded to Node,
its six runtime dependencies, and the small shared-license package.

The Node ELF uses `/system/bin/linker64`, is an AArch64 PIE, and every packaged
ELF has `PT_LOAD` alignment of at least `0x4000`. The original Node dependencies
were `libz.so.1`, c-ares, SQLite, OpenSSL 3, ICU 78, `libc++_shared.so`, and Android
system libraries. Android Gradle packaging ignores version-suffixed names such as
`libz.so.1`, so the script installs APK-valid `.so` names and rewrites matching
`DT_NEEDED`/`DT_SONAME` entries. All non-system dependencies then resolve inside
the APK, and every Termux library `RUNPATH` is `$ORIGIN`.

Runtime prefix assumptions are handled explicitly:

- c-ares reads an app-local `resolv.conf` generated from Android
  `ConnectivityManager`, and `/system/etc/hosts`.
- Node's Termux `/bin/sh` and `/bin/bash` fallbacks resolve to `/system/bin/sh`.
- `HOME` and `TMPDIR` point to this app's private storage; `LD_LIBRARY_PATH`
  points to its installed native library directory.
- Gemini's bundled clipboardy module throws at import time on Android unless
  `TERMUX_VERSION` exists. The probe supplies `TERMUX_VERSION=rava-standalone`.
  This only selects clipboardy's Termux adapter; no clipboard operation is used
  by the verified version/headless startup path.

## License verification

The APK assets contain Node's full package copyright/third-party notice plus the
license texts for libc++, OpenSSL, c-ares, SQLite, zlib, and ICU. The Termux
libicu package's own license file is the text `404: Not Found`, caused by a bad
Git ref in its current recipe. The preparation script obtains the official
`release-78.3` ICU license directly and verifies SHA-256
`e55522d81edc687a341a4411e0776e54ca654e90147f354a90458aaced4116af`.

## Remaining gates

This result proves standalone runtime loading and Gemini CLI JavaScript startup,
not full Gemini product integration. Google browser login and callback routing,
credential storage, a real headless response, streaming/error schema,
cancellation, and multi-turn restoration across process restart still require
implementation and device testing. Clipboard operations require an Android-native
bridge because the selected bundled adapter calls Termux clipboard commands.

The optional Node 24.21.0 source-build route remains incomplete. Its first build
found and corrected a host/target archiver mix-up; a later clean build was stopped
for host disk pressure before the correction reached the original failure point.
The official Termux binary route avoids that large source build and is the only
verified runtime source in this report.
