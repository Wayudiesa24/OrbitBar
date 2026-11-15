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
    static void setMode(Context c, String mode) { p(c).edit().putString("mode", mode).apply(); }
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
