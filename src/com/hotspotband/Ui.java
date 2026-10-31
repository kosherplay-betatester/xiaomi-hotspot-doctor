package com.hotspotband;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Colors and small view factories for the app's hand-built (no XML, no AppCompat) screen. */
final class Ui {
    static final int THEME_AUTO = 0;
    static final int THEME_LIGHT = 1;
    static final int THEME_DARK = 2;

    // Band and state colors, the same in light and dark.
    static final int BLUE = 0xFF3D7BFD;
    static final int VIOLET = 0xFF7C5CFF;
    static final int TEAL = 0xFF12B886;
    static final int GREEN = 0xFF2FB344;
    static final int RED = 0xFFE5484D;
    static final int AMBER = 0xFFF59F00;

    final boolean dark;
    final int background;
    final int surface;
    final int surfaceAlt;
    final int stroke;
    final int text;
    final int textSecondary;
    private final Context context;

    Ui(Context context) {
        this.context = context;
        // The palette follows the saved choice directly: HyperOS can ignore the per-app night
        // mode, and every color on the screen comes from here anyway.
        int theme = Prefs.theme(context);
        dark = theme == THEME_DARK || (theme == THEME_AUTO
                && (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES);
        background = dark ? 0xFF0E1015 : 0xFFF3F5FA;
        surface = dark ? 0xFF191C23 : 0xFFFFFFFF;
        surfaceAlt = dark ? 0xFF232733 : 0xFFEEF1F7;
        stroke = dark ? 0xFF2A2F3B : 0xFFE3E7EF;
        text = dark ? 0xFFF1F3F8 : 0xFF141821;
        textSecondary = dark ? 0xFF9AA3B5 : 0xFF5F6878;
    }

    /**
     * Applies the saved theme choice to this app. Android recreates the activity when the
     * effective day/night mode changes. Auto follows the phone's dark-mode setting.
     */
    static void applyTheme(Context context, int theme) {
        if (Build.VERSION.SDK_INT < 31) return;
        UiModeManager manager = context.getSystemService(UiModeManager.class);
        if (manager == null) return;
        int mode = theme == THEME_LIGHT ? UiModeManager.MODE_NIGHT_NO
                : theme == THEME_DARK ? UiModeManager.MODE_NIGHT_YES : UiModeManager.MODE_NIGHT_AUTO;
        manager.setApplicationNightMode(mode);
    }

    /** Colors the window and picks dark or light system-bar icons to match. */
    void styleWindow(Activity activity) {
        activity.getWindow().getDecorView().setBackgroundColor(background);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = activity.getWindow().getInsetsController();
            if (controller != null) {
                int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(dark ? 0 : light, light);
            }
        }
    }

    int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    static int withAlpha(int color, float alpha) {
        return Color.argb(Math.round(alpha * 255), Color.red(color), Color.green(color), Color.blue(color));
    }

    GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    GradientDrawable outlined(int fill, int strokeColor, float radiusDp) {
        GradientDrawable drawable = rounded(fill, radiusDp);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    /** Wraps a background in a touch ripple. */
    Drawable pressable(Drawable content, float radiusDp) {
        int ripple = dark ? 0x33FFFFFF : 0x22000000;
        return new RippleDrawable(ColorStateList.valueOf(ripple), content, rounded(Color.WHITE, radiusDp));
    }

    TextView text(String value, float sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return view;
    }

    /** A rounded panel holding one section, with an optional title. */
    LinearLayout card(LinearLayout parent, String title) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(18));
        card.setBackground(outlined(surface, stroke, 22));
        card.setElevation(dark ? 0 : dp(1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(14);
        parent.addView(card, params);
        if (title != null) {
            TextView heading = text(title.toUpperCase(), 12.5f, textSecondary, true);
            heading.setLetterSpacing(0.08f);
            heading.setPadding(0, 0, 0, dp(10));
            card.addView(heading);
        }
        return card;
    }

    /** A filled button with rounded corners. */
    TextView button(String label, int fill, int textColor, View.OnClickListener listener) {
        TextView button = text(label, 15.5f, textColor, true);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(50));
        button.setPadding(dp(18), dp(12), dp(18), dp(12));
        button.setBackground(pressable(rounded(fill, 16), 16));
        button.setOnClickListener(listener);
        button.setClickable(true);
        return button;
    }

    /** A button with a colored outline and no fill. */
    TextView outlineButton(String label, int color, View.OnClickListener listener) {
        TextView button = text(label, 15.5f, color, true);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(50));
        button.setPadding(dp(18), dp(12), dp(18), dp(12));
        button.setBackground(pressable(outlined(Color.TRANSPARENT, withAlpha(color, 0.55f), 16), 16));
        button.setOnClickListener(listener);
        return button;
    }

    ImageView icon(int drawable, int tint, int sizeDp) {
        ImageView view = new ImageView(context);
        view.setImageResource(drawable);
        view.setImageTintList(ColorStateList.valueOf(tint));
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return view;
    }

    /** A colored circle with a letter in it, used as a device avatar. */
    TextView avatar(String letter, int color) {
        TextView view = text(letter, 17, color, true);
        view.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(withAlpha(color, dark ? 0.22f : 0.14f));
        view.setBackground(circle);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
        return view;
    }

    void tint(Switch view, int accent) {
        int[][] states = {{android.R.attr.state_checked}, {}};
        view.setThumbTintList(new ColorStateList(states, new int[]{accent, dark ? 0xFFB8BFCC : 0xFFFFFFFF}));
        view.setTrackTintList(new ColorStateList(states,
                new int[]{withAlpha(accent, 0.45f), dark ? 0xFF3A4050 : 0xFFC9CFDA}));
    }

    static LinearLayout.LayoutParams weight(float weight) {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
    }

    LinearLayout.LayoutParams matchWidth(int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMarginDp);
        return params;
    }
}
