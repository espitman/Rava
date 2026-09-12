package ir.rava.installer;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChatProviderRequestTest {
    @Test public void providerPromptPreventsFalseImageClaimsAndKeepsUserMessage() {
        ChatProvider.Request request = new ChatProvider.Request(
                "model", null, "یه عکس فضایی بده");

        String prompt = request.providerPrompt();

        assertTrue(prompt.contains("valid HTTPS Markdown image"));
        assertTrue(prompt.contains("Never describe an imagined image as if it was sent"));
        assertTrue(prompt.endsWith("یه عکس فضایی بده"));
    }
}
