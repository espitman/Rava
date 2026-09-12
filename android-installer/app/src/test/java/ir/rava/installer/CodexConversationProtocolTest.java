package ir.rava.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public final class CodexConversationProtocolTest {
    @Test public void parsesEveryReturnedModel() throws Exception {
        CodexConversationProtocol.Message message = CodexConversationProtocol.parse(
                "{\"id\":10,\"result\":{\"data\":["
                        + "{\"model\":\"gpt-6-astra\",\"isDefault\":true},"
                        + "{\"model\":\"gpt-5.6-sol\",\"isDefault\":false}]}}" );
        assertEquals(2, message.models.size());
        assertEquals("gpt-6-astra", message.model);
    }

    @Test public void newThreadPinsModelAndChatOnlyPolicy() throws Exception {
        JSONObject request = new JSONObject(CodexConversationProtocol.threadStartRequest(
                "/private/empty", "gpt-5.6-sol"));
        JSONObject params = request.getJSONObject("params");
        assertEquals("gpt-5.6-sol", params.getString("model"));
        assertEquals("never", params.getString("approvalPolicy"));
        assertEquals("read-only", params.getString("sandbox"));
        assertTrue(params.getString("developerInstructions").contains("do not use tools"));
    }

    @Test public void failsClosedOnCommandAndUnknownItemTypes() throws Exception {
        CodexConversationProtocol.Message command = CodexConversationProtocol.parse(
                "{\"method\":\"item/started\",\"params\":{\"item\":{"
                        + "\"type\":\"commandExecution\"}}}");
        CodexConversationProtocol.Message futureTool = CodexConversationProtocol.parse(
                "{\"method\":\"item/completed\",\"params\":{\"item\":{"
                        + "\"type\":\"futureLocalTool\"}}}");
        assertEquals(CodexConversationProtocol.Kind.LOCAL_ACTION_BLOCKED, command.kind);
        assertEquals(CodexConversationProtocol.Kind.LOCAL_ACTION_BLOCKED, futureTool.kind);
    }

    @Test public void permitsNonToolConversationItems() throws Exception {
        CodexConversationProtocol.Message reasoning = CodexConversationProtocol.parse(
                "{\"method\":\"item/started\",\"params\":{\"item\":{"
                        + "\"type\":\"reasoning\"}}}");
        assertEquals(CodexConversationProtocol.Kind.OTHER, reasoning.kind);
    }
}
