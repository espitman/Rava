# Rava Setup for Android

Rava Setup is a graphical companion which installs and controls the Termux
version of Rava Engine.

## Build

Requirements: Java 17 and Android SDK 35 or newer.

```bash
./prepare-bundle.sh
./gradlew :app:assembleDebug
```

The generated APK is located at
`app/build/outputs/apk/debug/app-debug.apk`.

`prepare-bundle.sh` packages the current Rava source tree into the APK. Runtime
directories, browser profiles, cookies, logs, virtual environments, and build
outputs are excluded.

## First-run security step

Termux rejects external commands by default. The user must copy the command
shown in step 1 into Termux once, then grant the Android permission named
**Run commands in Termux environment** to Rava Setup.

After this one-time action, the app can transfer the embedded source bundle,
install dependencies, open provider sign-in profiles, capture the Gemini
session, start the stack, and display command output.

The **Chat** destination in the bottom navigation is a small end-to-end test client. It discovers the models
reported by the local engine, lets the user select one, and keeps a provider
conversation active until **New chat** is tapped.

See the official
[Termux RUN_COMMAND documentation](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent)
for the security model and intent contract.
