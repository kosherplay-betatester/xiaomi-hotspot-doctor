package com.hotspotband;

import android.app.Activity;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;

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

    private TextView statusText;
    private TextView shizukuText;
    private EditText nameEdit;
    private TextView nameHint;
    private TextView clientsText;
    private TextView usbText;
    private Button usbButton;
    private Button[] actionButtons;
    private boolean busy;
    /** True while the name field holds text the user typed and has not saved yet. */
    private boolean nameEdited;
    private boolean settingName;
    private boolean cableConnected;
    private boolean usbTethered;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Hotspot Band");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);

        TextView title = text("Hotspot Band", 24);
        root.addView(title);
        root.addView(text("Switch the hotspot between 5 GHz and 2.4 GHz without the broken HyperOS "
                + "auto-channel. Do not change the band in the phone's hotspot settings - use this app "
                + "or its Quick Settings tiles.", 14));

        shizukuText = text("", 14);
        root.addView(shizukuText);

        statusText = text("Checking...", 20);
        statusText.setPadding(0, dp(16), 0, dp(16));
        root.addView(statusText);

        // ---- hotspot name ----
        root.addView(heading("Hotspot name"));
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        nameEdit = new EditText(this);
        nameEdit.setSingleLine(true);
        nameEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        nameEdit.setImeOptions(EditorInfo.IME_ACTION_DONE);
        nameEdit.setHint("Hotspot name");
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
        nameRow.addView(nameEdit, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button saveName = button("Save name", v -> saveName());
        nameRow.addView(saveName);
        root.addView(nameRow);
        nameHint = text("", 12);
        root.addView(nameHint);

        // ---- band ----
        root.addView(heading("Band"));
        LinearLayout channelRow = new LinearLayout(this);
        channelRow.setGravity(Gravity.CENTER_VERTICAL);
        channelRow.addView(text("5 GHz channel:  ", 16));
        Spinner channels = new Spinner(this);
        String[] labels = new String[Prefs.CHANNELS.length];
        int selected = 0;
        for (int i = 0; i < Prefs.CHANNELS.length; i++) {
            labels[i] = String.valueOf(Prefs.CHANNELS[i]);
            if (Prefs.CHANNELS[i] == Prefs.channel(this)) selected = i;
        }
        channels.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        channels.setSelection(selected);
        channels.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Prefs.setChannel(MainActivity.this, Prefs.CHANNELS[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        channelRow.addView(channels);
        root.addView(channelRow);

        Button on5 = button("Hotspot ON - 5 GHz", v -> runCommand(Prefs.command5GHz(this), false));
        Button on24 = button("Hotspot ON - 2.4 GHz", v -> runCommand("2g", false));
        Button off = button("Hotspot OFF", v -> runCommand("off", false));
        Button refresh = button("Refresh", v -> refresh());
        for (Button b : new Button[]{on5, on24, off, refresh}) root.addView(b);

        // ---- connected devices ----
        root.addView(heading("Connected devices"));
        clientsText = text("-", 15);
        root.addView(clientsText);

        // ---- USB ----
        root.addView(heading("Share internet over USB"));
        usbText = text("", 15);
        root.addView(usbText);
        usbButton = button("USB tethering ON", v -> runCommand(usbTethered ? "usb off" : "usb on", false));
        root.addView(usbButton);
        Switch usbAuto = new Switch(this);
        usbAuto.setText("Turn on automatically when a computer is plugged in");
        usbAuto.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        usbAuto.setPadding(0, dp(8), 0, dp(8));
        usbAuto.setChecked(Prefs.usbAuto(this));
        usbAuto.setOnCheckedChangeListener((button, checked) -> {
            Prefs.setUsbAuto(this, checked);
            UsbAutoService.sync(this);
        });
        root.addView(usbAuto);

        actionButtons = new Button[]{saveName, on5, on24, off, refresh, usbButton};

        if (Build.VERSION.SDK_INT >= 33) {
            root.addView(heading("Quick Settings"));
            root.addView(button("Add Quick Settings tiles", v -> addTiles()));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        scroll.addView(root);
        setContentView(scroll);

        Shizuku.addBinderReceivedListenerSticky(binderListener);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        UsbAutoService.sync(this);
    }

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

    private void refresh() {
        if (!Shizuku.pingBinder()) {
            shizukuText.setText("Shizuku: not running. Open the Shizuku app and start it.");
            statusText.setText("-");
            return;
        }
        if (!Runner.hasPermission()) {
            shizukuText.setText("Shizuku: waiting for permission...");
            if (!Shizuku.isPreV11()) Shizuku.requestPermission(SHIZUKU_REQUEST);
            return;
        }
        shizukuText.setText("Shizuku: connected");
        // Allow the status-bar band indicator notification (self-grant via Shizuku).
        new Thread(() -> Runner.shell(this,
                "pm grant " + getPackageName() + " android.permission.POST_NOTIFICATIONS 2>/dev/null")).start();
        runCommand("status", false);
    }

    private void saveName() {
        String name = nameEdit.getText().toString().trim();
        int bytes = name.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < 1 || bytes > 32) {
            nameHint.setText("The name must be 1-32 bytes long (it is " + bytes + ").");
            return;
        }
        nameEdit.clearFocus();
        runCommand(Runner.nameCommand(name), false);
    }

    /**
     * @param quiet a background poll: keeps the buttons enabled and the old text until the
     *              result is in.
     */
    private void runCommand(String command, boolean quiet) {
        if (busy) return;
        busy = true;
        boolean changesName = command.startsWith("name ");
        if (!quiet) {
            setButtonsEnabled(false);
            statusText.setText(command.equals("status") ? "Checking..." : "Working...");
        }
        new Thread(() -> {
            Status status = Runner.runWithDetails(this, command);
            main.post(() -> {
                busy = false;
                setButtonsEnabled(true);
                statusText.setText(status.describe());
                if (status.error == null) {
                    if (changesName) nameEdited = false;
                    showName(status.ssid);
                    showClients(status);
                    usbTethered = status.usbTethered;
                    showUsb();
                }
                if (status.isOn()) BandStatusService.start(this);
                TileService.requestListeningState(this, new ComponentName(this, Tile5GHz.class));
                TileService.requestListeningState(this, new ComponentName(this, Tile24GHz.class));
            });
        }).start();
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
        int bytes = nameEdit.getText().toString().trim().getBytes(StandardCharsets.UTF_8).length;
        nameHint.setText(nameEdited
                ? "Tap Save name to apply (" + bytes + "/32). A running hotspot restarts with the new name; "
                + "devices must reconnect to it."
                : "The band, channel and password stay the same.");
    }

    private void showClients(Status status) {
        if (status.clients.isEmpty()) {
            clientsText.setText(status.isOn() || status.usbTethered ? "No devices connected" : "Hotspot is off");
            return;
        }
        StringBuilder text = new StringBuilder();
        text.append(status.clients.size()).append(status.clients.size() == 1 ? " device" : " devices");
        for (Status.Client client : status.clients) text.append("\n\n").append(client.describe());
        clientsText.setText(text);
    }

    private void showUsb() {
        if (usbText == null) return;
        usbText.setText("Cable: " + (cableConnected ? "connected to a computer" : "not connected")
                + "\nUSB tethering: " + (usbTethered ? "ON" : "OFF"));
        usbButton.setText(usbTethered ? "USB tethering OFF" : "USB tethering ON");
    }

    private void addTiles() {
        StatusBarManager manager = getSystemService(StatusBarManager.class);
        manager.requestAddTileService(new ComponentName(this, Tile24GHz.class), "Hotspot 2.4G",
                Icon.createWithResource(this, R.drawable.ic_tile_24), getMainExecutor(), result -> { });
        manager.requestAddTileService(new ComponentName(this, Tile5GHz.class), "Hotspot 5G",
                Icon.createWithResource(this, R.drawable.ic_tile_5), getMainExecutor(), result -> { });
    }

    private void setButtonsEnabled(boolean enabled) {
        for (Button b : actionButtons) b.setEnabled(enabled);
    }

    private TextView heading(String value) {
        TextView view = text(value, 18);
        view.setPadding(0, dp(20), 0, dp(4));
        view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private TextView text(String value, int sp) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setPadding(0, dp(4), 0, dp(4));
        return view;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
