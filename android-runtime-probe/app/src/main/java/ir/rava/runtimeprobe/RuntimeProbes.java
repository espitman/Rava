package ir.rava.runtimeprobe;

/** Stable filenames shared with the Node and Codex Android build pipelines. */
public final class RuntimeProbes {
    public static final RuntimeProbeSpec NATIVE = new RuntimeProbeSpec(
            "native", "embedded native probe", "librava_probe_exec.so");
    public static final RuntimeProbeSpec NODE = new RuntimeProbeSpec(
            "node", "Node.js runtime", "libnode_runtime.so");
    public static final RuntimeProbeSpec CODEX = new RuntimeProbeSpec(
            "codex", "Codex app-server", "libcodex_app_server.so");

    private RuntimeProbes() {}
}
