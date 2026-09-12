package ir.rava.installer;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Maintains one long-lived app-server process while a managed ChatGPT login is pending. */
final class CodexAuthSession implements AutoCloseable {
    enum Mode { ACCOUNT_READ, DEVICE_CODE_LOGIN }

    interface Listener {
        void onStatus(String status);
        void onAccount(CodexAuthProtocol.Message account);
        void onDeviceCode(String verificationUrl, String userCode);
        void onLoginCompleted(boolean success, String error);
        void onDiagnostic(String diagnostic);
        void onStopped(int exitCode);
        void onError(String error);
    }

    private final ProcessBuilder processBuilder;
    private final Mode mode;
    private final Listener listener;
    private final ExecutorService workers = Executors.newFixedThreadPool(3);
    private final AtomicBoolean closed = new AtomicBoolean();

    private Process process;
    private BufferedWriter stdin;
    private volatile String loginId;

    CodexAuthSession(ProcessBuilder processBuilder, Mode mode, Listener listener) {
        this.processBuilder = processBuilder;
        this.mode = mode;
        this.listener = listener;
    }

    synchronized void start() throws IOException, JSONException {
        if (process != null) {
            throw new IllegalStateException("Codex auth session already started");
        }
        process = processBuilder.start();
        stdin = new BufferedWriter(new OutputStreamWriter(
                process.getOutputStream(), StandardCharsets.UTF_8));
        workers.submit(() -> readStdout(process.getInputStream()));
        workers.submit(() -> readStderr(process.getErrorStream()));
        workers.submit(this::waitForExit);
        listener.onStatus("Initializing Codex account session…");
        send(CodexAuthProtocol.initializeRequest());
    }

    synchronized void cancelLogin() {
        if (loginId == null) {
            listener.onStatus("No pending Codex login to cancel.");
            return;
        }
        try {
            send(CodexAuthProtocol.cancelLoginRequest(loginId));
            listener.onStatus("Canceling Codex login…");
        } catch (IOException | JSONException error) {
            listener.onError("Could not cancel Codex login: " + error.getMessage());
        }
    }

    private void readStdout(InputStream input) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                handle(CodexAuthProtocol.parse(line));
            }
        } catch (IOException | JSONException error) {
            if (!closed.get()) {
                listener.onError("Codex protocol read failed: " + error.getMessage());
            }
        }
    }

    private void readStderr(InputStream input) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                listener.onDiagnostic(line);
            }
        } catch (IOException error) {
            if (!closed.get()) {
                listener.onError("Codex diagnostic read failed: " + error.getMessage());
            }
        }
    }

    private void handle(CodexAuthProtocol.Message message) throws IOException, JSONException {
        switch (message.kind) {
            case INITIALIZED:
                send(CodexAuthProtocol.initializedNotification());
                if (mode == Mode.ACCOUNT_READ) {
                    listener.onStatus("Reading Codex account state…");
                    send(CodexAuthProtocol.accountReadRequest(CodexAuthProtocol.ACCOUNT_READ_ID));
                } else {
                    listener.onStatus("Requesting a ChatGPT device code…");
                    send(CodexAuthProtocol.deviceCodeLoginRequest());
                }
                break;
            case ACCOUNT:
                listener.onAccount(message);
                closeInput();
                break;
            case DEVICE_CODE:
                loginId = message.loginId;
                if (!CodexAuthProtocol.isTrustedVerificationUrl(message.verificationUrl)) {
                    listener.onError("Codex returned an untrusted verification URL.");
                    cancelLogin();
                    break;
                }
                listener.onDeviceCode(message.verificationUrl, message.userCode);
                break;
            case LOGIN_COMPLETED:
                listener.onLoginCompleted(message.success, message.error);
                if (message.success) {
                    send(CodexAuthProtocol.accountReadRequest(
                            CodexAuthProtocol.POST_LOGIN_ACCOUNT_READ_ID));
                } else {
                    closeInput();
                }
                break;
            case ACCOUNT_UPDATED:
                listener.onStatus("Codex account updated: "
                        + (message.authMode == null ? "signed out" : message.authMode)
                        + (message.planType == null ? "" : " / " + message.planType));
                break;
            case LOGIN_CANCELED:
                listener.onStatus("Codex login cancel result: " + message.cancelStatus);
                closeInput();
                break;
            case ERROR:
                listener.onError(message.error);
                closeInput();
                break;
            case OTHER:
                break;
        }
    }

    private synchronized void send(String json) throws IOException {
        if (stdin == null || closed.get()) {
            throw new IOException("Codex auth session is not writable");
        }
        stdin.write(json);
        stdin.newLine();
        stdin.flush();
    }

    private synchronized void closeInput() {
        if (stdin == null) {
            return;
        }
        try {
            stdin.close();
        } catch (IOException ignored) {
            // Process shutdown below remains authoritative.
        }
        stdin = null;
    }

    private void waitForExit() {
        int exitCode = -1;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            closed.set(true);
            listener.onStopped(exitCode);
            workers.shutdown();
        }
    }

    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        closeInput();
        if (process != null) {
            process.destroy();
        }
        workers.shutdownNow();
    }
}
