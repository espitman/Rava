# Rava standalone Android runtime probe

This is a separate Android application (`ir.rava.runtimeprobe`) for testing the
runtime gates in `STANDALONE_PLAN.md`. It does not modify or replace the current
Rava application. The app reports Android release/API, ABI, process bitness,
kernel page size, available app storage, and the installed native-library path.

## Pinned inputs

- Node.js `24.18.0-1` from the official Termux `aarch64` repository, repackaged
  with its complete shared-library closure. The source-build experiment for
  Node.js `24.21.0` remains available separately.
- Android NDK `27.2.12479018`, API 26, `arm64-v8a`.
- Gemini CLI `0.59.0`, using its npm release bundle.
- Codex app-server `0.154.0`, using the official Sigstore-signed Linux/musl ARM64
  release artifact.
- Android Gradle Plugin `8.7.3`, compile/target SDK 35, minimum SDK 26.

Hashes and npm integrity are in `versions.env` and
`termux-runtime-packages.tsv`. Generated Node, Gemini, download, and build files
are ignored by Git. The scripts fail on a checksum or package-size mismatch.
Downloaded archives are removed after a successful build; set
`RAVA_KEEP_DOWNLOADS=1` to retain the verified archives.

## Packaging strategy

The default build takes the Android PIE from the official Termux package and
stores it as `lib/arm64-v8a/libnode_runtime.so`. Its Termux shared libraries are
also packaged under APK-safe names ending in `.so`. The preparation script
changes their dynamic `RUNPATH` to `$ORIGIN`, rewrites matching `DT_NEEDED` and
`DT_SONAME` strings, and verifies the complete dependency closure with the pinned
NDK `llvm-readelf`. Android installs them into the app's read-only
`nativeLibraryDir`; the Java probe invokes Node there and never copies executable
code to writable app storage. JavaScript and the complete multi-file Gemini
bundle are ordinary assets recursively copied to private storage and interpreted
by the packaged runtime.

The Termux c-ares build hardcodes its own `resolv.conf` path. The probe changes it
to an app-local file and writes the active Android network's DNS servers before
launch. It also maps the Termux child-process shell paths to `/system/bin/sh`.
`HOME`, `TMPDIR`, and `LD_LIBRARY_PATH` point only into the app or its installed
native directory. `TERMUX_VERSION=rava-standalone` bypasses clipboardy's import
guard on Android; clipboard actions themselves still need an Android UI bridge.

This is a feasibility arrangement, not proof of upstream Android support. Node's
own build documentation says Android is unsupported and is not covered by Node CI.
The app deliberately keeps stdout and stderr separate so loader, TLS, and CLI
failures remain diagnosable.

Every host build also packages a small `librava_probe_exec.so` PIE. The first UI
button launches it directly from Android's `nativeLibraryDir` and prints native
ABI, page size, UID, kernel, working directory, and a Farsi Unicode marker as
JSON. The Codex hook expects `libcodex_app_server.so` at the same location,
launches `--listen stdio://`, and writes the pinned protocol's `initialize` and
`initialized` messages as newline-delimited JSON. Run
`scripts/fetch-codex-release.sh` to verify and stage the official artifact; see
`CODEX_ANDROID.md` for hashes, Sigstore identity, and the source-build experiment.
The native smoke test passes on a device only when it exits with code 0, prints
`فارسی ✓`, reports the same page size as the Java device report, and its command
path is under the installed `nativeLibraryDir`.

## Build

The existing Rava Gradle wrapper is reused so this probe stays isolated without
duplicating wrapper binaries.

```sh
cd android-runtime-probe
./scripts/build-apk.sh
```

`ANDROID_HOME` or `ANDROID_SDK_ROOT` must point at an SDK containing the pinned
NDK. `prepare-termux-node-runtime.sh` checks every package hash and size, ELF
architecture, dependency closure, and a minimum 16 KiB `PT_LOAD` alignment.
`build-node-android.sh` remains an optional source-build experiment and is not used
by the default APK build.

For a fast source/Gradle check that does not require generated provider runtime artifacts:

```sh
./scripts/verify-host.sh
```

For a connected physical device:

```sh
./scripts/device-smoke-test.sh
./scripts/device-node-gemini-smoke-test.sh
```

The script records Android release/API, ABI list, kernel page size, and `/data`
space, installs the probe, and opens it. A successful Node probe reports
Node/Android/arm64, Farsi file round-trip, Persian `Intl`, an incrementing restart
counter, HTTPS status 204, c-ares DNS, the default child-process shell, and exit
code 0. The Gemini button must print exactly the pinned CLI version and exit 0.
The same probes can be started over ADB with the `probe` intent extra set to
`node`, `gemini-version`, or `codex`; the latest result is saved as
`files/last-command.txt` for `run-as` inspection.

## Current boundary

The Node runtime and Gemini `--version` gates pass on the connected Android 14
arm64 phone, including a run while the installed Termux package was disabled.
The Codex app-server also completes JSON-RPC initialization directly from the
APK's `nativeLibraryDir` without a Termux startup lock. Its authentication,
conversation, command-tool, and sandbox paths remain separate gates.
Gemini's published bundle excludes optional native PTY/keyring packages, which is
sufficient for the verified version probe. Browser sign-in/callback handling,
credential persistence, a real model response, streaming/error schema, and
cancellation remain unverified. Clipboard calls also need a native Android bridge.
