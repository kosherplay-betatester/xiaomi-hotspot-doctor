package com.hotspotband;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

/**
 * While "Auto-share over USB" is enabled, watches the USB cable and turns on USB tethering
 * (like Settings > Connection & sharing > USB tethering) each time a computer is plugged in.
 *
 * Changing the USB function briefly drops and restores the USB connection, so a reconnect
 * shortly after a disconnect is treated as the same plug, not a new one. That way turning
 * USB tethering off by hand while the cable stays in is respected.
 */
public final class UsbAutoService extends Service {
    static final String ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE";
    private static final String CHANNEL = "usb_auto";
    private static final int NOTIF_ID = 5152;
    /** A reconnect within this time of a disconnect is a USB function change, not a new plug. */
    private static final long REPLUG_MILLIS = 4000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean connected;
    private long disconnectedAt;
    private boolean registered;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onUsbState(intent);
        }
    };

    static void sync(Context context) {
        Intent intent = new Intent(context, UsbAutoService.class);
        if (Prefs.usbAuto(context)) {
            try {
                context.startForegroundService(intent);
            } catch (Exception ignored) {
            }
        } else {
            context.stopService(intent);
        }
    }

    /** True when the phone is plugged into a computer (data connection, not just a charger). */
    static boolean isCableConnected(Context context) {
        Intent sticky = context.registerReceiver(null, new IntentFilter(ACTION_USB_STATE));
        return sticky != null && sticky.getBooleanExtra("connected", false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Prefs.usbAuto(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        ensureChannel();
        try {
            Notification notification = build();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            } else {
                startForeground(NOTIF_ID, notification);
            }
        } catch (Throwable t) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!registered) {
            registered = true;
            // Treat a cable that is already plugged in when auto mode starts as a new plug.
            disconnectedAt = 0;
            connected = false;
            Intent sticky;
            if (Build.VERSION.SDK_INT >= 33) {
                sticky = registerReceiver(receiver, new IntentFilter(ACTION_USB_STATE), RECEIVER_EXPORTED);
            } else {
                sticky = registerReceiver(receiver, new IntentFilter(ACTION_USB_STATE));
            }
            if (sticky != null) onUsbState(sticky);
        }
        return START_STICKY;
    }

    private void onUsbState(Intent intent) {
        boolean nowConnected = intent.getBooleanExtra("connected", false);
        boolean tethering = intent.getBooleanExtra("rndis", false) || intent.getBooleanExtra("ncm", false);
        long now = SystemClock.elapsedRealtime();
        if (!nowConnected) {
            if (connected) disconnectedAt = now;
            connected = false;
            return;
        }
        if (connected) return;
        connected = true;
        boolean newPlug = disconnectedAt == 0 || now - disconnectedAt > REPLUG_MILLIS;
        if (!newPlug || tethering) return;
        // Give the USB connection a moment to settle before switching its function.
        main.postDelayed(() -> new Thread(() -> {
            if (connected && isCableConnected(this)) Runner.run(this, "usb on");
        }).start(), 1500);
    }

    private Notification build() {
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_usb)
                .setContentTitle("USB internet sharing: auto")
                .setContentText("Shares the phone's internet when a computer is plugged in")
                .setOngoing(true)
                .setContentIntent(PendingIntent.getActivity(this, 1,
                        new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE))
                .build();
    }

    private void ensureChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL, "USB auto-share",
                NotificationManager.IMPORTANCE_MIN);
        channel.setShowBadge(false);
        nm.createNotificationChannel(channel);
    }

    @Override
    public void onDestroy() {
        if (registered) unregisterReceiver(receiver);
        registered = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
