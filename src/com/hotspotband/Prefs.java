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

    static final int NAME_HISTORY_SIZE = 10;

    /** Hotspot names used most recently first, at most {@link #NAME_HISTORY_SIZE}. */
    static java.util.List<String> nameHistory(Context context) {
        java.util.List<String> names = new java.util.ArrayList<>();
        try {
            org.json.JSONArray array = new org.json.JSONArray(context.getSharedPreferences("settings",
                    Context.MODE_PRIVATE).getString("name_history", "[]"));
            for (int i = 0; i < array.length(); i++) names.add(array.getString(i));
        } catch (org.json.JSONException ignored) {
        }
        return names;
    }

    /** Moves {@code name} to the front of the history. Returns true if the history changed. */
    static boolean rememberName(Context context, String name) {
        if (name == null || name.isEmpty()) return false;
        java.util.List<String> names = nameHistory(context);
        if (!names.isEmpty() && names.get(0).equals(name)) return false;
        names.remove(name);
        names.add(0, name);
        while (names.size() > NAME_HISTORY_SIZE) names.remove(names.size() - 1);
        saveNameHistory(context, names);
        return true;
    }

    static void forgetName(Context context, String name) {
        java.util.List<String> names = nameHistory(context);
        names.remove(name);
        saveNameHistory(context, names);
    }

    private static void saveNameHistory(Context context, java.util.List<String> names) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putString("name_history", new org.json.JSONArray(names).toString()).apply();
    }

    /** One of {@link Ui#THEME_AUTO}, {@link Ui#THEME_LIGHT}, {@link Ui#THEME_DARK}. */
    static int theme(Context context) {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("theme", Ui.THEME_AUTO);
    }

    static void setTheme(Context context, int theme) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putInt("theme", theme).apply();
    }

    static void setUsbAuto(Context context, boolean enabled) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("usb_auto", enabled).apply();
    }
}
