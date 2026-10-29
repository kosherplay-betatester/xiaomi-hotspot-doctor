package com.hotspotband;

import android.app.Activity;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.TileService;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private static final int SHIZUKU_REQUEST = 1;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Shizuku.OnBinderReceivedListener binderListener = () -> main.post(this::refresh);
    private final Shizuku.OnRequestPermissionResultListener permissionListener =
            (requestCode, result) -> main.post(this::refresh);

    private TextView statusText;
    private TextView shizukuText;
    private Button[] actionButtons;
    private boolean busy;

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

        Button on5 = button("Hotspot ON - 5 GHz", v -> runCommand(Prefs.command5GHz(this)));
        Button on24 = button("Hotspot ON - 2.4 GHz", v -> runCommand("2g"));
        Button off = button("Hotspot OFF", v -> runCommand("off"));
        Button refresh = button("Refresh", v -> refresh());
        actionButtons = new Button[]{on5, on24, off, refresh};
        for (Button b : actionButtons) root.addView(b);

        if (Build.VERSION.SDK_INT >= 33) {
            root.addView(button("Add Quick Settings tiles", v -> addTiles()));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        scroll.addView(root);
        setContentView(scroll);

        Shizuku.addBinderReceivedListenerSticky(binderListener);
        Shizuku.addRequestPermissionResultListener(permissionListener);
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
        refresh();
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
        runCommand("status");
    }

    private void runCommand(String command) {
        if (busy) return;
        busy = true;
        setButtonsEnabled(false);
        statusText.setText(command.equals("status") ? "Checking..." : "Working...");
        new Thread(() -> {
            Status status = Runner.run(this, command);
            main.post(() -> {
                busy = false;
                setButtonsEnabled(true);
                statusText.setText(status.describe());
                TileService.requestListeningState(this, new ComponentName(this, Tile5GHz.class));
                TileService.requestListeningState(this, new ComponentName(this, Tile24GHz.class));
            });
        }).start();
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
