package ir.rava.runtimeprobe;

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

/** Runs one new or resumed Codex thread turn over a long-lived JSONL process. */
final class CodexConversationSession implements AutoCloseable {
    enum Mode { NEW_THREAD, RESUME_THREAD }

    interface Listener {
        void onStatus(String status);
        void onModels(int count, String defaultModel);
        void onThreadReady(String threadId, String model);
        void onTurnStarted(String turnId);
        void onAssistantDelta(String delta);
        void onAssistantFinal(String text);
        void onTurnCompleted(String status, String error);
        void onServerRequestDenied();
        void onDiagnostic();
        void onStopped(int exitCode);
        void onError(String error);
    }

    private final ProcessBuilder processBuilder;
    private final Mode mode;
    private final String requestedThreadId;
    private final String cwd;
    private final String prompt;
    private final Listener listener;
    private final ExecutorService workers = Executors.newFixedThreadPool(3);
    private final AtomicBoolean closed = new AtomicBoolean();

    private Process process;
    private BufferedWriter stdin;
    private volatile String threadId;
    private volatile String turnId;

    CodexConversationSession(ProcessBuilder processBuilder, Mode mode, String requestedThreadId,
            String cwd, String prompt, Listener listener) {
        this.processBuilder = processBuilder;
        this.mode = mode;
        this.requestedThreadId = requestedThreadId;
        this.cwd = cwd;
        this.prompt = prompt;
        this.listener = listener;
    }

    synchronized void start() throws IOException, JSONException {
        if (process != null) throw new IllegalStateException("Codex conversation already started");
        if (mode == Mode.RESUME_THREAD
                && (requestedThreadId == null || requestedThreadId.isBlank())) {
            throw new IllegalArgumentException("A persisted threadId is required to resume");
        }
        process = processBuilder.start();
        stdin = new BufferedWriter(new OutputStreamWriter(
                process.getOutputStream(), StandardCharsets.UTF_8));
        workers.submit(() -> readStdout(process.getInputStream()));
        workers.submit(() -> readStderr(process.getErrorStream()));
        workers.submit(this::waitForExit);
        listener.onStatus("Initializing Codex conversation…");
        send(CodexConversationProtocol.initializeRequest());
    }

    synchronized void interrupt() {
        if (threadId == null || turnId == null) {
            listener.onStatus("No active Codex turn to cancel.");
            return;
        }
        try {
            send(CodexConversationProtocol.turnInterruptRequest(threadId, turnId));
            listener.onStatus("Canceling Codex turn…");
        } catch (IOException | JSONException error) {
            listener.onError("Could not cancel Codex turn: " + error.getMessage());
        }
    }

    private void readStdout(InputStream input) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                handle(CodexConversationProtocol.parse(line));
            }
        } catch (IOException | JSONException error) {
            if (!closed.get()) listener.onError("Codex protocol read failed: " + error.getMessage());
        }
    }

    private void readStderr(InputStream input) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                input, StandardCharsets.UTF_8))) {
            while (reader.readLine() != null) listener.onDiagnostic();
        } catch (IOException error) {
            if (!closed.get()) listener.onError("Codex diagnostic read failed");
        }
    }

    private void handle(CodexConversationProtocol.Message message)
            throws IOException, JSONException {
        switch (message.kind) {
            case INITIALIZED:
                send(CodexConversationProtocol.initializedNotification());
                send(CodexConversationProtocol.modelListRequest());
                break;
            case MODELS:
                listener.onModels(message.modelCount, message.model);
                if (mode == Mode.NEW_THREAD) {
                    send(CodexConversationProtocol.threadStartRequest(cwd));
                } else {
                    send(CodexConversationProtocol.threadResumeRequest(requestedThreadId, cwd));
                }
                break;
            case THREAD_READY:
                threadId = message.threadId;
                listener.onThreadReady(threadId, message.model);
                send(CodexConversationProtocol.turnStartRequest(threadId, prompt));
                break;
            case TURN_STARTED:
                turnId = message.turnId;
                listener.onTurnStarted(turnId);
                break;
            case ASSISTANT_DELTA:
                listener.onAssistantDelta(message.text);
                break;
            case ASSISTANT_FINAL:
                listener.onAssistantFinal(message.text);
                break;
            case TURN_COMPLETED:
                listener.onTurnCompleted(message.status, message.error);
                closeInput();
                break;
            case TURN_INTERRUPTED:
                listener.onStatus("Codex turn interrupt acknowledged.");
                break;
            case APPROVAL_REQUEST:
            case UNSUPPORTED_SERVER_REQUEST:
                send(CodexConversationProtocol.denyServerRequest(message));
                listener.onServerRequestDenied();
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
        if (stdin == null || closed.get()) throw new IOException("Codex session is not writable");
        stdin.write(json);
        stdin.newLine();
        stdin.flush();
    }

    private synchronized void closeInput() {
        if (stdin == null) return;
        try { stdin.close(); } catch (IOException ignored) {}
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
        if (!closed.compareAndSet(false, true)) return;
        closeInput();
        if (process != null) process.destroy();
        workers.shutdownNow();
    }
}
