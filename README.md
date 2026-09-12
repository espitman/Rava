# Rava

Rava is a personal Android chat engine with two account-backed providers:

- **Codex** runs directly inside the Rava APK through OpenAI's official
  `codex-app-server` release.
- **Antigravity** runs Google's official ARM64 `agy` CLI inside Termux. Termux is
  required for this provider; Termux:X11, Chromium automation, copied cookies,
  API keys, and a separate HTTP server are not used.

The app includes model selection, Persian/RTL chat, local conversation history,
continuation, individual and bulk archive deletion, cancellation, and a
signature-protected Android service for the owner's other apps.

After setup, use only the app named **Rava**. The separate runtime-probe APK is a
development test fixture and is not part of the phone installation. Open Rava,
choose **Chat**, select a model, and send a message. Termux stays in the
background and is involved only when an Antigravity/Google model is selected.

## Supported device

The current build targets Android 8.0 or newer and `arm64-v8a`. It was verified
on a Xiaomi 2107113SG running Android 14/API 34. Provider models run remotely, so
internet access and eligible ChatGPT and Google accounts are required. In
regions where Google endpoints are unavailable, Termux must be included in the
VPN connection.

## Installation on a phone

1. Install the Rava APK. Android must show and complete its normal package
   installation screen; Rava cannot silently install itself.
2. Open **Setup → Install Termux → Open page**. Install the official Termux APK
   from the Termux GitHub releases page. Do not mix the GitHub and Play Store
   Termux ecosystems.
3. In Rava, tap **Enable Termux access**. Rava copies this one-time Termux
   setting and opens Termux:

   ```sh
   mkdir -p ~/.termux && (grep -q '^allow-external-apps=true$' ~/.termux/termux.properties 2>/dev/null || echo 'allow-external-apps=true' >> ~/.termux/termux.properties) && termux-reload-settings
   ```

   Paste it into Termux and press Enter.
4. Return to Rava and tap **Grant command permission**. Accept Android's
   `com.termux.permission.RUN_COMMAND` prompt.
5. Tap **Install Antigravity**. Rava installs exact official Termux packages,
   downloads the pinned Google CLI archive over HTTPS, verifies its published
   size and SHA-512 plus the executable SHA-256, and installs it under Termux's
   private home. The CLI archive is about 54 MB; the compatibility packages use
   roughly 400 MB after installation. Existing matching installations are
   verified and reused.
6. Tap **Sign in to Google**. A visible Termux session prints Google's official
   URL and one-time code. In Termux, use **Long press → More → Select URL** so
   the complete URL is selected, open it, enter the code, and return to Rava.
   Rava never reads or copies the resulting credential files.
7. In the Codex section, tap **Sign in**. Rava opens the HTTPS URL returned by
   `codex-app-server` and displays its one-time code. Complete the official
   ChatGPT sign-in, return to Rava, and tap **Check Codex**.
8. Open **Chat** and tap **Reload**. The selector should list both Codex and
   Antigravity models. Pick a model and send a message.

No command from an old Rava installation is needed. Python, Node.js, Chromium,
Termux:X11, `chatgpt-web2api`, and the legacy local HTTP service are not part of
the new runtime.

## Security boundaries

- Provider authentication and network calls remain inside the official provider
  process. Rava does not extract cookies or read/export access or refresh tokens.
- The Codex process uses an app-private `CODEX_HOME`, backup is disabled, its
  workspace is private and empty, sandbox mode is read-only, approval policy is
  `never`, and all app-server tool/approval requests are declined.
- Rava's Antigravity chat process overlays a dedicated deny-all permission file
  through `proot`. It denies file reads/writes, commands, URL tools, browser
  actions, unsandboxed execution, and MCP calls. The overlay applies only to
  Rava; it does not overwrite the user's normal Antigravity settings.
- Rava uses the unmodified, hash-pinned Google executable. It never enables
  `--dangerously-skip-permissions`, changes client identity, calls private
  endpoints, bypasses quotas, or automates account creation.
- Chat output returned through Termux is marked sensitive and is not copied to
  Rava's command-result preferences or diagnostic screen.
- Other Android apps can bind only when signed with the same signing certificate
  as Rava. Caller identity is checked by both a signature permission and UID
  signature comparison.

These controls reduce local risk. Provider eligibility and terms can still
change, and no client can guarantee that an account will never be restricted.

## Chat and archive behavior

Rava stores messages, the selected provider/model, and provider conversation IDs
in its private local archive. Opening an archive entry resumes that provider
conversation. Changing the selected model starts a new local conversation so an
ID cannot cross provider boundaries.

Individual and bulk deletion require confirmation. Deletion removes Rava's local
archive. The official CLIs currently expose no verified remote-delete operation,
so Rava states this in the confirmation dialog and does not claim that provider
history was deleted. Legacy website entries remain readable as local archive
records but are not resumable through the new CLIs.

Antigravity responses are returned when the Termux command completes; the chat
shows animated waiting dots meanwhile. Codex text is processed from app-server
stream events. Markdown HTTPS images are rendered when a provider response
contains a supported image URL.

## Use Rava from another Android app

Build the client AAR:

```sh
cd android-installer
./gradlew :rava-client:assembleRelease
```

The artifact is written to:

```text
android-installer/rava-client/build/outputs/aar/rava-client-release.aar
```

The client app must be signed with the same certificate as Rava. Add the AAR as
a dependency, create `RavaClient`, call `bind()`, then call `listModels()` or
`sendMessage()`. Model IDs use `codex/<model>` or `antigravity/<model>`.
`sendMessage()` returns `conversation_id`, `model`, and `text` in its result
bundle; pass the conversation ID on the next call to continue. See
[`docs/ANDROID_ENGINE_CLIENT.md`](docs/ANDROID_ENGINE_CLIENT.md) for complete
sample code and the Messenger protocol.

## Build

Requirements:

- Android SDK 35
- Android NDK `27.2.12479018`
- JDK 17
- The verified Codex runtime prepared by `android-runtime-probe`

Build and validate:

```sh
android-runtime-probe/scripts/fetch-codex-release.sh
android-runtime-probe/tests/test-antigravity-termux-scripts.sh
cd android-installer
./gradlew clean testDebugUnitTest lintDebug assembleDebug :rava-client:assembleRelease
```

The debug APK is:

```text
android-installer/app/build/outputs/apk/debug/app-debug.apk
```

Exact runtime versions and hashes are recorded in
`android-runtime-probe/codex-versions.env` and
`android-runtime-probe/antigravity-termux.env`. Feasibility evidence is in
`FEASIBILITY_REPORT.md`; final device results are in
[`docs/RELEASE_SMOKE_TEST.md`](docs/RELEASE_SMOKE_TEST.md). The retired
browser-based implementation remains in git history and `PLAN.md` only as a
migration reference.

## Troubleshooting

- **No Google models:** repeat Install Antigravity, ensure command permission is
  granted, finish Google sign-in, include Termux in the VPN, then tap Reload.
- **Google sign-in says invalid request:** use Termux's Select URL action; manually
  selecting only the visible wrapped line truncates the OAuth URL.
- **Codex is signed out:** tap Sign in, complete the browser flow, return to Rava,
  and tap Check Codex.
- **Only one provider appears:** that provider is usable; complete the other
  provider's Setup steps and tap Reload.
- **Request canceled:** send again. Rava terminates only its active provider
  process and leaves stored credentials untouched.
- **Upgrade:** install the newer APK over the existing app with the same signing
  key. Uninstalling first removes Rava's local archive and Codex credentials.
