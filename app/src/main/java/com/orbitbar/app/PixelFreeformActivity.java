package com.orbitbar.app;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

/**
 * Pixel/AOSP freeform launch, adapted from the user's Orbit-AI-Launcher
 * (its Smart Edge-inspired, MIT-licensed approach; see SMART-EDGE-LICENSE.txt).
 * Unsupported Android API: may be blocked or ignored on Pixel OS updates.
 */
public class PixelFreeformActivity extends Activity {
    static final String EXTRA_PACKAGE = "com.orbitbar.app.targetPackage";
    private static final int FREEFORM_WINDOWING_MODE = 5;
    private static final String LOG = "OrbitBarPixelFreeform";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String pkg = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_PACKAGE);
        if (pkg == null || pkg.isEmpty() || pkg.equals(getPackageName())) {
            finish();
            return;
        }
        Intent target = getPackageManager().getLaunchIntentForPackage(pkg);
        if (target == null) {
            Toast.makeText(this, "Couldn't find that app", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        ActivityOptions options = ActivityOptions.makeBasic();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int width = dm.widthPixels, height = dm.heightPixels;
        // This changes the requested START position, not an already-open window.
        // Pixel's window manager may override these bounds on the active display.
        float scale = Prefs.freeformCompact(this) ? 0.72f : 1.0f;
        int marginX = Math.round(dm.density * 8);
        int marginTop = Math.round(dm.density * 30);
        int marginBottom = Math.round(dm.density * 40);
        int usableW = Math.max(1, width - 2 * marginX);
        int usableH = Math.max(1, height - marginTop - marginBottom);
        int winW = Math.min(usableW,
                (int)(width * (width > height ? .78f : .82f) * scale));
        int winH = Math.min(usableH,
                (int)(height * (width > height ? .80f : .75f) * scale));
        int left = marginX + Math.round((usableW - winW) * Prefs.freeformX(this) / 100f);
        int top = marginTop + Math.round((usableH - winH) * Prefs.freeformY(this) / 100f);
        options.setLaunchBounds(new Rect(left, top, left + winW, top + winH));
        try {
            // Explicitly request freeform mode, rather than bounds alone.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                HiddenApiBypass.addHiddenApiExemptions("L");
                HiddenApiBypass.invoke(ActivityOptions.class, options,
                        "setLaunchWindowingMode", FREEFORM_WINDOWING_MODE);
            } else {
                ActivityOptions.class.getDeclaredMethod("setLaunchWindowingMode", int.class)
                        .invoke(options, FREEFORM_WINDOWING_MODE);
            }
            startActivity(target, options.toBundle());
            Toast.makeText(this, "Requested freeform at saved position (Pixel decides)",
                    Toast.LENGTH_SHORT).show();
        } catch (Throwable error) {
            Log.w(LOG, "Freeform launch blocked", error);
            Toast.makeText(this,
                    "Android blocked freeform launch. App not opened in fullscreen.",
                    Toast.LENGTH_LONG).show();
        } finally {
            finish();
        }
    }
}
