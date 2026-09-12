package ir.rava.runtimeprobe;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;

/** Creates the app-private Codex home and pins credential persistence to a file. */
final class CodexAuthStorage {
    private static final String CONFIG = "cli_auth_credentials_store = \"file\"\n";
    private static final File ANDROID_CA_DIRECTORY =
            new File("/system/etc/security/cacerts");

    private CodexAuthStorage() {}

    static File prepare(File appFilesDirectory) throws IOException {
        File files = appFilesDirectory.getCanonicalFile();
        File codexHome = new File(files, "codex-home").getCanonicalFile();
        if (!codexHome.toPath().startsWith(files.toPath())) {
            throw new IOException("Codex home escaped app-private storage");
        }
        if (!codexHome.isDirectory() && !codexHome.mkdirs()) {
            throw new IOException("Cannot create app-private Codex home");
        }
        restrictToOwner(codexHome, true);

        File config = new File(codexHome, "config.toml");
        if (!config.exists()) {
            try (FileOutputStream target = new FileOutputStream(config)) {
                target.write(CONFIG.getBytes(StandardCharsets.UTF_8));
            }
        }
        restrictToOwner(config, false);
        return codexHome;
    }

    /** Builds the PEM bundle expected by the pinned Linux/musl rustls-native-certs path. */
    static File prepareTrustBundle(File appFilesDirectory) throws IOException {
        return prepareTrustBundle(appFilesDirectory, ANDROID_CA_DIRECTORY);
    }

    static File prepareTrustBundle(File appFilesDirectory, File trustDirectory)
            throws IOException {
        File codexHome = prepare(appFilesDirectory);
        File[] certificates = trustDirectory.listFiles(File::isFile);
        if (certificates == null || certificates.length == 0) {
            throw new IOException("Android system CA directory is unavailable or empty");
        }
        Arrays.sort(certificates, Comparator.comparing(File::getName));

        File bundle = new File(codexHome, "android-ca-bundle.pem");
        byte[] buffer = new byte[16 * 1024];
        try (FileOutputStream target = new FileOutputStream(bundle, false)) {
            for (File certificate : certificates) {
                int lastByte = -1;
                try (FileInputStream source = new FileInputStream(certificate)) {
                    int count;
                    while ((count = source.read(buffer)) != -1) {
                        target.write(buffer, 0, count);
                        lastByte = buffer[count - 1] & 0xff;
                    }
                }
                if (lastByte != '\n') {
                    target.write('\n');
                }
            }
        }
        restrictToOwner(bundle, false);
        return bundle;
    }

    private static void restrictToOwner(File file, boolean executable) throws IOException {
        boolean changed = file.setReadable(false, false)
                && file.setWritable(false, false)
                && file.setExecutable(false, false)
                && file.setReadable(true, true)
                && file.setWritable(true, true);
        if (executable) {
            changed = changed && file.setExecutable(true, true);
        }
        if (!changed) {
            throw new IOException("Cannot restrict " + file.getName() + " to the app UID");
        }
    }
}
