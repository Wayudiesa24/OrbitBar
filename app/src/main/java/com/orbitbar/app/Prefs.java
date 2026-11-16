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

    static String windowSizeMode(Context c) {
        return p(c).getString("windowSizeMode", freeformCompact(c) ? "small" : "medium");
    }
    static void setWindowSizeMode(Context c, String v) {
        if (v.equals("small") || v.equals("medium") || v.equals("large") || v.equals("custom"))
            p(c).edit().putString("windowSizeMode", v).apply();
    }
    static int windowWidth(Context c) {
        switch (windowSizeMode(c)) {
            case "small": return 59; case "large": return 94;
            case "custom": return p(c).getInt("customWidth", 70);
            default: return 82;
        }
    }
    static int windowHeight(Context c) {
        switch (windowSizeMode(c)) {
            case "small": return 54; case "large": return 88;
            case "custom": return p(c).getInt("customHeight", 65);
            default: return 75;
        }
    }
    static int customWidth(Context c) { return p(c).getInt("customWidth", 70); }
    static int customHeight(Context c) { return p(c).getInt("customHeight", 65); }
    static void setCustomWidth(Context c, int v) {
        p(c).edit().putInt("customWidth", clamp(v,35,96)).putString("windowSizeMode","custom").apply();
    }
    static void setCustomHeight(Context c, int v) {
        p(c).edit().putInt("customHeight", clamp(v,35,94)).putString("windowSizeMode","custom").apply();
    }
    private static int clamp(int v,int lo,int hi) { return Math.max(lo,Math.min(hi,v)); }
    private static String presetKey(String pkg) { return "windowPreset:" + pkg; }
    static boolean hasPreset(Context c,String pkg) { return pkg!=null && p(c).contains(presetKey(pkg)); }
    static void savePreset(Context c,String pkg) {
        if (pkg==null || pkg.isEmpty()) return;
        String v=freeformX(c)+","+freeformY(c)+","+windowWidth(c)+","+windowHeight(c);
        p(c).edit().putString(presetKey(pkg),v).apply();
    }
    static void clearPreset(Context c,String pkg) {
        if (pkg!=null) p(c).edit().remove(presetKey(pkg)).apply();
    }
    static int[] windowSpec(Context c,String pkg) {
        int[] standard={freeformX(c),freeformY(c),windowWidth(c),windowHeight(c)};
        if (!hasPreset(c,pkg)) return standard;
        String[] s=p(c).getString(presetKey(pkg),"").split(",");
        if(s.length!=4)return standard;
        try { return new int[]{clamp(Integer.parseInt(s[0]),0,100),
                clamp(Integer.parseInt(s[1]),0,100),clamp(Integer.parseInt(s[2]),35,96),
                clamp(Integer.parseInt(s[3]),35,94)}; }
        catch(NumberFormatException ex){return standard;}
    }
    static String pairFirst(Context c){return p(c).getString("pairFirst","");}
    static String pairSecond(Context c){return p(c).getString("pairSecond","");}
    static void setPair(Context c,boolean first,String pkg){
        p(c).edit().putString(first?"pairFirst":"pairSecond",pkg).apply();
    }
    static String pairLayout(Context c){return p(c).getString("pairLayout","side");}
    static void setPairLayout(Context c,String mode){
        p(c).edit().putString("pairLayout",mode.equals("stack")?"stack":"side").apply();
    }
    static boolean pairReady(Context c){
        return !pairFirst(c).isEmpty() && !pairSecond(c).isEmpty()
                && !pairFirst(c).equals(pairSecond(c));
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
