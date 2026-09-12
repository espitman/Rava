package ir.rava.runtimeprobe;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressLint("SetTextI18n")
public final class MainActivity extends Activity {
    private static final String CODEX_INITIALIZE =
            "{\"method\":\"initialize\",\"id\":1,\"params\":{" +
            "\"clientInfo\":{\"name\":\"rava_runtime_probe\"," +
            "\"title\":\"Rava Runtime Probe\",\"version\":\"0.1.0\"}," +
            "\"capabilities\":{\"experimentalApi\":false}}}\n" +
            "{\"method\":\"initialized\"}\n";
    private static final long CODEX_STDIN_SETTLE_MILLIS = 3000;

    private final ExecutorService commands = Executors.newSingleThreadExecutor();
    private volatile Process activeProcess;
    private TextView output;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText("Rava standalone runtime probe");
        title.setTextSize(22);
        content.addView(title);

        Button nativeButton = new Button(this);
        nativeButton.setText("Run embedded native probe");
        nativeButton.setOnClickListener(view -> runNativeProbe());
        content.addView(nativeButton);

        Button nodeButton = new Button(this);
        nodeButton.setText("Run Node runtime checks");
        nodeButton.setOnClickListener(view -> runNodeProbe());
        content.addView(nodeButton);

        Button geminiButton = new Button(this);
        geminiButton.setText("Run Gemini CLI --version");
        geminiButton.setOnClickListener(view -> runGeminiVersion());
        content.addView(geminiButton);

        Button codexButton = new Button(this);
        codexButton.setText("Run Codex JSON-RPC initialize");
        codexButton.setOnClickListener(view -> runCodexProbe());
        content.addView(codexButton);

        Button cancelButton = new Button(this);
        cancelButton.setText("Cancel running process");
        cancelButton.setOnClickListener(view -> cancelActiveProcess());
        content.addView(cancelButton);

        output = new TextView(this);
        output.setTextIsSelectable(true);
        output.setTextSize(13);
        output.setMovementMethod(new ScrollingMovementMethod());
        try {
            output.setText("[device]\n" + DeviceReport.collect(this).toString(2) + "\n");
        } catch (Exception error) {
            output.setText("Device report failed: " + error + "\n");
        }
        content.addView(output, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);

        String requestedProbe = getIntent().getStringExtra("probe");
        if ("node".equals(requestedProbe)) {
            runNodeProbe();
        } else if ("gemini-version".equals(requestedProbe)) {
            runGeminiVersion();
        }

