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
}
