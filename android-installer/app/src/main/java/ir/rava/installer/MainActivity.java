package ir.rava.installer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@SuppressLint({"SetTextI18n", "UnspecifiedRegisterReceiverFlag"})
public class MainActivity extends Activity {
    private static final int RUN_PERMISSION_REQUEST = 41;
    private static final String ENABLE_EXTERNAL_APPS =
            "mkdir -p ~/.termux && (grep -q '^allow-external-apps=true$' ~/.termux/termux.properties 2>/dev/null || echo 'allow-external-apps=true' >> ~/.termux/termux.properties) && termux-reload-settings";

    private TextView prerequisites;
    private TextView progress;
    private TextView output;
    private ProgressBar spinner;
    private Spinner modelSpinner;
    private ArrayAdapter<String> modelAdapter;
    private EditText chatInput;
    private LinearLayout chatMessages;
    private ScrollView chatScroll;
    private TextView emptyChat;
    private TextView chatStatus;
    private ImageButton sendButton;
    private String conversationId;
    private Typeface chatTypeface;
    private final BroadcastReceiver resultReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            renderLastResult();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        chatTypeface = getResources().getFont(R.font.vazirmatn_regular);
        setTitle("Rava Setup");
        setContentView(buildUi());
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(CommandResultService.ACTION_RESULT);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(resultReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(resultReceiver, filter);
        }
        refreshPrerequisites();
        renderLastResult();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(resultReceiver);
        super.onStop();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247, 247, 252));

        FrameLayout pages = new FrameLayout(this);
        View setupPage = buildSetupUi();
        View chatPage = buildChatUi();
        pages.addView(setupPage, new FrameLayout.LayoutParams(-1, -1));
        pages.addView(chatPage, new FrameLayout.LayoutParams(-1, -1));
        chatPage.setVisibility(View.GONE);

        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setPadding(dp(18), 0, dp(18), 0);
        navigation.setBackgroundColor(Color.WHITE);
        ImageButton setupDestination = navigationItem(R.drawable.ic_home, "Setup", true);
        ImageButton chatDestination = navigationItem(R.drawable.ic_chat, "Chat", false);
        navigation.addView(setupDestination, new LinearLayout.LayoutParams(0, dp(50), 1));
        navigation.addView(chatDestination, new LinearLayout.LayoutParams(0, dp(50), 1));

        setupDestination.setOnClickListener(view -> {
            setupPage.setVisibility(View.VISIBLE);
            chatPage.setVisibility(View.GONE);
            styleNavigationItem(setupDestination, true);
            styleNavigationItem(chatDestination, false);
        });
        chatDestination.setOnClickListener(view -> {
            setupPage.setVisibility(View.GONE);
            chatPage.setVisibility(View.VISIBLE);
            styleNavigationItem(setupDestination, false);
            styleNavigationItem(chatDestination, true);
            if (modelAdapter.isEmpty()) loadModels();
        });

        root.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
        View topShadow = new View(this);
        topShadow.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0x24000000, 0x00000000}));
        root.addView(topShadow, new LinearLayout.LayoutParams(-1, dp(5)));
        root.addView(navigation, new LinearLayout.LayoutParams(-1, dp(48)));
        return root;
    }

    private View buildSetupUi() {
        int pad = dp(16);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, pad);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        content.setBackgroundColor(Color.rgb(247, 247, 252));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(20), dp(20), dp(20));
        hero.setBackground(rounded(Color.rgb(83, 55, 150), 24));
        hero.setElevation(dp(4));

        TextView badge = text("R", 24, true);
        badge.setTextColor(Color.rgb(83, 55, 150));
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(Color.WHITE, 14));
        hero.addView(badge, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = text("Rava Setup", 29, true);
        title.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams titleParams = spaced();
        titleParams.topMargin = dp(16);
        hero.addView(title, titleParams);
        TextView subtitle = text("Install, connect, and run your local AI engine", 15, false);
        subtitle.setTextColor(Color.rgb(232, 224, 255));
        hero.addView(subtitle, smallGap());
        content.addView(hero);

        TextView section = text("DEVICE READINESS", 12, true);
        section.setTextColor(Color.rgb(103, 80, 164));
        section.setLetterSpacing(0.08f);
        content.addView(section, sectionGap());

        prerequisites = text("", 15, true);
        prerequisites.setPadding(dp(16), dp(15), dp(16), dp(15));
        prerequisites.setBackground(rounded(Color.rgb(238, 234, 247), 18));
        content.addView(prerequisites, smallGap());

        section = text("SETUP STEPS", 12, true);
        section.setTextColor(Color.rgb(103, 80, 164));
        section.setLetterSpacing(0.08f);
        content.addView(section, sectionGap());

        addStep(content, "01", "Enable Termux access", "Copy the one-time security setting and run it in Termux.", "OPEN TERMUX", view -> prepareTermux());
        addStep(content, "02", "Grant command permission", "Allow this setup app to send approved commands to Termux.", "GRANT", view -> requestRunPermission());
        addStep(content, "03", "Test the connection", "Confirm that Termux accepts commands and returns results.", "TEST", view -> runCommand("Access test", "printf 'RAVA_TERMUX_READY\\n'", true));
        addStep(content, "04", "Install or update Rava", "Deploy the bundled project and install Python, Chromium, and connectors.", "INSTALL", view -> installRava());
        addStep(content, "05", "Sign in to ChatGPT", "Open the dedicated ChatGPT browser profile.", "SIGN IN", view -> openChatGptLogin());
        addStep(content, "06", "Sign in to Gemini", "Open a separate browser profile for Gemini.", "SIGN IN", view -> openGeminiLogin());
        addStep(content, "07", "Capture Gemini session", "Securely save the signed-in session inside Termux.", "CAPTURE", view -> runProjectCommand("Capture Gemini session", ".venv/bin/python scripts/capture-gemini-session.py"));
        addStep(content, "08", "Start the engine", "Launch both browser connectors and the local Rava API.", "START", view -> runProjectCommand("Start Rava", "bash scripts/start-rava-stack.sh"));
        addStep(content, "09", "Check status", "Verify providers and list the models available to local apps.", "CHECK", view -> runProjectCommand("Rava status", "curl -fsS http://127.0.0.1:8766/health && printf '\\n' && curl -fsS http://127.0.0.1:8766/v1/models"));

        spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        spinner.setVisibility(View.GONE);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        spinnerParams.topMargin = dp(20);
        content.addView(spinner, spinnerParams);

        progress = text("Ready", 16, true);
        progress.setTextColor(Color.rgb(42, 95, 63));
        content.addView(progress, smallGap());

        output = text("No command has run yet.", 13, false);
        output.setTextColor(Color.rgb(225, 231, 239));
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextDirection(View.TEXT_DIRECTION_LTR);
        output.setGravity(Gravity.START);
        output.setTextIsSelectable(true);
        output.setMovementMethod(new ScrollingMovementMethod());
        output.setBackground(rounded(Color.rgb(28, 30, 38), 18));
        output.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams outputParams = new LinearLayout.LayoutParams(-1, dp(260));
        outputParams.bottomMargin = dp(24);
        content.addView(output, outputParams);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        return scroll;
    }

    private View buildChatUi() {
        int pad = dp(12);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, pad);
        content.setBackgroundColor(Color.WHITE);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = chatText("Rava", 22, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        ImageButton newChat = iconButton(R.drawable.ic_new_chat, "New chat", Color.rgb(32, 30, 34));
        newChat.setOnClickListener(view -> resetChat());
        header.addView(newChat, new LinearLayout.LayoutParams(dp(48), dp(48)));
        content.addView(header);

        LinearLayout modelRow = new LinearLayout(this);
        modelRow.setOrientation(LinearLayout.HORIZONTAL);
        modelRow.setGravity(Gravity.CENTER_VERTICAL);
        modelSpinner = new Spinner(this);
        modelAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new ArrayList<>());
        modelSpinner.setAdapter(modelAdapter);
        modelSpinner.setBackground(rounded(Color.rgb(245, 245, 245), 14));
        modelRow.addView(modelSpinner, new LinearLayout.LayoutParams(0, dp(46), 1));
        Button reload = new Button(this);
        reload.setText("Reload");
        reload.setAllCaps(false);
        reload.setTypeface(chatTypeface);
        reload.setBackground(rounded(Color.rgb(245, 245, 245), 14));
        reload.setOnClickListener(view -> loadModels());
        LinearLayout.LayoutParams reloadParams = new LinearLayout.LayoutParams(dp(92), dp(46));
        reloadParams.leftMargin = dp(8);
        modelRow.addView(reload, reloadParams);
        content.addView(modelRow, smallGap());

        chatStatus = chatText("Open Chat after starting the engine.", 12, false);
        chatStatus.setTextColor(Color.rgb(92, 88, 99));
        content.addView(chatStatus, smallGap());

        chatMessages = new LinearLayout(this);
        chatMessages.setOrientation(LinearLayout.VERTICAL);
        chatMessages.setPadding(dp(2), dp(14), dp(2), dp(14));
        emptyChat = chatText("How can I help?", 24, true);
        emptyChat.setGravity(Gravity.CENTER);
        emptyChat.setTextColor(Color.rgb(55, 55, 55));
        chatMessages.addView(emptyChat, new LinearLayout.LayoutParams(-1, dp(180)));
        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        chatScroll.addView(chatMessages);
        LinearLayout.LayoutParams transcriptParams = new LinearLayout.LayoutParams(-1, 0, 1);
        transcriptParams.topMargin = dp(4);
        content.addView(chatScroll, transcriptParams);

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.BOTTOM);
        composer.setPadding(dp(6), dp(4), dp(5), dp(4));
        composer.setBackground(rounded(Color.rgb(244, 244, 244), 24));

        chatInput = new EditText(this);
        chatInput.setHint("پیام خود را بنویسید…");
        chatInput.setTextSize(16);
        chatInput.setTypeface(chatTypeface);
        chatInput.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        chatInput.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        chatInput.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        chatInput.setSingleLine(false);
        chatInput.setMaxLines(5);
        chatInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        chatInput.setPadding(dp(10), dp(8), dp(10), dp(8));
        chatInput.setBackgroundColor(Color.TRANSPARENT);
        chatInput.setOnClickListener(view -> {
            chatInput.requestFocus();
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                    .showSoftInput(chatInput, InputMethodManager.SHOW_IMPLICIT);
        });
        chatInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendChatMessage();
                return true;
            }
            return false;
        });
        composer.addView(chatInput, new LinearLayout.LayoutParams(0, -2, 1));

        sendButton = iconButton(R.drawable.ic_send, "Send", Color.WHITE);
        sendButton.setBackground(rounded(Color.rgb(32, 30, 34), 22));
        sendButton.setOnClickListener(view -> sendChatMessage());
        composer.addView(sendButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout.LayoutParams composerParams = new LinearLayout.LayoutParams(-1, -2);
        composerParams.topMargin = dp(8);
        composerParams.bottomMargin = dp(8);
        content.addView(composer, composerParams);

        return content;
    }

    private void prepareTermux() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Enable Rava", ENABLE_EXTERNAL_APPS));
        Toast.makeText(this, "Command copied. Paste and run it in Termux.", Toast.LENGTH_LONG).show();
        launchPackage(TermuxBridge.TERMUX_PACKAGE, "https://github.com/termux/termux-app/releases");
    }

    private void requestRunPermission() {
        if (!isInstalled(TermuxBridge.TERMUX_PACKAGE)) {
            showMessage("Install Termux first.");
            return;
        }
        if (checkSelfPermission(TermuxBridge.RUN_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            showMessage("Command permission is already granted.");
            return;
        }
        requestPermissions(new String[]{TermuxBridge.RUN_PERMISSION}, RUN_PERMISSION_REQUEST);
    }

    private void installRava() {
        try {
            String encoded = android.util.Base64.encodeToString(readAsset("rava.tar.gz"), android.util.Base64.NO_WRAP);
            String script = "set -eu\n"
                    + "mkdir -p \"$HOME/Rava\"\n"
                    + "tmp=\"$HOME/.rava-installer.tar.gz\"\n"
                    + "cat <<'RAVA_BUNDLE' | base64 -d > \"$tmp\"\n"
                    + encoded + "\nRAVA_BUNDLE\n"
                    + "tar -xzf \"$tmp\" -C \"$HOME/Rava\"\n"
                    + "rm -f \"$tmp\"\n"
                    + "bash \"$HOME/Rava/scripts/device-full-install.sh\"\n";
            runCommand("Install Rava", script, true);
        } catch (IOException exception) {
            showMessage("Could not read the embedded Rava package: " + exception.getMessage());
        }
    }

    private void openChatGptLogin() {
        runProjectCommand("Open ChatGPT sign-in", "bash scripts/start-termux-browser.sh https://chatgpt.com/");
        output.postDelayed(() -> launchPackage(TermuxBridge.TERMUX_X11_PACKAGE,
                "https://github.com/termux/termux-x11/releases"), 2500);
    }

    private void openGeminiLogin() {
        runProjectCommand("Open Gemini sign-in", "bash scripts/start-gemini-login-browser.sh");
        output.postDelayed(() -> launchPackage(TermuxBridge.TERMUX_X11_PACKAGE,
                "https://github.com/termux/termux-x11/releases"), 2500);
    }

    private void runProjectCommand(String label, String command) {
        runCommand(label, "set -e\ncd \"$HOME/Rava\"\n" + command + "\n", true);
    }

    private void runCommand(String label, String script, boolean background) {
        if (!isInstalled(TermuxBridge.TERMUX_PACKAGE)) {
            showMessage("Termux is not installed.");
            return;
        }
        if (checkSelfPermission(TermuxBridge.RUN_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            showMessage("Complete step 2 and grant command permission first.");
            return;
        }
        try {
            int id = TermuxBridge.run(this, label, script, background);
            showRunning(label, id);
        } catch (SecurityException exception) {
            showMessage("Termux denied access. Check steps 1 and 2.");
        } catch (Exception exception) {
            showMessage("Could not run the command: " + exception.getMessage());
        }
    }

    private void loadModels() {
        chatStatus.setText("Loading models…");
        new Thread(() -> {
            try {
                JSONObject response = requestJson("GET", "/v1/models", null);
                JSONArray data = response.getJSONArray("data");
                List<String> models = new ArrayList<>();
                for (int index = 0; index < data.length(); index++) {
                    models.add(data.getJSONObject(index).getString("id"));
                }
                models.sort((left, right) -> Integer.compare(modelPriority(left), modelPriority(right)));
                runOnUiThread(() -> {
                    modelAdapter.clear();
                    modelAdapter.addAll(models);
                    modelAdapter.notifyDataSetChanged();
                    chatStatus.setText(models.isEmpty()
                            ? "The engine returned no available models."
                            : models.size() + " model" + (models.size() == 1 ? "" : "s") + " available");
                });
            } catch (Exception exception) {
                runOnUiThread(() -> chatStatus.setText(
                        "Could not reach Rava. Start the engine in Setup, then tap Reload.\n"
                                + exception.getMessage()));
            }
        }).start();
    }

    private void sendChatMessage() {
        String message = chatInput.getText().toString().trim();
        Object selected = modelSpinner.getSelectedItem();
        if (message.isEmpty()) {
            chatStatus.setText("Type a message first.");
            return;
        }
        if (selected == null) {
            chatStatus.setText("Load and select a model first.");
            return;
        }

        String model = selected.toString();
        addMessageBubble("You", message, true);
        TextView answerBubble = addMessageBubble(model, "…", false);
        chatInput.setText("");
        sendButton.setEnabled(false);
        chatStatus.setText("Waiting for " + model + "…");

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("app_id", "ir.rava.installer.chat");
                body.put("model", model);
                body.put("stream", true);
                JSONArray messages = new JSONArray();
                messages.put(new JSONObject().put("role", "user").put("content", message));
                body.put("messages", messages);
                if (conversationId != null) body.put("conversation_id", conversationId);

                String answer = streamChatCompletion(body, answerBubble);
                runOnUiThread(() -> {
                    if (answer.isEmpty()) answerBubble.setText("(Empty response)");
                    chatStatus.setText("Conversation active");
                    sendButton.setEnabled(true);
                    scrollChatToBottom();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    conversationId = null;
                    answerBubble.setText(exception.getMessage());
                    answerBubble.setTextColor(Color.rgb(176, 0, 32));
                    chatStatus.setText("Request failed");
                    sendButton.setEnabled(true);
                    scrollChatToBottom();
                });
            }
        }).start();
    }

    private String streamChatCompletion(JSONObject body, TextView answerBubble) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://127.0.0.1:8766/v1/chat/completions").openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(180000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Accept", "text/event-stream");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("X-Rava-App-Id", "ir.rava.installer.chat");
        connection.getOutputStream().write(body.toString().getBytes(StandardCharsets.UTF_8));

        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            InputStream errorStream = connection.getErrorStream();
            String detail = errorStream == null ? "" : readText(errorStream);
            connection.disconnect();
            try {
                detail = new JSONObject(detail).getJSONObject("error").getString("message");
            } catch (Exception ignored) {
                // Preserve the raw response when it does not use the Rava error schema.
            }
            throw new IOException("HTTP " + status + (detail.isEmpty() ? "" : ": " + detail));
        }

        StringBuilder answer = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data: ")) continue;
                String data = line.substring(6);
                if ("[DONE]".equals(data)) break;
                JSONObject event = new JSONObject(data);
                conversationId = event.optString("conversation_id", conversationId);
                JSONObject choice = event.getJSONArray("choices").getJSONObject(0);
                JSONObject delta = choice.getJSONObject("delta");
                String chunk = delta.optString("content", "");
                if ("error".equals(choice.optString("finish_reason"))) {
                    throw new IOException(chunk.isEmpty() ? "The provider stream failed." : chunk);
                }
                if (chunk.isEmpty()) continue;
                answer.append(chunk);
                String visibleText = answer.toString();
                runOnUiThread(() -> {
                    answerBubble.setText(visibleText);
                    applyMessageDirection(answerBubble, visibleText);
                    chatStatus.setText("Receiving response…");
                    scrollChatToBottom();
                });
            }
        } finally {
            connection.disconnect();
        }
        return answer.toString();
    }

    private JSONObject requestJson(String method, String path, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://127.0.0.1:8766" + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(180000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("X-Rava-App-Id", "ir.rava.installer.chat");
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.getOutputStream().write(payload);
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
        String response = stream == null ? "" : readText(stream);
        connection.disconnect();
        if (status < 200 || status >= 300) {
            String detail = response;
            try {
                detail = new JSONObject(response).getJSONObject("error").getString("message");
            } catch (Exception ignored) {
                // Preserve the raw response when it does not use the Rava error schema.
            }
            throw new IOException("HTTP " + status + (detail.isEmpty() ? "" : ": " + detail));
        }
        return new JSONObject(response);
    }

    private String readText(InputStream input) throws IOException {
        try (InputStream source = input; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = source.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return bytes.toString(StandardCharsets.UTF_8.name());
        }
    }

    private int modelPriority(String model) {
        if ("gemini/gemini-flash".equals(model)) return 0;
        if (model.startsWith("gemini/")) return 1;
        return 2;
    }

    private TextView addMessageBubble(String author, String message, boolean user) {
        emptyChat.setVisibility(View.GONE);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(user ? Gravity.END : Gravity.START);

        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setGravity(user ? Gravity.END : Gravity.START);

        TextView authorView = chatText(author, 11, true);
        authorView.setTextColor(Color.rgb(105, 105, 105));
        group.addView(authorView);

        TextView bubble = chatText(message, 16, false);
        bubble.setTextIsSelectable(true);
        bubble.setLineSpacing(0, 1.15f);
        bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.84f));
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setBackground(rounded(
                user ? Color.rgb(235, 229, 248) : Color.rgb(245, 245, 245), 18));
        applyMessageDirection(bubble, message);
        group.addView(bubble, smallGap());

        row.addView(group, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.topMargin = dp(10);
        chatMessages.addView(row, rowParams);
        scrollChatToBottom();
        return bubble;
    }

    private void applyMessageDirection(TextView view, String message) {
        boolean rtl = containsRtl(message);
        view.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        view.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        view.setGravity(Gravity.START);
    }

    private boolean containsRtl(String value) {
        for (int index = 0; index < value.length(); index++) {
            byte direction = Character.getDirectionality(value.charAt(index));
            if (direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                    || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) return true;
            if (direction == Character.DIRECTIONALITY_LEFT_TO_RIGHT) return false;
        }
        return false;
    }

    private void scrollChatToBottom() {
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void resetChat() {
        conversationId = null;
        chatMessages.removeAllViews();
        emptyChat = chatText("How can I help?", 24, true);
        emptyChat.setGravity(Gravity.CENTER);
        emptyChat.setTextColor(Color.rgb(55, 55, 55));
        chatMessages.addView(emptyChat, new LinearLayout.LayoutParams(-1, dp(180)));
        chatStatus.setText("New conversation ready");
        chatInput.requestFocus();
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .showSoftInput(chatInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void refreshPrerequisites() {
        boolean termux = isInstalled(TermuxBridge.TERMUX_PACKAGE);
        boolean x11 = isInstalled(TermuxBridge.TERMUX_X11_PACKAGE);
        boolean permission = checkSelfPermission(TermuxBridge.RUN_PERMISSION) == PackageManager.PERMISSION_GRANTED;
        prerequisites.setText(
                (termux ? "✓" : "✗") + " Termux    "
                        + (x11 ? "✓" : "✗") + " Termux:X11    "
                        + (permission ? "✓" : "✗") + " Command permission"
        );
        prerequisites.setTextColor(allReady(termux, x11, permission)
                ? Color.rgb(35, 91, 57) : Color.rgb(70, 62, 83));
        prerequisites.setBackground(rounded(
                allReady(termux, x11, permission) ? Color.rgb(224, 244, 231) : Color.rgb(238, 234, 247),
                18));
    }

    private void renderLastResult() {
        SharedPreferences preferences = getSharedPreferences("command_results", MODE_PRIVATE);
        int activeId = preferences.getInt("active_id", -1);
        if (activeId >= 0) {
            showRunning(preferences.getString("active_label", "Command"), activeId);
            return;
        }
        long finished = preferences.getLong("finished_at", 0);
        if (finished == 0 || progress == null) return;
        String label = preferences.getString("label", "Command");
        int exitCode = preferences.getInt("exit_code", -1);
        int internalError = preferences.getInt("internal_error", -1);
        String stdout = preferences.getString("stdout", "");
        String stderr = preferences.getString("stderr", "");
        String error = preferences.getString("error_message", "");
        spinner.setVisibility(View.GONE);
        progress.setText(label + (exitCode == 0 && internalError == -1 ? " completed" : " failed")
                + " — " + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(finished)));
        output.setText("exit=" + exitCode + " internal=" + internalError + "\n\n"
                + stdout + (stderr.isEmpty() ? "" : "\n[stderr]\n" + stderr)
                + (error.isEmpty() ? "" : "\n[error]\n" + error));
    }

    private void showRunning(String label, int id) {
        spinner.setVisibility(View.VISIBLE);
        progress.setText(label + " is running… (ID " + id + ")");
        if ("Start Rava".equals(label)) {
            output.setText("Starting Chromium, ChatGPT sidecar, Gemini session, and the local API.\n\nThis normally takes 30–90 seconds. Keep Termux:X11 open until completion.");
        } else if ("Install Rava".equals(label)) {
            output.setText("Installing packages and building native dependencies.\n\nThe first installation may take several minutes. You may leave the app and return later.");
        } else {
            output.setText("The result will appear here when the command finishes. You may leave the app and return later.");
        }
    }

    private byte[] readAsset(String name) throws IOException {
        try (InputStream input = getAssets().open(name); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    private boolean isInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException exception) {
            return false;
        }
    }

    private void launchPackage(String packageName, String fallbackUrl) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch != null) {
            startActivity(launch);
        } else {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)));
        }
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        if (progress != null) progress.setText(message);
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(32, 30, 34));
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private TextView chatText(String value, int size, boolean bold) {
        TextView view = text(value, size, false);
        view.setTypeface(chatTypeface, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private ImageButton iconButton(int icon, String description, int color) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(icon);
        button.setColorFilter(color);
        button.setScaleType(ImageButton.ScaleType.CENTER);
        button.setPadding(dp(11), dp(11), dp(11), dp(11));
        button.setContentDescription(description);
        button.setBackgroundColor(Color.TRANSPARENT);
        return button;
    }

    private ImageButton navigationItem(int icon, String label, boolean selected) {
        ImageButton item = new ImageButton(this);
        item.setImageResource(icon);
        item.setScaleType(ImageButton.ScaleType.CENTER);
        item.setPadding(dp(13), dp(13), dp(13), dp(13));
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription(label);
        styleNavigationItem(item, selected);
        return item;
    }

    private void styleNavigationItem(ImageButton item, boolean selected) {
        item.setColorFilter(selected ? Color.rgb(103, 80, 164) : Color.rgb(32, 30, 34));
        item.setBackgroundColor(Color.TRANSPARENT);
    }

    private boolean allReady(boolean termux, boolean x11, boolean permission) {
        return termux && x11 && permission;
    }

    private void addStep(LinearLayout parent, String number, String title, String description,
                         String action, View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(rounded(Color.WHITE, 20));
        card.setElevation(dp(2));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);

        TextView numberView = text(number, 13, true);
        numberView.setTextColor(Color.WHITE);
        numberView.setGravity(Gravity.CENTER);
        numberView.setBackground(rounded(Color.rgb(103, 80, 164), 18));
        heading.addView(numberView, new LinearLayout.LayoutParams(dp(38), dp(38)));

        TextView titleView = text(title, 17, true);
        LinearLayout.LayoutParams titleLayout = new LinearLayout.LayoutParams(0, -2, 1);
        titleLayout.leftMargin = dp(12);
        heading.addView(titleView, titleLayout);
        card.addView(heading);

        TextView descriptionView = text(description, 14, false);
        descriptionView.setTextColor(Color.rgb(92, 88, 99));
        card.addView(descriptionView, smallGap());

        Button button = new Button(this);
        button.setText(action);
        button.setTextSize(13);
        button.setTextColor(Color.WHITE);
        button.setTypeface(button.getTypeface(), Typeface.BOLD);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        button.setBackground(rounded(Color.rgb(103, 80, 164), 14));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, dp(48));
        buttonParams.topMargin = dp(12);
        card.addView(button, buttonParams);
        parent.addView(card, spaced());
    }

    private LinearLayout.LayoutParams spaced() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(10);
        return params;
    }

    private LinearLayout.LayoutParams smallGap() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(6);
        return params;
    }

    private LinearLayout.LayoutParams sectionGap() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(22);
        params.bottomMargin = dp(4);
        return params;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