        if ("codex".equals(getIntent().getStringExtra("probe"))) {
            runCodexProbe();
        }
    }

    private File nodeRuntime() {
        return runtime(RuntimeProbes.NODE);
    }

    private File runtime(RuntimeProbe probe) {
        return new File(getApplicationInfo().nativeLibraryDir, probe.libraryFileName());
    }

    private void runNativeProbe() {
        commands.execute(() -> runPackaged(RuntimeProbes.NATIVE, List.of("--json")));
    }

    private void runNodeProbe() {
        commands.execute(() -> {
            try {
                File script = copyAsset("node-probe.js", "node-probe.js");
                File work = new File(getFilesDir(), "node-probe-work");
                if (!work.isDirectory() && !work.mkdirs()) {
                    throw new IOException("Cannot create " + work);
                }
                run(List.of(nodeRuntime().getAbsolutePath(), script.getAbsolutePath(), work.getAbsolutePath()));
            } catch (Exception error) {
                append("ERROR: " + error + "\n");
            }
        });
    }

    private void runGeminiVersion() {
        commands.execute(() -> {
            try {
                File bundle = copyAssetTree("gemini", "gemini");
                File script = new File(bundle, "gemini.js");
                run(List.of(nodeRuntime().getAbsolutePath(), script.getAbsolutePath(), "--version"));
            } catch (Exception error) {
                append("ERROR: " + error + "\n");
            }
        });
    }

    private void runCodexProbe() {
        commands.execute(() -> runPackaged(
                RuntimeProbes.CODEX,
                List.of("--listen", "stdio://"),
                CODEX_INITIALIZE,
                CODEX_STDIN_SETTLE_MILLIS));
    }

    private void runPackaged(RuntimeProbe probe, List<String> arguments) {
        runPackaged(probe, arguments, null);
    }

    private void runPackaged(RuntimeProbe probe, List<String> arguments, String stdinPayload) {
        runPackaged(probe, arguments, stdinPayload, 0);
    }

    private void runPackaged(
            RuntimeProbe probe,
            List<String> arguments,
            String stdinPayload,
            long stdinSettleMillis) {
        try {
            List<String> command = new ArrayList<>();
            command.add(runtime(probe).getAbsolutePath());
            command.addAll(arguments);
            run(command, stdinPayload, stdinSettleMillis);
        } catch (Exception error) {
            append("[" + probe.id() + "] NOT_PACKAGED_OR_FAILED: " + error + "\n");
        }
    }

    private void run(List<String> command) throws Exception {
        run(command, null);
    }

    private void run(List<String> command, String stdinPayload) throws Exception {
        run(command, stdinPayload, 0);
    }

    private void run(List<String> command, String stdinPayload, long stdinSettleMillis)
            throws Exception {
        File executable = new File(command.get(0));
        if (!executable.isFile()) {
            throw new IOException("Runtime executable is absent from nativeLibraryDir: "
                    + executable.getName());
        }

        append("\n$ " + String.join(" ", command) + "\n");
        ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(command));
        builder.directory(getFilesDir());
        Map<String, String> environment = builder.environment();
        File home = new File(getFilesDir(), "runtime-home");
        File temporary = new File(getCacheDir(), "node-tmp");
        home.mkdirs();
        temporary.mkdirs();
        environment.put("HOME", home.getAbsolutePath());
        environment.put("TMPDIR", temporary.getAbsolutePath());
        environment.put("LANG", "en_US.UTF-8");
        environment.put("LC_ALL", "en_US.UTF-8");
        environment.put("LD_LIBRARY_PATH", getApplicationInfo().nativeLibraryDir);
        // clipboardy throws during module initialization on Android unless this
        // marker is present. Clipboard actions still require a separate bridge;
        // the standalone version/headless paths do not invoke them.
        environment.put("TERMUX_VERSION", "rava-standalone");
        writeResolverConfig();

        File stdoutCapture = new File(getCacheDir(), "last-stdout.txt");
        File stderrCapture = new File(getCacheDir(), "last-stderr.txt");
        builder.redirectOutput(stdoutCapture);
        builder.redirectError(stderrCapture);

        Process process = builder.start();
        activeProcess = process;
        if (stdinPayload != null) {
            process.getOutputStream().write(stdinPayload.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            if (stdinSettleMillis > 0) {
                Thread.sleep(stdinSettleMillis);
            }
            process.getOutputStream().close();
        }
        int exitCode = process.waitFor();
        String stdoutText = readAll(stdoutCapture);
        String stderrText = readAll(stderrCapture);
        activeProcess = null;

        String report = "$ " + String.join(" ", command) + "\n"
                + "[stdout]\n" + stdoutText
                + "[stderr]\n" + stderrText
                + "[exit] " + exitCode + "\n";
        try (FileOutputStream target = new FileOutputStream(
                new File(getFilesDir(), "last-command.txt"))) {
            target.write(report.getBytes(StandardCharsets.UTF_8));
        }
        append(report);
    }

    private void writeResolverConfig() throws IOException {
        ConnectivityManager manager = getSystemService(ConnectivityManager.class);
        Network activeNetwork = manager == null ? null : manager.getActiveNetwork();
        LinkProperties properties = activeNetwork == null
                ? null : manager.getLinkProperties(activeNetwork);
        if (properties == null || properties.getDnsServers().isEmpty()) {
            throw new IOException("No DNS servers reported for the active Android network");
        }
        StringBuilder config = new StringBuilder();
        for (InetAddress address : properties.getDnsServers()) {
            config.append("nameserver ").append(address.getHostAddress()).append('\n');
        }
        try (FileOutputStream target = new FileOutputStream(
                new File(getFilesDir(), "resolv.conf"))) {
            target.write(config.toString().getBytes(StandardCharsets.US_ASCII));
        }
    }

    private File copyAsset(String assetName, String relativeDestination) throws IOException {
        File destination = new File(getFilesDir(), relativeDestination);
        File parent = destination.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        }
        try (InputStream input = getAssets().open(assetName);
             FileOutputStream target = new FileOutputStream(destination)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                target.write(buffer, 0, count);
            }
        }
        return destination;
    }

    /** Copies a bundled JavaScript module tree while keeping executable code in nativeLibraryDir. */
    private File copyAssetTree(String assetPath, String relativeDestination) throws IOException {
        String[] children = getAssets().list(assetPath);
        if (children == null || children.length == 0) {
            return copyAsset(assetPath, relativeDestination);
        }

        File directory = new File(getFilesDir(), relativeDestination);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Cannot create " + directory);
        }
        for (String child : children) {
            copyAssetTree(assetPath + "/" + child, relativeDestination + "/" + child);
        }
        return directory;
    }

    private static String readAll(InputStream input) throws IOException {
        return ProbeOutput.readUtf8(input, ProbeOutput.MAX_BYTES);
    }

    private static String readAll(File input) throws IOException {
        try (FileInputStream stream = new FileInputStream(input)) {
            return readAll(stream);
        }
    }

    private void append(String text) {
        runOnUiThread(() -> output.append(text));
    }

    private void cancelActiveProcess() {
        Process process = activeProcess;
        if (process != null) {
            process.destroy();
            append("Cancellation requested.\n");
        }
    }

    @Override
    protected void onDestroy() {
        cancelActiveProcess();
        commands.shutdownNow();
        super.onDestroy();
    }
}
