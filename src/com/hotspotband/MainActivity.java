package com.hotspotband;

import android.app.Activity;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.TileService;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private static final int SHIZUKU_REQUEST = 1;
    private static final long POLL_MILLIS = 5000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Shizuku.OnBinderReceivedListener binderListener = () -> main.post(this::refresh);
    private final Shizuku.OnRequestPermissionResultListener permissionListener =
            (requestCode, result) -> main.post(this::refresh);
    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (!busy && Runner.hasPermission()) runCommand("status", true);
            main.postDelayed(this, POLL_MILLIS);
        }
    };
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            cableConnected = intent.getBooleanExtra("connected", false);
            showUsb();
        }
    };

    private Ui ui;
    private LinearLayout hero;
    private TextView heroPill;
    private TextView heroTitle;
    private TextView heroName;
    private TextView heroDetails;
    private LinearLayout heroStats;
    private TextView statDevices;
    private TextView statSpeed;
    private TextView statUsed;
    private LinearLayout historyChips;
    private TextView historyLabel;
    private TextView passwordText;
    private TextView passwordShow;
    private EditText passwordEdit;
    /** The password once fetched, and what to do with it when it arrives. */
    private String password;
    private boolean passwordVisible;
    private String afterPassword;
    private long lastDown = -1;
    private long lastUp = -1;
    private long lastBytesAt;
    private TextView shizukuChip;
    private TextView band24;
    private TextView band5;
    private final List<TextView> channelChips = new ArrayList<>();
    private LinearLayout chips24;
    private LinearLayout chips5;
    /** The channel lists the chips were last built from, to skip identical rebuilds. */
    private String channelsKey;
    /** The hotspot name the history chips last marked as current. */
    private String historySsid;
    private TextView offButton;
    private EditText nameEdit;
    private TextView nameHint;
    private TextView clientsCount;
    private LinearLayout clientsList;
    private TextView usbSubtitle;
    private Switch usbSwitch;
    private final List<View> actionViews = new ArrayList<>();

    private Status last;
    private boolean busy;
    /** True while the running command is a background poll, which a tap may queue behind. */
    private boolean pollRunning;
    private String pendingCommand;
    /** True while the name field holds text the user typed and has not saved yet. */
    private boolean nameEdited;
    private boolean settingName;
    private boolean settingUsbSwitch;
    private boolean cableConnected;
    private boolean usbTethered;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Hotspot Band");
        ui = new Ui(this);
        ui.styleWindow(this);
        if (getActionBar() != null) getActionBar().hide();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(28));

        buildHeader(root);
        buildHero(root);
        buildBand(root);
        buildName(root);
        buildClients(root);
        buildUsb(root);
        buildAppearance(root);
        if (Build.VERSION.SDK_INT >= 33) {
            LinearLayout tiles = ui.card(root, "Quick Settings");
            tiles.addView(ui.text("Add the 2.4 and 5 tiles to switch bands from the notification shade.",
                    14, ui.textSecondary, false));
            tiles.addView(ui.button("Add Quick Settings tiles", ui.surfaceAlt, ui.text, v -> addTiles()),
                    ui.matchWidth(12));
        }
        TextView tip = ui.text("Tip: don't change the band in the phone's own hotspot settings. HyperOS saves 5 GHz "
                + "with an automatic channel, which fails to start. Use this app or its tiles instead.",
                12.5f, ui.textSecondary, false);
        tip.setPadding(ui.dp(6), ui.dp(18), ui.dp(6), 0);
        root.addView(tip);

        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        scroll.setBackgroundColor(ui.background);
        scroll.addView(root);
        setContentView(scroll);
        showHero(null);

        Shizuku.addBinderReceivedListenerSticky(binderListener);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        UsbAutoService.sync(this);
    }

    // ---- layout ----

    private void buildHeader(LinearLayout root) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(ui.dp(4), ui.dp(4), 0, ui.dp(2));

        ImageView logo = ui.icon(R.drawable.ic_hotspot, Color.WHITE, 40);
        logo.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
        GradientDrawable logoBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Ui.VIOLET, Ui.BLUE});
        logoBg.setCornerRadius(ui.dp(13));
        logo.setBackground(logoBg);
        header.addView(logo);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(ui.dp(12), 0, 0, 0);
        titles.addView(ui.text("Hotspot Band", 22, ui.text, true));
        shizukuChip = ui.text("Shizuku: checking...", 12.5f, ui.textSecondary, false);
        titles.addView(shizukuChip);
        header.addView(titles, Ui.weight(1));

        TextView refresh = ui.text("↻", 22, ui.text, false);
        refresh.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(ui.surface);
        circle.setStroke(ui.dp(1), ui.stroke);
        refresh.setBackground(ui.pressable(circle, 22));
        refresh.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)));
        refresh.setContentDescription("Refresh");
        refresh.setOnClickListener(v -> refresh());
        header.addView(refresh);
        root.addView(header);
    }

    private void buildHero(LinearLayout root) {
        hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(ui.dp(20), ui.dp(18), ui.dp(20), ui.dp(20));
        hero.setElevation(ui.dp(3));

        heroPill = ui.text("CHECKING", 11.5f, Color.WHITE, true);
        heroPill.setLetterSpacing(0.1f);
        heroPill.setPadding(ui.dp(10), ui.dp(4), ui.dp(10), ui.dp(4));
        heroPill.setBackground(ui.rounded(0x33FFFFFF, 20));
        LinearLayout pillRow = new LinearLayout(this);
        pillRow.addView(heroPill);
        hero.addView(pillRow);

        heroTitle = ui.text("Hotspot", 34, Color.WHITE, true);
        heroTitle.setPadding(0, ui.dp(10), 0, 0);
        hero.addView(heroTitle);
        heroName = ui.text("", 17, Color.WHITE, true);
        hero.addView(heroName);
        heroDetails = ui.text("", 14, 0xE6FFFFFF, false);
        heroDetails.setPadding(0, ui.dp(4), 0, 0);
        hero.addView(heroDetails);

        heroStats = new LinearLayout(this);
        heroStats.setPadding(ui.dp(4), ui.dp(12), ui.dp(4), ui.dp(12));
        heroStats.setBackground(ui.rounded(0x26FFFFFF, 18));
        statDevices = heroStat("DEVICES");
        statSpeed = heroStat("SPEED");
        statUsed = heroStat("USED");
        LinearLayout.LayoutParams statsParams = ui.matchWidth(16);
        hero.addView(heroStats, statsParams);

        LinearLayout.LayoutParams params = ui.matchWidth(14);
        root.addView(hero, params);
    }

    /** Adds a labeled column to the hero's stats row and returns its value view. */
    private TextView heroStat(String label) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = ui.text(label, 10.5f, 0xCCFFFFFF, true);
        title.setLetterSpacing(0.1f);
        column.addView(title);
        TextView value = ui.text("-", 14.5f, Color.WHITE, true);
        value.setGravity(Gravity.CENTER);
        value.setPadding(0, ui.dp(3), 0, 0);
        column.addView(value);
        heroStats.addView(column, Ui.weight(1));
        return value;
    }

    private void buildBand(LinearLayout root) {
        LinearLayout card = ui.card(root, "Band");
        LinearLayout row = new LinearLayout(this);
        band24 = bandTile("2.4 GHz", "Longer range", v -> runCommand(Prefs.command24GHz(this), false));
        band5 = bandTile("5 GHz", "Faster", v -> runCommand(Prefs.command5GHz(this), false));
        LinearLayout.LayoutParams left = Ui.weight(1);
        left.rightMargin = ui.dp(6);
        LinearLayout.LayoutParams right = Ui.weight(1);
        right.leftMargin = ui.dp(6);
        row.addView(band24, left);
        row.addView(band5, right);
        card.addView(row);

        chips24 = channelRow(card, "2.4 GHz channel");
        chips5 = channelRow(card, "5 GHz channel");
        TextView dfsHint = ui.text("DFS channels need a radar check first: the hotspot can take about a minute "
                + "to start on them, or not start at all.", 12, ui.textSecondary, false);
        dfsHint.setPadding(ui.dp(2), ui.dp(8), 0, 0);
        card.addView(dfsHint);
        int[] all24 = new int[13];
        for (int i = 0; i < all24.length; i++) all24[i] = i + 1;
        fillChannels(all24, Prefs.CHANNELS);

        offButton = ui.outlineButton("Turn hotspot off", Ui.RED, v -> runCommand("off", false));
        card.addView(offButton, ui.matchWidth(16));
        actionViews.add(band24);
        actionViews.add(band5);
        actionViews.add(offButton);
    }

    private LinearLayout channelRow(LinearLayout card, String label) {
        TextView title = ui.text(label, 13.5f, ui.textSecondary, true);
        title.setPadding(0, ui.dp(16), 0, ui.dp(8));
        card.addView(title);
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        scroll.addView(chips);
        card.addView(scroll);
        return chips;
    }

    /** Rebuilds the channel chips from the channels the phone supports (0 = automatic). */
    private void fillChannels(int[] list24, int[] list5) {
        String key = java.util.Arrays.toString(list24) + java.util.Arrays.toString(list5);
        if (key.equals(channelsKey)) return;
        channelsKey = key;
        for (TextView chip : channelChips) actionViews.remove(chip);
        channelChips.clear();
        chips24.removeAllViews();
        chips5.removeAllViews();
        addChip(chips24, 1, 0);
        for (int channel : list24) addChip(chips24, 1, channel);
        for (int channel : list5) addChip(chips5, 2, channel);
        showChannels();
    }

    private void addChip(LinearLayout row, int band, int channel) {
        String label = channel == 0 ? "Auto" : Status.isDfs(channel) ? channel + " DFS" : String.valueOf(channel);
        TextView chip = ui.text(label, 14.5f, ui.text, true);
        chip.setGravity(Gravity.CENTER);
        chip.setMinWidth(ui.dp(50));
        chip.setPadding(ui.dp(13), ui.dp(9), ui.dp(13), ui.dp(9));
        chip.setTag(new int[]{band, channel});
        chip.setOnClickListener(v -> selectChannel(band, channel));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.rightMargin = ui.dp(8);
        row.addView(chip, params);
        channelChips.add(chip);
        actionViews.add(chip);
    }

    private TextView bandTile(String label, String hint, View.OnClickListener listener) {
        TextView tile = ui.text(label + "\n" + hint, 15, ui.text, true);
        tile.setGravity(Gravity.CENTER);
        tile.setLineSpacing(ui.dp(2), 1);
        tile.setMinHeight(ui.dp(76));
        tile.setPadding(ui.dp(10), ui.dp(12), ui.dp(10), ui.dp(12));
        tile.setOnClickListener(listener);
        return tile;
    }

    private void buildName(LinearLayout root) {
        LinearLayout card = ui.card(root, "Hotspot name");
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        nameEdit = new EditText(this);
        nameEdit.setSingleLine(true);
        nameEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        nameEdit.setImeOptions(EditorInfo.IME_ACTION_DONE);
        nameEdit.setHint("Hotspot name");
        nameEdit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        nameEdit.setTextColor(ui.text);
        nameEdit.setHintTextColor(ui.textSecondary);
        nameEdit.setBackground(ui.rounded(ui.surfaceAlt, 14));
        nameEdit.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        nameEdit.setMinHeight(ui.dp(50));
        nameEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!settingName) nameEdited = true;
                showNameHint();
            }
        });
        nameEdit.setOnEditorActionListener((v, actionId, event) -> {
            saveName();
            return true;
        });
        row.addView(nameEdit, Ui.weight(1));
        TextView save = ui.button("Save", Ui.BLUE, Color.WHITE, v -> saveName());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        saveParams.leftMargin = ui.dp(10);
        row.addView(save, saveParams);
        card.addView(row);
        nameHint = ui.text("", 12.5f, ui.textSecondary, false);
        nameHint.setPadding(ui.dp(2), ui.dp(8), 0, 0);
        card.addView(nameHint);
        showNameHint();
        actionViews.add(save);

        historyLabel = ui.text("Recent names - tap to use, long-press to remove", 12.5f, ui.textSecondary, true);
        historyLabel.setPadding(ui.dp(2), ui.dp(14), 0, ui.dp(8));
        card.addView(historyLabel);
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        historyChips = new LinearLayout(this);
        scroll.addView(historyChips);
        card.addView(scroll);
        showHistory();

        buildPassword(root);
    }

    private void showHistory() {
        historyChips.removeAllViews();
        List<String> names = Prefs.nameHistory(this);
        String current = last != null && last.error == null ? last.ssid : "";
        historyLabel.setVisibility(names.isEmpty() ? View.GONE : View.VISIBLE);
        for (String name : names) {
            boolean active = name.equals(current);
            TextView chip = ui.text((active ? "✓  " : "") + name, 14, active ? Color.WHITE : ui.text, true);
            chip.setPadding(ui.dp(14), ui.dp(8), ui.dp(14), ui.dp(8));
            chip.setBackground(ui.pressable(active ? ui.rounded(Ui.BLUE, 20)
                    : ui.outlined(ui.surfaceAlt, ui.stroke, 20), 20));
            chip.setOnClickListener(v -> {
                if (busy || name.equals(last != null ? last.ssid : null)) return;
                nameEdited = false;
                settingName = true;
                nameEdit.setText(name);
                settingName = false;
                runCommand(Runner.hexCommand("name", name), false);
            });
            chip.setOnLongClickListener(v -> {
                if (active) {
                    android.widget.Toast.makeText(this, "This is the current name", android.widget.Toast.LENGTH_SHORT).show();
                    return true;
                }
                Prefs.forgetName(this, name);
                showHistory();
                return true;
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.rightMargin = ui.dp(8);
            historyChips.addView(chip, params);
        }
    }

    private void buildPassword(LinearLayout root) {
        LinearLayout card = ui.card(root, "Password");
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        passwordText = ui.text("••••••••••", 17, ui.text, true);
        passwordText.setTextIsSelectable(false);
        row.addView(passwordText, Ui.weight(1));
        passwordShow = smallButton("Show", v -> {
            if (passwordVisible) {
                passwordVisible = false;
                showPassword();
            } else {
                withPassword("show");
            }
        });
        row.addView(passwordShow);
        TextView copy = smallButton("Copy", v -> withPassword("copy"));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        copyParams.leftMargin = ui.dp(8);
        row.addView(copy, copyParams);
        card.addView(row);

        LinearLayout editRow = new LinearLayout(this);
        editRow.setGravity(Gravity.CENTER_VERTICAL);
        passwordEdit = new EditText(this);
        passwordEdit.setSingleLine(true);
        passwordEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        passwordEdit.setHint("New password (8-63 characters)");
        passwordEdit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        passwordEdit.setTypeface(android.graphics.Typeface.DEFAULT);
        passwordEdit.setTextColor(ui.text);
        passwordEdit.setHintTextColor(ui.textSecondary);
        passwordEdit.setBackground(ui.rounded(ui.surfaceAlt, 14));
        passwordEdit.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        passwordEdit.setMinHeight(ui.dp(50));
        editRow.addView(passwordEdit, Ui.weight(1));
        TextView change = ui.button("Change", Ui.BLUE, Color.WHITE, v -> savePassword());
        LinearLayout.LayoutParams changeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        changeParams.leftMargin = ui.dp(10);
        editRow.addView(change, changeParams);
        card.addView(editRow, ui.matchWidth(14));
        TextView hint = ui.text("Changing it here keeps the band and channel. Devices need the new password "
                + "to reconnect.", 12.5f, ui.textSecondary, false);
        hint.setPadding(ui.dp(2), ui.dp(8), 0, 0);
        card.addView(hint);
        actionViews.add(passwordShow);
        actionViews.add(copy);
        actionViews.add(change);
    }

    private TextView smallButton(String label, View.OnClickListener listener) {
        TextView button = ui.text(label, 14, Ui.BLUE, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(ui.dp(14), ui.dp(8), ui.dp(14), ui.dp(8));
        button.setBackground(ui.pressable(ui.rounded(Ui.withAlpha(Ui.BLUE, ui.dark ? 0.2f : 0.1f), 14), 14));
        button.setOnClickListener(listener);
        return button;
    }

    /** Runs {@code action} ("show" or "copy") with the password, fetching it first if needed. */
    private void withPassword(String action) {
        if (password != null) {
            passwordAction(action);
            return;
        }
        afterPassword = action;
        runCommand("pass", false);
    }

    private void passwordAction(String action) {
        if (action.equals("copy")) {
            android.content.ClipboardManager clipboard = getSystemService(android.content.ClipboardManager.class);
            if (clipboard != null) {
                android.content.ClipData clip = android.content.ClipData.newPlainText("Hotspot password", password);
                android.os.PersistableBundle extras = new android.os.PersistableBundle();
                extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
                clip.getDescription().setExtras(extras);
                clipboard.setPrimaryClip(clip);
                android.widget.Toast.makeText(this, "Password copied", android.widget.Toast.LENGTH_SHORT).show();
            }
        } else {
            passwordVisible = true;
            showPassword();
        }
    }

    private void showPassword() {
        passwordText.setText(passwordVisible && password != null ? (password.isEmpty() ? "(open network)" : password)
                : "••••••••••");
        passwordShow.setText(passwordVisible ? "Hide" : "Show");
    }

    private void savePassword() {
        String value = passwordEdit.getText().toString();
        if (value.length() < 8 || value.length() > 63) {
            android.widget.Toast.makeText(this, "The password must be 8-63 characters", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        passwordEdit.clearFocus();
        InputMethodManager keyboard = getSystemService(InputMethodManager.class);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(passwordEdit.getWindowToken(), 0);
        password = null;
        passwordVisible = false;
        showPassword();
        passwordEdit.setText("");
        runCommand(Runner.hexCommand("setpass", value), false);
    }

    private void buildClients(LinearLayout root) {
        LinearLayout card = ui.card(root, null);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = ui.text("CONNECTED DEVICES", 12.5f, ui.textSecondary, true);
        title.setLetterSpacing(0.08f);
        titleRow.addView(title, Ui.weight(1));
        clientsCount = ui.text("0", 12.5f, Ui.BLUE, true);
        clientsCount.setPadding(ui.dp(9), ui.dp(2), ui.dp(9), ui.dp(2));
        clientsCount.setBackground(ui.rounded(Ui.withAlpha(Ui.BLUE, ui.dark ? 0.22f : 0.12f), 12));
        titleRow.addView(clientsCount);
        card.addView(titleRow);
        clientsList = new LinearLayout(this);
        clientsList.setOrientation(LinearLayout.VERTICAL);
        clientsList.setPadding(0, ui.dp(6), 0, 0);
        card.addView(clientsList);
        showClients(null);
    }

    private void buildUsb(LinearLayout root) {
        LinearLayout card = ui.card(root, "Share internet over USB");

        LinearLayout tetherRow = switchRow(R.drawable.ic_usb, "USB tethering", "");
        usbSubtitle = (TextView) ((LinearLayout) tetherRow.getChildAt(1)).getChildAt(1);
        usbSwitch = new Switch(this);
        ui.tint(usbSwitch, Ui.GREEN);
        usbSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (settingUsbSwitch) return;
            runCommand(checked ? "usb on" : "usb off", false);
        });
        tetherRow.addView(usbSwitch);
        card.addView(tetherRow);

        View divider = new View(this);
        divider.setBackgroundColor(ui.stroke);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(1));
        dividerParams.topMargin = ui.dp(12);
        dividerParams.bottomMargin = ui.dp(12);
        card.addView(divider, dividerParams);

        LinearLayout autoRow = switchRow(R.drawable.ic_usb, "Turn on automatically",
                "When the phone is plugged into a computer");
        Switch auto = new Switch(this);
        ui.tint(auto, Ui.BLUE);
        auto.setChecked(Prefs.usbAuto(this));
        auto.setOnCheckedChangeListener((button, checked) -> {
            Prefs.setUsbAuto(this, checked);
            UsbAutoService.sync(this);
        });
        autoRow.addView(auto);
        card.addView(autoRow);
        actionViews.add(usbSwitch);
    }

    private LinearLayout switchRow(int icon, String title, String subtitle) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView image = ui.icon(icon, ui.text, 38);
        image.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
        image.setBackground(ui.rounded(ui.surfaceAlt, 12));
        row.addView(image);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(ui.dp(12), 0, ui.dp(8), 0);
        texts.addView(ui.text(title, 15.5f, ui.text, true));
        texts.addView(ui.text(subtitle, 13, ui.textSecondary, false));
        row.addView(texts, Ui.weight(1));
        return row;
    }

    private void buildAppearance(LinearLayout root) {
        LinearLayout card = ui.card(root, "Appearance");
        LinearLayout segmented = new LinearLayout(this);
        segmented.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));
        segmented.setBackground(ui.rounded(ui.surfaceAlt, 14));
        String[] labels = {"Auto", "Light", "Dark"};
        int current = Prefs.theme(this);
        for (int i = 0; i < labels.length; i++) {
            int theme = i;
            boolean selected = theme == current;
            TextView option = ui.text(labels[i], 14.5f, selected ? ui.text : ui.textSecondary, true);
            option.setGravity(Gravity.CENTER);
            option.setPadding(0, ui.dp(10), 0, ui.dp(10));
            if (selected) {
                option.setBackground(ui.outlined(ui.surface, ui.stroke, 11));
                option.setElevation(ui.dp(1));
            } else {
                option.setBackground(ui.pressable(ui.rounded(Color.TRANSPARENT, 11), 11));
            }
            option.setOnClickListener(v -> {
                if (theme == Prefs.theme(this)) return;
                Prefs.setTheme(this, theme);
                Ui.applyTheme(this, theme);
                recreate();
            });
            segmented.addView(option, Ui.weight(1));
        }
        card.addView(segmented);
        TextView hint = ui.text("Auto follows the phone's dark mode setting.", 12.5f, ui.textSecondary, false);
        hint.setPadding(ui.dp(2), ui.dp(8), 0, 0);
        card.addView(hint);
    }

    // ---- lifecycle ----

    @Override
    protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(binderListener);
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent sticky = Build.VERSION.SDK_INT >= 33
                ? registerReceiver(usbReceiver, new IntentFilter(UsbAutoService.ACTION_USB_STATE), RECEIVER_EXPORTED)
                : registerReceiver(usbReceiver, new IntentFilter(UsbAutoService.ACTION_USB_STATE));
        cableConnected = sticky != null && sticky.getBooleanExtra("connected", false);
        showUsb();
        refresh();
        main.postDelayed(poll, POLL_MILLIS);
    }

    @Override
    protected void onPause() {
        main.removeCallbacks(poll);
        unregisterReceiver(usbReceiver);
        super.onPause();
    }

    // ---- actions ----

    private void refresh() {
        if (!Shizuku.pingBinder()) {
            showShizuku("Shizuku is not running - open Shizuku and start it", Ui.RED);
            showHero(Status.error(Runner.NOT_RUNNING));
            return;
        }
        if (!Runner.hasPermission()) {
            showShizuku("Waiting for Shizuku permission...", Ui.AMBER);
            if (!Shizuku.isPreV11()) Shizuku.requestPermission(SHIZUKU_REQUEST);
            return;
        }
        showShizuku("Shizuku connected", Ui.GREEN);
        // Allow the status-bar band indicator notification (self-grant via Shizuku).
        new Thread(() -> Runner.shell(this,
                "pm grant " + getPackageName() + " android.permission.POST_NOTIFICATIONS 2>/dev/null")).start();
        runCommand("status", false);
    }

    private void selectChannel(int band, int channel) {
        if (band == 1) {
            Prefs.setChannel24(this, channel);
        } else {
            Prefs.setChannel(this, channel);
        }
        showChannels();
        // Apply right away if the hotspot is running on that band on another channel.
        if (last == null || last.error != null) return;
        if (band == 2 && last.isOn5GHz() && last.channel != channel) {
            runCommand(Prefs.command5GHz(this), false);
        } else if (band == 1 && last.isOn24GHz() && last.channel != channel) {
            runCommand(Prefs.command24GHz(this), false);
        }
    }

    private void saveName() {
        String name = nameEdit.getText().toString().trim();
        int bytes = name.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < 1 || bytes > 32) {
            nameHint.setTextColor(Ui.RED);
            nameHint.setText("The name must be 1-32 bytes long (it is " + bytes + ").");
            return;
        }
        nameEdit.clearFocus();
        InputMethodManager keyboard = getSystemService(InputMethodManager.class);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(nameEdit.getWindowToken(), 0);
        runCommand(Runner.hexCommand("name", name), false);
    }

    /**
     * @param quiet a background poll: keeps the controls enabled and the old state until the
     *              result is in.
     */
    private void runCommand(String command, boolean quiet) {
        if (busy) {
            // A tap during a background poll runs as soon as the poll finishes.
            if (!quiet && pollRunning) {
                pendingCommand = command;
                setWorking();
            }
            return;
        }
        busy = true;
        pollRunning = quiet;
        boolean changesName = command.startsWith("name ");
        if (!quiet) {
            if (command.equals("status")) {
                heroPill.setText("CHECKING");
            } else {
                setWorking();
            }
        }
        new Thread(() -> {
            Status status = Runner.runWithDetails(this, command);
            main.post(() -> {
                busy = false;
                pollRunning = false;
                if (pendingCommand != null) {
                    String next = pendingCommand;
                    pendingCommand = null;
                    runCommand(next, false);
                    return;
                }
                setEnabled(true);
                last = status;
                showHero(status);
                if (status.error == null) {
                    if (changesName) nameEdited = false;
                    showName(status.ssid);
                    boolean historyChanged = Prefs.rememberName(this, status.ssid);
                    if (historyChanged || !status.ssid.equals(historySsid)) {
                        historySsid = status.ssid;
                        showHistory();
                    }
                    showClients(status);
                    if (status.channels24 != null && status.channels5 != null) {
                        fillChannels(status.channels24, status.channels5);
                    }
                    usbTethered = status.usbTethered;
                    showUsb();
                    if (status.password != null) {
                        password = status.password;
                        if (afterPassword != null) passwordAction(afterPassword);
                    }
                } else if (command.startsWith("setpass") || command.startsWith("name")) {
                    android.widget.Toast.makeText(this, status.error, android.widget.Toast.LENGTH_LONG).show();
                }
                afterPassword = null;
                if (status.isOn()) BandStatusService.start(this);
                TileService.requestListeningState(this, new ComponentName(this, Tile5GHz.class));
                TileService.requestListeningState(this, new ComponentName(this, Tile24GHz.class));
            });
        }).start();
    }

    private void setWorking() {
        setEnabled(false);
        heroPill.setText("WORKING...");
    }

    private void addTiles() {
        StatusBarManager manager = getSystemService(StatusBarManager.class);
        manager.requestAddTileService(new ComponentName(this, Tile24GHz.class), "Hotspot 2.4G",
                Icon.createWithResource(this, R.drawable.ic_tile_24), getMainExecutor(), result -> { });
        manager.requestAddTileService(new ComponentName(this, Tile5GHz.class), "Hotspot 5G",
                Icon.createWithResource(this, R.drawable.ic_tile_5), getMainExecutor(), result -> { });
    }

    // ---- state -> views ----

    private void setEnabled(boolean enabled) {
        for (View view : actionViews) {
            view.setEnabled(enabled);
            view.setAlpha(enabled ? 1f : 0.5f);
        }
    }

    private void showShizuku(String text, int color) {
        shizukuChip.setText("●  " + text);
        shizukuChip.setTextColor(color == Ui.GREEN ? ui.textSecondary : color);
    }

    private void showHero(Status status) {
        int[] colors;
        String pill;
        String title;
        String name = "";
        String details = "";
        if (status == null) {
            colors = new int[]{0xFF56607A, 0xFF3B4357};
            pill = "CHECKING";
            title = "Hotspot";
        } else if (status.error != null) {
            colors = new int[]{0xFFE5484D, 0xFFB4232A};
            pill = "PROBLEM";
            title = "Can't reach hotspot";
            details = status.error;
        } else if (status.isOn()) {
            boolean five = status.isOn5GHz();
            colors = five ? new int[]{Ui.VIOLET, Ui.BLUE} : new int[]{Ui.TEAL, 0xFF0CA678};
            pill = "●  ON";
            title = five ? "5 GHz" : "2.4 GHz";
            name = status.ssid;
            StringBuilder line = new StringBuilder();
            int channel = channelOf(status.frequency);
            if (channel > 0) line.append("Channel ").append(channel);
            if (status.frequency > 0) line.append(line.length() > 0 ? "  ·  " : "").append(status.frequency).append(" MHz");
            details = line.toString();
        } else {
            boolean failed = status.apState == 14;
            colors = failed ? new int[]{0xFFE5484D, 0xFFB4232A} : new int[]{0xFF56607A, 0xFF3B4357};
            pill = failed ? "FAILED" : "OFF";
            title = failed ? "Didn't start" : "Hotspot off";
            name = status.ssid;
            details = "Saved: " + (status.band == 1 ? "2.4 GHz"
                    : status.band == 2 ? (status.channel != 0 ? "5 GHz, channel " + status.channel : "5 GHz, auto channel")
                    : "band " + status.band);
        }
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        background.setCornerRadius(ui.dp(26));
        hero.setBackground(background);
        heroPill.setText(pill);
        heroTitle.setText(title);
        heroName.setText(name);
        heroName.setVisibility(name.isEmpty() ? View.GONE : View.VISIBLE);
        heroDetails.setText(details);
        heroDetails.setVisibility(details.isEmpty() ? View.GONE : View.VISIBLE);
        showStats(status);
        showBand(status);
    }

    /** Device count, live speed (from the change since the last poll) and data used. */
    private void showStats(Status status) {
        boolean sharing = status != null && status.error == null && (status.isOn() || status.usbTethered);
        heroStats.setVisibility(sharing ? View.VISIBLE : View.GONE);
        if (!sharing) {
            lastDown = -1;
            return;
        }
        statDevices.setText(String.valueOf(status.clients.size()));
        if (status.downloaded < 0) {
            statSpeed.setText("-");
            statUsed.setText("-");
            return;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        if (lastDown >= 0 && status.downloaded >= lastDown && status.uploaded >= lastUp && now > lastBytesAt) {
            double seconds = (now - lastBytesAt) / 1000.0;
            long down = Math.round((status.downloaded - lastDown) / seconds);
            long up = Math.round((status.uploaded - lastUp) / seconds);
            statSpeed.setText("↓ " + Status.bytes(down) + "/s\n↑ " + Status.bytes(up) + "/s");
        } else if (lastDown < 0 || status.downloaded < lastDown) {
            statSpeed.setText("...");
        }
        statUsed.setText("↓ " + Status.bytes(status.downloaded) + "\n↑ " + Status.bytes(status.uploaded));
        lastDown = status.downloaded;
        lastUp = status.uploaded;
        lastBytesAt = now;
    }

    private static int channelOf(int frequency) {
        if (frequency >= 5000 && frequency < 5900) return (frequency - 5000) / 5;
        if (frequency >= 2412 && frequency <= 2472) return (frequency - 2407) / 5;
        return 0;
    }

    private void showBand(Status status) {
        boolean on24 = status != null && status.isOn24GHz();
        boolean on5 = status != null && status.isOn5GHz();
        styleBandTile(band24, on24, Ui.TEAL);
        styleBandTile(band5, on5, Ui.VIOLET);
        offButton.setVisibility(status != null && status.isOn() ? View.VISIBLE : View.GONE);
    }

    private void styleBandTile(TextView tile, boolean active, int color) {
        if (active) {
            tile.setBackground(ui.pressable(ui.rounded(color, 18), 18));
            tile.setTextColor(Color.WHITE);
        } else {
            tile.setBackground(ui.pressable(ui.outlined(ui.surfaceAlt, ui.stroke, 18), 18));
            tile.setTextColor(ui.text);
        }
    }

    private void showChannels() {
        int selected5 = Prefs.channel(this);
        int selected24 = Prefs.channel24(this);
        for (TextView chip : channelChips) {
            int[] tag = (int[]) chip.getTag();
            boolean active = tag[1] == (tag[0] == 1 ? selected24 : selected5);
            int color = tag[0] == 1 ? Ui.TEAL : Ui.VIOLET;
            chip.setTextColor(active ? Color.WHITE : ui.text);
            chip.setBackground(ui.pressable(active ? ui.rounded(color, 20)
                    : ui.outlined(ui.surfaceAlt, ui.stroke, 20), 20));
        }
    }

    /** Shows the current hotspot name unless the user is in the middle of typing a new one. */
    private void showName(String ssid) {
        if (nameEdited || nameEdit.hasFocus() || ssid.isEmpty()) return;
        if (!ssid.equals(nameEdit.getText().toString())) {
            settingName = true;
            nameEdit.setText(ssid);
            settingName = false;
        }
        showNameHint();
    }

    private void showNameHint() {
        if (nameHint == null) return;
        int bytes = nameEdit.getText().toString().trim().getBytes(StandardCharsets.UTF_8).length;
        nameHint.setTextColor(ui.textSecondary);
        nameHint.setText(nameEdited
                ? "Tap Save to apply (" + bytes + "/32). A running hotspot restarts and devices reconnect."
                : "Changing the name keeps the band, channel and password.");
    }

    private void showClients(Status status) {
        clientsList.removeAllViews();
        int count = status == null ? 0 : status.clients.size();
        clientsCount.setText(String.valueOf(count));
        if (count == 0) {
            String message = status == null ? "Checking..."
                    : status.isOn() || status.usbTethered ? "No devices connected yet" : "Turn on the hotspot to share internet";
            TextView empty = ui.text(message, 14, ui.textSecondary, false);
            empty.setPadding(0, ui.dp(6), 0, 0);
            clientsList.addView(empty);
            return;
        }
        for (Status.Client client : status.clients) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, ui.dp(8), 0, ui.dp(8));
            String name = client.name != null && !client.name.isEmpty() ? client.name : "Unknown device";
            int color = client.isUsb() ? Ui.GREEN : Ui.BLUE;
            row.addView(ui.avatar(name.substring(0, 1).toUpperCase(), color));
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(ui.dp(12), 0, ui.dp(8), 0);
            texts.addView(ui.text(name, 15.5f, ui.text, true));
            texts.addView(ui.text(client.ip + "  ·  " + client.mac, 12.5f, ui.textSecondary, false));
            row.addView(texts, Ui.weight(1));
            TextView via = ui.text(client.isUsb() ? "USB" : "Wi-Fi", 12, color, true);
            via.setPadding(ui.dp(9), ui.dp(3), ui.dp(9), ui.dp(3));
            via.setBackground(ui.rounded(Ui.withAlpha(color, ui.dark ? 0.22f : 0.12f), 12));
            row.addView(via);
            clientsList.addView(row);
        }
    }

    private void showUsb() {
        if (usbSwitch == null) return;
        usbSubtitle.setText(cableConnected
                ? (usbTethered ? "Sharing internet with the computer" : "Cable connected to a computer")
                : (usbTethered ? "On - waiting for a computer" : "No computer connected"));
        settingUsbSwitch = true;
        usbSwitch.setChecked(usbTethered);
        settingUsbSwitch = false;
    }
}
