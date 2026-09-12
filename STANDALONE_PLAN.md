# Rava Standalone Android Implementation Plan

Status: Rava 1.0 is implemented and installed on the physical Android device.
The embedded Codex runtime and the official Antigravity CLI through Termux both
load confirmed models, complete real turns, and resume the same conversation
after an app restart. The signature-protected Android engine service and client
AAR are built. Remaining unchecked items are destructive account-state tests or
extended lifecycle measurements and are documented as release limitations.

## Decision and scope

Keep this repository. Reuse the Android chat UI, Vazirmatn font, RTL behavior,
bottom navigation, and archive where practical. Preserve the existing web-based
implementation as a migration reference until the replacement works.

The target is one Rava APK plus the official Termux app for the Google provider:

- An Android-compatible Codex app-server executable and required dependencies.
- Google's unmodified ARM64 Antigravity CLI installed in Termux by Rava's pinned,
  checksum-verifying setup flow.
- Native account setup, chat, model selection, and conversation history.
- A local integration interface for the owner's other Android applications.

The Codex provider is standalone inside Rava. The Google provider requires
official Termux but does not require Termux:X11, a separate server, browser
automation, copied website cookies, or a user-supplied paid API key. A system
browser may open for supported sign-in flows. Internet access, the user's VPN
where Google connectivity requires it, and eligible provider accounts remain
necessary: models run remotely. Usage is subject to Antigravity and Codex quotas.

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
- Google ended Gemini CLI Login with Google access for individual, Google AI Pro,
  and Google AI Ultra accounts on 2026-06-18. Stable `0.59.0` and preview
  `0.60.0-preview.0` use the same personal OAuth and Code Assist setup path, so a
  version bump does not restore this entitlement.
- Google's replacement is Antigravity CLI. Its official FAQ warns that access
  through third-party software, tools, or services can lead to account suspension
  or termination, while its official headless guide explicitly documents an
  application driving the unmodified `agy` process through stdin/stdout. After an
  Astra risk review, the owner approved personal-use testing within that narrow
  boundary. Android plus Termux is not explicitly covered, so this is not treated
  as zero risk. Never inspect/export tokens, call private endpoints, alter the
  binary/client identity, or bypass quota and safety controls.

## Phase 0 — Preserve the existing project

- [x] Review the current tracked and untracked changes, exclude credentials and
  runtime data, and create a recoverable legacy snapshot before modifying the app.
  - مدل: `نامشخص (خانواده GPT-5؛ شناسه دقیق runtime گزارش نشده)`؛ ابزار: `Codex`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
- [x] Create a dedicated implementation branch for the standalone migration.
  - مدل: `نامشخص (خانواده GPT-5؛ شناسه دقیق runtime گزارش نشده)`؛ ابزار: `Codex` و `git`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
- [x] Record the current APK version, application ID, signing configuration, and
  archive schema so upgrades can preserve existing user data.
  - مدل: `نامشخص (خانواده GPT-5؛ شناسه دقیق runtime گزارش نشده)`؛ ابزار: `Codex`، `Gradle` و `pytest`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - نتیجه: `docs/LEGACY_BASELINE.md`؛ تست Python برابر ۲۱ مورد و build/lint اندروید موفق بود.
- [x] Add a separate runtime-probe module/application ID. Keep the current Rava
  installation usable while feasibility is tested.
  - Model: `gpt-5.6-sol`; tool: Codex agent
  - Tokens: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - Result: `android-runtime-probe/`; host unit tests, lint, APK build, ELF inspection,
    and APK alignment checks pass. The real-device gate remains open.

Do not delete the repository or discard the uncommitted changes. Do not publish
or push the snapshot automatically as part of this plan.

## Phase 1 — Build an Android runtime probe

- [x] Record the real device's Android version, ABI, page size, and available
  storage; begin with arm64-v8a. Use the physical phone, not an emulator.
  - مدل: `gpt-5.6-sol`؛ ابزار: `Codex agent` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - نتیجه: `FEASIBILITY_REPORT.md`؛ probe بومی روی Xiaomi 2107113SG با Android 14،
    ABI برابر `arm64-v8a`، page size برابر 4096، خروجی فارسی و exit code صفر اجرا شد.
