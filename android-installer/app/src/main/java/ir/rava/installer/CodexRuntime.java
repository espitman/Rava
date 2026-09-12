package ir.rava.installer;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class CodexRuntime {
    private final Context context;

    CodexRuntime(Context context) {
        this.context = context.getApplicationContext();
    }

    ProcessBuilder processBuilder() throws IOException {
        File executable = new File(context.getApplicationInfo().nativeLibraryDir,
                "libcodex_app_server.so");
        if (!executable.isFile()) throw new IOException("Codex runtime is missing from this APK");
        File files = context.getFilesDir();
        writeResolverConfig(files);
        ProcessBuilder builder = new ProcessBuilder(
                executable.getAbsolutePath(), "--listen", "stdio://");
        builder.directory(files);
        Map<String, String> environment = builder.environment();
        File home = new File(files, "runtime-home");
        File temporary = new File(context.getCacheDir(), "codex-tmp");
        if (!home.isDirectory() && !home.mkdirs()) throw new IOException("Cannot create runtime home");
        if (!temporary.isDirectory() && !temporary.mkdirs()) throw new IOException("Cannot create runtime temp");
        File codexHome = CodexAuthStorage.prepare(files);
        File trustBundle = CodexAuthStorage.prepareTrustBundle(files);
        environment.put("HOME", home.getAbsolutePath());
        environment.put("TMPDIR", temporary.getAbsolutePath());
        environment.put("LANG", "en_US.UTF-8");
        environment.put("LC_ALL", "en_US.UTF-8");
        environment.put("LD_LIBRARY_PATH", context.getApplicationInfo().nativeLibraryDir);
        environment.put("CODEX_HOME", codexHome.getAbsolutePath());
        environment.put("SSL_CERT_FILE", trustBundle.getAbsolutePath());
        return builder;
    }

    File workspace() throws IOException {
        File workspace = new File(context.getFilesDir(), "codex-chat-workspace");
        if (!workspace.isDirectory() && !workspace.mkdirs()) {
            throw new IOException("Cannot create Codex chat workspace");
        }
        return workspace;
    }

    private void writeResolverConfig(File files) throws IOException {
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        Network activeNetwork = manager == null ? null : manager.getActiveNetwork();
        LinkProperties properties = activeNetwork == null
                ? null : manager.getLinkProperties(activeNetwork);
        if (properties == null || properties.getDnsServers().isEmpty()) {
            throw new IOException("No DNS servers are available for Codex");
        }
        StringBuilder config = new StringBuilder();
        for (InetAddress address : properties.getDnsServers()) {
            config.append("nameserver ").append(address.getHostAddress()).append('\n');
        }
        File resolver = new File(files, "resolv.conf");
        try (FileOutputStream target = new FileOutputStream(resolver, false)) {
            target.write(config.toString().getBytes(StandardCharsets.US_ASCII));
        }
        resolver.setReadable(false, false);
        resolver.setWritable(false, false);
        resolver.setReadable(true, true);
        resolver.setWritable(true, true);
    }
}
