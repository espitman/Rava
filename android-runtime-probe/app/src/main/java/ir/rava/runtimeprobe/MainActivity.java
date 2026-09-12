package ir.rava.runtimeprobe;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONObject;
import org.json.JSONException;

@SuppressLint("SetTextI18n")
public final class MainActivity extends Activity {
    private static final String CODEX_INITIALIZE =
            "{\"method\":\"initialize\",\"id\":1,\"params\":{" +
            "\"clientInfo\":{\"name\":\"rava_runtime_probe\"," +
            "\"title\":\"Rava Runtime Probe\",\"version\":\"0.1.0\"}," +
            "\"capabilities\":{\"experimentalApi\":false}}}\n" +
            "{\"method\":\"initialized\"}\n";
    private static final long CODEX_STDIN_SETTLE_MILLIS = 3000;
    private static final String CODEX_FIRST_PROMPT = "Reply with exactly: سلام راوا";
    private static final String CODEX_FIRST_EXPECTED = "سلام راوا";
    private static final String CODEX_RESUME_PROMPT = "Reply with exactly: ادامه راوا";
    private static final String CODEX_RESUME_EXPECTED = "ادامه راوا";

    private final ExecutorService commands = Executors.newSingleThreadExecutor();
    private final ExecutorService authObserver = Executors.newSingleThreadExecutor();
    private final GeminiAuthLifecycle geminiAuthLifecycle = new GeminiAuthLifecycle();
    private final Object geminiAuthProcessLock = new Object();
    private volatile Process activeProcess;
    private volatile Process geminiAuthProcess;
    private volatile CodexAuthSession codexAuthSession;
    private volatile CodexConversationSession codexConversationSession;
    private volatile boolean geminiAuthBrowserOpened;
    private volatile long geminiAuthGeneration;
    private volatile String codexVerificationUrl;
    private TextView output;
    private EditText geminiAuthCode;
    private EditText geminiPrompt;
    private TextView geminiAuthStatus;
    private Button geminiLoginButton;
    private Button geminiSubmitCodeButton;
    private Button geminiHeadlessButton;
    private TextView codexAuthDetails;
    private Button codexOpenLoginButton;
    private TextView codexConversationDetails;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText("Rava standalone runtime probe");
        title.setTextSize(22);
        content.addView(title);

        Button nativeButton = new Button(this);
        nativeButton.setText("Run embedded native probe");
        nativeButton.setOnClickListener(view -> runNativeProbe());
        content.addView(nativeButton);

        Button nodeButton = new Button(this);
        nodeButton.setText("Run Node runtime checks");
        nodeButton.setOnClickListener(view -> runNodeProbe());
        content.addView(nodeButton);

        Button geminiButton = new Button(this);
        geminiButton.setText("Run Gemini CLI --version");
        geminiButton.setOnClickListener(view -> runGeminiVersion());
        content.addView(geminiButton);

        geminiLoginButton = new Button(this);
        geminiLoginButton.setText("Start Gemini Google sign-in");
        geminiLoginButton.setOnClickListener(view -> startGeminiLogin());
        content.addView(geminiLoginButton);

        geminiAuthStatus = new TextView(this);
        geminiAuthStatus.setTextIsSelectable(true);
        content.addView(geminiAuthStatus);

        geminiAuthCode = new EditText(this);
        geminiAuthCode.setHint("Paste the Google authorization code");
        geminiAuthCode.setSingleLine(true);
        content.addView(geminiAuthCode);

        geminiSubmitCodeButton = new Button(this);
        geminiSubmitCodeButton.setText("Submit Gemini authorization code");
        geminiSubmitCodeButton.setOnClickListener(view -> submitGeminiAuthorizationCode());
        content.addView(geminiSubmitCodeButton);

        geminiPrompt = new EditText(this);
        geminiPrompt.setHint("Authenticated Gemini prompt");
        geminiPrompt.setText("Reply with exactly: سلام راوا");
        content.addView(geminiPrompt);

        geminiHeadlessButton = new Button(this);
        geminiHeadlessButton.setText("Run authenticated Gemini headless probe");
        geminiHeadlessButton.setOnClickListener(view -> runGeminiHeadless());
        content.addView(geminiHeadlessButton);

        Button codexButton = new Button(this);
        codexButton.setText("Run Codex JSON-RPC initialize");
        codexButton.setOnClickListener(view -> runCodexProbe());
        content.addView(codexButton);

        Button codexAccountButton = new Button(this);
        codexAccountButton.setText("Check Codex account");
        codexAccountButton.setOnClickListener(view -> startCodexAccountRead());
        content.addView(codexAccountButton);

        Button codexLoginButton = new Button(this);
        codexLoginButton.setText("Start Codex ChatGPT device sign-in");
        codexLoginButton.setOnClickListener(view -> startCodexDeviceLogin());
        content.addView(codexLoginButton);