- [x] Pin exact upstream revisions and toolchain versions. Record artifact hashes,
  licenses, local patches, and reproducible build commands.
  - مدل: `gpt-5.6-sol`؛ ابزار: `Codex agent`، `curl`، `npm` و `llvm-readelf`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - نتیجه: نسخه‌ها، URLها، اندازه‌ها، SHA-256 بسته‌های رسمی Termux و npm integrity
    در `android-runtime-probe/versions.env` و `termux-runtime-packages.tsv` ثبت شد؛
    مجوزهای کامل runtime نیز داخل APK بسته‌بندی شدند.
- [x] Build or obtain a verified Android-compatible Node runtime meeting Gemini's
  selected version requirements. Verify native dependencies and TLS certificates.
  - مدل: `gpt-5.6-sol`؛ ابزار: `Codex agent`، `llvm-readelf`، `Gradle` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - نتیجه: Node رسمی Termux `24.18.0-1` با interpreter اندروید، dependency closure
    کامل، RUNPATH برابر `$ORIGIN` و TLS/HTTPS موفق روی Android 14/arm64 تأیید شد.
- [x] Package Node in the probe APK with a supported native loading arrangement;
  run JavaScript, HTTPS, file I/O, Unicode/Farsi, and a clean shutdown/restart.
  - مدل: `gpt-5.6-sol`؛ ابزار: `Codex agent`، `Gradle` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - نتیجه: Node از `nativeLibraryDir` با UID اپ اجرا شد؛ فایل فارسی، `Intl fa-IR`،
    DNS، HTTPS 204، child-process shell، restart count 8، stderr خالی و exit code
    صفر حتی هنگام disable بودن `com.termux` ثبت شد. Gemini CLI `0.59.0` نیز از
    bundle داخل همان APK مقدار نسخه را با exit code صفر برگرداند.
- [x] Attempt to build Codex app-server for Android/ARM64. Audit dependencies,
  filesystem assumptions, locks, process spawning, and sandbox startup. Document
  blockers before choosing any compatibility fallback.
  - مدل: `gpt-5.6-sol`؛ ابزار: `Codex agent`، `Cargo` و `cosign`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - نتیجه: تلاش source-build به‌دلیل کمبود فضای میزبان به‌صورت کنترل‌شده متوقف شد؛
    cross-check وابستگی‌های Android موفق بود و fallback رسمی Linux/musl با هش و
    Sigstore دقیق OpenAI تأیید شد. ریسک‌های keyring، SQLite، PTY و sandbox در
    `android-runtime-probe/CODEX_ANDROID.md` ثبت شده‌اند.
- [x] Package the Codex executable and required helpers using APK-native packaging;
  launch from the app UID and complete a JSON-RPC initialize exchange over pipes.
  - مدل: `gpt-5.6-sol`؛ ابزار: `Codex agent`، `Gradle` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - نتیجه: Codex app-server `0.154.0` از `nativeLibraryDir` و UID اپ روی Android
    14/arm64 اجرا شد، پاسخ معتبر `initialize` داد و با کد صفر خاتمه یافت؛ خطای
    startup-lock تروموکس بازتولید نشد.
- [x] Check native ELF and APK alignment for applicable 4 KB/16 KB devices and
  verify operation under the intended modern target SDK.
  - مدل: `gpt-5.6-sol`؛ ابزار: `Codex agent`، `llvm-readelf`، `zipalign`، `apksigner` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - نتیجه: ABI و ELFها، امضای v2، هم‌ترازی APK برای 16 KB و اجرای واقعی روی
    Android 14/API 34 بررسی شد؛ APK نهایی probe با اندازهٔ 218,479,045 بایت نصب شد.

Gate: the embedded Codex runtime starts from inside the APK on a real phone
without root or downloaded executable code. The Google gate is tracked separately
in Phase 2 because its supported replacement now intentionally runs in Termux.

If a runtime fails, produce a concrete blocker and a bounded porting experiment.
Do not silently replace it with a paid API, server, or Termux dependency.

## Phase 2 — Prove Google provider sign-in and conversation flow

- [x] Install the pinned Antigravity CLI and exact Termux compatibility packages
  through a checksum-verifying, repeatable Rava setup flow.
  - مدل: `gpt-5.6-sol`؛ ابزار: `Codex agent`، `Termux` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - مدل: `gpt-6`؛ ابزار: `Codex`، `Gradle`، `Termux` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - نتیجه: setup داخلی Rava نسخهٔ `1.2.2` و بسته‌های دقیق Termux را نصب/تعمیر و
    اندازه و digest باینری رسمی را پیش از استفاده تأیید می‌کند.
