package ir.rava.installer;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

public class CommandResultService extends IntentService {
    static final String EXTRA_EXECUTION_ID = "execution_id";
    static final String EXTRA_LABEL = "label";
    static final String ACTION_RESULT = "ir.rava.installer.COMMAND_RESULT";

    public CommandResultService() {
        super("RavaCommandResult");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) return;
        Bundle result = intent.getBundleExtra("result");
        String label = intent.getStringExtra(EXTRA_LABEL);
        int executionId = intent.getIntExtra(EXTRA_EXECUTION_ID, 0);
        int exitCode = result == null ? -1 : result.getInt("exitCode", -1);
        int internalError = result == null ? -1 : result.getInt("err", -1);
        String stdout = result == null ? "" : result.getString("stdout", "");
        String stderr = result == null ? "" : result.getString("stderr", "");
        String errorMessage = result == null ? "Termux returned no result." : result.getString("errmsg", "");

        SharedPreferences preferences = getSharedPreferences("command_results", MODE_PRIVATE);
        preferences.edit()
                .putString("label", label == null ? "Command" : label)
                .putInt("execution_id", executionId)
                .putInt("exit_code", exitCode)
                .putInt("internal_error", internalError)
                .putString("stdout", trimOutput(stdout))
                .putString("stderr", trimOutput(stderr))
                .putString("error_message", trimOutput(errorMessage))
                .putLong("finished_at", System.currentTimeMillis())
                .remove("active_id")
                .remove("active_label")
                .remove("active_started_at")
                .apply();

        Intent update = new Intent(ACTION_RESULT).setPackage(getPackageName());
        sendBroadcast(update);
    }

    private static String trimOutput(String value) {
        if (value == null) return "";
        int limit = 12000;
        return value.length() <= limit ? value : value.substring(value.length() - limit);
    }
}
