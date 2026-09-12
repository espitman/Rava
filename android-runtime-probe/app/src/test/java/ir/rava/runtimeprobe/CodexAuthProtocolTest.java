package ir.rava.runtimeprobe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public final class CodexAuthProtocolTest {
    @Test
    public void emitsPinnedDeviceCodeRequests() throws Exception {
        JSONObject initialize = new JSONObject(CodexAuthProtocol.initializeRequest());
        assertEquals("initialize", initialize.getString("method"));
        assertEquals(1, initialize.getInt("id"));
        assertFalse(initialize.getJSONObject("params").getJSONObject("capabilities")
                .getBoolean("experimentalApi"));

        JSONObject login = new JSONObject(CodexAuthProtocol.deviceCodeLoginRequest());
        assertEquals("account/login/start", login.getString("method"));
        assertEquals("chatgptDeviceCode", login.getJSONObject("params").getString("type"));

        JSONObject account = new JSONObject(CodexAuthProtocol.accountReadRequest(2));
        assertEquals("account/read", account.getString("method"));
        assertFalse(account.getJSONObject("params").getBoolean("refreshToken"));
    }

    @Test
    public void parsesDeviceCodeWithoutAcceptingAnUntrustedBrowserUrl() throws Exception {
        CodexAuthProtocol.Message message = CodexAuthProtocol.parse(
                "{\"id\":3,\"result\":{\"type\":\"chatgptDeviceCode\","
                        + "\"loginId\":\"login-1\","
                        + "\"verificationUrl\":\"https://auth.openai.com/codex/device\","
                        + "\"userCode\":\"ABCD-1234\"}}");
        assertEquals(CodexAuthProtocol.Kind.DEVICE_CODE, message.kind);
        assertEquals("login-1", message.loginId);
        assertEquals("ABCD-1234", message.userCode);
        assertTrue(CodexAuthProtocol.isTrustedVerificationUrl(message.verificationUrl));
        assertFalse(CodexAuthProtocol.isTrustedVerificationUrl(
                "https://auth.openai.com.attacker.example/codex/device"));
        assertFalse(CodexAuthProtocol.isTrustedVerificationUrl("http://auth.openai.com/"));
    }

    @Test
    public void parsesAccountAndCompletionNotifications() throws Exception {
        CodexAuthProtocol.Message account = CodexAuthProtocol.parse(
                "{\"id\":2,\"result\":{\"account\":null,"
                        + "\"requiresOpenaiAuth\":true}}");
        assertEquals(CodexAuthProtocol.Kind.ACCOUNT, account.kind);
        assertTrue(account.requiresOpenaiAuth);
        assertNull(account.accountType);

        CodexAuthProtocol.Message completed = CodexAuthProtocol.parse(
                "{\"method\":\"account/login/completed\",\"params\":{"
                        + "\"loginId\":\"login-1\",\"success\":true,\"error\":null}}");
        assertEquals(CodexAuthProtocol.Kind.LOGIN_COMPLETED, completed.kind);
        assertTrue(completed.success);
        assertNull(completed.error);

        CodexAuthProtocol.Message updated = CodexAuthProtocol.parse(
                "{\"method\":\"account/updated\",\"params\":{"
                        + "\"authMode\":\"chatgpt\",\"planType\":\"plus\"}}");
        assertEquals(CodexAuthProtocol.Kind.ACCOUNT_UPDATED, updated.kind);
        assertEquals("chatgpt", updated.authMode);
        assertEquals("plus", updated.planType);
    }
}
