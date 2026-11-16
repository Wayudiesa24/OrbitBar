package com.orbitbar.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Optional Orbit bubble companion protocol ported from Orbit-AI-Launcher.
 * co.bubbles decides whether the requested app can be bubbled.
 */
final class OrbitBubbleBridge {
    private static final String HOST = "co.bubbles";
    private static final String EXTRA = "co.bubbles.extra.PENDING_LAUNCH";

    static String launch(Context context, String appPackage) {
        if (appPackage == null || appPackage.trim().isEmpty()) return "App not found.";
        if (HOST.equals(appPackage)) return "Open the Orbit bubble host normally.";
        Intent request = new Intent();
        request.setComponent(new ComponentName(HOST, "co.bubbles.MainActivity"));
        request.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        request.putStringArrayListExtra(EXTRA,
                new ArrayList<>(Collections.singletonList(appPackage)));
        try {
            ActivityInfo info = context.getPackageManager()
                    .getActivityInfo(request.getComponent(), 0);
            if (!info.enabled || !info.exported) {
                return "Orbit bubble host is installed but its launcher is unavailable.";
            }
            context.startActivity(request);
            return null;
        } catch (PackageManager.NameNotFoundException error) {
            return "Install or enable the Orbit bubble companion app first.";
        } catch (SecurityException error) {
            return "Android denied the Orbit bubble request.";
        } catch (ActivityNotFoundException error) {
            return "Orbit bubble launcher couldn't be opened.";
        } catch (Exception error) {
            return "Unable to send this app to Orbit.";
        }
    }
    private OrbitBubbleBridge() {}
}
