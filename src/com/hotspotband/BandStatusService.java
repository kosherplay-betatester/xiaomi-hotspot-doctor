package com.hotspotband;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * While the hotspot is on, shows an ongoing notification whose small icon appears in the status bar
 * (near battery/signal) as "5" or "2.4" for the active band. Polls the real hotspot state through
 * Shizuku and hides itself automatically when the hotspot goes off.
 */
public final class BandStatusService extends Service {
    private static final String CHANNEL = "band_status_v2";
    private static final int NOTIF_ID = 5151;
    private volatile boolean running;

    static void start(Context ctx) {
        Intent i = new Intent(ctx, BandStatusService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
            else ctx.startService(i);
        } catch (Exception ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureChannel();
        try {
            startInForeground(build(true, "Checking hotspot…"));
        } catch (Throwable t) {
            // Never crash the app if the OS rejects the foreground service; just stop.
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!running) {
            running = true;
            new Thread(this::loop).start();
        }
        return START_STICKY;
    }

    private void loop() {
        // Make sure we may post/update notifications on Android 13+ (self-grant via Shizuku).
        if (Build.VERSION.SDK_INT >= 33) {
            Runner.shell(this, "pm grant " + getPackageName() + " android.permission.POST_NOTIFICATIONS");
        }
        while (running) {
            Status s = Runner.run(this, "status");
            if (s == null || !s.isOn()) {
                running = false;
                stopForeground(true);
                stopSelf();
                return;
            }
            boolean five = s.isOn5GHz();
            String band = five ? "5 GHz" : "2.4 GHz";
            String text = "Hotspot on " + band + (s.frequency > 0 ? " (" + s.frequency + " MHz)" : "");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.notify(NOTIF_ID, build(five, text));
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void startInForeground(Notification n) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private Notification build(boolean five, String text) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        b.setSmallIcon(five ? R.drawable.ic_stat_5 : R.drawable.ic_stat_24)
                .setContentTitle("Hotspot Band")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
        if (Build.VERSION.SDK_INT >= 21) b.setCategory(Notification.CATEGORY_STATUS);
        return b.build();
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null) return;
            // DEFAULT importance so HyperOS keeps the icon in the status bar, but silenced
            // (no sound, no vibration) since it is only a persistent indicator.
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Hotspot band indicator",
                    NotificationManager.IMPORTANCE_DEFAULT);
            ch.setShowBadge(false);
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.enableLights(false);
            nm.createNotificationChannel(ch);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
