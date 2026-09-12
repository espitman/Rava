# Rava 1.0 physical-device smoke test

Date: 2026-09-12 (Asia/Tehran)

## Delivered artifacts

- Application ID: `ir.rava.installer`
- Version: `1.0.1` (`versionCode 14`)
- Device target: Android 8.0+ on `arm64-v8a`
- Debug APK: `android-installer/app/build/outputs/apk/debug/app-debug.apk`
  - Size: 78,049,423 bytes
  - SHA-256: `024d0133702bc201ce423670b8ae2145fb2a270a3b0e4e3422a226c0fe0fad38`
- Client AAR: `android-installer/rava-client/build/outputs/aar/rava-client-release.aar`
  - Size: 4,453 bytes
  - SHA-256: `47c984bf1a9bca4204e22462050a5b198f0ce869904b28832e9a25d3a8aebb96`

The APK above is debug-signed. A distributable build must use the owner's stable
release signing key. Apps using the engine service must use that same signing
certificate.

## Device and runtime

- Physical device: Xiaomi 2107113SG
- Android: 14 / API 34
- ABI: arm64-v8a
- Native page size: 4 KB; APK/ELF 16 KB compatibility checks also pass
- Codex app-server: 0.154.0, embedded and hash-verified at build/startup
- Antigravity CLI: 1.2.2, installed under official Termux and hash-verified
- Cold activity launch measured with `am start -W`: 555 ms
- Idle/background app `TOTAL PSS` observed after launch: 44,530 KB

The launch and memory figures are one observation on this device, not a
benchmark across phones. Provider response latency was not reported because the
manual test did not capture a reliable start/end timestamp pair.

## Passed checks

- Shell syntax and Antigravity safety assertions passed.
- Gradle `clean testDebugUnitTest lintDebug assembleDebug
  :rava-client:assembleRelease` passed: 116 actionable tasks.
- Final APK installed as an upgrade while preserving the local archive and both
  provider logins.
- The Setup dashboard correctly reported all five live readiness checks on the
  device: Termux installed, command access granted, Antigravity installed,
  Google signed in, and ChatGPT signed in.
- The redesigned Setup screen keeps each status beside its related action. The
  Settings tab switches and persists the app-wide light/dark appearance, and
  the selected model plus the complete model dropdown remain readable in dark
  mode.
- Rava loaded 20 confirmed models: six Codex models and fourteen Antigravity
  models. A provider failure does not hide the other provider's models.
- `codex/gpt-5.6-sol` returned `CODEX_OK`, then after force-stop/restart resumed
  the same conversation ID and returned `SECOND_OK`.
- `antigravity/gemini-3.8-flash-low` returned `GEMINI_OK`, then after
  force-stop/restart resumed the same conversation ID and returned
  `GEMINI_SECOND`.
- Antigravity stream parsing requires the selected model in the `init` event,
  requires a successful final result, and fails closed on tool events.
- Antigravity chat uses a fresh temporary copy of the deny-all tool policy for
  every turn. The copy is deleted after the CLI exits; the canonical policy is
  kept unchanged.
- Codex uses an empty private workspace, read-only sandbox policy and approval
  policy `never`. Rava declines server approval requests and terminates the
  process if any local-action item is observed.
- Backup and cleartext network traffic are disabled in the Android manifest.
- An Android shell UID was denied access to `RavaEngineService`; the exported
  service requires the signature-level `USE_ENGINE` permission and also checks
  each Messenger message's sending UID certificate.
- The temporary `ir.rava.runtimeprobe` development app was removed from the
  phone. Only the final `ir.rava.installer` Rava package remains.

## Deliberately unforced checks

The test did not exhaust quotas, corrupt/expire credentials, or log either
provider out. Those actions would disrupt the owner's working accounts without
improving the delivered chat path. UI cancellation, network-loss recovery, and
screen-off behavior remain bounded implementation paths rather than release-gate
measurements. Android may terminate an in-progress turn when the app is kept in
the background for a long time; the completed local archive remains intact.

Google's CLI is unmodified and the documented headless interface is used, but
Android plus Termux is not an explicitly supported Antigravity platform. Provider
terms and eligibility can change, so the project does not claim zero account
risk. Rava does not extract tokens, impersonate another OAuth client, automate a
website, use private endpoints, or bypass provider quotas.
