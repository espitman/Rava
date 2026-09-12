package ir.rava.installer;

import java.util.List;

interface ChatProvider extends AutoCloseable {
    final class Request {
        final String modelId;
        final String conversationId;
        final String prompt;

        Request(String modelId, String conversationId, String prompt) {
            this.modelId = modelId;
            this.conversationId = conversationId;
            this.prompt = prompt;
        }

        String providerPrompt() {
            return "Rava client capability: Only claim that an image is shown or attached if "
                    + "this response actually contains a valid HTTPS Markdown image in "
                    + "![alt](https://...) format. If you cannot produce one, clearly say that "
                    + "image generation is unavailable in this chat. Never describe an imagined "
                    + "image as if it was sent.\n\nUser message:\n" + prompt;
        }
    }

    final class Response {
        final String conversationId;
        final String text;

        Response(String conversationId, String text) {
            this.conversationId = conversationId;
            this.text = text;
        }
    }

    interface Result<T> {
        void onSuccess(T value);
        void onError(String message);
    }

    String id();
    void listModels(Result<List<ProviderModel>> result);
    void send(Request request, Result<Response> result);
    void cancel();
    @Override void close();
}