        codexAuthDetails = new TextView(this);
        codexAuthDetails.setText("Codex account state has not been checked.");
        codexAuthDetails.setTextIsSelectable(true);
        content.addView(codexAuthDetails);

        codexOpenLoginButton = new Button(this);
        codexOpenLoginButton.setText("Open official Codex sign-in page");
        codexOpenLoginButton.setEnabled(false);
        codexOpenLoginButton.setOnClickListener(view -> openCodexVerificationUrl());
        content.addView(codexOpenLoginButton);

        Button codexCancelLoginButton = new Button(this);
        codexCancelLoginButton.setText("Cancel Codex sign-in");
        codexCancelLoginButton.setOnClickListener(view -> cancelCodexLogin());
        content.addView(codexCancelLoginButton);

        Button codexFirstTurnButton = new Button(this);
        codexFirstTurnButton.setText("Run Codex authenticated turn");
        codexFirstTurnButton.setOnClickListener(view -> startCodexConversation(false));
        content.addView(codexFirstTurnButton);

        Button codexResumeTurnButton = new Button(this);
        codexResumeTurnButton.setText("Resume saved Codex thread");
        codexResumeTurnButton.setOnClickListener(view -> startCodexConversation(true));
        content.addView(codexResumeTurnButton);

        codexConversationDetails = new TextView(this);
        codexConversationDetails.setText("No Codex conversation probe has run.");
        codexConversationDetails.setTextIsSelectable(true);
        content.addView(codexConversationDetails);

        Button codexCancelTurnButton = new Button(this);
        codexCancelTurnButton.setText("Cancel Codex turn");
        codexCancelTurnButton.setOnClickListener(view -> cancelCodexTurn());
        content.addView(codexCancelTurnButton);

        Button cancelButton = new Button(this);
        cancelButton.setText("Cancel running process");
        cancelButton.setOnClickListener(view -> cancelActiveProcess());
        content.addView(cancelButton);

        output = new TextView(this);
        output.setTextIsSelectable(true);
        output.setTextSize(13);
        output.setMovementMethod(new ScrollingMovementMethod());
        try {
            output.setText("[device]\n" + DeviceReport.collect(this).toString(2) + "\n");
        } catch (Exception error) {
            output.setText("Device report failed: " + error + "\n");
        }
        content.addView(output, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);

        clearPotentialGeminiAuthTranscripts();
        clearStaleGeminiAuthState();
        refreshGeminiAuthUi();

