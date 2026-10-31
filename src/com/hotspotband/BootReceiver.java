package com.hotspotband;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restarts the USB auto-share watcher after a reboot or app update, if it is enabled. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        UsbAutoService.sync(context);
    }
}
