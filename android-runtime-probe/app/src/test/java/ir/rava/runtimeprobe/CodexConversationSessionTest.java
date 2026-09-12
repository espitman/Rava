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
import java.util.concurrent.atomic.AtomicReference;

public final class CodexConversationSessionTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void runsPinnedConversationSequenceAndSeparatesFinalText() throws Exception {
        File transcript = temporary.newFile("conversation.jsonl");
        File server = temporary.newFile("fake-conversation.sh");
        Files.write(server.toPath(), ("#!/bin/sh\n"
                + "IFS= read -r x; printf '%s\\n' \"$x\" >> \"$1\"\n"
                + "printf '%s\\n' '{\"id\":1,\"result\":{}}'\n"
                + "IFS= read -r x; printf '%s\\n' \"$x\" >> \"$1\"\n"
                + "IFS= read -r x; printf '%s\\n' \"$x\" >> \"$1\"\n"
                + "printf '%s\\n' '{\"id\":10,\"result\":{\"data\":[{\"model\":\"gpt-test\",\"isDefault\":true}]}}'\n"
                + "IFS= read -r x; printf '%s\\n' \"$x\" >> \"$1\"\n"
                + "printf '%s\\n' '{\"id\":11,\"result\":{\"thread\":{\"id\":\"thread-1\"},\"model\":\"gpt-test\"}}'\n"
                + "IFS= read -r x; printf '%s\\n' \"$x\" >> \"$1\"\n"
                + "printf '%s\\n' '{\"id\":12,\"result\":{\"turn\":{\"id\":\"turn-1\",\"status\":\"inProgress\"}}}'\n"
                + "printf '%s\\n' '{\"method\":\"item/agentMessage/delta\",\"params\":{\"threadId\":\"thread-1\",\"turnId\":\"turn-1\",\"delta\":\"سلام \"}}'\n"
                + "printf '%s\\n' '{\"method\":\"item/completed\",\"params\":{\"threadId\":\"thread-1\",\"turnId\":\"turn-1\",\"item\":{\"type\":\"agentMessage\",\"id\":\"item-1\",\"text\":\"سلام راوا\"}}}'\n"
                + "printf '%s\\n' '{\"method\":\"turn/completed\",\"params\":{\"threadId\":\"thread-1\",\"turn\":{\"id\":\"turn-1\",\"status\":\"completed\",\"items\":[],\"error\":null}}}'\n"
                ).getBytes(StandardCharsets.UTF_8));
        assertTrue(server.setExecutable(true));

        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<String> finalText = new AtomicReference<>();
        CodexConversationSession session = new CodexConversationSession(
                new ProcessBuilder(server.getAbsolutePath(), transcript.getAbsolutePath()),
                CodexConversationSession.Mode.NEW_THREAD, null, temporary.getRoot().getAbsolutePath(),
                "Reply with exactly: سلام راوا", new ListenerAdapter() {
                    @Override public void onAssistantFinal(String text) { finalText.set(text); }
                    @Override public void onTurnCompleted(String status, String error) {
                        assertEquals("completed", status);
                        completed.countDown();
                    }
                });
        session.start();
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        assertEquals("سلام راوا", finalText.get());

        List<String> requests = Files.readAllLines(transcript.toPath(), StandardCharsets.UTF_8);
        assertEquals("initialize", new JSONObject(requests.get(0)).getString("method"));
        assertEquals("initialized", new JSONObject(requests.get(1)).getString("method"));
        assertEquals("model/list", new JSONObject(requests.get(2)).getString("method"));
        assertEquals("thread/start", new JSONObject(requests.get(3)).getString("method"));
        assertEquals("turn/start", new JSONObject(requests.get(4)).getString("method"));
    }

    @Test
    public void sendsTurnInterruptForTheActiveIds() throws Exception {
        File transcript = temporary.newFile("interrupt.jsonl");
        File server = temporary.newFile("fake-interrupt.sh");
        Files.write(server.toPath(), ("#!/bin/sh\n"
                + "IFS= read -r x; printf '%s\\n' '{\"id\":1,\"result\":{}}'\n"
                + "IFS= read -r x; IFS= read -r x\n"
                + "printf '%s\\n' '{\"id\":10,\"result\":{\"data\":[]}}'\n"
                + "IFS= read -r x\n"
                + "printf '%s\\n' '{\"id\":11,\"result\":{\"thread\":{\"id\":\"thread-2\"},\"model\":\"gpt-test\"}}'\n"
                + "IFS= read -r x\n"
                + "printf '%s\\n' '{\"id\":12,\"result\":{\"turn\":{\"id\":\"turn-2\",\"status\":\"inProgress\"}}}'\n"
                + "IFS= read -r x; printf '%s\\n' \"$x\" >> \"$1\"\n"
                + "printf '%s\\n' '{\"id\":13,\"result\":{}}'\n"
                ).getBytes(StandardCharsets.UTF_8));
        assertTrue(server.setExecutable(true));
        CountDownLatch started = new CountDownLatch(1);
        CodexConversationSession session = new CodexConversationSession(
                new ProcessBuilder(server.getAbsolutePath(), transcript.getAbsolutePath()),
                CodexConversationSession.Mode.NEW_THREAD, null, temporary.getRoot().getAbsolutePath(),
                "hello", new ListenerAdapter() {
                    @Override public void onTurnStarted(String turnId) { started.countDown(); }
                });
        session.start();
        assertTrue(started.await(5, TimeUnit.SECONDS));
        session.interrupt();
        Thread.sleep(200);
        session.close();
        JSONObject interrupt = new JSONObject(
                Files.readAllLines(transcript.toPath(), StandardCharsets.UTF_8).get(0));
        assertEquals("turn/interrupt", interrupt.getString("method"));
        assertEquals("thread-2", interrupt.getJSONObject("params").getString("threadId"));
        assertEquals("turn-2", interrupt.getJSONObject("params").getString("turnId"));
    }

    private static class ListenerAdapter implements CodexConversationSession.Listener {
        @Override public void onStatus(String status) {}
        @Override public void onModels(int count, String defaultModel) {}
        @Override public void onThreadReady(String threadId, String model) {}
        @Override public void onTurnStarted(String turnId) {}
        @Override public void onAssistantDelta(String delta) {}
        @Override public void onAssistantFinal(String text) {}
        @Override public void onTurnCompleted(String status, String error) {}
        @Override public void onServerRequestDenied() {}
        @Override public void onDiagnostic() {}
        @Override public void onStopped(int exitCode) {}
        @Override public void onError(String error) {}
    }
}
