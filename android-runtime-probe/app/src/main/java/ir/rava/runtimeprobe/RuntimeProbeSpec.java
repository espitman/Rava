package ir.rava.runtimeprobe;

/** Describes an executable installed by Android under the APK native-library directory. */
public final class RuntimeProbeSpec implements RuntimeProbe {
    private final String id;
    private final String displayName;
    private final String libraryFileName;

    public RuntimeProbeSpec(String id, String displayName, String libraryFileName) {
        if (!libraryFileName.startsWith("lib") || !libraryFileName.endsWith(".so")
                || libraryFileName.contains("/") || libraryFileName.contains("\\")) {
            throw new IllegalArgumentException("Runtime executable must be a plain lib*.so name");
        }
        this.id = id;
        this.displayName = displayName;
        this.libraryFileName = libraryFileName;
    }

    @Override public String id() { return id; }

    @Override public String displayName() { return displayName; }

    @Override public String libraryFileName() { return libraryFileName; }
}
