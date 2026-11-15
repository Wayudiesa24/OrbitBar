package com.orbitbar.app;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Process;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AppRepository {
    static class Entry {
        final String pkg, label;
        final Drawable icon;
        Entry(String pkg, String label, Drawable icon) {
            this.pkg = pkg; this.label = label; this.icon = icon;
        }
    }
    static List<Entry> installed(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(intent, 0);
        Map<String, Entry> found = new LinkedHashMap<>();
        for (ResolveInfo r : resolved) {
            if (r.activityInfo == null) continue;
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || found.containsKey(pkg)) continue;
            CharSequence label = r.loadLabel(pm);
            found.put(pkg, new Entry(pkg, label == null ? pkg : label.toString(), r.loadIcon(pm)));
        }
        List<Entry> entries = new ArrayList<>(found.values());
        Collections.sort(entries, Comparator.comparing(e -> e.label.toLowerCase(java.util.Locale.ROOT)));
        return entries;
    }
    static List<Entry> select(List<Entry> all, List<String> packages, int limit) {
        Map<String, Entry> byPkg = new HashMap<>();
        for (Entry e : all) byPkg.put(e.pkg, e);
        List<Entry> out = new ArrayList<>();
        for (String pkg : packages) {
            Entry e = byPkg.get(pkg);
            if (e != null) out.add(e);
            if (out.size() >= limit) break;
        }
        return out;
    }
    static boolean hasUsagePermission(Context c) {
        AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        return ops != null && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), c.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }
    static List<Entry> recent(Context c, List<Entry> installed, int limit) {
        List<String> pkgs = new ArrayList<>();
        if (hasUsagePermission(c)) {
            try {
                UsageStatsManager manager = (UsageStatsManager)c.getSystemService(Context.USAGE_STATS_SERVICE);
                long now = System.currentTimeMillis();
                UsageEvents events = manager.queryEvents(now - 3L * 86400000L, now);
                Map<String, Long> latest = new HashMap<>();
                if (events != null) {
                    UsageEvents.Event event = new UsageEvents.Event();
                    while (events.hasNextEvent()) {
                        events.getNextEvent(event);
                        if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                            latest.put(event.getPackageName(), event.getTimeStamp());
                        }
                    }
                }
                List<Map.Entry<String, Long>> sorted = new ArrayList<>(latest.entrySet());
                sorted.sort((a,b) -> Long.compare(b.getValue(), a.getValue()));
                for (Map.Entry<String, Long> item : sorted) pkgs.add(item.getKey());
            } catch (SecurityException ex) {
                // Usage access may be revoked while OrbitBar is running.
            }
        }
        for (String pkg : Prefs.history(c)) if (!pkgs.contains(pkg)) pkgs.add(pkg);
        List<Entry> candidates = select(installed, pkgs, installed.size());
        return candidates.subList(0, Math.min(limit, candidates.size()));
    }
    private AppRepository() {}
}