        String requestedProbe = getIntent().getStringExtra("probe");
        if ("node".equals(requestedProbe)) {
            runNodeProbe();
        } else if ("gemini-version".equals(requestedProbe)) {
            runGeminiVersion();
        } else if ("gemini-auth".equals(requestedProbe)) {
            startGeminiLogin();
        } else if ("gemini-headless".equals(requestedProbe)) {
            runGeminiHeadless();
        } else if ("codex".equals(requestedProbe)) {
            runCodexProbe();
        } else if ("codex-account".equals(requestedProbe)) {
            startCodexAccountRead();
        } else if ("codex-device-login".equals(requestedProbe)) {
            startCodexDeviceLogin();
        } else if ("codex-turn".equals(requestedProbe)) {
            startCodexConversation(false);
        } else if ("codex-resume-turn".equals(requestedProbe)) {
            startCodexConversation(true);
        }
    }

    private File nodeRuntime() {
        return runtime(RuntimeProbes.NODE);
    }

    private File runtime(RuntimeProbe probe) {
        return new File(getApplicationInfo().nativeLibraryDir, probe.libraryFileName());
    }

    private void runNativeProbe() {
        commands.execute(() -> runPackaged(RuntimeProbes.NATIVE, List.of("--json")));
    }

    private void runNodeProbe() {
        commands.execute(() -> {
            try {
                File script = copyAsset("node-probe.js", "node-probe.js");
                File work = new File(getFilesDir(), "node-probe-work");
                if (!work.isDirectory() && !work.mkdirs()) {
                    throw new IOException("Cannot create " + work);
                }
                run(List.of(nodeRuntime().getAbsolutePath(), script.getAbsolutePath(), work.getAbsolutePath()));
            } catch (Exception error) {
                append("ERROR: " + error + "\n");
            }
        });
    }

    private void runGeminiVersion() {
        commands.execute(() -> {
            try {
                File bundle = copyAssetTree("gemini", "gemini");
                File script = new File(bundle, "gemini.js");
                run(List.of(nodeRuntime().getAbsolutePath(), script.getAbsolutePath(), "--version"));
            } catch (Exception error) {
                append("ERROR: " + error + "\n");
            }
        });
    }

    private File prepareGeminiBundle() throws IOException {
        File bundle = copyAssetTree("gemini", "gemini");
        prepareGeminiSettings();
        return bundle;
    }

    private void prepareGeminiSettings() throws IOException {
        File directory = new File(new File(getFilesDir(), "runtime-home"), ".gemini");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Cannot create " + directory);
        }
        File settings = new File(directory, "settings.json");
        if (!settings.isFile()) {
            String json = "{\n"
                    + "  \"security\": {\n"
                    + "    \"auth\": {\"selectedType\": \"oauth-personal\"},\n"
                    + "    \"folderTrust\": {\"enabled\": false}\n"
                    + "  }\n"
                    + "}\n";
            try (FileOutputStream target = new FileOutputStream(settings)) {
                target.write(json.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private void startGeminiLogin() {
        if (!geminiAuthLifecycle.begin()) {
            append("Gemini sign-in is already in progress.\n");
            refreshGeminiAuthUi();
            return;
        }
        final long generation = ++geminiAuthGeneration;
        geminiAuthCode.setText("");
        refreshGeminiAuthUi();
        commands.execute(() -> {
            try {
                prepareGeminiBundle();
                File adapter = copyAsset("gemini-auth-probe.js", "gemini-auth-probe.js");
                File state = new File(getFilesDir(), "gemini-auth-state.json");
                if (state.isFile() && !state.delete()) {
                    throw new IOException("Cannot clear " + state);
                }
                geminiAuthBrowserOpened = false;
                authObserver.execute(() -> observeGeminiAuthState(state, generation));
                runGeminiAuthCommand(List.of(nodeRuntime().getAbsolutePath(),
                        adapter.getAbsolutePath(), state.getAbsolutePath()), state, generation);
            } catch (Exception error) {
                geminiAuthLifecycle.failed();
                clearGeminiAuthCode();
                refreshGeminiAuthUi();
                append("Gemini sign-in could not start. Try again.\n");
            }
        });
    }

    private void observeGeminiAuthState(File state, long generation) {
        String previous = "";
        for (int attempt = 0; attempt < 600
                && generation == geminiAuthGeneration
                && !Thread.currentThread().isInterrupted(); attempt++) {
            try {
                if (state.isFile()) {
                    String raw = readAll(state).trim();
                    if (!raw.equals(previous)) {
                        previous = raw;
                        JSONObject authState = new JSONObject(raw);
                        String status = authState.optString("status");
                        if ("authorization_required".equals(status)) {
                            if (isGeminiAuthProcessAlive()
                                    && geminiAuthLifecycle.authorizationRequired()) {
                                refreshGeminiAuthUi();
                                openGeminiAuthorizationUrl(
                                        authState.getString("authorizationUrl"), generation);
                            }
                        } else if ("authenticated".equals(status)) {
                            geminiAuthLifecycle.authenticated();
                            clearGeminiAuthCode();
                            refreshGeminiAuthUi();
                            append("Gemini Google sign-in completed.\n");
                            return;
                        } else if ("failed".equals(status)) {
                            geminiAuthLifecycle.failed();
                            clearGeminiAuthCode();
                            refreshGeminiAuthUi();
                            append("Gemini rejected the authorization exchange. Start sign-in again.\n");
                            return;
                        }
                    }
                }
                Thread.sleep(500);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception error) {
                geminiAuthLifecycle.failed();
                clearGeminiAuthCode();
                refreshGeminiAuthUi();
                append("Gemini sign-in state could not be read. Start sign-in again.\n");
                return;
            }
        }
    }

    private void openGeminiAuthorizationUrl(String url, long generation) {
        if (!GeminiAuthProtocol.isAllowedAuthorizationUrl(url)) {
            geminiAuthLifecycle.failed();
            clearGeminiAuthCode();
            refreshGeminiAuthUi();
            append("Refused unexpected Gemini authorization URL.\n");
            return;
        }
        Uri uri = Uri.parse(url);
        if (geminiAuthBrowserOpened || generation != geminiAuthGeneration) return;
        geminiAuthBrowserOpened = true;
        runOnUiThread(() -> {
            append("Opening the official Google authorization page.\n");
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        });
    }

    private void submitGeminiAuthorizationCode() {
        String code = geminiAuthCode.getText().toString().trim();
        synchronized (geminiAuthProcessLock) {
            Process process = geminiAuthProcess;
            if (code.isEmpty()
                    || !geminiAuthLifecycle.canAcceptCode()
                    || process == null
                    || !process.isAlive()) {
                append("No Gemini authorization request is waiting for a code. Start sign-in again.\n");
                refreshGeminiAuthUi();
                return;
            }
            try {
                process.getOutputStream().write((code + "\n").getBytes(StandardCharsets.UTF_8));
                process.getOutputStream().flush();
                geminiAuthLifecycle.codeSubmitted();
                geminiAuthCode.setText("");
                refreshGeminiAuthUi();
                append("Authorization code submitted; waiting for Google token exchange.\n");
            } catch (IOException error) {
                geminiAuthLifecycle.failed();
                clearGeminiAuthCode();
                refreshGeminiAuthUi();
                append("The Gemini authorization process stopped. Start sign-in again.\n");
            }
        }
    }

    private void runGeminiHeadless() {
        if (geminiAuthLifecycle.isPending()) {
            append("Finish or cancel Gemini sign-in before running the headless probe.\n");
            refreshGeminiAuthUi();
            return;
        }
        if (!hasGeminiCredentialFile()) {
            append("Gemini credentials are absent. Complete Google sign-in first.\n");
            refreshGeminiAuthUi();
            return;
        }
        String prompt = geminiPrompt.getText().toString().trim();
        if (prompt.isEmpty()) {
            append("Enter a Gemini prompt first.\n");
            return;
        }
        commands.execute(() -> {
            try {
                File bundle = prepareGeminiBundle();
                File script = new File(bundle, "gemini.js");
                run(List.of(nodeRuntime().getAbsolutePath(), script.getAbsolutePath(),
                        "--output-format", "stream-json", "--prompt", prompt));
            } catch (Exception error) {
                append("Gemini headless probe failed: " + error + "\n");
            }
        });
    }

    private boolean hasGeminiCredentialFile() {
        File credentials = new File(new File(
                new File(getFilesDir(), "runtime-home"), ".gemini"),
                "gemini-credentials.json");
        return credentials.isFile() && credentials.length() > 0;
    }

    private boolean isGeminiAuthProcessAlive() {
        synchronized (geminiAuthProcessLock) {
            return geminiAuthProcess != null && geminiAuthProcess.isAlive();
        }
    }

    private void refreshGeminiAuthUi() {
        GeminiAuthLifecycle.State state = geminiAuthLifecycle.state();
        boolean processAlive = isGeminiAuthProcessAlive();
        boolean canSubmit = state == GeminiAuthLifecycle.State.AUTHORIZATION_REQUIRED
                && processAlive;
        boolean pending = geminiAuthLifecycle.isPending();
        boolean hasCredentials = hasGeminiCredentialFile();
        String status;
        switch (state) {
            case STARTING:
                status = "Gemini sign-in: starting secure Google authorization…";
                break;
            case AUTHORIZATION_REQUIRED:
                status = processAlive
                        ? "Gemini sign-in: waiting for the code from Google's page."
                        : "Gemini sign-in: request expired; start again.";
                break;
            case CODE_SUBMITTED:
                status = "Gemini sign-in: code submitted; exchanging credentials…";
                break;
            case AUTHENTICATED:
                status = "Gemini sign-in: authenticated; headless probe is ready.";
                break;
            case FAILED:
                status = "Gemini sign-in: failed; start a new sign-in request.";
                break;
            case CANCELLED:
                status = "Gemini sign-in: cancelled; start again when ready.";
                break;
            case IDLE:
            default:
                status = hasCredentials
                        ? "Gemini sign-in: saved credentials found; headless probe is ready."
                        : "Gemini sign-in: not authenticated. Start Google sign-in.";
                break;
        }
        runOnUiThread(() -> {
            geminiAuthStatus.setText(status);
            geminiLoginButton.setEnabled(!pending);
            geminiAuthCode.setEnabled(canSubmit);
            geminiSubmitCodeButton.setEnabled(canSubmit);
            geminiHeadlessButton.setEnabled(!pending && hasCredentials);
        });
    }

    private void clearGeminiAuthCode() {
        runOnUiThread(() -> geminiAuthCode.setText(""));
    }

    private void clearPotentialGeminiAuthTranscripts() {
        deleteQuietly(new File(getFilesDir(), "last-command.txt"));
        deleteQuietly(new File(getCacheDir(), "last-stdout.txt"));
        deleteQuietly(new File(getCacheDir(), "last-stderr.txt"));
        deleteQuietly(new File(getCacheDir(), "gemini-auth-stdout.tmp"));
        deleteQuietly(new File(getCacheDir(), "gemini-auth-stderr.tmp"));
    }

    private void clearStaleGeminiAuthState() {
        deleteQuietly(new File(getFilesDir(), "gemini-auth-state.json"));
        if (hasGeminiCredentialFile()) geminiAuthLifecycle.authenticated();
    }

    private static void deleteQuietly(File file) {
        if (file.isFile()) file.delete();
    }

    private void runGeminiAuthCommand(List<String> command, File state, long generation)
            throws Exception {
        File executable = new File(command.get(0));
        if (!executable.isFile()) {
            throw new IOException("Gemini runtime executable is absent");
        }
        ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(command));
        builder.directory(getFilesDir());
        configureRuntimeEnvironment(builder);
        File stdoutCapture = new File(getCacheDir(), "gemini-auth-stdout.tmp");
        File stderrCapture = new File(getCacheDir(), "gemini-auth-stderr.tmp");
        builder.redirectOutput(stdoutCapture);
        builder.redirectError(stderrCapture);

        Process process = builder.start();
        synchronized (geminiAuthProcessLock) {
            if (generation != geminiAuthGeneration) {
                process.destroy();
                return;
            }
            geminiAuthProcess = process;
        }
        refreshGeminiAuthUi();

        int exitCode;
        try {
            exitCode = process.waitFor();
            applyTerminalGeminiAuthState(state, generation);
            if (generation == geminiAuthGeneration && geminiAuthLifecycle.isPending()) {
                if (exitCode == 130) {
                    geminiAuthLifecycle.cancelled();
                    append("Gemini sign-in was cancelled. Start it again when ready.\n");
                } else {
                    geminiAuthLifecycle.failed();
                    append("Gemini sign-in ended before authentication completed (exit "
                            + exitCode + "). Start it again.\n");
                }
            }
        } finally {
            synchronized (geminiAuthProcessLock) {
                if (geminiAuthProcess == process) geminiAuthProcess = null;
            }
            deleteQuietly(stdoutCapture);
            deleteQuietly(stderrCapture);
            if (!geminiAuthLifecycle.isPending()) clearGeminiAuthCode();
            refreshGeminiAuthUi();
        }
    }

    private void applyTerminalGeminiAuthState(File state, long generation) {
        if (generation != geminiAuthGeneration || !state.isFile()) return;
        try {
            String status = new JSONObject(readAll(state).trim()).optString("status");
            if ("authenticated".equals(status)) {
                geminiAuthLifecycle.authenticated();
            } else if ("failed".equals(status)) {
                geminiAuthLifecycle.failed();
            }
        } catch (Exception ignored) {
            // The observer reports malformed state without persisting provider output.
        }
    }

    private void runCodexProbe() {
        commands.execute(() -> runPackaged(
                RuntimeProbes.CODEX,
                List.of("--listen", "stdio://"),
                CODEX_INITIALIZE,
                CODEX_STDIN_SETTLE_MILLIS));
    }

    private void startCodexAccountRead() {
        startCodexAuthSession(CodexAuthSession.Mode.ACCOUNT_READ);
    }

    private void startCodexDeviceLogin() {
        codexVerificationUrl = null;
        runOnUiThread(() -> {
            codexAuthDetails.setText("Starting Codex ChatGPT device sign-in…");
            codexOpenLoginButton.setEnabled(false);
        });
        startCodexAuthSession(CodexAuthSession.Mode.DEVICE_CODE_LOGIN);
    }

    private void startCodexAuthSession(CodexAuthSession.Mode mode) {
        commands.execute(() -> {
            closeCodexAuthSession();
            try {
                File previousState = new File(getFilesDir(), "codex-auth-state.json");
                if (previousState.isFile() && !previousState.delete()) {
                    throw new IOException("Cannot clear previous Codex auth state");
                }
                File executable = runtime(RuntimeProbes.CODEX);
                if (!executable.isFile()) {
                    throw new IOException("Codex app-server is not packaged");
                }
                ProcessBuilder builder = new ProcessBuilder(
                        executable.getAbsolutePath(), "--listen", "stdio://");
                builder.directory(getFilesDir());
                configureRuntimeEnvironment(builder);
                File codexHome = CodexAuthStorage.prepare(getFilesDir());
                File trustBundle = CodexAuthStorage.prepareTrustBundle(getFilesDir());
                builder.environment().put("CODEX_HOME", codexHome.getAbsolutePath());
                builder.environment().put("SSL_CERT_FILE", trustBundle.getAbsolutePath());

                CodexAuthSession session = new CodexAuthSession(
                        builder, mode, createCodexAuthListener());
                codexAuthSession = session;
                session.start();
            } catch (Exception error) {
                append("Codex account session failed: " + error.getMessage() + "\n");
            }
        });
    }

    private CodexAuthSession.Listener createCodexAuthListener() {
        return new CodexAuthSession.Listener() {
            @Override
            public void onStatus(String status) {
                append("[codex-account] " + status + "\n");
            }

            @Override
            public void onAccount(CodexAuthProtocol.Message account) {
                String summary;
                if (account.accountType == null) {
                    summary = "No Codex account is signed in.\n"
                            + "OpenAI authentication required: " + account.requiresOpenaiAuth;
                } else {
                    summary = "Codex account: " + account.accountType
                            + (account.email == null ? "" : "\nEmail: " + account.email)
                            + (account.planType == null ? "" : "\nPlan: " + account.planType);
                }
                runOnUiThread(() -> codexAuthDetails.setText(summary));
                writeCodexAuthState(codexState(
                        "status", account.accountType == null ? "signed_out" : "authenticated",
                        "requiresOpenaiAuth", account.requiresOpenaiAuth));
                append("[codex-account] Account state received.\n");
            }

            @Override
            public void onDeviceCode(String verificationUrl, String userCode) {
                codexVerificationUrl = verificationUrl;
                runOnUiThread(() -> {
                    codexAuthDetails.setText("Open:\n" + verificationUrl
                            + "\n\nOne-time code:\n" + userCode);
                    codexOpenLoginButton.setEnabled(true);
                });
                writeCodexAuthState(codexState(
                        "status", "authorization_required"));
                append("[codex-account] Device code ready; use the displayed official URL.\n");
            }

            @Override
            public void onLoginCompleted(boolean success, String error) {
                String result = success ? "Codex ChatGPT sign-in completed."
                        : "Codex ChatGPT sign-in failed: " + error;
                runOnUiThread(() -> codexAuthDetails.setText(result));
                writeCodexAuthState(codexState(
                        "status", success ? "authenticated" : "failed"));
                append("[codex-account] " + result + "\n");
            }

            @Override
            public void onDiagnostic(String diagnostic) {
                append("[codex-diagnostic] " + diagnostic + "\n");
            }

            @Override
            public void onStopped(int exitCode) {
                append("[codex-account] app-server exited " + exitCode + "\n");
            }

            @Override
            public void onError(String error) {
                writeCodexAuthState(codexState("status", "failed"));
                append("[codex-account] ERROR: " + error + "\n");
            }
        };
    }

    private void openCodexVerificationUrl() {
        String url = codexVerificationUrl;
        if (!CodexAuthProtocol.isTrustedVerificationUrl(url)) {
            append("No trusted Codex verification URL is ready.\n");
            return;
        }
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private void cancelCodexLogin() {
        CodexAuthSession session = codexAuthSession;
        if (session == null) {
            append("No Codex account session is active.\n");
            return;
        }
        session.cancelLogin();
    }

    private void closeCodexAuthSession() {
        CodexAuthSession session = codexAuthSession;
        codexAuthSession = null;
        if (session != null) {
            session.close();
        }
    }

    private void startCodexConversation(boolean resume) {
        String prompt = resume ? CODEX_RESUME_PROMPT : CODEX_FIRST_PROMPT;
        String expected = resume ? CODEX_RESUME_EXPECTED : CODEX_FIRST_EXPECTED;
        commands.execute(() -> {
            closeCodexConversationSession();
            try {
                String savedThreadId = resume
                        ? CodexConversationStorage.readThreadId(getFilesDir()) : null;
                if (resume && savedThreadId == null) {
                    throw new IOException("No saved Codex thread is available to resume");
                }
                File workspace = new File(getFilesDir(), "codex-chat-workspace");
                if (!workspace.isDirectory() && !workspace.mkdirs()) {
                    throw new IOException("Cannot create Codex chat workspace");
                }
                File executable = runtime(RuntimeProbes.CODEX);
                ProcessBuilder builder = new ProcessBuilder(
                        executable.getAbsolutePath(), "--listen", "stdio://");
                builder.directory(getFilesDir());
                configureRuntimeEnvironment(builder);
                File codexHome = CodexAuthStorage.prepare(getFilesDir());
                File trustBundle = CodexAuthStorage.prepareTrustBundle(getFilesDir());
                builder.environment().put("CODEX_HOME", codexHome.getAbsolutePath());
                builder.environment().put("SSL_CERT_FILE", trustBundle.getAbsolutePath());

                runOnUiThread(() -> codexConversationDetails.setText(
                        resume ? "Resuming Codex thread…" : "Starting Codex thread…"));
                CodexConversationSession session = new CodexConversationSession(
                        builder,
                        resume ? CodexConversationSession.Mode.RESUME_THREAD
                                : CodexConversationSession.Mode.NEW_THREAD,
                        savedThreadId, workspace.getAbsolutePath(), prompt,
                        createCodexConversationListener(expected));
                codexConversationSession = session;
                session.start();
            } catch (Exception error) {
                append("[codex-conversation] ERROR: " + error.getMessage() + "\n");
            }
        });
    }

    private CodexConversationSession.Listener createCodexConversationListener(String expected) {
        return new CodexConversationSession.Listener() {
            private volatile String threadId;
            private volatile String finalText;

            @Override public void onStatus(String status) {
                append("[codex-conversation] " + status + "\n");
            }

            @Override public void onModels(int count, String defaultModel) {
                append("[codex-conversation] Model catalog received: " + count + " model(s).\n");
            }

            @Override public void onThreadReady(String id, String model) {
                threadId = id;
                try {
                    CodexConversationStorage.write(getFilesDir(), id, "running", false);
                } catch (Exception error) {
                    onError("Could not persist Codex thread state");
                }
                append("[codex-conversation] Thread ready.\n");
            }

            @Override public void onTurnStarted(String turnId) {
                runOnUiThread(() -> codexConversationDetails.setText(""));
                append("[codex-conversation] Turn started.\n");
            }

            @Override public void onAssistantDelta(String delta) {
                runOnUiThread(() -> codexConversationDetails.append(delta));
            }

            @Override public void onAssistantFinal(String text) {
                finalText = text;
                runOnUiThread(() -> codexConversationDetails.setText(text));
            }

            @Override public void onTurnCompleted(String status, String error) {
                boolean matched = "completed".equals(status)
                        && expected.equals(finalText == null ? "" : finalText.trim());
                try {
                    CodexConversationStorage.write(getFilesDir(), threadId, status, matched);
                } catch (Exception storageError) {
                    append("[codex-conversation] Could not persist result status.\n");
                }
                append("[codex-conversation] Turn " + status
                        + "; expected response matched: " + matched + "\n");
                if (error != null) append("[codex-conversation] Turn error received.\n");
            }

            @Override public void onServerRequestDenied() {
                append("[codex-conversation] Denied a tool/approval request.\n");
            }

            @Override public void onDiagnostic() {
                // Keep stderr separate and do not copy diagnostic payloads into UI/log state.
            }

            @Override public void onStopped(int exitCode) {
                append("[codex-conversation] app-server exited " + exitCode + "\n");
            }

            @Override public void onError(String error) {
                if (threadId != null) {
                    try {
                        CodexConversationStorage.write(getFilesDir(), threadId, "failed", false);
                    } catch (Exception ignored) {}
                }
                append("[codex-conversation] ERROR: app-server reported a conversation failure.\n");
            }
        };
    }

    private void cancelCodexTurn() {
        CodexConversationSession session = codexConversationSession;
        if (session == null) {
            append("No Codex turn is active.\n");
            return;
        }
        session.interrupt();
    }

    private void closeCodexConversationSession() {
        CodexConversationSession session = codexConversationSession;
        codexConversationSession = null;
        if (session != null) session.close();
    }

    private synchronized void writeCodexAuthState(JSONObject state) {
        File stateFile = new File(getFilesDir(), "codex-auth-state.json");
        try (FileOutputStream target = new FileOutputStream(stateFile)) {
            target.write((state.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            if (!stateFile.setReadable(false, false)
                    || !stateFile.setWritable(false, false)
                    || !stateFile.setExecutable(false, false)
                    || !stateFile.setReadable(true, true)
                    || !stateFile.setWritable(true, true)) {
                throw new IOException("Could not restrict Codex auth state");
            }
        } catch (IOException error) {
            append("Could not write Codex auth state: " + error.getMessage() + "\n");
        }
    }

    private static JSONObject codexState(Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("Codex auth state entries must be key/value pairs");
        }
        JSONObject state = new JSONObject();
        try {
            for (int index = 0; index < entries.length; index += 2) {
                state.put((String) entries[index], entries[index + 1]);
            }
        } catch (JSONException impossible) {
            throw new IllegalStateException("Cannot create Codex auth state", impossible);
        }
        return state;
    }

    private void runPackaged(RuntimeProbe probe, List<String> arguments) {
        runPackaged(probe, arguments, null);
    }

    private void runPackaged(RuntimeProbe probe, List<String> arguments, String stdinPayload) {
        runPackaged(probe, arguments, stdinPayload, 0);
    }

    private void runPackaged(
            RuntimeProbe probe,
            List<String> arguments,
            String stdinPayload,
            long stdinSettleMillis) {
        try {
            List<String> command = new ArrayList<>();
            command.add(runtime(probe).getAbsolutePath());
            command.addAll(arguments);
            run(command, stdinPayload, stdinSettleMillis);
        } catch (Exception error) {
            append("[" + probe.id() + "] NOT_PACKAGED_OR_FAILED: " + error + "\n");
        }
    }

    private void run(List<String> command) throws Exception {
        run(command, null);
    }

    private void run(List<String> command, String stdinPayload) throws Exception {
        run(command, stdinPayload, 0);
    }

    private void run(List<String> command, String stdinPayload, long stdinSettleMillis)
            throws Exception {
        File executable = new File(command.get(0));
        if (!executable.isFile()) {
            throw new IOException("Runtime executable is absent from nativeLibraryDir: "
                    + executable.getName());
        }

        append("\n$ " + String.join(" ", command) + "\n");
        ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(command));
        builder.directory(getFilesDir());
        configureRuntimeEnvironment(builder);

        File stdoutCapture = new File(getCacheDir(), "last-stdout.txt");
        File stderrCapture = new File(getCacheDir(), "last-stderr.txt");
        builder.redirectOutput(stdoutCapture);
        builder.redirectError(stderrCapture);

        Process process = builder.start();
        activeProcess = process;
        if (stdinPayload != null) {
            process.getOutputStream().write(stdinPayload.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            if (stdinSettleMillis > 0) {
                Thread.sleep(stdinSettleMillis);
            }
            process.getOutputStream().close();
        }
        int exitCode = process.waitFor();
        String stdoutText = readAll(stdoutCapture);
        String stderrText = readAll(stderrCapture);
        activeProcess = null;

        String report = "$ " + String.join(" ", command) + "\n"
                + "[stdout]\n" + stdoutText
                + "[stderr]\n" + stderrText
                + "[exit] " + exitCode + "\n";
        try (FileOutputStream target = new FileOutputStream(
                new File(getFilesDir(), "last-command.txt"))) {
            target.write(report.getBytes(StandardCharsets.UTF_8));
        }
        append(report);
    }

    private void configureRuntimeEnvironment(ProcessBuilder builder) throws IOException {
        Map<String, String> environment = builder.environment();
        File home = new File(getFilesDir(), "runtime-home");
        File temporary = new File(getCacheDir(), "node-tmp");
        home.mkdirs();
        temporary.mkdirs();
        environment.put("HOME", home.getAbsolutePath());
        environment.put("TMPDIR", temporary.getAbsolutePath());
        environment.put("LANG", "en_US.UTF-8");
        environment.put("LC_ALL", "en_US.UTF-8");
        environment.put("LD_LIBRARY_PATH", getApplicationInfo().nativeLibraryDir);
        // clipboardy throws during module initialization on Android unless this
        // marker is present. Clipboard actions still require a separate bridge;
        // the standalone version/headless paths do not invoke them.
        environment.put("TERMUX_VERSION", "rava-standalone");
        environment.put("GEMINI_DEFAULT_AUTH_TYPE", "oauth-personal");
        environment.put("GEMINI_FORCE_ENCRYPTED_FILE_STORAGE", "true");
        environment.put("GEMINI_FORCE_FILE_STORAGE", "true");
        writeResolverConfig();
    }

    private void writeResolverConfig() throws IOException {
        ConnectivityManager manager = getSystemService(ConnectivityManager.class);
        Network activeNetwork = manager == null ? null : manager.getActiveNetwork();
        LinkProperties properties = activeNetwork == null
                ? null : manager.getLinkProperties(activeNetwork);
        if (properties == null || properties.getDnsServers().isEmpty()) {
            throw new IOException("No DNS servers reported for the active Android network");
        }
        StringBuilder config = new StringBuilder();
        for (InetAddress address : properties.getDnsServers()) {
            config.append("nameserver ").append(address.getHostAddress()).append('\n');
        }
        try (FileOutputStream target = new FileOutputStream(
                new File(getFilesDir(), "resolv.conf"))) {
            target.write(config.toString().getBytes(StandardCharsets.US_ASCII));
        }
    }

    private File copyAsset(String assetName, String relativeDestination) throws IOException {
        File destination = new File(getFilesDir(), relativeDestination);
        File parent = destination.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        }
        try (InputStream input = getAssets().open(assetName);
             FileOutputStream target = new FileOutputStream(destination)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                target.write(buffer, 0, count);
            }
        }
        return destination;
    }

    /** Copies a bundled JavaScript module tree while keeping executable code in nativeLibraryDir. */
    private File copyAssetTree(String assetPath, String relativeDestination) throws IOException {
        String[] children = getAssets().list(assetPath);
        if (children == null || children.length == 0) {
            return copyAsset(assetPath, relativeDestination);
        }

        File directory = new File(getFilesDir(), relativeDestination);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Cannot create " + directory);
        }
        for (String child : children) {
            copyAssetTree(assetPath + "/" + child, relativeDestination + "/" + child);
        }
        return directory;
    }

    private static String readAll(InputStream input) throws IOException {
        return ProbeOutput.readUtf8(input, ProbeOutput.MAX_BYTES);
    }

    private static String readAll(File input) throws IOException {
        try (FileInputStream stream = new FileInputStream(input)) {
            return readAll(stream);
        }
    }

    private void append(String text) {
        runOnUiThread(() -> output.append(text));
    }

    private void cancelActiveProcess() {
        closeCodexAuthSession();
        closeCodexConversationSession();
        Process authProcess;
        synchronized (geminiAuthProcessLock) {
            authProcess = geminiAuthProcess;
        }
        if (authProcess != null) {
            ++geminiAuthGeneration;
            authProcess.destroy();
            geminiAuthLifecycle.cancelled();
            clearGeminiAuthCode();
            refreshGeminiAuthUi();
            append("Gemini sign-in cancellation requested.\n");
        }
        Process process = activeProcess;
        if (process != null) {
            process.destroy();
            append("Cancellation requested.\n");
        }
    }

    @Override
    protected void onDestroy() {
        cancelActiveProcess();
        commands.shutdownNow();
        authObserver.shutdownNow();
        super.onDestroy();
    }
}
