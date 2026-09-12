package ir.rava.runtimeprobe;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Pinned conversation messages for codex-app-server 0.154.0. */
final class CodexConversationProtocol {
    static final int INITIALIZE_ID = 1;
    static final int MODEL_LIST_ID = 10;
    static final int THREAD_ID = 11;
    static final int TURN_START_ID = 12;
    static final int TURN_INTERRUPT_ID = 13;

    enum Kind {
        INITIALIZED, MODELS, THREAD_READY, TURN_STARTED, ASSISTANT_DELTA,
        ASSISTANT_FINAL, TURN_COMPLETED, TURN_INTERRUPTED, APPROVAL_REQUEST,
        UNSUPPORTED_SERVER_REQUEST, ERROR, OTHER
    }

    static final class Message {
        final Kind kind;
        final Object serverRequestId;
        final String threadId;
        final String turnId;
        final String text;
        final String status;
        final String error;
        final String model;
        final int modelCount;

        Message(Kind kind, Object serverRequestId, String threadId, String turnId,
                String text, String status, String error, String model, int modelCount) {
            this.kind = kind;
            this.serverRequestId = serverRequestId;
            this.threadId = threadId;
            this.turnId = turnId;
            this.text = text;
            this.status = status;
            this.error = error;
            this.model = model;
            this.modelCount = modelCount;
        }

        static Message simple(Kind kind) {
            return new Message(kind, null, null, null, null, null, null, null, 0);
        }
    }

    private CodexConversationProtocol() {}

    static String initializeRequest() throws JSONException {
        return CodexAuthProtocol.initializeRequest();
    }

    static String initializedNotification() throws JSONException {
        return CodexAuthProtocol.initializedNotification();
    }

    static String modelListRequest() throws JSONException {
        return request("model/list", MODEL_LIST_ID,
                new JSONObject().put("limit", 100).put("includeHidden", false));
    }

    static String threadStartRequest(String cwd) throws JSONException {
        JSONObject params = chatOnlySettings(cwd)
                .put("ephemeral", false)
                .put("developerInstructions", "Chat-only Android probe. Reply directly and do not use tools.");
        return request("thread/start", THREAD_ID, params);
    }

    static String threadResumeRequest(String threadId, String cwd) throws JSONException {
        requireId(threadId, "threadId");
        JSONObject params = chatOnlySettings(cwd)
                .put("threadId", threadId)
                .put("developerInstructions", "Chat-only Android probe. Reply directly and do not use tools.");
        return request("thread/resume", THREAD_ID, params);
    }

    static String turnStartRequest(String threadId, String prompt) throws JSONException {
        requireId(threadId, "threadId");
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("prompt is required");
        JSONObject input = new JSONObject().put("type", "text").put("text", prompt)
                .put("textElements", new JSONArray());
        JSONObject params = new JSONObject().put("threadId", threadId)
                .put("input", new JSONArray().put(input));
        return request("turn/start", TURN_START_ID, params);
    }

    static String turnInterruptRequest(String threadId, String turnId) throws JSONException {
        requireId(threadId, "threadId");
        requireId(turnId, "turnId");
        return request("turn/interrupt", TURN_INTERRUPT_ID,
                new JSONObject().put("threadId", threadId).put("turnId", turnId));
    }

    static String denyServerRequest(Message request) throws JSONException {
        if (request.serverRequestId == null) throw new IllegalArgumentException("request id required");
        JSONObject root = new JSONObject().put("id", request.serverRequestId);
        if (request.kind == Kind.APPROVAL_REQUEST) {
            return root.put("result", new JSONObject().put("decision", "decline")).toString();
        }
        return root.put("error", new JSONObject().put("code", -32601)
                .put("message", "Disabled by the chat-only profile")).toString();
    }