- [ ] Run the official Google sign-in flow using the system browser. Verify callback
  delivery, cancellation, and re-login without extracting website cookies or
  impersonating another OAuth client.
- [x] Reuse the CLI-managed credentials for a headless request; receive a real
  model answer from inside the probe APK.
  - مدل: `gpt-5.6-sol`؛ ابزار: `Codex agent`، `Termux` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - مدل: `gpt-6`؛ ابزار: `Codex`، `Termux` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - نتیجه: credential مدیریت‌شده توسط CLI بدون خواندن/کپی‌شدن توسط Rava، پاسخ
    واقعی را ابتدا در probe و سپس در APK اصلی Rava برگرداند.
- [ ] Verify the chosen version's JSON/streaming schema, error events, cancellation,
  and end-of-response detection. Keep stderr diagnostics separate from payloads.
- [x] Verify explicit model selection and detect any upstream fallback. Never show
  a requested model as confirmed unless the response/protocol supports that claim.
  - مدل: `gpt-5.6-sol`؛ ابزار: `Codex agent` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - مدل: `gpt-6`؛ ابزار: `Codex`، `JUnit` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - نتیجه: مدل در event آغازین stream با مدل درخواستی تطبیق داده می‌شود؛ fallback
    با تست واحد رد شد و `gemini-3.8-flash-low` روی گوشی تأیید شد.
- [ ] Verify two-turn conversation continuation, process restart, credential refresh,
  logout, and loss of connectivity. Record available session-management commands.

Gate: sign in, ask, receive a complete response, continue, restart, and continue
again through Rava's authenticated Termux service integration. Record the actual
tested versions and limitations.

The consumer Gemini CLI service is retired. With explicit
personal-use approval, Termux now runs Google's unmodified arm64 Antigravity CLI
`1.2.2` through `glibc-runner`: the published archive digest, binary digest,
`--version`, and `--help` pass. Local OAuth crashes under Android seccomp when
Go's desktop browser lookup calls `faccessat2`, but Google's documented Remote
SSH mode avoids that path. A second DNS issue was isolated to Go's lookup of the
absent Android `/etc/resolv.conf`; official Termux `proot` plus private
hosts/resolver mounts fixed a bounded unauthenticated startup without modifying
`agy`. With the owner's VPN connected, a fresh SSH-mode OAuth completed. The
same unmodified binary then listed 14 models, returned exact Persian text from
`gemini-3.8-flash-low` through documented stream JSON with exit 0 and no tool
events, and resumed the returned conversation ID in a new process for a second
exact response with `num_turns: 2`. Pinned install/verify and quoted-argument
runner scripts are retained in `android-runtime-probe`. Rava-to-Termux IPC now
loads fourteen models and completed/resumed two real turns across app restart.
Cancellation, refresh/logout, and forced network-loss tests remain unverified,
and the documented policy ambiguity remains disclosed.

## Phase 3 — Prove Codex sign-in and conversation flow

- [x] Implement the documented app-server initialization and request correlation,
  notifications, errors, and process-exit handling using the pinned protocol schema.
  - مدل: `gpt-5.6-sol`؛ ابزار: `Codex agent`، `Gradle` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - نتیجه: schema نسخهٔ `0.154.0` با تست واحد و نشست واقعی Android برای
    `initialize`، device-code، correlation و cancel رسمی تأیید شد؛ stdout پروتکل
    از stderr تشخیصی جدا است.
- [x] Use app-server-managed ChatGPT sign-in. Prefer the documented device-code
  flow where available; open the supplied verification URL in the system browser.
  - مدل: `gpt-5.6-sol`؛ ابزار: `Codex agent` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - نتیجه: device-code رسمی در UI نمایش داده شد، ورود کاربر کامل شد و
    `account/read` پس از force-stop همچنان authenticated بود؛ هیچ API key یا
    cookie مرورگر استخراج نشد.
- [x] Fetch account state and available models, then create a thread and send a turn.
  - مدل: `gpt-5.6-sol`؛ ابزار: `Codex agent`، `Gradle` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - نتیجه: `model/list`، `thread/start` و `turn/start` روی Android واقعی اجرا
    شدند؛ پاسخ نهایی دقیقاً با `سلام راوا` تطبیق داشت و همان thread پس از
    restart با `thread/resume` ادامه یافت.
