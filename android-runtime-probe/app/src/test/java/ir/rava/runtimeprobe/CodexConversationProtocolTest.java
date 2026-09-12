package ir.rava.runtimeprobe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public final class CodexConversationProtocolTest {
    @Test
    public void emitsPinnedChatOnlyRequests() throws Exception {
        JSONObject start = new JSONObject(CodexConversationProtocol.threadStartRequest("/private"));
        JSONObject params = start.getJSONObject("params");
        assertEquals("thread/start", start.getString("method"));
        assertEquals("never", params.getString("approvalPolicy"));
        assertEquals("read-only", params.getString("sandbox"));
        assertEquals(false, params.getBoolean("ephemeral"));

        JSONObject turn = new JSONObject(CodexConversationProtocol.turnStartRequest("t1", "سلام"));
        JSONObject input = turn.getJSONObject("params").getJSONArray("input").getJSONObject(0);
        assertEquals("text", input.getString("type"));
        assertEquals("سلام", input.getString("text"));

        JSONObject resume = new JSONObject(
                CodexConversationProtocol.threadResumeRequest("t1", "/private"));
        assertEquals("thread/resume", resume.getString("method"));
        assertEquals("t1", resume.getJSONObject("params").getString("threadId"));
    }

    @Test
    public void separatesAssistantEventsAndTurnCompletion() throws Exception {
        CodexConversationProtocol.Message delta = CodexConversationProtocol.parse(
                "{\"method\":\"item/agentMessage/delta\",\"params\":{"
                        + "\"threadId\":\"t1\",\"turnId\":\"u1\",\"itemId\":\"i1\","
                        + "\"delta\":\"سلام\"}}");
        assertEquals(CodexConversationProtocol.Kind.ASSISTANT_DELTA, delta.kind);
        assertEquals("سلام", delta.text);

        CodexConversationProtocol.Message item = CodexConversationProtocol.parse(
                "{\"method\":\"item/completed\",\"params\":{"
                        + "\"threadId\":\"t1\",\"turnId\":\"u1\","
                        + "\"item\":{\"type\":\"agentMessage\",\"id\":\"i1\","
                        + "\"text\":\"سلام راوا\"}}}");
        assertEquals(CodexConversationProtocol.Kind.ASSISTANT_FINAL, item.kind);
        assertEquals("سلام راوا", item.text);

        CodexConversationProtocol.Message done = CodexConversationProtocol.parse(
                "{\"method\":\"turn/completed\",\"params\":{"
                        + "\"threadId\":\"t1\",\"turn\":{\"id\":\"u1\","
                        + "\"status\":\"completed\",\"items\":[],\"error\":null}}}");
        assertEquals(CodexConversationProtocol.Kind.TURN_COMPLETED, done.kind);
        assertEquals("completed", done.status);

        CodexConversationProtocol.Message reasoning = CodexConversationProtocol.parse(
                "{\"method\":\"item/completed\",\"params\":{"
                        + "\"threadId\":\"t1\",\"turnId\":\"u1\","
                        + "\"item\":{\"type\":\"reasoning\",\"id\":\"r1\","
                        + "\"summary\":[\"hidden\"],\"content\":[]}}}");
        assertEquals(CodexConversationProtocol.Kind.OTHER, reasoning.kind);

        CodexConversationProtocol.Message failed = CodexConversationProtocol.parse(
                "{\"method\":\"turn/completed\",\"params\":{"
                        + "\"threadId\":\"t1\",\"turn\":{\"id\":\"u2\","
                        + "\"status\":\"failed\",\"items\":[],"
                        + "\"error\":{\"message\":\"quota\"}}}}}");
        assertEquals("failed", failed.status);
        assertEquals("quota", failed.error);
    }

    @Test
    public void deniesApprovalRequestsAndEmitsInterrupt() throws Exception {
        CodexConversationProtocol.Message approval = CodexConversationProtocol.parse(
                "{\"id\":\"server-1\",\"method\":"
                        + "\"item/commandExecution/requestApproval\",\"params\":{}}");
        JSONObject denial = new JSONObject(CodexConversationProtocol.denyServerRequest(approval));
        assertEquals("server-1", denial.getString("id"));
        assertEquals("decline", denial.getJSONObject("result").getString("decision"));

        JSONObject interrupt = new JSONObject(
                CodexConversationProtocol.turnInterruptRequest("t1", "u1"));
        assertEquals("turn/interrupt", interrupt.getString("method"));
        assertTrue(interrupt.getJSONObject("params").has("turnId"));
    }
}