    static Message parse(String line) throws JSONException {
        JSONObject root = new JSONObject(line);
        if (root.has("error")) {
            JSONObject value = root.optJSONObject("error");
            String error = value == null ? String.valueOf(root.opt("error"))
                    : value.optString("message", "Unknown app-server error");
            return new Message(Kind.ERROR, null, null, null, null, null, error, null, 0);
        }

        String method = root.optString("method", "");
        if (root.has("id") && !method.isEmpty()) {
            Object id = root.get("id");
            if ("item/commandExecution/requestApproval".equals(method)
                    || "item/fileChange/requestApproval".equals(method)) {
                return new Message(Kind.APPROVAL_REQUEST, id, null, null,
                        null, null, null, null, 0);
            }
            return new Message(Kind.UNSUPPORTED_SERVER_REQUEST, id, null, null,
                    null, null, null, null, 0);
        }

        JSONObject params = root.optJSONObject("params");
        if ("item/agentMessage/delta".equals(method) && params != null) {
            return new Message(Kind.ASSISTANT_DELTA, null,
                    nullable(params, "threadId"), nullable(params, "turnId"),
                    params.optString("delta", ""), null, null, null, 0);
        }
        if ("item/completed".equals(method) && params != null) {
            JSONObject item = params.optJSONObject("item");
            if (item != null && "agentMessage".equals(item.optString("type"))) {
                return new Message(Kind.ASSISTANT_FINAL, null,
                        nullable(params, "threadId"), nullable(params, "turnId"),
                        item.optString("text", ""), null, null, null, 0);
            }
        }
        if ("turn/completed".equals(method) && params != null) {
            JSONObject turn = params.getJSONObject("turn");
            JSONObject failure = turn.optJSONObject("error");
            return new Message(Kind.TURN_COMPLETED, null, nullable(params, "threadId"),
                    nullable(turn, "id"), null, nullable(turn, "status"),
                    failure == null ? null : nullable(failure, "message"), null, 0);
        }

        int id = root.optInt("id", -1);
        JSONObject result = root.optJSONObject("result");
        if (id == INITIALIZE_ID && result != null) return Message.simple(Kind.INITIALIZED);
        if (id == MODEL_LIST_ID && result != null) {
            JSONArray data = result.optJSONArray("data");
            String defaultModel = null;
            if (data != null) for (int index = 0; index < data.length(); index++) {
                JSONObject model = data.optJSONObject(index);
                if (model != null && model.optBoolean("isDefault")) {
                    defaultModel = nullable(model, "model");
                    break;
                }
            }
            return new Message(Kind.MODELS, null, null, null, null, null, null,
                    defaultModel, data == null ? 0 : data.length());
        }
        if (id == THREAD_ID && result != null) {
            JSONObject thread = result.getJSONObject("thread");
            return new Message(Kind.THREAD_READY, null, require(thread, "id"), null,
                    null, null, null, nullable(result, "model"), 0);
        }
        if (id == TURN_START_ID && result != null) {
            JSONObject turn = result.getJSONObject("turn");
            return new Message(Kind.TURN_STARTED, null, null, require(turn, "id"),
                    null, nullable(turn, "status"), null, null, 0);
        }
        if (id == TURN_INTERRUPT_ID && result != null) return Message.simple(Kind.TURN_INTERRUPTED);
        return Message.simple(Kind.OTHER);
    }

    private static JSONObject chatOnlySettings(String cwd) throws JSONException {
        if (cwd == null || cwd.isBlank()) throw new IllegalArgumentException("cwd is required");
        return new JSONObject().put("cwd", cwd).put("approvalPolicy", "never")
                .put("sandbox", "read-only");
    }

    private static String request(String method, int id, JSONObject params) throws JSONException {
        return new JSONObject().put("method", method).put("id", id).put("params", params).toString();
    }

    private static void requireId(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }

    private static String require(JSONObject value, String key) throws JSONException {
        String result = value.getString(key);
        if (result.isBlank()) throw new JSONException(key + " must not be blank");
        return result;
    }

    private static String nullable(JSONObject value, String key) {
        return value.isNull(key) ? null : value.optString(key, null);
    }
}
