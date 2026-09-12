package ir.rava.installer;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class AntigravityProtocol {
    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9._-]*");

    private AntigravityProtocol() {}

    static List<ProviderModel> parseModels(String stdout) {
        List<ProviderModel> models = new ArrayList<>();
        for (String raw : stdout.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s+", 2);
            if (!SLUG.matcher(parts[0]).matches()) continue;
            String label = parts.length == 2 ? parts[1].trim() : parts[0];
            models.add(new ProviderModel("antigravity", parts[0], label));
        }
        return models;
    }

    static ChatProvider.Response parseResponse(String stdout, String expectedModel)
            throws JSONException {
        JSONObject value = null;
        boolean toolEvent = false;
        String actualModel = null;
        for (String raw : stdout.split("\\R")) {
            if (raw.isBlank()) continue;
            JSONObject event = new JSONObject(raw);
            if ("init".equals(event.optString("event"))) {
                JSONObject init = event.optJSONObject("init");
                if (init != null) actualModel = init.optString("model", null);
            } else if ("step_update".equals(event.optString("event"))) {
                JSONObject step = event.optJSONObject("step_update");
                if (step != null && "tool".equals(step.optString("step_type"))) {
                    toolEvent = true;
                }
            } else if ("result".equals(event.optString("event"))) {
                value = event.optJSONObject("result");
            }
        }
        if (toolEvent) throw new JSONException("Rava blocked a provider tool request");
        if (!expectedModel.equals(actualModel)) {
            throw new JSONException("Antigravity did not confirm the selected model");
        }
        if (value == null) throw new JSONException("Missing Antigravity result event");
        String status = value.optString("status", "");
        if (!"SUCCESS".equals(status)) {
            String error = value.optString("error", "Antigravity request failed").trim();
            throw new JSONException(error.isEmpty() ? "Antigravity request failed" : error);
        }
        String conversationId = value.optString("conversation_id", "").trim();
        if (conversationId.isEmpty()) throw new JSONException("Missing conversation ID");
        return new ChatProvider.Response(conversationId, value.optString("response", ""));
    }

    static boolean isSafeIdentifier(String value) {
        return value != null && SLUG.matcher(value).matches();
    }
}
