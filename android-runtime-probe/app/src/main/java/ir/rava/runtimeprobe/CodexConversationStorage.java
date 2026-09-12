package ir.rava.runtimeprobe;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Stores only the nonsecret thread identity and probe result flags. */
final class CodexConversationStorage {
    private static final String NAME = "codex-conversation-state.json";

    private CodexConversationStorage() {}

    static void write(File files, String threadId, String status, boolean responseMatched)
            throws IOException, JSONException {
        if (threadId == null || threadId.isBlank()) throw new IOException("threadId is required");
        JSONObject state = new JSONObject().put("threadId", threadId)
                .put("status", status).put("responseMatched", responseMatched);
        File target = new File(files, NAME);
        try (FileOutputStream output = new FileOutputStream(target, false)) {
            output.write((state.toString() + "\n").getBytes(StandardCharsets.UTF_8));
        }
        if (!target.setReadable(false, false) || !target.setWritable(false, false)
                || !target.setReadable(true, true) || !target.setWritable(true, true)) {
            throw new IOException("Cannot restrict Codex conversation state");
        }
    }

    static String readThreadId(File files) throws IOException, JSONException {
        File target = new File(files, NAME);
        if (!target.isFile()) return null;
        byte[] data;
        try (FileInputStream input = new FileInputStream(target)) {
            data = new byte[(int) target.length()];
            int offset = 0;
            while (offset < data.length) {
                int count = input.read(data, offset, data.length - offset);
                if (count < 0) break;
                offset += count;
            }
            if (offset != data.length) throw new IOException("Incomplete Codex state read");
        }
        String threadId = new JSONObject(new String(data, StandardCharsets.UTF_8))
                .optString("threadId", null);
        return threadId == null || threadId.isBlank() ? null : threadId;
    }
}
