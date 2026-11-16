package com.orbitbar.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class Prefs {
    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("orbitbar", Context.MODE_PRIVATE);
    }
    static String mode(Context c) { return p(c).getString("mode", "dock"); }
    static int edgeOffset(Context c) { return p(c).getInt("edgeOffset", 0); }
    static void setEdgeOffset(Context c, int value) { p(c).edit().putInt("edgeOffset", value).apply(); }
    static void setMode(Context c, String mode) { p(c).edit().putString("mode", mode).apply(); }
    static String hideStyle(Context c) { return p(c).getString("hideStyle", "pill"); }
    static void setHideStyle(Context c, String style) { p(c).edit().putString("hideStyle", style).apply(); }
    static int opacity(Context c) { return p(c).getInt("opacity", 93); }
    static void setOpacity(Context c, int pct) { p(c).edit().putInt("opacity", Math.max(40, Math.min(100, pct))).apply(); }
    static int bubbleSize(Context c) { return p(c).getInt("bubbleSize", 58); }
    static void setBubbleSize(Context c, int dp) { p(c).edit().putInt("bubbleSize", Math.max(48, Math.min(76, dp))).apply(); }
    static int bubbleX(Context c) { return p(c).getInt("bubbleX", -1); }
    static int bubbleY(Context c) { return p(c).getInt("bubbleY", -1); }
    static void setBubblePosition(Context c, int x, int y) { p(c).edit().putInt("bubbleX", x).putInt("bubbleY", y).apply(); }
    static int miniX(Context c) { return p(c).getInt("miniX", -1); }
    static int miniY(Context c) { return p(c).getInt("miniY", -1); }
    static void setMiniPosition(Context c, int x, int y) { p(c).edit().putInt("miniX", x).putInt("miniY", y).apply(); }
    static int freeformX(Context c) { return p(c).getInt("freeformX", 50); }
    static int freeformY(Context c) { return p(c).getInt("freeformY", 50); }
    static void setFreeformPosition(Context c, int x, int y) {
        p(c).edit().putInt("freeformX", Math.max(0, Math.min(100, x)))
                .putInt("freeformY", Math.max(0, Math.min(100, y))).apply();
    }
    static boolean freeformCompact(Context c) { return p(c).getBoolean("freeformCompact", false); }
    static void setFreeformCompact(Context c, boolean compact) {
        p(c).edit().putBoolean("freeformCompact", compact).apply();
    }
    static String launchMode(Context c) { return p(c).getString("launchMode", "normal"); }
    static void setLaunchMode(Context c, String mode) { p(c).edit().putString("launchMode", mode).apply(); }
    static List<String> pins(Context c) { return decode(p(c).getString("pins", "")); }
    static void togglePin(Context c, String pkg) {
        LinkedHashSet<String> s = new LinkedHashSet<>(pins(c));
        if (!s.remove(pkg)) s.add(pkg);
        p(c).edit().putString("pins", String.join("|", s)).apply();
    }
    static void ensureInitialPins(Context c, List<AppRepository.Entry> entries) {
        if (p(c).contains("pins")) return;
        List<String> priority = new ArrayList<>();
        String[] preferred = {"com.android.chrome", "com.google.android.youtube", "com.whatsapp",
                "com.google.android.gm", "com.google.android.apps.maps", "com.google.android.calendar"};
        for (String pkg : preferred) {
            for (AppRepository.Entry e : entries) {
                if (e.pkg.equals(pkg) && !priority.contains(pkg)) { priority.add(pkg); break; }
            }
        }
        for (AppRepository.Entry e : entries) {
            if (priority.size() >= 5) break;
            if (!priority.contains(e.pkg)) priority.add(e.pkg);
        }
        p(c).edit().putString("pins", String.join("|", priority)).apply();
    }
    static List<String> history(Context c) { return decode(p(c).getString("history", "")); }
    static void recordLaunch(Context c, String pkg) {
        List<String> h = history(c); h.remove(pkg); h.add(0, pkg);
        if (h.size() > 15) h = new ArrayList<>(h.subList(0, 15));
        p(c).edit().putString("history", String.join("|", h)).apply();
    }
    private static List<String> decode(String s) {
        List<String> result = new ArrayList<>();
        if (!s.isEmpty()) for (String part : s.split("\\|")) if (!part.isEmpty()) result.add(part);
        return result;
    }
    private Prefs() {}
}
