package ir.rava.runtimeprobe;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;

/** Pinned JSONL auth messages for codex-app-server 0.154.0. */
final class CodexAuthProtocol {
    static final int INITIALIZE_ID = 1;
    static final int ACCOUNT_READ_ID = 2;
    static final int DEVICE_LOGIN_ID = 3;
    static final int CANCEL_LOGIN_ID = 4;
    static final int POST_LOGIN_ACCOUNT_READ_ID = 5;

    enum Kind {
        INITIALIZED,
        ACCOUNT,
        DEVICE_CODE,
        LOGIN_COMPLETED,
        ACCOUNT_UPDATED,
        LOGIN_CANCELED,
        ERROR,
        OTHER
    }

    static final class Message {
        final Kind kind;
        final String loginId;
        final String verificationUrl;
        final String userCode;
        final String accountType;
        final String email;
        final String planType;
        final String authMode;
        final String error;
        final String cancelStatus;
        final boolean requiresOpenaiAuth;
        final boolean success;

        private Message(
                Kind kind,
                String loginId,
                String verificationUrl,
                String userCode,
                String accountType,
                String email,
                String planType,
                String authMode,
                String error,
                String cancelStatus,
                boolean requiresOpenaiAuth,
                boolean success) {
            this.kind = kind;
            this.loginId = loginId;
            this.verificationUrl = verificationUrl;
            this.userCode = userCode;
            this.accountType = accountType;
            this.email = email;
            this.planType = planType;
            this.authMode = authMode;
            this.error = error;
            this.cancelStatus = cancelStatus;
            this.requiresOpenaiAuth = requiresOpenaiAuth;
            this.success = success;
        }

        static Message simple(Kind kind) {
            return new Message(kind, null, null, null, null, null, null, null,
                    null, null, false, false);
        }
    }

    private CodexAuthProtocol() {}

    static String initializeRequest() throws JSONException {
        JSONObject clientInfo = new JSONObject()
                .put("name", "rava_runtime_probe")
                .put("title", "Rava Runtime Probe")
                .put("version", "0.1.0");
        JSONObject params = new JSONObject()
                .put("clientInfo", clientInfo)
                .put("capabilities", new JSONObject().put("experimentalApi", false));
        return request("initialize", INITIALIZE_ID, params);
    }

    static String initializedNotification() throws JSONException {
        return new JSONObject().put("method", "initialized").toString();
    }

    static String accountReadRequest(int id) throws JSONException {
        return request("account/read", id, new JSONObject().put("refreshToken", false));
    }

    static String deviceCodeLoginRequest() throws JSONException {
        return request("account/login/start", DEVICE_LOGIN_ID,
                new JSONObject().put("type", "chatgptDeviceCode"));
    }

    static String cancelLoginRequest(String loginId) throws JSONException {
        if (loginId == null || loginId.isBlank()) {
            throw new IllegalArgumentException("loginId is required");
        }
        return request("account/login/cancel", CANCEL_LOGIN_ID,
                new JSONObject().put("loginId", loginId));
    }

    private static String request(String method, int id, JSONObject params) throws JSONException {
        return new JSONObject().put("method", method).put("id", id).put("params", params)
                .toString();
    }

    static Message parse(String line) throws JSONException {
        JSONObject root = new JSONObject(line);
        if (root.has("error")) {
            JSONObject rpcError = root.optJSONObject("error");
            String error = rpcError == null ? String.valueOf(root.opt("error"))
                    : rpcError.optString("message", "Unknown app-server error");
            return new Message(Kind.ERROR, null, null, null, null, null, null,
                    null, error, null, false, false);
        }

        String method = root.optString("method", "");
        if ("account/login/completed".equals(method)) {
            JSONObject params = root.getJSONObject("params");
            return new Message(Kind.LOGIN_COMPLETED,
                    nullableString(params, "loginId"), null, null, null, null, null, null,
                    nullableString(params, "error"), null, false,
                    params.optBoolean("success", false));
        }
        if ("account/updated".equals(method)) {
            JSONObject params = root.getJSONObject("params");
            return new Message(Kind.ACCOUNT_UPDATED, null, null, null, null, null,
                    nullableString(params, "planType"), nullableString(params, "authMode"),
                    null, null, false, false);
        }

        int id = root.optInt("id", -1);
        JSONObject result = root.optJSONObject("result");
        if (id == INITIALIZE_ID && result != null) {
            return Message.simple(Kind.INITIALIZED);
        }
        if ((id == ACCOUNT_READ_ID || id == POST_LOGIN_ACCOUNT_READ_ID) && result != null) {
            JSONObject account = result.optJSONObject("account");
            return new Message(Kind.ACCOUNT, null, null, null,
                    account == null ? null : nullableString(account, "type"),
                    account == null ? null : nullableString(account, "email"),
                    account == null ? null : nullableString(account, "planType"),
                    null, null, null, result.optBoolean("requiresOpenaiAuth", false), false);
        }
        if (id == DEVICE_LOGIN_ID && result != null
                && "chatgptDeviceCode".equals(result.optString("type"))) {
            return new Message(Kind.DEVICE_CODE,
                    requiredString(result, "loginId"),
                    requiredString(result, "verificationUrl"),
                    requiredString(result, "userCode"),
                    null, null, null, null, null, null, false, false);
        }
        if (id == CANCEL_LOGIN_ID && result != null) {
            return new Message(Kind.LOGIN_CANCELED, null, null, null, null, null,
                    null, null, null, nullableString(result, "status"), false, false);
        }
        return Message.simple(Kind.OTHER);
    }

    static boolean isTrustedVerificationUrl(String value) {
        if (value == null) {
            return false;
        }
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "auth.openai.com".equalsIgnoreCase(uri.getHost());
        } catch (URISyntaxException ignored) {
            return false;
        }
    }

    private static String requiredString(JSONObject object, String key) throws JSONException {
        String value = object.getString(key);
        if (value.isBlank()) {
            throw new JSONException(key + " must not be blank");
        }
        return value;
    }

    private static String nullableString(JSONObject object, String key) {
        return object.isNull(key) ? null : object.optString(key, null);
    }
}