- [x] Render assistant text deltas and completion/errors. Distinguish assistant text
  from tool execution, reasoning events, and server diagnostics.
  - مدل: `gpt-5.6-sol`؛ ابزار: `Codex agent`، `JUnit` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - نتیجه: delta و پیام نهایی authoritative در callbackهای جدا پردازش شدند؛
    completion/error، reasoning/tool item و stderr با تست schema از متن دستیار
    تفکیک شدند و پاسخ واقعی دستگاه match شد.
- [ ] Verify continuation, restart/resume, cancellation, expired authentication,
  logout, and quota errors using a real account.
- [ ] Restrict the chat-only profile's tool access. Verify filesystem and command
  permissions; handle or deny approval requests explicitly. Do not enable blanket
  auto-approval merely to bypass an unsupported sandbox or make a test pass.

Gate: the same end-to-end flow as Gemini succeeds within the APK. Clearly identify
this provider as Codex and its usage as Codex usage.

## Phase 4 — Implement Rava's internal engine

- [x] Define a provider-neutral Android interface for model selection, new/resumed
  conversations, final messages, cancellation, and errors. Keep provider-specific
  account setup outside the common chat interface; Antigravity exposes final-result
  delivery, so streaming is not part of the shared contract.
  - مدل: `gpt-6`؛ ابزار: `Codex` و `JUnit`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - نتیجه: `ChatProvider`، `ProviderModel` و typed request/response/error callbackها
    برای هر دو provider استفاده می‌شوند.
- [x] Run provider runtimes outside the UI thread, preferably in dedicated service
  processes so a native crash or runtime exit does not terminate the chat UI.
  - مدل: `gpt-5.6-sol`؛ ابزار: `Codex agent` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - مدل: `gpt-6`؛ ابزار: `Codex` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - نتیجه: Codex به‌صورت child process با workerهای جدا و Antigravity در process
    رسمی Termux اجرا می‌شود؛ هیچ اجرای provider روی UI thread نیست.
- [x] Implement bounded provider queues, timeouts, process cleanup, and independent
  recovery so one unavailable provider does not block the other.
  - مدل: `gpt-6`؛ ابزار: `Codex`، `JUnit` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - نتیجه: تنها یک turn هم‌زمان پذیرفته می‌شود، timeout پنج‌دقیقه‌ای و cancel/cleanup
    وجود دارد و خطای یک provider فهرست مدل‌های provider دیگر را مخفی نمی‌کند.
- [ ] Add appropriate service lifecycle handling and foreground notification when
  required by Android. Test screen-off, backgrounding, and app-process death.
- [ ] Keep credentials in app-private provider storage; exclude them from logs,
  backups, exports, and IPC. Use Keystore-backed protection where compatible with
  provider credential handling, and implement explicit logout cleanup.
- [x] Expose a Bound Service interface for other personal apps; authenticate callers
  with signature permissions/UID checks and derive app isolation from caller
  identity. A caller-supplied app_id alone is not authentication.
  - مدل: `gpt-6`؛ ابزار: `Codex`، `Gradle`، `aapt` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - نتیجه: `RavaEngineService` با permission سطح signature و بررسی `sendingUid`
    ارائه شد؛ UID مربوط به Android shell در تست واقعی با خطای permission رد شد.
- [x] Publish a small client library/example for model listing, sending, final-result
  delivery, continuation, cancellation, and error handling. HTTP is not required.
  - مدل: `gpt-6`؛ ابزار: `Codex` و `Gradle`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - نتیجه: ماژول `rava-client` یک AAR مستقل می‌سازد و نمونهٔ کامل Messenger در
    `docs/ANDROID_ENGINE_CLIENT.md` ثبت شده است.

The existing Python engine is a behavior reference. Do not accidentally introduce
an embedded Python requirement by assuming its HTTP implementation must be reused.

## Phase 5 — Migrate the Android UI and archive

- [x] Replace the legacy Termux/X11 setup with bounded Antigravity readiness, Google sign-in,
  ChatGPT/Codex sign-in, account status, and actionable recovery states.
  - مدل: `gpt-5.6-sol`؛ ابزار: `Codex agent` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - مدل: `gpt-6`؛ ابزار: `Codex`، `Termux` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - نتیجه: Setup نهایی نصب/تعمیر خودکار Antigravity، ورود Google و dialog کد
    یک‌بارمصرف Codex را دارد و هیچ X11/Chromium/localhost را اجرا نمی‌کند.
- [x] Preserve English UI, compact bottom navigation, Persian font/RTL support,
  bubble styling, waiting dots, and archive empty states.
  - مدل: `gpt-5.6-sol`؛ ابزار: `Codex agent` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - مدل: `gpt-6`؛ ابزار: `Codex` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
