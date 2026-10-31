package com.hotspotband;

import android.content.Context;

final class Prefs {
    static final int[] CHANNELS = {149, 153, 157, 161, 36, 40, 44, 48};

    private Prefs() {
    }

    static int channel(Context context) {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("channel", 149);
    }

    static void setChannel(Context context, int channel) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putInt("channel", channel).apply();
    }

    static String command5GHz(Context context) {
        return "5g " + channel(context);
    }

    /** Whether to turn on USB tethering automatically when a computer is plugged in. */
    static boolean usbAuto(Context context) {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("usb_auto", false);
    }

    static void setUsbAuto(Context context, boolean enabled) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("usb_auto", enabled).apply();
    }
}
