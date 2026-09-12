package ir.rava.installer;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class CodexProvider implements ChatProvider {
    interface AccountListener {
        void onStatus(String status);
        void onAccount(boolean authenticated, String accountType, String planType);
        void onDeviceCode(String verificationUrl, String userCode);
        void onLoginCompleted(boolean success, String error);
        void onError(String error);
    }

    private final CodexRuntime runtime;
    private volatile CodexConversationSession conversationSession;
    private volatile CodexConversationSession modelSession;
    private volatile CodexAuthSession authSession;

    CodexProvider(Context context) {
        runtime = new CodexRuntime(context);
    }

    @Override public String id() {
        return "codex";
    }

    @Override public void listModels(Result<List<ProviderModel>> result) {
        closeModelSession();
        AtomicBoolean delivered = new AtomicBoolean();
        try {
            CodexConversationSession session = new CodexConversationSession(
                    runtime.processBuilder(), CodexConversationSession.Mode.LIST_MODELS,
                    null, runtime.workspace().getAbsolutePath(), null, null,
                    new EmptyConversationListener() {
                        @Override public void onModels(List<String> ids, String defaultModel) {
                            List<ProviderModel> models = new ArrayList<>();
                            for (String id : ids) models.add(new ProviderModel("codex", id, id));
                            if (delivered.compareAndSet(false, true)) result.onSuccess(models);
                        }

                        @Override public void onError(String error) {
                            if (delivered.compareAndSet(false, true)) result.onError(error);
                        }

                        @Override public void onStopped(int exitCode) {
                            if (exitCode != 0 && delivered.compareAndSet(false, true)) {
                                result.onError("Codex model process exited with code " + exitCode);
                            }
                        }
                    });
            modelSession = session;
            session.start();
        } catch (Exception error) {
            if (delivered.compareAndSet(false, true)) result.onError(error.getMessage());
        }
    }

    @Override public void send(Request request, Result<Response> result) {
        cancel();
        AtomicBoolean delivered = new AtomicBoolean();
        StringBuilder finalText = new StringBuilder();
        String[] threadId = {request.conversationId};
        try {
            CodexConversationSession session = new CodexConversationSession(
                    runtime.processBuilder(),
                    request.conversationId == null
                            ? CodexConversationSession.Mode.NEW_THREAD
                            : CodexConversationSession.Mode.RESUME_THREAD,
                    request.conversationId, runtime.workspace().getAbsolutePath(), request.prompt,
                    request.modelId, new EmptyConversationListener() {
                        @Override public void onThreadReady(String id, String model) {
                            threadId[0] = id;
                        }

                        @Override public void onAssistantFinal(String text) {
                            finalText.setLength(0);
                            finalText.append(text);
                        }

                        @Override public void onTurnCompleted(String status, String error) {
                            if (!"completed".equals(status)) {
                                if (delivered.compareAndSet(false, true)) {
                                    result.onError(error == null ? "Codex turn did not complete" : error);
                                }
                            } else if (threadId[0] == null) {
                                if (delivered.compareAndSet(false, true)) {
                                    result.onError("Codex did not return a thread ID");
                                }
                            } else if (delivered.compareAndSet(false, true)) {
                                result.onSuccess(new Response(threadId[0], finalText.toString()));
                            }
                        }

                        @Override public void onError(String error) {
                            if (delivered.compareAndSet(false, true)) result.onError(error);
                        }

                        @Override public void onStopped(int exitCode) {
                            if (!delivered.get() && exitCode != 0
                                    && delivered.compareAndSet(false, true)) {
                                result.onError("Codex process exited with code " + exitCode);
                            }
                        }
                    });
            conversationSession = session;
            session.start();
        } catch (Exception error) {
            if (delivered.compareAndSet(false, true)) result.onError(error.getMessage());
        }
    }

    void readAccount(AccountListener listener) {
        startAuth(CodexAuthSession.Mode.ACCOUNT_READ, listener);
    }

    void startLogin(AccountListener listener) {
        startAuth(CodexAuthSession.Mode.DEVICE_CODE_LOGIN, listener);
    }

    private void startAuth(CodexAuthSession.Mode mode, AccountListener listener) {
        closeAuthSession();
        try {
            CodexAuthSession session = new CodexAuthSession(runtime.processBuilder(), mode,
                    new CodexAuthSession.Listener() {
                        @Override public void onStatus(String status) { listener.onStatus(status); }
                        @Override public void onAccount(CodexAuthProtocol.Message account) {
                            listener.onAccount(account.accountType != null, account.accountType,
                                    account.planType);
                        }
                        @Override public void onDeviceCode(String url, String code) {
                            listener.onDeviceCode(url, code);
                        }
                        @Override public void onLoginCompleted(boolean success, String error) {
                            listener.onLoginCompleted(success, error);
                        }
                        @Override public void onDiagnostic(String diagnostic) {}
                        @Override public void onStopped(int exitCode) {}
                        @Override public void onError(String error) { listener.onError(error); }
                    });
            authSession = session;
            session.start();
        } catch (Exception error) {
            listener.onError(error.getMessage());
        }
    }

    void cancelLogin() {
        CodexAuthSession session = authSession;
        if (session != null) session.cancelLogin();
    }

    @Override public void cancel() {
        CodexConversationSession session = conversationSession;
        if (session != null) session.interrupt();
    }

    private void closeModelSession() {
        CodexConversationSession session = modelSession;
        modelSession = null;
        if (session != null) session.close();
    }

    private void closeAuthSession() {
        CodexAuthSession session = authSession;
        authSession = null;
        if (session != null) session.close();
    }

    @Override public void close() {
        CodexConversationSession session = conversationSession;
        conversationSession = null;
        if (session != null) session.close();
        closeModelSession();
        closeAuthSession();
    }

    private abstract static class EmptyConversationListener
            implements CodexConversationSession.Listener {
        @Override public void onStatus(String status) {}
        @Override public void onModels(List<String> models, String defaultModel) {}
        @Override public void onThreadReady(String threadId, String model) {}
        @Override public void onTurnStarted(String turnId) {}
        @Override public void onAssistantDelta(String delta) {}
        @Override public void onAssistantFinal(String text) {}
        @Override public void onTurnCompleted(String status, String error) {}
        @Override public void onServerRequestDenied() {}
        @Override public void onDiagnostic() {}
        @Override public void onStopped(int exitCode) {}
        @Override public void onError(String error) {}
    }
}
