# Rava Standalone Android Implementation Plan

Status: proposed implementation; Android runtime feasibility is not yet proven.

## Decision and scope

Keep this repository. Reuse the Android chat UI, Vazirmatn font, RTL behavior,
bottom navigation, and archive where practical. Preserve the existing web-based
implementation as a migration reference until the replacement works.

The target is one APK containing:

- An Android-compatible Node.js runtime and Gemini CLI.
- An Android-compatible Codex app-server executable and required dependencies.
- Native account setup, chat, model selection, and conversation history.
- A local integration interface for the owner's other Android applications.

The installed app must not require Termux, Termux:X11, a separate server,
browser automation, copied website cookies, or a user-supplied paid API key.
A system browser may open for the provider's supported sign-in flow. Internet
access and eligible provider accounts remain necessary: models run remotely.
Usage is subject to Gemini CLI and Codex quotas, not unlimited website access.

This document supersedes PLAN.md for the new implementation. PLAN.md describes
the legacy engine and remains a historical record.

## Evidence and unresolved constraints

- Gemini CLI supports Google sign-in and reuses cached authentication in headless
  mode. It requires Node.js 20 or newer. Android is not listed among its
  recommended operating systems.
- The nodejs-mobile ready-made release inspected during research was 18.20.4.
  Do not assume that release satisfies Gemini CLI. Select and verify a compatible,
  maintained Node version and Android build instead.
- Codex app-server provides a documented client protocol and ChatGPT sign-in.
  The inspected npm launcher maps Android to Linux/musl binaries; that is not
  proof of native Android compatibility. Termux startup failures have also been
  reported. Verify each selected version rather than treating reports as universal.
- Modern Android restricts execution from writable app directories. Native code
  must be packaged using an Android-compatible loading/execution arrangement.
  Copying an executable into filesDir and calling chmod is not the deployment plan.
- CLI software being official does not make our Android port officially supported
  or guarantee account availability. Keep provider authentication and limits intact.

## Phase 0 — Preserve the existing project

- [x] Review the current tracked and untracked changes, exclude credentials and
  runtime data, and create a recoverable legacy snapshot before modifying the app.
  - مدل: `نامشخص (خانواده GPT-5؛ شناسه دقیق runtime گزارش نشده)`؛ ابزار: `Codex`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
- [x] Create a dedicated implementation branch for the standalone migration.
  - مدل: `نامشخص (خانواده GPT-5؛ شناسه دقیق runtime گزارش نشده)`؛ ابزار: `Codex` و `git`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
- [ ] Record the current APK version, application ID, signing configuration, and
  archive schema so upgrades can preserve existing user data.
- [ ] Add a separate runtime-probe module/application ID. Keep the current Rava
  installation usable while feasibility is tested.

Do not delete the repository or discard the uncommitted changes. Do not publish
or push the snapshot automatically as part of this plan.

## Phase 1 — Build an Android runtime probe

- [ ] Record the real device's Android version, ABI, page size, and available
  storage; begin with arm64-v8a. Use the physical phone, not an emulator.
- [ ] Pin exact upstream revisions and toolchain versions. Record artifact hashes,
  licenses, local patches, and reproducible build commands.
- [ ] Build or obtain a verified Android-compatible Node runtime meeting Gemini's
  selected version requirements. Verify native dependencies and TLS certificates.
- [ ] Package Node in the probe APK with a supported native loading arrangement;
  run JavaScript, HTTPS, file I/O, Unicode/Farsi, and a clean shutdown/restart.
- [ ] Attempt to build Codex app-server for Android/ARM64. Audit dependencies,
  filesystem assumptions, locks, process spawning, and sandbox startup. Document
  blockers before choosing any compatibility fallback.
- [ ] Package the Codex executable and required helpers using APK-native packaging;
  launch from the app UID and complete a JSON-RPC initialize exchange over pipes.
- [ ] Check native ELF and APK alignment for applicable 4 KB/16 KB devices and
  verify operation under the intended modern target SDK.

Gate: both runtimes start from inside the APK on a real phone without Termux,
root, an external runtime, or downloading executable code after installation.
Passing a desktop or Termux test does not satisfy this gate.

If a runtime fails, produce a concrete blocker and a bounded porting experiment.
Do not silently replace it with a paid API, server, or Termux dependency.

## Phase 2 — Prove Gemini sign-in and conversation flow

- [ ] Bundle the pinned Gemini CLI and production dependencies during the build.
  Audit native modules such as PTY and credential storage; adapt optional features
  only where their absence is explicitly supported and tested.
- [ ] Run the official Google sign-in flow using the system browser. Verify callback
  delivery, cancellation, and re-login without extracting website cookies or
  impersonating another OAuth client.
- [ ] Reuse the CLI-managed credentials for a headless request; receive a real
  model answer from inside the probe APK.
- [ ] Verify the chosen version's JSON/streaming schema, error events, cancellation,
  and end-of-response detection. Keep stderr diagnostics separate from payloads.
- [ ] Verify explicit model selection and detect any upstream fallback. Never show
  a requested model as confirmed unless the response/protocol supports that claim.
- [ ] Verify two-turn conversation continuation, process restart, credential refresh,
  logout, and loss of connectivity. Record available session-management commands.

Gate: sign in, ask, receive a complete response, continue, restart, and continue
again entirely through the APK. Record the actual tested versions and limitations.

## Phase 3 — Prove Codex sign-in and conversation flow

- [ ] Implement the documented app-server initialization and request correlation,
  notifications, errors, and process-exit handling using the pinned protocol schema.
