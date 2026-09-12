package ir.rava.runtimeprobe;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class ProbeOutput {
    static final int MAX_BYTES = 256 * 1024;
    private static final byte[] TRUNCATION = "\n[output truncated]\n".getBytes(StandardCharsets.UTF_8);

    private ProbeOutput() {}

    static String readUtf8(InputStream stream, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 8192));
        byte[] buffer = new byte[4096];
        int total = 0;
        boolean truncated = false;
        int count;
        while ((count = stream.read(buffer)) != -1) {
            int remaining = limit - total;
            if (remaining > 0) {
                int accepted = Math.min(remaining, count);
                output.write(buffer, 0, accepted);
                total += accepted;
            }
            if (count > remaining) truncated = true;
        }
        if (truncated) output.write(TRUNCATION);
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
