package ir.rava.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.Test;

import java.util.List;

public final class AntigravityProtocolTest {
    @Test public void parsesModelSlugsAndLabels() {
        List<ProviderModel> models = AntigravityProtocol.parseModels(
                "gemini-3.8-flash-low Gemini 3.8 Flash (Low)\n"
                        + "claude-sonnet-4.6 Claude Sonnet 4.6\n");
        assertEquals(2, models.size());
        assertEquals("antigravity/gemini-3.8-flash-low", models.get(0).archiveId());
        assertEquals("Gemini 3.8 Flash (Low)", models.get(0).displayName);
    }

    @Test public void parsesSuccessfulJsonEnvelope() throws Exception {
        ChatProvider.Response response = AntigravityProtocol.parseResponse(
                "{\"event\":\"init\",\"conversation_id\":\"abc-123\","
                        + "\"init\":{\"model\":\"gemini-3.8-flash-low\"}}\n"
                        + "{\"event\":\"result\",\"result\":{"
                        + "\"conversation_id\":\"abc-123\",\"status\":\"SUCCESS\","
                        + "\"response\":\"سلام\"}}", "gemini-3.8-flash-low");
        assertEquals("abc-123", response.conversationId);
        assertEquals("سلام", response.text);
    }

    @Test(expected = JSONException.class)
    public void rejectsErrorEnvelope() throws Exception {
        AntigravityProtocol.parseResponse(
                "{\"event\":\"init\",\"init\":{\"model\":\"m\"}}\n"
                        + "{\"event\":\"result\",\"result\":{\"conversation_id\":\"\","
                        + "\"status\":\"ERROR\",\"error\":\"quota\"}}", "m");
    }

    @Test(expected = JSONException.class)
    public void rejectsAnyToolStepEvenWhenTheRunCompletes() throws Exception {
        AntigravityProtocol.parseResponse(
                "{\"event\":\"init\",\"init\":{\"model\":\"m\"}}\n"
                        + "{\"event\":\"step_update\",\"step_update\":{\"step_type\":\"tool\"}}\n"
                        + "{\"event\":\"result\",\"result\":{"
                        + "\"conversation_id\":\"abc\",\"status\":\"SUCCESS\","
                        + "\"response\":\"done\"}}", "m");
    }

    @Test(expected = JSONException.class)
    public void rejectsSilentModelFallback() throws Exception {
        AntigravityProtocol.parseResponse(
                "{\"event\":\"init\",\"init\":{\"model\":\"other\"}}\n"
                        + "{\"event\":\"result\",\"result\":{"
                        + "\"conversation_id\":\"abc\",\"status\":\"SUCCESS\","
                        + "\"response\":\"done\"}}", "requested");
    }

    @Test public void validatesIdentifiersBeforeShellUse() {
        assertTrue(AntigravityProtocol.isSafeIdentifier("gemini-3.8-flash-low"));
        assertTrue(AntigravityProtocol.isSafeIdentifier("32304ca1-3736-47d7-a175-c755c6646dd2"));
        assertFalse(AntigravityProtocol.isSafeIdentifier("model'; rm -rf ~"));
    }
}
