package com.hotspotband;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * Quick Settings tile for one band. Tap when off (or on the other band) to start the hotspot
 * on this band; tap when already on this band to turn the hotspot off.
 */
public abstract class BandTile extends TileService {
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean busy;

    protected abstract boolean is5GHz();

    protected abstract Class<? extends BandTile> otherTile();

    @Override
    public void onStartListening() {
        if (busy) return;
        new Thread(() -> {
            Status status = Runner.run(this, "status");
            main.post(() -> show(status));
        }).start();
    }

    @Override
    public void onClick() {
        if (busy) return;
        Tile tile = getQsTile();
        busy = true;
        if (tile != null) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setSubtitle("Working...");
            tile.updateTile();
        }
        new Thread(() -> {
            // Decide from the live state, not the tile's last shown state, which may be stale.
            Status current = Runner.run(this, "status");
            Status status;
            if (current.error != null) {
                status = current;
            } else {
                boolean onThisBand = is5GHz() ? current.isOn5GHz() : current.isOn24GHz();
                status = Runner.run(this, onThisBand ? "off" : is5GHz() ? Prefs.command5GHz(this) : Prefs.command24GHz(this));
            }
            busy = false;
            main.post(() -> {
                show(status);
                if (Runner.NOT_RUNNING.equals(status.error) || Runner.NO_PERMISSION.equals(status.error)) {
                    openApp();
                }
                TileService.requestListeningState(this, new ComponentName(this, otherTile()));
            });
        }).start();
    }

    private void show(Status status) {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean onThisBand = is5GHz() ? status.isOn5GHz() : status.isOn24GHz();
        if (status.isOn()) BandStatusService.start(this);
        tile.setIcon(Icon.createWithResource(this, is5GHz() ? R.drawable.ic_tile_5 : R.drawable.ic_tile_24));
        tile.setState(onThisBand ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (status.error != null) {
            tile.setSubtitle("Open app");
        } else if (onThisBand) {
            tile.setSubtitle(status.frequency > 0 ? status.frequency + " MHz" : "On");
        } else {
            tile.setSubtitle(status.isOn() ? "Tap to switch" : "Off");
        }
        tile.updateTile();
    }

    private void openApp() {
        Intent intent = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(intent);
        }
    }
}
