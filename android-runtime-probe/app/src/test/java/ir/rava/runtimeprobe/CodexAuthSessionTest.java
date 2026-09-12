package ir.rava.runtimeprobe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class CodexAuthSessionTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void keepsDeviceLoginAliveAndSendsCancelWithReturnedLoginId() throws Exception {
        File transcript = temporary.newFile("requests.jsonl");
        File server = temporary.newFile("fake-codex-server.sh");
        Files.write(server.toPath(), ("#!/bin/sh\n"
                + "IFS= read -r line; printf '%s\\n' \"$line\" >> \"$1\"\n"
                + "printf '%s\\n' '{\"id\":1,\"result\":{}}'\n"
                + "IFS= read -r line; printf '%s\\n' \"$line\" >> \"$1\"\n"
                + "IFS= read -r line; printf '%s\\n' \"$line\" >> \"$1\"\n"
                + "printf '%s\\n' '{\"id\":3,\"result\":{\"type\":\"chatgptDeviceCode\",\"loginId\":\"login-1\",\"verificationUrl\":\"https://auth.openai.com/codex/device\",\"userCode\":\"ABCD-1234\"}}'\n"
                + "IFS= read -r line; printf '%s\\n' \"$line\" >> \"$1\"\n"
                + "printf '%s\\n' '{\"id\":4,\"result\":{\"status\":\"canceled\"}}'\n"
                ).getBytes(StandardCharsets.UTF_8));
        assertTrue(server.setExecutable(true));

        CountDownLatch deviceCode = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        CodexAuthSession session = new CodexAuthSession(
                new ProcessBuilder(server.getAbsolutePath(), transcript.getAbsolutePath()),
                CodexAuthSession.Mode.DEVICE_CODE_LOGIN,
                new ListenerAdapter() {
                    @Override
                    public void onDeviceCode(String verificationUrl, String userCode) {
                        deviceCode.countDown();
                    }

                    @Override
                    public void onStopped(int exitCode) {
                        stopped.countDown();
                    }
                });
        session.start();
        assertTrue(deviceCode.await(5, TimeUnit.SECONDS));
        session.cancelLogin();
        assertTrue(stopped.await(5, TimeUnit.SECONDS));

        List<String> requests = Files.readAllLines(transcript.toPath(), StandardCharsets.UTF_8);
        assertEquals("initialize", new JSONObject(requests.get(0)).getString("method"));
        assertEquals("initialized", new JSONObject(requests.get(1)).getString("method"));
        assertEquals("account/login/start", new JSONObject(requests.get(2)).getString("method"));
        JSONObject cancel = new JSONObject(requests.get(3));
        assertEquals("account/login/cancel", cancel.getString("method"));
        assertEquals("login-1", cancel.getJSONObject("params").getString("loginId"));
    }

    private static class ListenerAdapter implements CodexAuthSession.Listener {
        @Override public void onStatus(String status) {}
        @Override public void onAccount(CodexAuthProtocol.Message account) {}
        @Override public void onDeviceCode(String verificationUrl, String userCode) {}
        @Override public void onLoginCompleted(boolean success, String error) {}
        @Override public void onDiagnostic(String diagnostic) {}
        @Override public void onStopped(int exitCode) {}
        @Override public void onError(String error) {}
    }
}
