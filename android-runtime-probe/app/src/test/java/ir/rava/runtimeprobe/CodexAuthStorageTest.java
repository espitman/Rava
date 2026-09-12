package ir.rava.runtimeprobe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class CodexAuthStorageTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void createsAFileBackedCodexHomeInsideAppFiles() throws Exception {
        File files = temporary.newFolder("files");
        File codexHome = CodexAuthStorage.prepare(files);
        assertTrue(codexHome.getCanonicalPath().startsWith(files.getCanonicalPath()));
        File config = new File(codexHome, "config.toml");
        assertTrue(config.isFile());
        assertEquals("cli_auth_credentials_store = \"file\"\n",
                new String(Files.readAllBytes(config.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void doesNotOverwriteAnExistingCodexConfig() throws Exception {
        File files = temporary.newFolder("existing");
        File home = new File(files, "codex-home");
        assertTrue(home.mkdir());
        File config = new File(home, "config.toml");
        Files.write(config.toPath(), "model = \"example\"\n".getBytes(StandardCharsets.UTF_8));
        CodexAuthStorage.prepare(files);
        assertEquals("model = \"example\"\n",
                new String(Files.readAllBytes(config.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void buildsDeterministicAppPrivateTrustBundle() throws Exception {
        File files = temporary.newFolder("trust-files");
        File roots = temporary.newFolder("system-roots");
        Files.write(new File(roots, "b.0").toPath(),
                "-----BEGIN CERTIFICATE-----\nBBB\n-----END CERTIFICATE-----"
                        .getBytes(StandardCharsets.US_ASCII));
        Files.write(new File(roots, "a.0").toPath(),
                "-----BEGIN CERTIFICATE-----\nAAA\n-----END CERTIFICATE-----\n"
                        .getBytes(StandardCharsets.US_ASCII));

        File bundle = CodexAuthStorage.prepareTrustBundle(files, roots);

        assertTrue(bundle.getCanonicalPath().startsWith(files.getCanonicalPath()));
        assertEquals(
                "-----BEGIN CERTIFICATE-----\nAAA\n-----END CERTIFICATE-----\n"
                        + "-----BEGIN CERTIFICATE-----\nBBB\n-----END CERTIFICATE-----\n",
                new String(Files.readAllBytes(bundle.toPath()), StandardCharsets.US_ASCII));
    }
}
