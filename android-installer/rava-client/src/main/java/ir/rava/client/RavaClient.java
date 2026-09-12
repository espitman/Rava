package ir.rava.client;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Small Messenger client for Rava's signature-protected local engine service. */
public final class RavaClient implements AutoCloseable {
    private static final int LIST_MODELS = 1;
    private static final int SEND_MESSAGE = 2;
    private static final int CANCEL = 3;
    private static final int MODELS_RESULT = 101;
    private static final int MESSAGE_RESULT = 102;
    private static final int ERROR_RESULT = 199;

    public interface Callback {
        void onResult(Bundle result);
        void onError(String error);
    }

    private final Context context;
    private final AtomicInteger nextId = new AtomicInteger();
    private final Map<Integer, Callback> callbacks = new ConcurrentHashMap<>();
    private final Messenger incoming = new Messenger(
            new Handler(Looper.getMainLooper(), this::handle));
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            remote = new Messenger(service);
        }
        @Override public void onServiceDisconnected(ComponentName name) { remote = null; }
    };
    private volatile Messenger remote;
    private boolean bound;

    public RavaClient(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean bind() {
        if (bound) return true;
        Intent intent = new Intent("ir.rava.installer.action.BIND_ENGINE")
                .setPackage("ir.rava.installer");
        bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        return bound;
    }

    public int listModels(Callback callback) {
        return send(LIST_MODELS, new Bundle(), callback);
    }

    public int sendMessage(String model, String conversationId, String prompt,
            Callback callback) {
        Bundle data = new Bundle();
        data.putString("model", model);
        data.putString("conversation_id", conversationId);
        data.putString("prompt", prompt);
        return send(SEND_MESSAGE, data, callback);
    }

    public void cancel() {
        send(CANCEL, new Bundle(), null);
    }

    private int send(int what, Bundle data, Callback callback) {
        Messenger target = remote;
        int id = nextId.incrementAndGet();
        if (target == null) {
            if (callback != null) callback.onError("Rava engine is not connected");
            return id;
        }
        if (callback != null) callbacks.put(id, callback);
        Message message = Message.obtain(null, what, id, 0);
        message.replyTo = incoming;
        message.setData(data);
        try {
            target.send(message);
        } catch (RemoteException error) {
            callbacks.remove(id);
            if (callback != null) callback.onError("Rava engine disconnected");
        }
        return id;
    }

    private boolean handle(Message message) {
        Callback callback = callbacks.remove(message.arg1);
        if (callback == null) return true;
        if (message.what == MODELS_RESULT || message.what == MESSAGE_RESULT) {
            callback.onResult(message.getData());
        } else if (message.what == ERROR_RESULT) {
            callback.onError(message.getData().getString("error", "Rava request failed"));
        } else {
            callback.onError("Unknown Rava response");
        }
        return true;
    }

    @Override public void close() {
        callbacks.clear();
        if (bound) context.unbindService(connection);
        bound = false;
        remote = null;
    }
}
