package com.orbitbar.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int BG = Color.rgb(10, 15, 29);
    static final int SURFACE = Color.rgb(23, 30, 50);
    static final int SURFACE2 = Color.rgb(35, 43, 68);
    static final int ACCENT = Color.rgb(147, 160, 255);
    static final int TEXT = Color.rgb(244, 246, 255);
    static final int MUTED = Color.rgb(164, 176, 203);

    static int dp(Context c, float v) { return (int) (v * c.getResources().getDisplayMetrics().density + .5f); }
    static GradientDrawable shape(int bg, float radius, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(dp(c, radius));
        return d;
    }
    static TextView text(Context c, String value, int sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    static TextView pill(Context c, String label, Runnable action) {
        TextView t = text(c, label, 13, TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 14), dp(c, 11), dp(c, 14), dp(c, 11));
        t.setBackground(shape(SURFACE2, 14, c));
        t.setOnClickListener(v -> action.run());
        return t;
    }
    static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    static LinearLayout horizontal(Context c) {
        LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL); return l;
    }
    static void gap(LinearLayout l, Context c, int h) {
        View v = new View(c);
        l.addView(v, new LinearLayout.LayoutParams(1, dp(c, h)));
    }
    static void hgap(LinearLayout l, Context c, int w) {
        View v = new View(c);
        l.addView(v, new LinearLayout.LayoutParams(dp(c, w), 1));
    }
    private Ui() {}
}
