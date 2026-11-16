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

/** Best-effort Pixel freeform windowing mode 5. Android decides final placement. */
public class PixelFreeformActivity extends Activity {
    static final String EXTRA_PACKAGE = "com.orbitbar.app.targetPackage";
    static final String EXTRA_SECOND = "com.orbitbar.app.pairSecondPackage";
    static final String EXTRA_LAYOUT = "com.orbitbar.app.pairLayout";
    private static final int FREEFORM_MODE = 5;
    private static final String TAG = "OrbitBarPixelFreeform";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String first = getIntent().getStringExtra(EXTRA_PACKAGE);
        if (!valid(first)) { finish(); return; }
        String second = getIntent().getStringExtra(EXTRA_SECOND);
        boolean pair = valid(second) && !first.equals(second);
        if (pair) {
            boolean stack = "stack".equals(getIntent().getStringExtra(EXTRA_LAYOUT));
            boolean ok1 = launchOne(first, stack
                    ? new int[]{50,0,82,43} : new int[]{0,50,46,78});
            boolean ok2 = launchOne(second, stack
                    ? new int[]{50,100,82,43} : new int[]{100,50,46,78});
            Toast.makeText(this, ok1 && ok2
                    ? "Requested both freeform windows; Pixel controls stacking"
                    : "Could not launch one or both freeform windows",
                    Toast.LENGTH_LONG).show();
        } else {
            boolean ok = launchOne(first, Prefs.windowSpec(this,first));
            Toast.makeText(this, ok ? "Requested saved freeform size and position"
                    : "Android blocked freeform launch", Toast.LENGTH_SHORT).show();
        }
        finish();
    }
    private boolean valid(String pkg) {
        return pkg!=null && !pkg.isEmpty() && !pkg.equals(getPackageName());
    }
    private boolean launchOne(String pkg,int[] spec){
        Intent target=getPackageManager().getLaunchIntentForPackage(pkg);
        if(target==null)return false;
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        ActivityOptions options=ActivityOptions.makeBasic();
        DisplayMetrics dm=getResources().getDisplayMetrics();
        int width=dm.widthPixels,height=dm.heightPixels;
        int marginX=Math.round(dm.density*8), marginTop=Math.round(dm.density*30),
                marginBottom=Math.round(dm.density*40);
        int usableW=Math.max(1,width-2*marginX),usableH=Math.max(1,height-marginTop-marginBottom);
        int winW=Math.max(1,Math.min(usableW,Math.round(width*Math.max(35,Math.min(96,spec[2]))/100f)));
        int winH=Math.max(1,Math.min(usableH,Math.round(height*Math.max(35,Math.min(94,spec[3]))/100f)));
        int left=marginX+Math.round((usableW-winW)*Math.max(0,Math.min(100,spec[0]))/100f);
        int top=marginTop+Math.round((usableH-winH)*Math.max(0,Math.min(100,spec[1]))/100f);
        options.setLaunchBounds(new Rect(left,top,left+winW,top+winH));
        try{
            // Preserve the approach proven on the user's Pixel in OrbitBar v0.4/v0.5.
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P){
                HiddenApiBypass.addHiddenApiExemptions("L");
                HiddenApiBypass.invoke(ActivityOptions.class,options,
                        "setLaunchWindowingMode",FREEFORM_MODE);
            }else{
                ActivityOptions.class.getDeclaredMethod("setLaunchWindowingMode",int.class)
                        .invoke(options,FREEFORM_MODE);
            }
            startActivity(target,options.toBundle());
            return true;
        }catch(Throwable error){
            Log.w(TAG,"Freeform request blocked for "+pkg,error);
            return false;
        }
    }
}
