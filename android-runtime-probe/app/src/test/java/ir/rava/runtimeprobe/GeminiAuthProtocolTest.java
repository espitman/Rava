package ir.rava.runtimeprobe;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class GeminiAuthProtocolTest {
    @Test
    public void acceptsOfficialGoogleAuthorizationUrl() {
        assertTrue(GeminiAuthProtocol.isAllowedAuthorizationUrl(
                "https://accounts.google.com/o/oauth2/v2/auth?client_id=test&state=abc"));
    }

    @Test
    public void rejectsLookalikeAndUnsafeAuthorizationUrls() {
        assertFalse(GeminiAuthProtocol.isAllowedAuthorizationUrl(
                "https://accounts.google.com.example.org/oauth"));
        assertFalse(GeminiAuthProtocol.isAllowedAuthorizationUrl(
                "http://accounts.google.com/oauth"));
        assertFalse(GeminiAuthProtocol.isAllowedAuthorizationUrl(
                "https://attacker@accounts.google.com/oauth"));
    }
}
