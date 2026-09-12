package ir.rava.runtimeprobe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public final class ProbeOutputTest {
    @Test
    public void readsUtf8WithoutChangingPersianText() throws Exception {
        String source = "فارسی ✓";
        assertEquals(source, ProbeOutput.readUtf8(
                new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)), 128));
    }

    @Test
    public void truncatesOversizedOutputWhileDrainingIt() throws Exception {
        String result = ProbeOutput.readUtf8(
                new ByteArrayInputStream("abcdefgh".getBytes(StandardCharsets.UTF_8)), 4);
        assertTrue(result.startsWith("abcd"));
        assertTrue(result.contains("output truncated"));
    }
}
