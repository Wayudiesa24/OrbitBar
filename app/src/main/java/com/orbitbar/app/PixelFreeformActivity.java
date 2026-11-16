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
        int winW = (int)(width * (width > height ? .78f : .82f));
        int winH = (int)(height * (width > height ? .80f : .75f));
        options.setLaunchBounds(new Rect((width - winW) / 2, (height - winH) / 2,
                (width + winW) / 2, (height + winH) / 2));
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
            Toast.makeText(this, "Requested Pixel freeform (system decides the window mode)",
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
