package ir.rava.runtimeprobe;

/** Hook implemented by every APK-packaged runtime that the probe can launch. */
public interface RuntimeProbe {
    String id();

    String displayName();

    String libraryFileName();
}