- [ ] Use app-server-managed ChatGPT sign-in. Prefer the documented device-code
  flow where available; open the supplied verification URL in the system browser.
- [ ] Fetch account state and available models, then create a thread and send a turn.
- [ ] Render assistant text deltas and completion/errors. Distinguish assistant text
  from tool execution, reasoning events, and server diagnostics.
- [ ] Verify continuation, restart/resume, cancellation, expired authentication,
  logout, and quota errors using a real account.
- [ ] Restrict the chat-only profile's tool access. Verify filesystem and command
  permissions; handle or deny approval requests explicitly. Do not enable blanket
  auto-approval merely to bypass an unsupported sandbox or make a test pass.

Gate: the same end-to-end flow as Gemini succeeds within the APK. Clearly identify
this provider as Codex and its usage as Codex usage.

## Phase 4 — Implement Rava's internal engine

- [ ] Define a provider-neutral Android interface for account state, model selection,
  new/resumed conversations, messages, streaming events, cancellation, and errors.
- [ ] Run provider runtimes outside the UI thread, preferably in dedicated service
  processes so a native crash or runtime exit does not terminate the chat UI.
- [ ] Implement bounded provider queues, timeouts, process cleanup, and independent
  recovery so one unavailable provider does not block the other.
- [ ] Add appropriate service lifecycle handling and foreground notification when
  required by Android. Test screen-off, backgrounding, and app-process death.
- [ ] Keep credentials in app-private provider storage; exclude them from logs,
  backups, exports, and IPC. Use Keystore-backed protection where compatible with
  provider credential handling, and implement explicit logout cleanup.
- [ ] Expose a Bound Service interface for other personal apps; authenticate callers
  with signature permissions/UID checks and derive app isolation from caller
  identity. A caller-supplied app_id alone is not authentication.
- [ ] Publish a small client library/example for model listing, sending, streaming,
  continuation, cancellation, and error handling. HTTP is optional, not required.

The existing Python engine is a behavior reference. Do not accidentally introduce
an embedded Python requirement by assuming its HTTP implementation must be reused.

## Phase 5 — Migrate the Android UI and archive

- [ ] Replace the Termux setup steps with runtime readiness, Google sign-in,
  ChatGPT/Codex sign-in, account status, and actionable recovery states.
- [ ] Preserve English UI, compact bottom navigation, Persian font/RTL support,
  bubble styling, waiting dots, and archive empty states.
- [ ] Connect chat to the internal engine with actual provider/model labels and
  clear cancellation, retry, quota, and sign-in-required states.
- [ ] Persist local messages, provider session IDs, model identity, and ownership
  in an appropriate local database, with migration from the existing archive.
- [ ] Keep legacy website conversations readable as legacy entries. Do not claim
  their website conversation IDs can be resumed by Gemini CLI or Codex.
- [ ] Support individual and bulk deletion with confirmation. Define deletion scope
  from verified CLI capabilities: local Rava data, CLI session data, and any remote
  state must not be conflated. Never claim to delete website history through CLI.
- [ ] Preserve image rendering where actual responses provide supported media;
  feature-test attachments and image generation rather than assuming website parity.

## Phase 6 — Validate and retire the legacy stack

- [ ] Test a clean installation and both login flows on a physical device with
  Termux absent or disabled. Verify that no com.termux paths or intents are used.
- [ ] Test an upgrade with existing archive data and verify no silent data loss.
- [ ] Test both providers: model choice, multi-turn chat, concurrent client access,
  cancellation, network loss, process death, re-login, and quota exhaustion.
- [ ] Measure APK size, installed size, cold start, idle memory, response latency,
  and background battery behavior. Report measurements, not estimates as facts.
- [ ] Run build/lint and relevant integration checks; retain a reproducible smoke
  test report identifying device, APK, runtime versions, and measured results.
- [ ] After the replacement passes, remove active browser/cookie adapters, Termux
  permissions/setup code, Chromium/X11 installers, and obsolete sidecar dependencies.
- [ ] Rewrite the English README and installation guide for the delivered behavior,
  supported devices, login flow, quotas, archive semantics, and known limitations.

## Completion rule and task accounting

All implementation items remain unchecked until their stated outcome is verified.
When marking an item complete, add its accounting directly beneath that item in
the same edit, following the repository's instructions. List each contributing
model separately and distinguish the model from its CLI/tool. Record measured
input/output/total tokens and their source when attributable to that item.
If model identity or task-level usage is unavailable, record `نامشخص` with a short
explanation; do not infer counts from account percentages or session-wide totals.

The first deliverable is the feasibility probe and its real-device report, not
a claim that the entire rewrite is complete. Full migration begins after both
provider gates pass.

## Reference material

- [Gemini CLI installation requirements](https://geminicli.com/docs/get-started/installation/)
- [Gemini CLI authentication](https://geminicli.com/docs/get-started/authentication/)
- [Gemini CLI headless mode](https://geminicli.com/docs/cli/headless/)
- [Node Android build instructions](https://github.com/nodejs/node/blob/main/BUILDING.md#android)
- [Node.js Mobile releases](https://github.com/nodejs-mobile/nodejs-mobile/releases)
- [Codex app-server protocol and authentication](https://developers.openai.com/codex/app-server)
- [Codex npm platform selection](https://github.com/openai/codex/blob/main/codex-cli/bin/codex.js)
- [Reported Codex Android startup-lock failure](https://github.com/openai/codex/issues/26277)
- [Android executable-code restrictions](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission)
- [Android native 16 KB page-size requirements](https://developer.android.com/guide/practices/page-sizes)

Re-check these references against the exact versions selected for implementation.
