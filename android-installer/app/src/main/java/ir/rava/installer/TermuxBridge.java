package ir.rava.installer;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.concurrent.atomic.AtomicInteger;

@SuppressLint("SdCardPath")
final class TermuxBridge {
    static final String TERMUX_PACKAGE = "com.termux";
    static final String TERMUX_X11_PACKAGE = "com.termux.x11";
    static final String RUN_PERMISSION = "com.termux.permission.RUN_COMMAND";
    static final String HOME = "/data/data/com.termux/files/home";
    static final String PREFIX = "/data/data/com.termux/files/usr";
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1000);

    private TermuxBridge() {}

    static int run(Context context, String label, String script, boolean background) {
        int id = NEXT_ID.incrementAndGet();
        Intent resultIntent = new Intent(context, CommandResultService.class);
        resultIntent.putExtra(CommandResultService.EXTRA_EXECUTION_ID, id);
        resultIntent.putExtra(CommandResultService.EXTRA_LABEL, label);
        int flags = PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent result = PendingIntent.getService(context, id, resultIntent, flags);

        Intent intent = new Intent();
        intent.setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService");
        intent.setAction("com.termux.RUN_COMMAND");
        intent.putExtra("com.termux.RUN_COMMAND_PATH", PREFIX + "/bin/bash");
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-s"});
        intent.putExtra("com.termux.RUN_COMMAND_STDIN", script);
        intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME);
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", background);
        intent.putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", label);
        intent.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", result);
        context.getSharedPreferences("command_results", Context.MODE_PRIVATE).edit()
                .putInt("active_id", id)
                .putString("active_label", label)
                .putLong("active_started_at", System.currentTimeMillis())
                .commit();
        try {
            context.startService(intent);
        } catch (RuntimeException exception) {
            context.getSharedPreferences("command_results", Context.MODE_PRIVATE).edit()
                    .remove("active_id")
                    .remove("active_label")
                    .remove("active_started_at")
                    .commit();
            throw exception;
        }
        return id;
    }
}