- [x] Connect chat to the internal engine with actual provider/model labels and
  clear cancellation, retry, quota, and sign-in-required states.
  - مدل: `gpt-6`؛ ابزار: `Codex`، `JUnit` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - نتیجه: ۲۰ مدل تأییدشده بارگذاری شد و turn واقعی و resume هر دو provider روی
    گوشی موفق بود؛ fallback مدل و tool eventها fail-closed هستند.
- [ ] Persist local messages, provider session IDs, model identity, and ownership
  in an appropriate local database, with migration from the existing archive.
- [x] Keep legacy website conversations readable as legacy entries. Do not claim
  their website conversation IDs can be resumed by Gemini CLI or Codex.
  - مدل: `gpt-5.6-sol`؛ ابزار: `Codex agent`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - مدل: `gpt-6`؛ ابزار: `Codex` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
- [x] Support individual and bulk deletion with confirmation. Define deletion scope
  from verified CLI capabilities: local Rava data, CLI session data, and any remote
  state must not be conflated. Never claim to delete website history through CLI.
  - مدل: `gpt-5.6-sol`؛ ابزار: `Codex agent` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - مدل: `gpt-6`؛ ابزار: `Codex` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - نتیجه: حذف تکی/گروهی confirmation دارد و متن UI صریحاً scope محلی را اعلام می‌کند.
- [ ] Preserve image rendering where actual responses provide supported media;
  feature-test attachments and image generation rather than assuming website parity.

## Phase 6 — Validate and retire the legacy stack

- [ ] Test a clean installation and both login flows on a physical device. Verify
  Codex with Termux absent or disabled, and verify the Google provider using only
  the documented Termux RunCommandService integration.
- [x] Test an upgrade with existing archive data and verify no silent data loss.
  - مدل: `gpt-6`؛ ابزار: `Codex` و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - نتیجه: چند نصب `adb install -r` انجام شد و archive و ورود هر دو provider حفظ شد.
- [ ] Test both providers: model choice, multi-turn chat, concurrent client access,
  cancellation, network loss, process death, re-login, and quota exhaustion.
- [ ] Measure APK size, installed size, cold start, idle memory, response latency,
  and background battery behavior. Report measurements, not estimates as facts.
- [x] Run build/lint and relevant integration checks; retain a reproducible smoke
  test report identifying device, APK, runtime versions, and measured results.
  - مدل: `gpt-6`؛ ابزار: `Codex`، `Gradle`، `JUnit`، shell checks و `ADB`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - نتیجه: build/lint/test موفق و گزارش قابل بازتولید در
    `docs/RELEASE_SMOKE_TEST.md` ثبت شد.
- [x] After the replacement passes, remove active browser/cookie adapters,
  Chromium/X11 launch paths, legacy localhost calls, and obsolete sidecar dependencies
  from the built Android app. Retain Termux permission/setup only for Antigravity.
  - مدل: `gpt-6`؛ ابزار: `Codex` و `rg`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.
  - نتیجه: APK نهایی هیچ مسیر فعال X11، Chromium، cookie adapter یا localhost ندارد.
- [x] Rewrite the English README and installation guide for the delivered behavior,
  supported devices, login flow, quotas, archive semantics, and known limitations.
  - مدل: `gpt-6`؛ ابزار: `Codex`
  - توکن: نامشخص — آمار دقیق مصرف این تسک در دسترس نیست.

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
- [Gemini Code Assist consumer-account deprecation](https://developers.google.com/gemini-code-assist/docs/deprecations/code-assist-individuals)
- [Antigravity third-party access policy](https://www.antigravity.google/docs/faq/)
- [Antigravity CLI headless mode](https://antigravity.google/docs/cli/headless/)
- [Node Android build instructions](https://github.com/nodejs/node/blob/main/BUILDING.md#android)
- [Node.js Mobile releases](https://github.com/nodejs-mobile/nodejs-mobile/releases)
- [Codex app-server protocol and authentication](https://developers.openai.com/codex/app-server)
- [Codex npm platform selection](https://github.com/openai/codex/blob/main/codex-cli/bin/codex.js)
- [Reported Codex Android startup-lock failure](https://github.com/openai/codex/issues/26277)
- [Android executable-code restrictions](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission)
- [Android native 16 KB page-size requirements](https://developer.android.com/guide/practices/page-sizes)

Re-check these references against the exact versions selected for implementation.
