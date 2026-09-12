package ir.rava.installer;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Signature-protected local engine endpoint for the owner's other Android apps. */
public final class RavaEngineService extends Service {
    public static final String ACTION_BIND = "ir.rava.installer.action.BIND_ENGINE";
    public static final int LIST_MODELS = 1;
    public static final int SEND_MESSAGE = 2;
    public static final int CANCEL = 3;
    public static final int MODELS_RESULT = 101;
    public static final int MESSAGE_RESULT = 102;
    public static final int ERROR_RESULT = 199;

    private Messenger messenger;
    private AntigravityProvider antigravity;
    private CodexProvider codex;
    private final AtomicInteger nextTurnId = new AtomicInteger();
    private final AtomicInteger activeTurnId = new AtomicInteger();
    private final AtomicInteger activeCallerUid = new AtomicInteger(-1);

    @Override public void onCreate() {
        super.onCreate();
        antigravity = new AntigravityProvider(this);
        codex = new CodexProvider(this);
        messenger = new Messenger(new Handler(Looper.getMainLooper(), this::handle));
    }

    @Override public IBinder onBind(Intent intent) {
        if (intent == null || !ACTION_BIND.equals(intent.getAction())) return null;
        return messenger.getBinder();
    }

    private boolean handle(Message message) {
        if (getPackageManager().checkSignatures(getApplicationInfo().uid, message.sendingUid)
                != PackageManager.SIGNATURE_MATCH) {
            replyError(message, "Caller signature is not authorized");
            return true;
        }
        if (message.replyTo == null) return true;
        switch (message.what) {
            case LIST_MODELS:
                listModels(message);
                return true;
            case SEND_MESSAGE:
                sendMessage(message, message.sendingUid);
                return true;
            case CANCEL:
                if (activeTurnId.get() != 0
                        && activeCallerUid.get() != message.sendingUid) {
                    replyError(message, "Only the app that owns the active turn can cancel it");
                    return true;
                }
                antigravity.cancel();
                codex.cancel();
                activeTurnId.set(0);
                activeCallerUid.set(-1);
                return true;
            default:
                replyError(message, "Unknown Rava engine request");
                return true;
        }
    }

    private void listModels(Message request) {
        List<ProviderModel> models = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger pending = new AtomicInteger(2);
        ChatProvider.Result<List<ProviderModel>> result =
                new ChatProvider.Result<List<ProviderModel>>() {
            @Override public void onSuccess(List<ProviderModel> value) {
                models.addAll(value);
                finish();
            }

            @Override public void onError(String ignored) {
                finish();
            }

            private void finish() {
                if (pending.decrementAndGet() != 0) return;
                JSONArray data = new JSONArray();
                try {
                    synchronized (models) {
                        for (ProviderModel model : models) {
                            data.put(new JSONObject().put("id", model.archiveId())
                                    .put("provider", model.providerId)
                                    .put("model", model.modelId)
                                    .put("name", model.displayName));
                        }
                    }
                } catch (JSONException impossible) {
                    replyError(request, "Could not encode model list");
                    return;
                }
                Bundle payload = new Bundle();
                payload.putString("models_json", data.toString());
                reply(request, MODELS_RESULT, payload);
            }
        };
        antigravity.listModels(result);
        codex.listModels(result);
    }

    private void sendMessage(Message request, int callerUid) {
        int turnId = nextTurnId.incrementAndGet();
        if (!activeTurnId.compareAndSet(0, turnId)) {
            replyError(request, "Another Rava engine turn is already running");
            return;
        }
        activeCallerUid.set(callerUid);
        Bundle data = request.getData();
        String model = data.getString("model", "");
        String prompt = data.getString("prompt", "");
        String conversationId = emptyToNull(data.getString("conversation_id"));
        int separator = model.indexOf('/');
        if (separator <= 0 || separator == model.length() - 1) {
            releaseTurn(turnId, callerUid);
            replyError(request, "Model must use provider/model format");
            return;
        }
        if (prompt.isBlank() || prompt.length() > 100_000) {
            releaseTurn(turnId, callerUid);
            replyError(request, "Prompt must contain 1 to 100000 characters");
            return;
        }
        String providerId = model.substring(0, separator);
        String modelId = model.substring(separator + 1);
        ChatProvider provider;
        if ("codex".equals(providerId)) provider = codex;
        else if ("antigravity".equals(providerId)) provider = antigravity;
        else {
            releaseTurn(turnId, callerUid);
            replyError(request, "Unknown provider");
            return;
        }
        provider.send(new ChatProvider.Request(modelId, conversationId, prompt),
                new ChatProvider.Result<ChatProvider.Response>() {
            @Override public void onSuccess(ChatProvider.Response value) {
                if (!releaseTurn(turnId, callerUid)) return;
                Bundle payload = new Bundle();
                payload.putString("conversation_id", value.conversationId);
                payload.putString("text", value.text);
                payload.putString("model", model);
                reply(request, MESSAGE_RESULT, payload);
            }

            @Override public void onError(String error) {
                if (!releaseTurn(turnId, callerUid)) return;
                replyError(request, error);
            }
        });
    }

    private boolean releaseTurn(int turnId, int callerUid) {
        if (!activeTurnId.compareAndSet(turnId, -turnId)) return false;
        activeCallerUid.compareAndSet(callerUid, -1);
        activeTurnId.set(0);
        return true;
    }

    private void replyError(Message request, String error) {
        Bundle payload = new Bundle();
        payload.putString("error", error == null ? "Provider request failed" : error);
        reply(request, ERROR_RESULT, payload);
    }

    private void reply(Message request, int what, Bundle data) {
        if (request.replyTo == null) return;
        Message response = Message.obtain(null, what, request.arg1, 0);
        response.setData(data);
        try {
            request.replyTo.send(response);
        } catch (RemoteException ignored) {
            // The caller disconnected; provider cleanup remains independent.
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @Override public void onDestroy() {
        antigravity.close();
        codex.close();
        super.onDestroy();
    }
}
