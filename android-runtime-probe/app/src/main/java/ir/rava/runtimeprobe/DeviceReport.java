package ir.rava.runtimeprobe;

import android.content.Context;
import android.os.Build;
import android.os.StatFs;
import android.system.Os;
import android.system.OsConstants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class DeviceReport {
    private DeviceReport() {}

    public static JSONObject collect(Context context) throws JSONException {
        StatFs internal = new StatFs(context.getFilesDir().getAbsolutePath());
        long pageSize;
        try {
            pageSize = Os.sysconf(OsConstants._SC_PAGESIZE);
        } catch (Exception ignored) {
            pageSize = -1;
        }

        return new JSONObject()
                .put("manufacturer", Build.MANUFACTURER)
                .put("model", Build.MODEL)
                .put("androidRelease", Build.VERSION.RELEASE)
                .put("sdkInt", Build.VERSION.SDK_INT)
                .put("supportedAbis", new JSONArray(Build.SUPPORTED_ABIS))
                .put("processIs64Bit", android.os.Process.is64Bit())
                .put("pageSizeBytes", pageSize)
                .put("availableStorageBytes", internal.getAvailableBytes())
                .put("totalStorageBytes", internal.getTotalBytes())
                .put("nativeLibraryDir", context.getApplicationInfo().nativeLibraryDir)
                .put("packageName", context.getPackageName())
                .put("termuxRequired", false);
    }
}
