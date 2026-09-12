# Rava Android engine client

Rava exports an Android `Messenger` service rather than a TCP port. Access is
protected by `ir.rava.installer.permission.USE_ENGINE` with Android's
`signature` protection level, and the service checks each Messenger message's
sending UID against Rava's signing certificate. A client distributed with a different
certificate cannot bind.

Use the `rava-client-release.aar` produced by the `:rava-client` Gradle module.
The library manifest contributes the permission and package visibility query.

```java
private RavaClient rava;

@Override protected void onStart() {
    super.onStart();
    rava = new RavaClient(this);
    if (!rava.bind()) {
        // Rava is absent or this app is not authorized.
    }
}

private void loadModels() {
    rava.listModels(new RavaClient.Callback() {
        @Override public void onResult(Bundle result) {
            String modelsJson = result.getString("models_json", "[]");
            // Each item: id, provider, model, name.
        }

        @Override public void onError(String error) {
            // Show a sign-in, setup, network, or provider error.
        }
    });
}

private void ask(String model, String conversationId, String prompt) {
    rava.sendMessage(model, conversationId, prompt, new RavaClient.Callback() {
        @Override public void onResult(Bundle result) {
            String nextConversationId = result.getString("conversation_id");
            String actualModel = result.getString("model");
            String text = result.getString("text", "");
        }

        @Override public void onError(String error) {
            // The request did not produce a response.
        }
    });
}

@Override protected void onStop() {
    rava.close();
    super.onStop();
}
```

`RavaClient.cancel()` requests cancellation of the active provider operation.
The service permits one active turn at a time. A second send receives an error
until the active turn completes or is canceled. Results are delivered once a
provider turn completes; this v1 client contract does not stream partial text.
Only the client UID that owns the active turn may cancel it.

For clients that implement Messenger directly, bind an explicit package intent
to action `ir.rava.installer.action.BIND_ENGINE` and set `replyTo` on every
request. Put a caller-generated request ID in `arg1`.

| Request | `what` | Bundle fields |
|---|---:|---|
| List models | 1 | none |
| Send | 2 | `model`, `prompt`, optional `conversation_id` |
| Cancel | 3 | none |

| Response | `what` | Bundle fields |
|---|---:|---|
| Models | 101 | `models_json` |
| Message | 102 | `conversation_id`, `model`, `text` |
| Error | 199 | `error` |
