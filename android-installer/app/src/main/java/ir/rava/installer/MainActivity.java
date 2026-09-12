package ir.rava.installer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressLint({"SetTextI18n", "UnspecifiedRegisterReceiverFlag"})
public class MainActivity extends Activity {
    private static final int RUN_PERMISSION_REQUEST = 41;
    private static final int PAGE_SETUP = 0;
    private static final int PAGE_CHAT = 1;
    private static final int PAGE_ARCHIVE = 2;
    private static final int PAGE_SETTINGS = 3;
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_READY = 1;
    private static final int STATUS_ACTION = 2;
    private static final String CHAT_ARCHIVE_PREFS = "chat_archive";
    private static final String CHAT_ARCHIVE_KEY = "chats";
    private static final String ENABLE_EXTERNAL_APPS =
            "mkdir -p ~/.termux && (grep -q '^allow-external-apps=true$' ~/.termux/termux.properties 2>/dev/null || echo 'allow-external-apps=true' >> ~/.termux/termux.properties) && termux-reload-settings";
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile(
            "!\\[([^\\]]*)\\]\\((https://[^\\s)]+)\\)");

    private TextView prerequisites;
    private TextView termuxStatus;
    private TextView termuxPermissionStatus;
    private TextView antigravityStatus;
    private TextView googleStatus;
    private TextView codexStatus;
    private TextView progress;
    private TextView output;
    private ProgressBar spinner;
    private Spinner modelSpinner;
    private ArrayAdapter<ProviderModel> modelAdapter;
    private EditText chatInput;
    private LinearLayout chatMessages;
    private ScrollView chatScroll;
    private TextView emptyChat;
    private TextView chatStatus;
    private ImageButton sendButton;
    private ImageButton cancelChatButton;
    private View setupPage;
    private View chatPage;
    private View archivePage;
    private View settingsPage;
    private LinearLayout archiveContent;
    private ImageButton setupDestination;
    private ImageButton chatDestination;
    private ImageButton archiveDestination;
    private ImageButton settingsDestination;
    private String conversationId;
    private String currentChatId;
    private String currentModel;
    private JSONArray currentMessages = new JSONArray();
    private Typeface chatTypeface;
    private AntigravityProvider antigravityProvider;
    private CodexProvider codexProvider;
    private TextView codexAuthStatus;
    private volatile ChatProvider activeChatProvider;
    private TextView activeAnswerBubble;
    private Runnable activeTypingAnimation;
    private final AtomicInteger chatGeneration = new AtomicInteger();
    private final AtomicInteger setupStatusGeneration = new AtomicInteger();
    private int currentPage = PAGE_SETUP;
    private boolean codexLoginPending;
    private boolean darkMode;
    private int termuxState = STATUS_PENDING;
    private int permissionState = STATUS_PENDING;
    private int antigravityState = STATUS_PENDING;
    private int googleState = STATUS_PENDING;
    private int codexState = STATUS_PENDING;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final BroadcastReceiver resultReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            renderLastResult();
            if (currentPage == PAGE_SETUP) refreshSetupStatus();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences appearance = getSharedPreferences("appearance", MODE_PRIVATE);
        darkMode = appearance.contains("dark_mode")
                ? appearance.getBoolean("dark_mode", false)
                : (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;
        applySystemBars();
        chatTypeface = getResources().getFont(R.font.vazirmatn_regular);
        antigravityProvider = new AntigravityProvider(this);
        codexProvider = new CodexProvider(this);
        if (savedInstanceState != null) {
            currentPage = savedInstanceState.getInt("current_page", PAGE_SETUP);
        }
        setTitle("Rava Setup");
        setContentView(buildUi());
        if (currentPage != PAGE_SETUP) navigateToPage(currentPage);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("current_page", currentPage);
        super.onSaveInstanceState(outState);
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
        refreshSetupStatus();
        renderLastResult();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(resultReceiver);
        super.onStop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RUN_PERMISSION_REQUEST) refreshSetupStatus();
    }

    @Override
    protected void onDestroy() {
        if (antigravityProvider != null) antigravityProvider.close();
        if (codexProvider != null) codexProvider.close();
        super.onDestroy();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(screenColor());

        FrameLayout pages = new FrameLayout(this);
        setupPage = buildSetupUi();
        chatPage = buildChatUi();
        archivePage = buildArchiveUi();
        settingsPage = buildSettingsUi();
        pages.addView(setupPage, new FrameLayout.LayoutParams(-1, -1));
        pages.addView(chatPage, new FrameLayout.LayoutParams(-1, -1));
        pages.addView(archivePage, new FrameLayout.LayoutParams(-1, -1));
        pages.addView(settingsPage, new FrameLayout.LayoutParams(-1, -1));
        chatPage.setVisibility(View.GONE);
        archivePage.setVisibility(View.GONE);
        settingsPage.setVisibility(View.GONE);

        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setPadding(dp(18), 0, dp(18), 0);
        navigation.setBackgroundColor(navigationColor());
        setupDestination = navigationItem(R.drawable.ic_home, "Setup", true);
        chatDestination = navigationItem(R.drawable.ic_chat, "Chat", false);
        archiveDestination = navigationItem(R.drawable.ic_history, "Archive", false);
        settingsDestination = navigationItem(R.drawable.ic_settings, "Settings", false);
        navigation.addView(setupDestination, new LinearLayout.LayoutParams(0, dp(50), 1));
        navigation.addView(chatDestination, new LinearLayout.LayoutParams(0, dp(50), 1));
        navigation.addView(archiveDestination, new LinearLayout.LayoutParams(0, dp(50), 1));
        navigation.addView(settingsDestination, new LinearLayout.LayoutParams(0, dp(50), 1));

        setupDestination.setOnClickListener(view -> navigateToPage(PAGE_SETUP));
        chatDestination.setOnClickListener(view -> navigateToPage(PAGE_CHAT));
        archiveDestination.setOnClickListener(view -> navigateToPage(PAGE_ARCHIVE));
        settingsDestination.setOnClickListener(view -> navigateToPage(PAGE_SETTINGS));

        root.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
        View topShadow = new View(this);
        topShadow.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0x24000000, 0x00000000}));
        root.addView(topShadow, new LinearLayout.LayoutParams(-1, dp(5)));
        root.addView(navigation, new LinearLayout.LayoutParams(-1, dp(48)));
        return root;
    }

    private void navigateToPage(int page) {
        currentPage = page;
        if (page != PAGE_CHAT) {
            chatInput.clearFocus();
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(chatInput.getWindowToken(), 0);
        }
        setupPage.setVisibility(page == PAGE_SETUP ? View.VISIBLE : View.GONE);
        chatPage.setVisibility(page == PAGE_CHAT ? View.VISIBLE : View.GONE);
        archivePage.setVisibility(page == PAGE_ARCHIVE ? View.VISIBLE : View.GONE);
        settingsPage.setVisibility(page == PAGE_SETTINGS ? View.VISIBLE : View.GONE);
        styleNavigationItem(setupDestination, page == PAGE_SETUP);
        styleNavigationItem(chatDestination, page == PAGE_CHAT);
        styleNavigationItem(archiveDestination, page == PAGE_ARCHIVE);
        styleNavigationItem(settingsDestination, page == PAGE_SETTINGS);
        if (page == PAGE_CHAT && modelAdapter.isEmpty()) loadModels();
        if (page == PAGE_ARCHIVE) renderChatArchive();
        if (page == PAGE_SETUP) refreshSetupStatus();
    }

    private View buildSetupUi() {
        int pad = dp(16);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, dp(12), pad, dp(28));
        content.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        content.setBackgroundColor(screenColor());

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(4), dp(4), dp(8));

        TextView badge = text("R", 20, true);
        badge.setTextColor(Color.WHITE);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(Color.rgb(103, 80, 164), 14));
        header.addView(badge, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout headerCopy = new LinearLayout(this);
        headerCopy.setOrientation(LinearLayout.VERTICAL);
        headerCopy.setPadding(dp(12), 0, 0, 0);
        TextView title = text("Rava", 24, true);
        title.setTextColor(primaryTextColor());
        headerCopy.addView(title);
        TextView subtitle = text("AI engines on this phone", 13, false);
        subtitle.setTextColor(secondaryTextColor());
        headerCopy.addView(subtitle);
        header.addView(headerCopy, new LinearLayout.LayoutParams(0, -2, 1));

        TextView refreshStatus = compactAction("Refresh", view -> refreshSetupStatus());
        header.addView(refreshStatus, new LinearLayout.LayoutParams(dp(82), dp(38)));
        content.addView(header);

        prerequisites = text("Checking your setup…", 16, true);
        prerequisites.setPadding(dp(16), dp(14), dp(16), dp(14));
        prerequisites.setBackground(rounded(softAccentColor(), 16));
        content.addView(prerequisites, spaced());

        TextView section = sectionTitle("GOOGLE · GEMINI");
        content.addView(section, sectionGap());

        LinearLayout googleCard = setupCard();
        termuxStatus = addSetupRow(googleCard, "Termux", "Required host app", "Open",
                view -> launchPackage(TermuxBridge.TERMUX_PACKAGE,
                        "https://github.com/termux/termux-app/releases"), true);
        termuxPermissionStatus = addSetupRow(googleCard, "Command access",
                "Lets Rava start the CLI", "Enable", view -> {
                    if (!isInstalled(TermuxBridge.TERMUX_PACKAGE)) {
                        launchPackage(TermuxBridge.TERMUX_PACKAGE,
                                "https://github.com/termux/termux-app/releases");
                    } else if (checkSelfPermission(TermuxBridge.RUN_PERMISSION)
                            != PackageManager.PERMISSION_GRANTED) {
                        boolean prepared = getSharedPreferences("setup_state", MODE_PRIVATE)
                                .getBoolean("termux_access_prepared", false);
                        if (prepared) requestRunPermission(); else prepareTermux();
                    } else {
                        showMessage("Termux access is already enabled.");
                    }
                }, true);
        antigravityStatus = addSetupRow(googleCard, "Antigravity CLI",
                "Google's command-line engine", "Install", view -> installAntigravity(), true);
        googleStatus = addSetupRow(googleCard, "Google account",
                "Used only by Google's official CLI", "Sign in",
                view -> startAntigravityLogin(), false);
        content.addView(googleCard);

        section = sectionTitle("OPENAI · CODEX");
        content.addView(section, sectionGap());

        LinearLayout codexCard = setupCard();
        TextView runtimeStatus = addSetupRow(codexCard, "Codex runtime",
                "Included inside Rava", null, null, true);
        setReadinessStatus(runtimeStatus, "✓ Built in", STATUS_READY);
        codexStatus = addSetupRow(codexCard, "ChatGPT account",
                "Your ChatGPT subscription", "Sign in", view -> {
                    codexLoginPending = true;
                    codexState = STATUS_PENDING;
                    setReadinessStatus(codexStatus, "Waiting…", STATUS_PENDING);
                    updateReadinessSummary();
                    codexProvider.startLogin(codexAccountListener());
                }, false);
        content.addView(codexCard);

        codexAuthStatus = text("Codex account has not been checked.", 14, false);
        codexAuthStatus.setVisibility(View.GONE);

        section = sectionTitle("SETUP ACTIVITY");
        content.addView(section, sectionGap());
        LinearLayout activityCard = setupCard();
        activityCard.setPadding(dp(16), dp(12), dp(16), dp(14));
        spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        spinner.setVisibility(View.GONE);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        activityCard.addView(spinner, spinnerParams);

        progress = text("No setup task is running", 14, true);
        progress.setTextColor(primaryTextColor());
        activityCard.addView(progress, smallGap());

        output = text("No command has run yet.", 13, false);
        output.setTextColor(secondaryTextColor());
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextDirection(View.TEXT_DIRECTION_LTR);
        output.setGravity(Gravity.START);
        output.setTextIsSelectable(true);
        output.setMovementMethod(new ScrollingMovementMethod());
        output.setBackground(rounded(softSurfaceColor(), 12));
        output.setPadding(dp(12), dp(10), dp(12), dp(10));
        activityCard.addView(output, new LinearLayout.LayoutParams(-1, dp(112)));
        content.addView(activityCard);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        return scroll;
    }

    private View buildChatUi() {
        int pad = dp(16);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, pad);
        content.setBackgroundColor(screenColor());

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = chatText("Chat", 24, true);
        title.setTextColor(primaryTextColor());
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        ImageButton newChat = iconButton(R.drawable.ic_new_chat, "New chat", primaryTextColor());
        newChat.setOnClickListener(view -> resetChat());
        header.addView(newChat, new LinearLayout.LayoutParams(dp(48), dp(48)));
        cancelChatButton = iconButton(R.drawable.ic_cancel, "Cancel response", Color.rgb(176, 0, 32));
        cancelChatButton.setEnabled(false);
        cancelChatButton.setOnClickListener(view -> cancelActiveChat());
        header.addView(cancelChatButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        content.addView(header);

        LinearLayout modelRow = new LinearLayout(this);
        modelRow.setOrientation(LinearLayout.HORIZONTAL);
        modelRow.setGravity(Gravity.CENTER_VERTICAL);
        modelSpinner = new Spinner(this);
        modelAdapter = new ThemedModelAdapter();
        modelSpinner.setAdapter(modelAdapter);
        modelSpinner.setPadding(dp(8), 0, dp(8), 0);
        modelSpinner.setBackground(strokedRounded(cardColor(), borderColor(), 14));
        modelRow.addView(modelSpinner, new LinearLayout.LayoutParams(0, dp(46), 1));
        TextView reload = compactAction("Reload", view -> loadModels());
        LinearLayout.LayoutParams reloadParams = new LinearLayout.LayoutParams(dp(92), dp(46));
        reloadParams.leftMargin = dp(8);
        modelRow.addView(reload, reloadParams);
        content.addView(modelRow, smallGap());

        chatStatus = chatText("Open Chat after starting the engine.", 12, false);
        chatStatus.setTextColor(secondaryTextColor());
        content.addView(chatStatus, smallGap());

        chatMessages = new LinearLayout(this);
        chatMessages.setOrientation(LinearLayout.VERTICAL);
        chatMessages.setPadding(dp(2), dp(18), dp(2), dp(18));
        emptyChat = chatText("How can I help?", 24, true);
        emptyChat.setGravity(Gravity.CENTER);
        emptyChat.setTextColor(primaryTextColor());
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
        composer.setBackground(strokedRounded(cardColor(), borderColor(), 24));
        composer.setElevation(dp(2));

        chatInput = new EditText(this);
        chatInput.setHint("پیام خود را بنویسید…");
        chatInput.setTextSize(16);
        chatInput.setTypeface(chatTypeface);
        chatInput.setTextColor(primaryTextColor());
        chatInput.setHintTextColor(secondaryTextColor());
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
        sendButton.setBackground(rounded(Color.rgb(103, 80, 164), 22));
        sendButton.setOnClickListener(view -> sendChatMessage());
        composer.addView(sendButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout.LayoutParams composerParams = new LinearLayout.LayoutParams(-1, -2);
        composerParams.topMargin = dp(8);
        composerParams.bottomMargin = dp(8);
        content.addView(composer, composerParams);

        return content;
    }

    private View buildArchiveUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(12), dp(16), dp(8));
        page.setBackgroundColor(screenColor());

        TextView title = chatText("Archive", 24, true);
        title.setTextColor(primaryTextColor());
        title.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(title, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView subtitle = chatText("Saved conversations", 13, false);
        subtitle.setTextColor(secondaryTextColor());
        page.addView(subtitle);

        archiveContent = new LinearLayout(this);
        archiveContent.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(-1, 0, 1);
        contentParams.topMargin = dp(12);
        page.addView(archiveContent, contentParams);
        return page;
    }

    private View buildSettingsUi() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(28));
        content.setBackgroundColor(screenColor());

        TextView title = chatText("Settings", 24, true);
        title.setTextColor(primaryTextColor());
        title.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(title, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView appearanceTitle = sectionTitle("APPEARANCE");
        content.addView(appearanceTitle, sectionGap());

        LinearLayout appearanceCard = setupCard();
        LinearLayout themeRow = new LinearLayout(this);
        themeRow.setOrientation(LinearLayout.HORIZONTAL);
        themeRow.setGravity(Gravity.CENTER_VERTICAL);
        themeRow.setPadding(dp(2), dp(12), dp(2), dp(12));
        LinearLayout themeCopy = new LinearLayout(this);
        themeCopy.setOrientation(LinearLayout.VERTICAL);
        TextView themeTitle = text("Dark mode", 16, true);
        themeTitle.setTextColor(primaryTextColor());
        themeCopy.addView(themeTitle);
        TextView themeDetail = text("Use a dark palette across every screen", 12, false);
        themeDetail.setTextColor(secondaryTextColor());
        themeCopy.addView(themeDetail, smallGap());
        themeRow.addView(themeCopy, new LinearLayout.LayoutParams(0, -2, 1));
        Switch darkSwitch = new Switch(this);
        darkSwitch.setContentDescription("Dark mode");
        darkSwitch.setChecked(darkMode);
        darkSwitch.setOnCheckedChangeListener((button, checked) -> setDarkMode(checked));
        themeRow.addView(darkSwitch, new LinearLayout.LayoutParams(dp(56), dp(48)));
        appearanceCard.addView(themeRow);
        content.addView(appearanceCard);

        TextView aboutTitle = sectionTitle("ABOUT");
        content.addView(aboutTitle, sectionGap());
        LinearLayout aboutCard = setupCard();
        addInformationRow(aboutCard, "Rava", "Version 1.0.1", true);
        addInformationRow(aboutCard, "Codex", "Embedded runtime · 6 models", true);
        addInformationRow(aboutCard, "Gemini", "Antigravity via Termux · 14 models", false);
        content.addView(aboutCard);

        TextView note = text("Your appearance choice is stored only on this phone.", 12, false);
        note.setTextColor(secondaryTextColor());
        content.addView(note, spaced());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        return scroll;
    }

    private void addInformationRow(LinearLayout parent, String label, String value,
            boolean divider) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(13), dp(2), dp(13));
        TextView labelView = text(label, 15, true);
        labelView.setTextColor(primaryTextColor());
        row.addView(labelView, new LinearLayout.LayoutParams(0, -2, 1));
        TextView valueView = text(value, 12, false);
        valueView.setTextColor(secondaryTextColor());
        valueView.setGravity(Gravity.END);
        row.addView(valueView, new LinearLayout.LayoutParams(-2, -2));
        parent.addView(row);
        if (divider) {
            View line = new View(this);
            line.setBackgroundColor(borderColor());
            parent.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        }
    }

    private void setDarkMode(boolean enabled) {
        if (darkMode == enabled) return;
        getSharedPreferences("appearance", MODE_PRIVATE).edit()
                .putBoolean("dark_mode", enabled).apply();
        recreate();
    }

    private void applySystemBars() {
        getWindow().setStatusBarColor(screenColor());
        getWindow().setNavigationBarColor(navigationColor());
        int flags = 0;
        if (!darkMode) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private void prepareTermux() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Enable Rava", ENABLE_EXTERNAL_APPS));
        getSharedPreferences("setup_state", MODE_PRIVATE).edit()
                .putBoolean("termux_access_prepared", true).apply();
        Toast.makeText(this, "Command copied. Paste and run it in Termux.", Toast.LENGTH_LONG).show();
        launchPackage(TermuxBridge.TERMUX_PACKAGE, "https://github.com/termux/termux-app/releases");
    }

    private void installAntigravity() {
        try {
            String root = "$HOME/.local/share/rava-antigravity/installer";
            StringBuilder script = new StringBuilder("set -eu\n")
                    .append("mkdir -p \"").append(root).append("/scripts\"\n");
            appendAssetInstall(script, "antigravity/antigravity-termux.env",
                    root + "/antigravity-termux.env", false);
            appendAssetInstall(script, "antigravity/scripts/install-antigravity-termux.sh",
                    root + "/scripts/install-antigravity-termux.sh", true);
            appendAssetInstall(script, "antigravity/scripts/run-antigravity-termux.sh",
                    root + "/scripts/run-antigravity-termux.sh", true);
            script.append("if ! \"").append(root)
                    .append("/scripts/install-antigravity-termux.sh\" verify; then\n")
                    .append("  \"").append(root)
                    .append("/scripts/install-antigravity-termux.sh\" install\n")
                    .append("fi\n");
            runCommand("Install Antigravity", script.toString(), true);
        } catch (IOException error) {
            showMessage("Could not prepare the verified Antigravity installer: "
                    + error.getMessage());
        }
    }

    private void appendAssetInstall(StringBuilder script, String asset, String destination,
            boolean executable) throws IOException {
        String encoded = android.util.Base64.encodeToString(readAsset(asset),
                android.util.Base64.NO_WRAP);
        script.append("printf '%s' '").append(encoded).append("' | base64 -d > \"")
                .append(destination).append("\"\n")
                .append("chmod ").append(executable ? "700" : "600").append(" \"")
                .append(destination).append("\"\n");
    }

    private void startAntigravityLogin() {
        String runner = "$HOME/.local/share/rava-antigravity/installer/scripts/"
                + "run-antigravity-termux.sh";
        runCommand("Google sign-in", "set -eu\nRAVA_AGY_REMOTE_AUTH=1 \"" + runner
                + "\"\n", false);
        uiHandler.postDelayed(() -> launchPackage(TermuxBridge.TERMUX_PACKAGE,
                "https://github.com/termux/termux-app/releases"), 600);
    }

    private CodexProvider.AccountListener codexAccountListener() {
        return new CodexProvider.AccountListener() {
            @Override public void onStatus(String status) {
                runOnUiThread(() -> {
                    codexAuthStatus.setText(status);
                    setReadinessStatus(codexStatus, "Checking…", STATUS_PENDING);
                });
            }

            @Override public void onAccount(boolean authenticated, String accountType,
                    String planType) {
                String value = authenticated
                        ? "Codex signed in" + (planType == null ? "" : " — " + planType)
                        : "Codex is signed out.";
                runOnUiThread(() -> {
                    codexLoginPending = false;
                    codexState = authenticated ? STATUS_READY : STATUS_ACTION;
                    setReadinessStatus(codexStatus,
                            authenticated ? "✓ Signed in" : "Sign-in required", codexState);
                    codexAuthStatus.setText(value);
                    updateReadinessSummary();
                });
            }

            @Override public void onDeviceCode(String verificationUrl, String userCode) {
                runOnUiThread(() -> {
                    codexAuthStatus.setText("Enter this one-time code on the official page:\n\n"
                            + userCode + "\n\n" + verificationUrl);
                    if (CodexAuthProtocol.isTrustedVerificationUrl(verificationUrl)) {
                        showCodexDeviceCode(verificationUrl, userCode);
                    }
                });
            }

            @Override public void onLoginCompleted(boolean success, String error) {
                runOnUiThread(() -> {
                    codexLoginPending = false;
                    codexAuthStatus.setText(success
                            ? "Codex sign-in completed."
                            : "Codex sign-in failed: "
                                    + (error == null ? "unknown error" : error));
                    if (success) {
                        codexState = STATUS_READY;
                        setReadinessStatus(codexStatus, "✓ Signed in", STATUS_READY);
                    } else {
                        codexState = STATUS_ACTION;
                        setReadinessStatus(codexStatus, "Could not sign in", STATUS_ACTION);
                    }
                    updateReadinessSummary();
                });
            }

            @Override public void onError(String error) {
                runOnUiThread(() -> {
                    codexLoginPending = false;
                    codexState = STATUS_ACTION;
                    codexAuthStatus.setText("Codex error: " + error);
                    setReadinessStatus(codexStatus, "Could not verify", STATUS_ACTION);
                    updateReadinessSummary();
                });
            }
        };
    }

    private void checkCodexAccount(int generation) {
        codexState = STATUS_PENDING;
        setReadinessStatus(codexStatus, "Checking…", STATUS_PENDING);
        updateReadinessSummary();
        codexProvider.readAccount(new CodexProvider.AccountListener() {
            @Override public void onStatus(String status) {}

            @Override public void onAccount(boolean authenticated, String accountType,
                    String planType) {
                runOnUiThread(() -> {
                    if (setupStatusGeneration.get() != generation) return;
                    codexState = authenticated ? STATUS_READY : STATUS_ACTION;
                    setReadinessStatus(codexStatus,
                            authenticated ? "✓ Signed in" : "Sign-in required", codexState);
                    codexAuthStatus.setText(authenticated
                            ? "Codex signed in" + (planType == null ? "" : " — " + planType)
                            : "Codex is signed out.");
                    updateReadinessSummary();
                });
            }

            @Override public void onDeviceCode(String verificationUrl, String userCode) {}
            @Override public void onLoginCompleted(boolean success, String error) {}

            @Override public void onError(String error) {
                runOnUiThread(() -> {
                    if (setupStatusGeneration.get() != generation) return;
                    codexState = STATUS_ACTION;
                    setReadinessStatus(codexStatus, "Could not verify", STATUS_ACTION);
                    codexAuthStatus.setText("Codex status could not be checked.");
                    updateReadinessSummary();
                });
            }
        });
    }

    private void showCodexDeviceCode(String verificationUrl, String userCode) {
        TextView code = chatText(userCode, 24, true);
        code.setTextIsSelectable(true);
        code.setGravity(Gravity.CENTER);
        code.setPadding(dp(24), dp(28), dp(24), dp(28));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("ChatGPT sign-in code")
                .setMessage("Copy this one-time code, then enter it on the official page.")
                .setView(code)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Copy code", null)
                .setPositiveButton("Open official page", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                ClipboardManager clipboard = (ClipboardManager)
                        getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText(
                        "ChatGPT one-time sign-in code", userCode));
                Toast.makeText(this, "Code copied", Toast.LENGTH_SHORT).show();
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view ->
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(verificationUrl))));
        });
        dialog.show();
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
        List<ProviderModel> loaded = Collections.synchronizedList(new ArrayList<>());
        List<String> errors = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger pending = new AtomicInteger(2);
        Runnable finish = () -> {
            if (pending.decrementAndGet() != 0) return;
            List<ProviderModel> models;
            synchronized (loaded) { models = new ArrayList<>(loaded); }
            models.sort((left, right) -> Integer.compare(
                    modelPriority(left.archiveId()), modelPriority(right.archiveId())));
            runOnUiThread(() -> {
                modelAdapter.clear();
                modelAdapter.addAll(models);
                modelAdapter.notifyDataSetChanged();
                if (currentModel != null) selectArchivedModel(currentModel);
                if (models.isEmpty()) {
                    chatStatus.setText("No provider is ready. Complete Setup first.\n"
                            + String.join("\n", errors));
                } else {
                    chatStatus.setText(models.size() + " model"
                            + (models.size() == 1 ? "" : "s") + " available"
                            + (errors.isEmpty() ? "" : " — one provider needs setup"));
                }
            });
        };
        ChatProvider.Result<List<ProviderModel>> collector =
                new ChatProvider.Result<List<ProviderModel>>() {
            @Override public void onSuccess(List<ProviderModel> models) {
                loaded.addAll(models);
                finish.run();
            }

            @Override public void onError(String message) {
                errors.add(message);
                finish.run();
            }
        };
        antigravityProvider.listModels(collector);
        codexProvider.listModels(collector);
    }

    private void sendChatMessage() {
        String message = chatInput.getText().toString().trim();
        ProviderModel selected = (ProviderModel) modelSpinner.getSelectedItem();
        if (message.isEmpty()) {
            chatStatus.setText("Type a message first.");
            return;
        }
        if (selected == null) {
            chatStatus.setText("Load and select a model first.");
            return;
        }

        String model = selected.archiveId();
        ensureCurrentChat(model);
        appendCurrentMessage("user", message);
        saveCurrentChat();
        addMessageBubble("You", message, true);
        TextView answerBubble = addMessageBubble(selected.displayName, "...", false);
        Runnable typingAnimation = startTypingAnimation(answerBubble);
        int requestGeneration = chatGeneration.incrementAndGet();
        chatInput.setText("");
        focusChatInput();
        sendButton.setEnabled(false);
        cancelChatButton.setEnabled(true);
        chatStatus.setText("Waiting for " + model + "…");

        ChatProvider provider = "codex".equals(selected.providerId)
                ? codexProvider : antigravityProvider;
        activeChatProvider = provider;
        activeAnswerBubble = answerBubble;
        activeTypingAnimation = typingAnimation;
        provider.send(new ChatProvider.Request(
                selected.modelId, conversationId, message),
                new ChatProvider.Result<ChatProvider.Response>() {
            @Override public void onSuccess(ChatProvider.Response response) {
                if (chatGeneration.get() != requestGeneration) return;
                runOnUiThread(() -> {
                    stopTypingAnimation(answerBubble, typingAnimation);
                    conversationId = response.conversationId;
                    String answer = response.text;
                    if (answer.isEmpty()) {
                        answerBubble.setText("(Empty response)");
                    } else {
                        renderRichAnswer(answerBubble, answer);
                    }
                    appendCurrentMessage("assistant", answer);
                    saveCurrentChat();
                    chatStatus.setText("Conversation active");
                    sendButton.setEnabled(true);
                    cancelChatButton.setEnabled(false);
                    activeChatProvider = null;
                    focusChatInput();
                    scrollChatToBottom();
                });
            }

            @Override public void onError(String error) {
                if (chatGeneration.get() != requestGeneration) return;
                runOnUiThread(() -> {
                    stopTypingAnimation(answerBubble, typingAnimation);
                    answerBubble.setText(error);
                    answerBubble.setTextColor(Color.rgb(176, 0, 32));
                    chatStatus.setText("Request failed");
                    sendButton.setEnabled(true);
                    cancelChatButton.setEnabled(false);
                    activeChatProvider = null;
                    focusChatInput();
                    scrollChatToBottom();
                });
            }
        });
    }

    private void cancelActiveChat() {
        ChatProvider provider = activeChatProvider;
        if (provider == null) return;
        chatGeneration.incrementAndGet();
        provider.cancel();
        activeChatProvider = null;
        if (activeAnswerBubble != null && activeTypingAnimation != null) {
            stopTypingAnimation(activeAnswerBubble, activeTypingAnimation);
            activeAnswerBubble.setText("Canceled");
        }
        activeAnswerBubble = null;
        activeTypingAnimation = null;
        sendButton.setEnabled(true);
        cancelChatButton.setEnabled(false);
        chatStatus.setText("Response canceled");
        focusChatInput();
    }

    private void renderRichAnswer(TextView bubble, String answer) {
        Matcher matcher = MARKDOWN_IMAGE.matcher(answer);
        StringBuffer plainText = new StringBuffer();
        List<String[]> images = new ArrayList<>();
        while (matcher.find()) {
            images.add(new String[]{matcher.group(2), matcher.group(1)});
            matcher.appendReplacement(plainText, "");
        }
        matcher.appendTail(plainText);
        String visibleText = plainText.toString()
                .replaceAll("(?m)^_\\d+\\s*$", "")
                .trim();
        bubble.setText(visibleText);
        bubble.setVisibility(visibleText.isEmpty() ? View.GONE : View.VISIBLE);
        applyMessageDirection(bubble, visibleText);

        LinearLayout group = (LinearLayout) bubble.getParent();
        int imageWidth = Math.min(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.84f), dp(420));
        for (String[] image : images) {
            String source = image[0];
            ImageView imageView = new ImageView(this);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setContentDescription(image[1].isEmpty() ? "Response image" : image[1]);
            imageView.setBackground(rounded(softSurfaceColor(), 18));
            imageView.setClipToOutline(true);
            imageView.setOnClickListener(view -> startActivity(
                    new Intent(Intent.ACTION_VIEW, Uri.parse(source))));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(imageWidth, dp(220));
            params.topMargin = dp(8);
            group.addView(imageView, params);
            loadImage(imageView, source);
        }
    }

    private void loadImage(ImageView imageView, String url) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                connection.setRequestProperty("Accept", "image/*");
                connection.setRequestProperty("X-Rava-App-Id", "ir.rava.installer.chat");
                Bitmap bitmap;
                try (InputStream stream = connection.getInputStream()) {
                    bitmap = BitmapFactory.decodeStream(stream);
                }
                if (bitmap == null) throw new IOException("Image data could not be decoded");
                Bitmap loaded = bitmap;
                runOnUiThread(() -> imageView.setImageBitmap(loaded));
            } catch (Exception exception) {
                Log.e("RavaImage", "Could not load " + url, exception);
                runOnUiThread(() -> {
                    imageView.setScaleType(ImageView.ScaleType.CENTER);
                    imageView.setImageResource(android.R.drawable.ic_menu_report_image);
                    imageView.setContentDescription("Tap to open image");
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private Runnable startTypingAnimation(TextView bubble) {
        Runnable animation = new Runnable() {
            private int frame;
            private final String[] frames = {".", "..", "..."};

            @Override public void run() {
                if (bubble.getTag() != this) return;
                bubble.setText(frames[frame++ % frames.length]);
                uiHandler.postDelayed(this, 320);
            }
        };
        bubble.setTag(animation);
        uiHandler.post(animation);
        return animation;
    }

    private void stopTypingAnimation(TextView bubble, Runnable animation) {
        if (bubble.getTag() == animation) bubble.setTag(null);
        uiHandler.removeCallbacks(animation);
    }

    private int modelPriority(String model) {
        if ("codex/gpt-5.6-sol".equals(model)) return 0;
        if ("antigravity/gemini-3.8-flash-low".equals(model)) return 1;
        if (model.startsWith("antigravity/gemini-")) return 2;
        if (model.startsWith("antigravity/")) return 3;
        if ("codex/gpt-6-astra".equals(model)) return 5;
        return 4;
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
        authorView.setTextColor(secondaryTextColor());
        group.addView(authorView);

        TextView bubble = chatText(message, 16, false);
        bubble.setTextIsSelectable(true);
        bubble.setLineSpacing(0, 1.15f);
        bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.84f));
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setTextColor(primaryTextColor());
        bubble.setBackground(user
                ? rounded(darkMode ? Color.rgb(70, 53, 112) : Color.rgb(235, 229, 248), 18)
                : strokedRounded(cardColor(), borderColor(), 18));
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
        chatScroll.post(() -> chatScroll.smoothScrollTo(0, chatMessages.getBottom()));
    }

    private void resetChat() {
        conversationId = null;
        currentChatId = null;
        currentModel = null;
        currentMessages = new JSONArray();
        clearChatTranscript();
        chatStatus.setText("New conversation ready");
        focusChatInput();
    }

    private void clearChatTranscript() {
        chatMessages.removeAllViews();
        emptyChat = chatText("How can I help?", 24, true);
        emptyChat.setGravity(Gravity.CENTER);
        emptyChat.setTextColor(primaryTextColor());
        chatMessages.addView(emptyChat, new LinearLayout.LayoutParams(-1, dp(180)));
    }

    private void ensureCurrentChat(String model) {
        if (currentChatId == null || !model.equals(currentModel)) {
            currentChatId = UUID.randomUUID().toString();
            currentModel = model;
            conversationId = null;
            currentMessages = new JSONArray();
            clearChatTranscript();
        }
    }

    private void appendCurrentMessage(String role, String content) {
        try {
            currentMessages.put(new JSONObject()
                    .put("role", role)
                    .put("content", content));
        } catch (Exception exception) {
            Log.e("RavaArchive", "Could not append chat message", exception);
        }
    }

    private JSONArray readChatArchive() {
        String saved = getSharedPreferences(CHAT_ARCHIVE_PREFS, MODE_PRIVATE)
                .getString(CHAT_ARCHIVE_KEY, "[]");
        try {
            return new JSONArray(saved);
        } catch (Exception exception) {
            Log.e("RavaArchive", "Could not read chat archive", exception);
            return new JSONArray();
        }
    }

    private void saveCurrentChat() {
        if (currentChatId == null || currentMessages.length() == 0) return;
        try {
            JSONObject chat = new JSONObject()
                    .put("id", currentChatId)
                    .put("title", currentChatTitle())
                    .put("model", currentModel == null ? "" : currentModel)
                    .put("conversation_id", conversationId == null ? "" : conversationId)
                    .put("updated_at", System.currentTimeMillis())
                    .put("messages", new JSONArray(currentMessages.toString()));
            JSONArray existing = readChatArchive();
            JSONArray updated = new JSONArray().put(chat);
            for (int index = 0; index < existing.length(); index++) {
                JSONObject item = existing.optJSONObject(index);
                if (item != null && !currentChatId.equals(item.optString("id"))) updated.put(item);
            }
            getSharedPreferences(CHAT_ARCHIVE_PREFS, MODE_PRIVATE)
                    .edit().putString(CHAT_ARCHIVE_KEY, updated.toString()).apply();
        } catch (Exception exception) {
            Log.e("RavaArchive", "Could not save chat", exception);
        }
    }

    private String currentChatTitle() {
        for (int index = 0; index < currentMessages.length(); index++) {
            JSONObject item = currentMessages.optJSONObject(index);
            if (item != null && "user".equals(item.optString("role"))) {
                String text = item.optString("content").replace('\n', ' ').trim();
                return text.length() > 42 ? text.substring(0, 42) + "…" : text;
            }
        }
        return "Untitled chat";
    }

    private void renderChatArchive() {
        archiveContent.removeAllViews();
        JSONArray chats = readChatArchive();

        if (chats.length() == 0) {
            LinearLayout emptyState = new LinearLayout(this);
            emptyState.setOrientation(LinearLayout.VERTICAL);
            emptyState.setGravity(Gravity.CENTER);
            emptyState.setPadding(dp(24), dp(32), dp(24), dp(32));

            ImageView emptyIcon = new ImageView(this);
            emptyIcon.setImageResource(R.drawable.ic_history);
            emptyIcon.setColorFilter(secondaryTextColor());
            emptyIcon.setContentDescription("No archived chats");
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(56), dp(56));
            iconParams.gravity = Gravity.CENTER_HORIZONTAL;
            iconParams.bottomMargin = dp(18);
            emptyState.addView(emptyIcon, iconParams);

            TextView emptyTitle = chatText("No chats yet", 19, true);
            emptyTitle.setGravity(Gravity.CENTER);
            emptyState.addView(emptyTitle, new LinearLayout.LayoutParams(-1, -2));

            TextView emptyDescription = chatText(
                    "Your conversations will appear here after you send a message.", 14, false);
            emptyDescription.setTextColor(secondaryTextColor());
            emptyDescription.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
            descriptionParams.topMargin = dp(8);
            emptyState.addView(emptyDescription, descriptionParams);
            archiveContent.addView(emptyState, new LinearLayout.LayoutParams(-1, 0, 1));
            return;
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button selectAll = new Button(this);
        selectAll.setText("Select all");
        selectAll.setAllCaps(false);
        selectAll.setTypeface(chatTypeface);
        Button deleteSelected = new Button(this);
        deleteSelected.setText("Delete selected");
        deleteSelected.setAllCaps(false);
        deleteSelected.setTypeface(chatTypeface);
        deleteSelected.setEnabled(false);
        actions.addView(selectAll, new LinearLayout.LayoutParams(0, dp(48), 1));
        actions.addView(deleteSelected, new LinearLayout.LayoutParams(0, dp(48), 1));
        archiveContent.addView(actions);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(8), 0, dp(8));
        List<CheckBox> checkBoxes = new ArrayList<>();
        Set<String> selectedIds = new HashSet<>();

        for (int index = 0; index < chats.length(); index++) {
            JSONObject chat = chats.optJSONObject(index);
            if (chat == null) continue;
            String chatId = chat.optString("id");

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(10), dp(8), dp(10));
            row.setBackground(strokedRounded(cardColor(), borderColor(), 16));

            CheckBox checkBox = new CheckBox(this);
            checkBox.setContentDescription("Select " + chat.optString("title"));
            checkBox.setOnCheckedChangeListener((button, checked) -> {
                if (checked) selectedIds.add(chatId); else selectedIds.remove(chatId);
                deleteSelected.setEnabled(!selectedIds.isEmpty());
                selectAll.setText(selectedIds.size() == checkBoxes.size()
                        ? "Clear selection" : "Select all");
            });
            checkBoxes.add(checkBox);
            row.addView(checkBox, new LinearLayout.LayoutParams(dp(48), dp(48)));

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setPadding(dp(4), dp(4), dp(8), dp(4));
            TextView chatTitle = chatText(chat.optString("title", "Untitled chat"), 15, true);
            TextView detail = chatText(chat.optString("model"), 11, false);
            detail.setTextColor(secondaryTextColor());
            labels.addView(chatTitle);
            labels.addView(detail);
            labels.setOnClickListener(view -> openArchivedChat(chat));
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

            ImageButton delete = iconButton(
                    R.drawable.ic_delete, "Delete chat", Color.rgb(176, 0, 32));
            delete.setOnClickListener(view -> confirmDeleteChats(
                    java.util.Collections.singleton(chatId)));
            row.addView(delete, new LinearLayout.LayoutParams(dp(48), dp(48)));

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.bottomMargin = dp(8);
            list.addView(row, rowParams);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        archiveContent.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        selectAll.setOnClickListener(view -> {
            boolean shouldSelect = selectedIds.size() != checkBoxes.size();
            for (CheckBox checkBox : checkBoxes) checkBox.setChecked(shouldSelect);
        });
        deleteSelected.setOnClickListener(view -> {
            if (!selectedIds.isEmpty()) confirmDeleteChats(new HashSet<>(selectedIds));
        });
    }

    private void openArchivedChat(JSONObject chat) {
        try {
            currentChatId = chat.getString("id");
            currentModel = chat.optString("model");
            conversationId = chat.optString("conversation_id");
            if (conversationId.isEmpty()) conversationId = null;
            currentMessages = new JSONArray(chat.getJSONArray("messages").toString());
            clearChatTranscript();
            for (int index = 0; index < currentMessages.length(); index++) {
                JSONObject message = currentMessages.getJSONObject(index);
                String role = message.getString("role");
                String text = message.getString("content");
                boolean user = "user".equals(role);
                TextView bubble = addMessageBubble(user ? "You" : currentModel, text, user);
                if (!user) renderRichAnswer(bubble, text);
            }
            navigateToPage(PAGE_CHAT);
            selectArchivedModel(currentModel);
            chatStatus.setText("Archived conversation");
            focusChatInput();
            scrollChatToBottom();
        } catch (Exception exception) {
            Log.e("RavaArchive", "Could not open archived chat", exception);
            Toast.makeText(this, "Could not open this chat.", Toast.LENGTH_SHORT).show();
        }
    }

    private void selectArchivedModel(String model) {
        for (int index = 0; index < modelAdapter.getCount(); index++) {
            ProviderModel item = modelAdapter.getItem(index);
            if (item != null && model.equals(item.archiveId())) {
                modelSpinner.setSelection(index);
                return;
            }
        }
    }

    private void confirmDeleteChats(Set<String> ids) {
        int count = ids.size();
        new AlertDialog.Builder(this)
                .setTitle(count == 1 ? "Delete this chat?" : "Delete " + count + " chats?")
                .setMessage("This deletes Rava's local archive. The official CLIs do not expose "
                        + "a verified remote-delete command, so their provider session may remain.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> deleteChatsFromProviders(ids))
                .show();
    }

    private void deleteChatsFromProviders(Set<String> ids) {
        deleteArchivedChats(ids);
        renderChatArchive();
        Toast.makeText(this, ids.size() == 1
                ? "Local chat deleted." : ids.size() + " local chats deleted.",
                Toast.LENGTH_SHORT).show();
    }

    private void deleteArchivedChats(Set<String> ids) {
        JSONArray chats = readChatArchive();
        JSONArray kept = new JSONArray();
        for (int index = 0; index < chats.length(); index++) {
            JSONObject chat = chats.optJSONObject(index);
            if (chat != null && !ids.contains(chat.optString("id"))) kept.put(chat);
        }
        getSharedPreferences(CHAT_ARCHIVE_PREFS, MODE_PRIVATE)
                .edit().putString(CHAT_ARCHIVE_KEY, kept.toString()).apply();
        if (currentChatId != null && ids.contains(currentChatId)) {
            conversationId = null;
            currentChatId = null;
            currentModel = null;
            currentMessages = new JSONArray();
            clearChatTranscript();
            chatStatus.setText("New conversation ready");
        }
    }

    private void focusChatInput() {
        chatInput.requestFocus();
        chatInput.post(() -> {
            chatInput.requestFocus();
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                    .showSoftInput(chatInput, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void refreshSetupStatus() {
        if (prerequisites == null) return;
        int generation = setupStatusGeneration.incrementAndGet();
        boolean termux = isInstalled(TermuxBridge.TERMUX_PACKAGE);
        boolean permission = checkSelfPermission(TermuxBridge.RUN_PERMISSION) == PackageManager.PERMISSION_GRANTED;
        termuxState = termux ? STATUS_READY : STATUS_ACTION;
        permissionState = permission ? STATUS_READY : STATUS_ACTION;
        setReadinessStatus(termuxStatus, termux ? "✓ Installed" : "Install", termuxState);
        setReadinessStatus(termuxPermissionStatus, permission ? "✓ Granted" : "Grant access",
                permissionState);

        if (!termux || !permission) {
            antigravityState = STATUS_ACTION;
            googleState = STATUS_ACTION;
            setReadinessStatus(antigravityStatus, "Setup required", STATUS_ACTION);
            setReadinessStatus(googleStatus, "Setup required", STATUS_ACTION);
        } else {
            SharedPreferences commands = getSharedPreferences("command_results", MODE_PRIVATE);
            int activeId = commands.getInt("active_id", -1);
            if (activeId >= 0) {
                String label = commands.getString("active_label", "Setup");
                antigravityState = STATUS_PENDING;
                googleState = STATUS_PENDING;
                setReadinessStatus(antigravityStatus,
                        "Google sign-in".equals(label) ? "Check after sign-in" : "Working…",
                        STATUS_PENDING);
                setReadinessStatus(googleStatus,
                        "Google sign-in".equals(label) ? "Signing in…" : "Waiting…",
                        STATUS_PENDING);
            } else {
                antigravityState = STATUS_PENDING;
                googleState = STATUS_PENDING;
                setReadinessStatus(antigravityStatus, "Checking…", STATUS_PENDING);
                setReadinessStatus(googleStatus, "Waiting…", STATUS_PENDING);
                antigravityProvider.checkInstallation(new ChatProvider.Result<Boolean>() {
                    @Override public void onSuccess(Boolean installed) {
                        runOnUiThread(() -> {
                            if (setupStatusGeneration.get() != generation) return;
                            antigravityState = installed ? STATUS_READY : STATUS_ACTION;
                            setReadinessStatus(antigravityStatus,
                                    installed ? "✓ Installed" : "Install", antigravityState);
                            if (!installed) {
                                googleState = STATUS_ACTION;
                                setReadinessStatus(googleStatus, "Setup required", STATUS_ACTION);
                                updateReadinessSummary();
                                return;
                            }
                            setReadinessStatus(googleStatus, "Checking…", STATUS_PENDING);
                            checkGoogleAccount(generation);
                            updateReadinessSummary();
                        });
                    }

                    @Override public void onError(String message) {
                        runOnUiThread(() -> {
                            if (setupStatusGeneration.get() != generation) return;
                            antigravityState = STATUS_ACTION;
                            googleState = STATUS_ACTION;
                            setReadinessStatus(antigravityStatus, "Could not verify", STATUS_ACTION);
                            setReadinessStatus(googleStatus, "Could not verify", STATUS_ACTION);
                            updateReadinessSummary();
                        });
                    }
                });
            }
        }

        if (codexLoginPending) {
            codexState = STATUS_PENDING;
            setReadinessStatus(codexStatus, "Waiting for sign-in…", STATUS_PENDING);
        } else {
            checkCodexAccount(generation);
        }
        updateReadinessSummary();
    }

    private void checkGoogleAccount(int generation) {
        antigravityProvider.checkReadiness(new ChatProvider.Result<List<ProviderModel>>() {
            @Override public void onSuccess(List<ProviderModel> models) {
                runOnUiThread(() -> {
                    if (setupStatusGeneration.get() != generation) return;
                    googleState = STATUS_READY;
                    setReadinessStatus(googleStatus, "✓ Signed in", STATUS_READY);
                    updateReadinessSummary();
                });
            }

            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    if (setupStatusGeneration.get() != generation) return;
                    googleState = STATUS_ACTION;
                    setReadinessStatus(googleStatus,
                            message != null && message.contains("sign-in")
                                    ? "Sign-in required" : "Could not verify",
                            STATUS_ACTION);
                    updateReadinessSummary();
                });
            }
        });
    }

    private void updateReadinessSummary() {
        int ready = (termuxState == STATUS_READY ? 1 : 0)
                + (permissionState == STATUS_READY ? 1 : 0)
                + (antigravityState == STATUS_READY ? 1 : 0)
                + (googleState == STATUS_READY ? 1 : 0)
                + (codexState == STATUS_READY ? 1 : 0);
        boolean checking = termuxState == STATUS_PENDING || permissionState == STATUS_PENDING
                || antigravityState == STATUS_PENDING || googleState == STATUS_PENDING
                || codexState == STATUS_PENDING;
        boolean complete = ready == 5;
        prerequisites.setText(complete ? "✓ Rava is ready"
                : ready + " of 5 ready" + (checking ? " — checking…" : ""));
        prerequisites.setTextColor(complete ? readyTextColor() : primaryTextColor());
        prerequisites.setBackground(rounded(complete ? readySurfaceColor() : softAccentColor(), 18));
    }

    private void setReadinessStatus(TextView view, String value, int state) {
        if (view == null) return;
        view.setText(value);
        int foreground = state == STATUS_READY
                ? readyTextColor()
                : state == STATUS_PENDING ? secondaryTextColor() : actionTextColor();
        int background = state == STATUS_READY
                ? readySurfaceColor()
                : state == STATUS_PENDING ? softSurfaceColor() : actionSurfaceColor();
        view.setTextColor(foreground);
        view.setBackground(rounded(background, 14));
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
        output.setText("The verified Termux command is running. Its result will appear here when it finishes. You may leave Rava and return later.");
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
        view.setTextColor(primaryTextColor());
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private TextView chatText(String value, int size, boolean bold) {
        TextView view = text(value, size, false);
        view.setTypeface(chatTypeface, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private final class ThemedModelAdapter extends ArrayAdapter<ProviderModel> {
        ThemedModelAdapter() {
            super(MainActivity.this, android.R.layout.simple_spinner_item, new ArrayList<>());
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            TextView view = (TextView) super.getView(position, convertView, parent);
            styleModelOption(view, false);
            return view;
        }

        @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
            TextView view = (TextView) super.getDropDownView(position, convertView, parent);
            styleModelOption(view, true);
            return view;
        }

        private void styleModelOption(TextView view, boolean dropdown) {
            view.setTextColor(primaryTextColor());
            view.setTextSize(14);
            view.setTypeface(chatTypeface);
            view.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            view.setPadding(dp(12), dropdown ? dp(12) : 0, dp(12), dropdown ? dp(12) : 0);
            view.setBackgroundColor(cardColor());
        }
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
        item.setSelected(selected);
        item.setColorFilter(selected ? accentColor() : primaryTextColor());
        item.setBackgroundColor(Color.TRANSPARENT);
    }

    private TextView sectionTitle(String value) {
        TextView section = text(value, 12, true);
        section.setTextColor(accentColor());
        section.setLetterSpacing(0.08f);
        return section;
    }

    private LinearLayout setupCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(4), dp(14), dp(4));
        card.setBackground(rounded(cardColor(), 20));
        return card;
    }

    private TextView addSetupRow(LinearLayout parent, String title, String detail,
            String action, View.OnClickListener listener, boolean divider) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(10), dp(2), dp(10));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(title, 15, true);
        titleView.setTextColor(primaryTextColor());
        labels.addView(titleView);
        TextView detailView = text(detail, 11, false);
        detailView.setTextColor(secondaryTextColor());
        labels.addView(detailView, smallGap());
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

        TextView statusView = text("Checking…", 13, true);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(dp(10), 0, dp(10), 0);
        setReadinessStatus(statusView, "Checking…", STATUS_PENDING);
        row.addView(statusView, new LinearLayout.LayoutParams(-2, dp(34)));

        if (action != null && listener != null) {
            TextView actionView = compactAction(action, listener);
            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                    dp(72), dp(36));
            actionParams.leftMargin = dp(8);
            row.addView(actionView, actionParams);
        }
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        if (divider) {
            View line = new View(this);
            line.setBackgroundColor(borderColor());
            LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(-1, dp(1));
            lineParams.leftMargin = dp(2);
            lineParams.rightMargin = dp(2);
            parent.addView(line, lineParams);
        }
        return statusView;
    }

    private TextView compactAction(String label, View.OnClickListener listener) {
        TextView action = text(label, 13, true);
        action.setTextColor(accentColor());
        action.setGravity(Gravity.CENTER);
        action.setBackground(strokedRounded(softAccentColor(), accentBorderColor(), 12));
        action.setOnClickListener(listener);
        action.setClickable(true);
        action.setFocusable(true);
        action.setContentDescription(label);
        return action;
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

    private GradientDrawable strokedRounded(int color, int strokeColor, int radiusDp) {
        GradientDrawable drawable = rounded(color, radiusDp);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private int screenColor() {
        return darkMode ? Color.rgb(18, 18, 21) : Color.rgb(248, 248, 250);
    }

    private int navigationColor() {
        return darkMode ? Color.rgb(25, 24, 28) : Color.WHITE;
    }

    private int cardColor() {
        return darkMode ? Color.rgb(31, 30, 35) : Color.WHITE;
    }

    private int softSurfaceColor() {
        return darkMode ? Color.rgb(43, 41, 47) : Color.rgb(240, 240, 244);
    }

    private int softAccentColor() {
        return darkMode ? Color.rgb(43, 37, 57) : Color.rgb(248, 246, 252);
    }

    private int primaryTextColor() {
        return darkMode ? Color.rgb(243, 240, 247) : Color.rgb(29, 27, 32);
    }

    private int secondaryTextColor() {
        return darkMode ? Color.rgb(184, 179, 190) : Color.rgb(102, 98, 108);
    }

    private int borderColor() {
        return darkMode ? Color.rgb(63, 60, 68) : Color.rgb(229, 226, 233);
    }

    private int accentColor() {
        return darkMode ? Color.rgb(210, 188, 255) : Color.rgb(103, 80, 164);
    }

    private int accentBorderColor() {
        return darkMode ? Color.rgb(89, 73, 119) : Color.rgb(215, 207, 232);
    }

    private int readyTextColor() {
        return darkMode ? Color.rgb(144, 224, 170) : Color.rgb(35, 91, 57);
    }

    private int readySurfaceColor() {
        return darkMode ? Color.rgb(29, 65, 43) : Color.rgb(224, 244, 231);
    }

    private int actionTextColor() {
        return darkMode ? Color.rgb(255, 190, 138) : Color.rgb(137, 61, 20);
    }

    private int actionSurfaceColor() {
        return darkMode ? Color.rgb(72, 45, 28) : Color.rgb(255, 238, 224);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
