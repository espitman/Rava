package ir.rava.installer;

import android.content.Context;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

final class AntigravityProvider implements ChatProvider {
    private static final String RUNNER =
            "$HOME/.local/share/rava-antigravity/installer/scripts/run-antigravity-termux.sh";
    private final Context context;
    private final AtomicInteger activeExecution = new AtomicInteger(-1);
    private final AtomicInteger generation = new AtomicInteger();

    AntigravityProvider(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override public String id() {
        return "antigravity";
    }

    @Override public void listModels(Result<List<ProviderModel>> result) {
        run("Antigravity models", "set -eu\ntimeout 90s \"" + RUNNER
                + "\" models\n", false,
                command -> {
                    if (!command.succeeded()) {
                        result.onError(failure(command));
                        return;
                    }
                    List<ProviderModel> models = AntigravityProtocol.parseModels(command.stdout);
                    if (models.isEmpty()) result.onError("Antigravity returned no models.");
                    else result.onSuccess(models);
                });
    }

    @Override public void send(Request request, Result<Response> result) {
        int requestGeneration = generation.incrementAndGet();
        if (!AntigravityProtocol.isSafeIdentifier(request.modelId)) {
            result.onError("Invalid Antigravity model identifier.");
            return;
        }
        if (request.conversationId != null
                && !AntigravityProtocol.isSafeIdentifier(request.conversationId)) {
            result.onError("Invalid Antigravity conversation identifier.");
            return;
        }
        String encodedPrompt = Base64.encodeToString(
                request.prompt.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        StringBuilder script = new StringBuilder("set -euo pipefail\n")
                .append("prompt=$(printf '%s' '").append(encodedPrompt)
                .append("' | base64 -d)\n")
                .append("args=(--prompt \"$prompt\" --model '")
                .append(request.modelId)
                .append("' --output-format stream-json --print-timeout 5m)\n");
        if (request.conversationId != null) {
            script.append("args+=(--conversation '")
                    .append(request.conversationId).append("')\n");
        }
        script.append("pid_file=$HOME/.local/share/rava-antigravity/active-rava.pid\n")
                .append("cleanup() { rm -f \"$pid_file\"; }\n")
                .append("trap cleanup EXIT INT TERM\n")
                .append("RAVA_AGY_CHAT_ONLY=1 \"").append(RUNNER)
                .append("\" \"${args[@]}\" &\n")
                .append("child=$!\nprintf '%s\\n' \"$child\" > \"$pid_file\"\n")
                .append("chmod 600 \"$pid_file\"\nwait \"$child\"\n");
        run("Antigravity chat", script.toString(), true, command -> {
            if (generation.get() != requestGeneration) return;
            if (!command.succeeded()) {
                result.onError(failure(command));
                return;
            }
            try {
                result.onSuccess(AntigravityProtocol.parseResponse(
                        command.stdout, request.modelId));
            } catch (Exception error) {
                result.onError(error.getMessage());
            }
        });
    }

    private void run(String label, String script, boolean sensitive,
            TermuxBridge.Callback callback) {
        try {
            int executionId = TermuxBridge.run(context, label, script, true, sensitive, result -> {
                activeExecution.compareAndSet(result.executionId, -1);
                callback.onResult(result);
            });
            activeExecution.set(executionId);
        } catch (Exception error) {
            callback.onResult(new TermuxCommandResult(-1, -1, -1, "", "", error.getMessage()));
        }
    }

    private static String failure(TermuxCommandResult result) {
        String diagnostic = (result.errorMessage + "\n" + result.stderr).toLowerCase();
        if (diagnostic.contains("auth") || diagnostic.contains("sign in")
                || diagnostic.contains("login")) return "Google sign-in is required.";
        if (diagnostic.contains("quota") || diagnostic.contains("credit")) {
            return "The selected model's quota is currently unavailable.";
        }
        if (diagnostic.contains("timeout") || diagnostic.contains("network")
                || diagnostic.contains("connect") || diagnostic.contains("resolve")) {
            return "Could not reach Google. Check the VPN and network connection.";
        }
        return "Antigravity exited with code " + result.exitCode + ".";
    }

    @Override public void cancel() {
        generation.incrementAndGet();
        if (activeExecution.getAndSet(-1) < 0) return;
        String script = "set -eu\n"
                + "pid_file=$HOME/.local/share/rava-antigravity/active-rava.pid\n"
                + "if [ -r \"$pid_file\" ]; then\n"
                + "  IFS= read -r pid < \"$pid_file\"\n"
                + "  case \"$pid\" in (*[!0-9]*|'') exit 1;; esac\n"
                + "  kill \"$pid\" 2>/dev/null || true\n"
                + "  rm -f \"$pid_file\"\n"
                + "fi\n";
        try {
            TermuxBridge.run(context, "Cancel Antigravity", script, true, true, ignored -> {});
        } catch (RuntimeException ignored) {}
    }

    @Override public void close() {
        cancel();
    }
}
