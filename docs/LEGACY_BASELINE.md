# Legacy Android Baseline

This baseline was recorded before the standalone runtime migration.

## Package and build identity

- Module: `android-installer/app`
- Namespace and application ID: `ir.rava.installer`
- Minimum SDK: 26
- Target SDK: 36
- Compile SDK: 35
- Version code: 12
- Version name: 0.6.0
- Signing: no release signing configuration is declared in the repository. Local
  debug builds use the Android Gradle Plugin's generated debug signing setup.

Keeping the application ID preserves access to the existing app-private data on
an in-place, compatibly signed upgrade. A differently signed APK cannot upgrade
the installed build and cannot read its sandbox.

## Archive storage schema

The legacy UI stores its archive as a JSON string in Android SharedPreferences:

- Preferences file: `chat_archive`
- Key: `chats`
- Root value: JSON array, newest conversation first

Each conversation object contains:

```json
{
  "id": "local UUID string",
  "title": "first user message, truncated for display",
  "model": "namespaced legacy model ID",
  "conversation_id": "legacy engine/provider conversation ID or empty string",
  "updated_at": 0,
  "messages": [
    {
      "role": "user or assistant",
      "content": "message text including supported Markdown image links"
    }
  ]
}
```

The standalone migration must treat `conversation_id` as legacy provider state.
It may preserve the transcript for reading, but must not present that identifier
as resumable through Gemini CLI or Codex app-server without explicit proof.

## Other app-private state

The legacy setup bridge stores command progress/results in the
`command_results` SharedPreferences file. This data has no standalone-runtime
meaning and does not need to migrate after the Termux setup UI is removed.

## Verified baseline

- Python suite: 21 tests passed.
- Android debug assembly and lint passed using the local ARM64 JDK 17 toolchain.
- The initially selected x64 JRE lacked `javac`; it was not a source-code failure.

No physical Android device was connected while this baseline was recorded.
