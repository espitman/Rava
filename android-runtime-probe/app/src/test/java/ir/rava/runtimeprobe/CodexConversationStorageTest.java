package ir.rava.runtimeprobe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class CodexConversationStorageTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void storesOnlyThreadAndResultFlags() throws Exception {
        File files = temporary.newFolder("files");
        CodexConversationStorage.write(files, "thread-1", "completed", true);
        File state = new File(files, "codex-conversation-state.json");
        JSONObject json = new JSONObject(
                new String(Files.readAllBytes(state.toPath()), StandardCharsets.UTF_8));
        assertEquals("thread-1", json.getString("threadId"));
        assertEquals("completed", json.getString("status"));
        assertFalse(json.has("assistantText"));
        assertFalse(json.has("email"));
        assertFalse(json.has("token"));
        assertEquals("thread-1", CodexConversationStorage.readThreadId(files));
    }
}
