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
