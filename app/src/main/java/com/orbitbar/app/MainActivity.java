package com.orbitbar.app;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private LinearLayout content;
    private boolean showingPicker;
    private final List<AppRepository.Entry> apps = new ArrayList<>();

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        apps.addAll(AppRepository.installed(this));
        Prefs.ensureInitialPins(this, apps);
        showHome();
    }
    @Override protected void onResume() {
        super.onResume();
        if (!showingPicker && content != null) showHome();
    }
    private void shell() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BG);
        content = Ui.vertical(this);
        content.setPadding(Ui.dp(this, 20), Ui.dp(this, 28), Ui.dp(this, 20), Ui.dp(this, 32));
        scroll.addView(content);
        setContentView(scroll);
    }
    private void section(String title, String description) {
        TextView head = Ui.text(this, title, 19, Ui.TEXT, true);
        content.addView(head);
        Ui.gap(content, this, 5);
        TextView sub = Ui.text(this, description, 13, Ui.MUTED, false);
        content.addView(sub);
        Ui.gap(content, this, 14);
    }
    private void addButton(LinearLayout row, String name, Runnable action) {
        TextView b = Ui.pill(this, name, action);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Ui.dp(this, 47), 1);
        p.setMargins(0, 0, Ui.dp(this, 6), 0);
        row.addView(b, p);
    }
    private void showHome() {
        showingPicker = false;
        shell();
        TextView eyebrow = Ui.text(this, "ORBIT SYSTEMS  /  V0.6", 11, Ui.ACCENT, true);
        content.addView(eyebrow); Ui.gap(content, this, 8);
        content.addView(Ui.text(this, "OrbitBar", 34, Ui.TEXT, true));
        Ui.gap(content, this, 4);
        content.addView(Ui.text(this, "Your apps, always within reach.", 15, Ui.MUTED, false));
        Ui.gap(content, this, 26);

        section("Taskbar layout", "Switch layouts at any time, including from the expanded overlay.");
        LinearLayout modes = Ui.horizontal(this);
        addButton(modes, modeName("dock"), () -> selectMode("dock"));
        addButton(modes, modeName("taskbar"), () -> selectMode("taskbar"));
        addButton(modes, modeName("edge"), () -> selectMode("edge"));
        addButton(modes, modeName("bubble"), () -> selectMode("bubble"));
        content.addView(modes);
        Ui.gap(content, this, 11);
        TextView chosen = Ui.text(this, "Selected: " + modeName(Prefs.mode(this)), 13, Ui.ACCENT, true);
        content.addView(chosen);
        Ui.gap(content, this, 10);
        content.addView(Ui.text(this,
                "Bubble mode is OrbitBar's own movable overlay. Other apps open normally, " +
                "or try Float on devices with supported freeform windows.", 12, Ui.MUTED, false));
        Ui.gap(content, this, 24);

        section("When you hide the bar", "Tap Hide on the taskbar to tuck it into a small handle. Tap the handle to open it again.");
        LinearLayout hideChoices = Ui.horizontal(this);
        addButton(hideChoices, "Slim pill", () -> selectHideStyle("pill"));
        addButton(hideChoices, "Small dot", () -> selectHideStyle("dot"));
        addButton(hideChoices, "Side tab", () -> selectHideStyle("side"));
        content.addView(hideChoices);
        Ui.gap(content, this, 7);
        content.addView(Ui.pill(this, "◌  Fully hidden — no floating handle", () -> selectHideStyle("off")));
        Ui.gap(content, this, 9);
        String hide = Prefs.hideStyle(this);
        String hideLabel = hide.equals("off") ? "Fully hidden" :
                hide.equals("dot") ? "Small dot" : hide.equals("side") ? "Side tab" : "Slim pill";
        content.addView(Ui.text(this, "Selected hide style: " + hideLabel, 13, Ui.ACCENT, true));
        Ui.gap(content, this, 7);
        content.addView(Ui.text(this,
                "The slim pill can also be dragged. Long-press it to hide completely. " +
                "Restore OrbitBar from its notification or the Show button below.", 12, Ui.MUTED, false));
        Ui.gap(content, this, 22);

        section("Appearance", "Choose a comfortable bubble size and taskbar transparency.");
        TextView opacity = Ui.text(this, "Background opacity: " + Prefs.opacity(this) + "%", 13, Ui.TEXT, true);
        content.addView(opacity);
        SeekBar alphaSeek = new SeekBar(this);
        alphaSeek.setMax(100);
        alphaSeek.setProgress(Prefs.opacity(this));
        alphaSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar v) {}
            public void onStopTrackingTouch(SeekBar v) {
                Prefs.setOpacity(MainActivity.this, Math.max(40, v.getProgress()));
                refreshOverlay();
            }
            public void onProgressChanged(SeekBar v, int progress, boolean fromUser) {
                opacity.setText("Background opacity: " + Math.max(40, progress) + "%");
            }
        });
        content.addView(alphaSeek);
        Ui.gap(content, this, 7);
        TextView bubbleSize = Ui.text(this, "Bubble diameter: " + Prefs.bubbleSize(this) + " dp", 13, Ui.TEXT, true);
        content.addView(bubbleSize);
        SeekBar bubbleSeek = new SeekBar(this);
        bubbleSeek.setMax(76);
        bubbleSeek.setProgress(Prefs.bubbleSize(this));
        bubbleSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar v) {}
            public void onStopTrackingTouch(SeekBar v) {
                Prefs.setBubbleSize(MainActivity.this, Math.max(48, v.getProgress()));
                refreshOverlay();
            }
            public void onProgressChanged(SeekBar v, int progress, boolean fromUser) {
                bubbleSize.setText("Bubble diameter: " + Math.max(48, progress) + " dp");
            }
        });
        content.addView(bubbleSeek);
        Ui.gap(content, this, 24);

        section("Pixel Freeform window placement",
                "Choose where a floating app starts. This cannot drag an already-open window. " +
                "Pixel may adjust the requested location.");
        LinearLayout startPosition = Ui.horizontal(this);
        addButton(startPosition, "↖ Top-left", () -> setFreeformPosition(0, 0));
        addButton(startPosition, "⊙ Centre", () -> setFreeformPosition(50, 50));
        addButton(startPosition, "↗ Top-right", () -> setFreeformPosition(100, 0));
        content.addView(startPosition);
        Ui.gap(content, this, 7);
        LinearLayout lowerPosition = Ui.horizontal(this);
        addButton(lowerPosition, "↙ Bottom-left", () -> setFreeformPosition(0, 100));
        addButton(lowerPosition, "↘ Bottom-right", () -> setFreeformPosition(100, 100));
        content.addView(lowerPosition);
        Ui.gap(content, this, 9);
        String positionName = freeformPositionName();
        content.addView(Ui.text(this, "Freeform start position: " + positionName,
                13, Ui.ACCENT, true));
        Ui.gap(content, this, 8);
        content.addView(Ui.text(this, "Window size: " + Prefs.windowSizeMode(this) +
                "  (" + Prefs.windowWidth(this) + "% × " + Prefs.windowHeight(this) + "%)",
                13, Ui.ACCENT, true));
        Ui.gap(content, this, 8);
        LinearLayout sizeOptions = Ui.horizontal(this);
        for (String size : new String[]{"small","medium","large","custom"}) {
            addButton(sizeOptions, size.substring(0,1).toUpperCase(Locale.ROOT) + size.substring(1),
                    () -> { Prefs.setWindowSizeMode(this, size); showHome(); });
        }
        content.addView(sizeOptions);
        Ui.gap(content, this, 9);
        addWindowSlider("Custom width", true);
        addWindowSlider("Custom height", false);
        Ui.gap(content, this, 22);

        section("Per-app freeform presets",
                "Set the default position and size above, then save them to an individual app. " +
                "Pixel Float will use its saved settings automatically.");
        content.addView(Ui.pill(this, "★  Save or remove app presets",
                () -> showConfigurationPicker(true, false)));
        Ui.gap(content, this, 8);
        int savedCount = 0;
        for (AppRepository.Entry entry : apps) {
            if (!Prefs.hasPreset(this, entry.pkg)) continue;
            savedCount++;
            if (savedCount > 6) continue;
            LinearLayout savedRow = Ui.horizontal(this);
            savedRow.addView(Ui.text(this, entry.label, 13, Ui.TEXT, false),
                    new LinearLayout.LayoutParams(0, Ui.dp(this, 41), 1));
            savedRow.addView(Ui.pill(this, "Remove", () -> {
                Prefs.clearPreset(this, entry.pkg); showHome();
            }));
            content.addView(savedRow);
            Ui.gap(content, this, 4);
        }
        if (savedCount == 0)
            content.addView(Ui.text(this, "No saved presets yet.", 12, Ui.MUTED, false));
        Ui.gap(content, this, 22);

        section("Two-app workspace",
                "Choose two apps and a layout. OrbitBar requests two separate freeform windows; " +
                "Pixel controls their final stacking and may adjust the positions.");
        LinearLayout pairApps = Ui.horizontal(this);
        addButton(pairApps, "① " + pairLabel(Prefs.pairFirst(this)),
                () -> showConfigurationPicker(false, true));
        addButton(pairApps, "② " + pairLabel(Prefs.pairSecond(this)),
                () -> showConfigurationPicker(false, false));
        content.addView(pairApps);
        Ui.gap(content, this, 7);
        LinearLayout layouts = Ui.horizontal(this);
        addButton(layouts, "◧ Side by side", () -> {
            Prefs.setPairLayout(this,"side"); showHome();
        });
        addButton(layouts, "▤ Stacked", () -> {
            Prefs.setPairLayout(this,"stack"); showHome();
        });
        content.addView(layouts);
        Ui.gap(content, this, 7);
        content.addView(Ui.text(this, "Chosen layout: " +
                (Prefs.pairLayout(this).equals("side") ? "Side by side" : "Stacked"),
                13, Ui.ACCENT, true));
        Ui.gap(content, this, 8);
        content.addView(Ui.pill(this, "▶  Launch two-app workspace", this::launchConfiguredPair));
        Ui.gap(content, this, 24);

        section("Your pinned apps", "Pick which apps appear on the taskbar. Long-press an icon on the overlay to pin or unpin.");
        HorizontalScrollView pinnedScroll = new HorizontalScrollView(this);
        pinnedScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout pinnedRow = Ui.horizontal(this);
        for (AppRepository.Entry e : AppRepository.select(apps, Prefs.pins(this), 12)) {
            LinearLayout item = Ui.vertical(this);
            item.setGravity(Gravity.CENTER);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(e.icon);
            item.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
            Ui.gap(item, this, 6);
            TextView label = Ui.text(this, abbreviate(e.label, 11), 11, Ui.MUTED, false);
            label.setGravity(Gravity.CENTER);
            item.addView(label);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Ui.dp(this, 76), Ui.dp(this, 78));
            pinnedRow.addView(item, p);
        }
        pinnedScroll.addView(pinnedRow);
        content.addView(pinnedScroll);
        Ui.gap(content, this, 10);
        content.addView(Ui.pill(this, "Edit pinned favourites  +", this::showPicker));
        Ui.gap(content, this, 24);

        section("Permissions", "OrbitBar works locally. Only grant access to features you want.");
        permissionRow("Display over other apps", Settings.canDrawOverlays(this), () -> {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });
        permissionRow("Recent app usage", AppRepository.hasUsagePermission(this),
                () -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        permissionRow("Media session controls", hasListenerPermission(),
                () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        if (Build.VERSION.SDK_INT >= 33) {
            boolean granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            permissionRow("Taskbar notification", granted,
                    () -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 75));
        }
        Ui.gap(content, this, 22);
        TextView start = Ui.pill(this, "▶   Start / Show OrbitBar", () -> {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Grant overlay permission first", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
                return;
            }
            Intent i = new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_SHOW);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
            Toast.makeText(this, "OrbitBar started", Toast.LENGTH_SHORT).show();
        });
        start.setTextSize(17);
        start.setBackground(Ui.shape(Ui.ACCENT, 16, this));
        start.setTextColor(Color.rgb(15, 20, 39));
        content.addView(start, new LinearLayout.LayoutParams(-1, Ui.dp(this, 58)));
        Ui.gap(content, this, 11);
        TextView stop = Ui.pill(this, "■   Stop OrbitBar", () -> {
            stopService(new Intent(this, OverlayService.class));
            Toast.makeText(this, "OrbitBar stopped", Toast.LENGTH_SHORT).show();
        });
        content.addView(stop, new LinearLayout.LayoutParams(-1, Ui.dp(this, 49)));
        Ui.gap(content, this, 16);
        content.addView(Ui.text(this,
                "Split and Float shortcuts depend on your device's multi-window support. " +
                "No Accessibility Service or full app-list permission is required.",
                12, Ui.MUTED, false));
    }
    private void addWindowSlider(String title, boolean width) {
        int min = 35, max = width ? 96 : 94;
        int value = width ? Prefs.customWidth(this) : Prefs.customHeight(this);
        TextView label = Ui.text(this, title + ": " + value + "%", 12, Ui.MUTED, false);
        content.addView(label);
        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(value - min);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar v) { }
            public void onStopTrackingTouch(SeekBar v) {
                if (width) Prefs.setCustomWidth(MainActivity.this, min + v.getProgress());
                else Prefs.setCustomHeight(MainActivity.this, min + v.getProgress());
                showHome();
            }
            public void onProgressChanged(SeekBar v, int progress, boolean fromUser) {
                label.setText(title + ": " + (min + progress) + "%");
            }
        });
        content.addView(bar);
    }
    private String pairLabel(String pkg) {
        if (pkg == null || pkg.isEmpty()) return "Choose app";
        for (AppRepository.Entry entry : apps)
            if (pkg.equals(entry.pkg)) return abbreviate(entry.label, 12);
        return "Unavailable";
    }
    private void launchConfiguredPair() {
        if (!Prefs.pairReady(this)) {
            Toast.makeText(this, "Choose two different apps first", Toast.LENGTH_LONG).show();
            return;
        }
        Intent request = new Intent(this, PixelFreeformActivity.class);
        request.putExtra(PixelFreeformActivity.EXTRA_PACKAGE, Prefs.pairFirst(this));
        request.putExtra(PixelFreeformActivity.EXTRA_SECOND, Prefs.pairSecond(this));
        request.putExtra(PixelFreeformActivity.EXTRA_LAYOUT, Prefs.pairLayout(this));
        startActivity(request);
    }
    private void showConfigurationPicker(boolean presets, boolean first) {
        showingPicker = true;
        shell();
        content.addView(Ui.pill(this, "‹  Back to settings", this::showHome));
        Ui.gap(content, this, 18);
        section(presets ? "Per-app window presets" : (first ? "Choose first app" : "Choose second app"),
                presets ? "Tap Save to store the current window size and position, or Reset to remove."
                        : "Choose an app for your two-app workspace.");
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search installed apps");
        search.setTextColor(Ui.TEXT);
        search.setHintTextColor(Ui.MUTED);
        search.setPadding(Ui.dp(this,14),Ui.dp(this,8),Ui.dp(this,14),Ui.dp(this,8));
        search.setBackground(Ui.shape(Ui.SURFACE, 13, this));
        content.addView(search, new LinearLayout.LayoutParams(-1, Ui.dp(this, 49)));
        Ui.gap(content,this,10);
        LinearLayout rows = Ui.vertical(this);
        content.addView(rows);
        populateConfigurationRows(rows, "", presets, first);
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                populateConfigurationRows(rows,s.toString(),presets,first);
            }
            public void afterTextChanged(Editable e){}
        });
    }
    private void populateConfigurationRows(LinearLayout rows,String filter,boolean presets,boolean first){
        rows.removeAllViews();
        String query=filter.toLowerCase(Locale.ROOT);
        int n=0;
        for(AppRepository.Entry entry:apps){
            if(!entry.label.toLowerCase(Locale.ROOT).contains(query))continue;
            if(++n>90)break;
            LinearLayout row=Ui.horizontal(this);
            row.setPadding(Ui.dp(this,6),Ui.dp(this,5),Ui.dp(this,6),Ui.dp(this,5));
            ImageView image=new ImageView(this);
            image.setImageDrawable(entry.icon);
            row.addView(image,new LinearLayout.LayoutParams(Ui.dp(this,32),Ui.dp(this,32)));
            Ui.hgap(row,this,10);
            row.addView(Ui.text(this,entry.label,13,Ui.TEXT,false),
                    new LinearLayout.LayoutParams(0,Ui.dp(this,41),1));
            if(presets){
                TextView action=Ui.pill(this,Prefs.hasPreset(this,entry.pkg)?"Reset":"Save",()->{
                    if(Prefs.hasPreset(this,entry.pkg))Prefs.clearPreset(this,entry.pkg);
                    else Prefs.savePreset(this,entry.pkg);
                    populateConfigurationRows(rows,filter,true,first);
                });
                row.addView(action);
            }else{
                row.setOnClickListener(v->{
                    Prefs.setPair(this,first,entry.pkg);
                    showHome();
                });
                row.addView(Ui.text(this,"Select ›",12,Ui.ACCENT,true));
            }
            rows.addView(row);
            Ui.gap(rows,this,3);
        }
        if(n==0)rows.addView(Ui.text(this,"No matching applications",13,Ui.MUTED,false));
    }
    private void setFreeformPosition(int x, int y) {
        Prefs.setFreeformPosition(this, x, y);
        showHome();
    }
    private String freeformPositionName() {
        int x = Prefs.freeformX(this), y = Prefs.freeformY(this);
        if (x == 0 && y == 0) return "Top-left";
        if (x == 100 && y == 0) return "Top-right";
        if (x == 0 && y == 100) return "Bottom-left";
        if (x == 100 && y == 100) return "Bottom-right";
        return "Centre";
    }
    private String modeName(String mode) {
        switch (mode) {
            case "taskbar": return "Taskbar";
            case "edge": return "EdgeBar";
            case "bubble": return "Bubble";
            default: return "Dock";
        }
    }
    private void refreshOverlay() {
        if (OverlayService.isRunning && Settings.canDrawOverlays(this)) {
            Intent refresh = new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_REFRESH);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(refresh);
            else startService(refresh);
        }
    }
    private void selectMode(String mode) {
        Prefs.setMode(this, mode);
        refreshOverlay();
        showHome();
    }
    private void selectHideStyle(String style) {
        Prefs.setHideStyle(this, style);
        refreshOverlay();
        showHome();
    }
    private void permissionRow(String name, boolean granted, Runnable action) {
        LinearLayout row = Ui.horizontal(this);
        row.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        row.setBackground(Ui.shape(Ui.SURFACE, 14, this));
        TextView label = Ui.text(this, name, 13, Ui.TEXT, true);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        TextView status = Ui.text(this, granted ? "✓ Ready" : "Set up  ›", 12,
                granted ? Color.rgb(111, 227, 175) : Ui.ACCENT, true);
        row.addView(status);
        row.setOnClickListener(v -> action.run());
        content.addView(row);
        Ui.gap(content, this, 8);
    }
    private boolean hasListenerPermission() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        return enabled != null && enabled.contains(getPackageName());
    }
    private void showPicker() {
        showingPicker = true;
        shell();
        content.addView(Ui.pill(this, "‹  Back", this::showHome));
        Ui.gap(content, this, 20);
        section("Pinned favourites", "Tap an app to pin or unpin it. Changes are saved immediately.");
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search installed apps");
        search.setTextColor(Ui.TEXT);
        search.setHintTextColor(Ui.MUTED);
        search.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        search.setBackground(Ui.shape(Ui.SURFACE, 13, this));
        content.addView(search, new LinearLayout.LayoutParams(-1, Ui.dp(this, 52)));
        Ui.gap(content, this, 12);
        LinearLayout list = Ui.vertical(this);
        content.addView(list);
        renderPickerRows(list, "");
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderPickerRows(list, s.toString());
            }
            @Override public void afterTextChanged(Editable e) { }
        });
    }
    private void renderPickerRows(LinearLayout list, String filter) {
        list.removeAllViews();
        String q = filter.toLowerCase(Locale.ROOT);
        int count = 0;
        for (AppRepository.Entry e : apps) {
            if (!e.label.toLowerCase(Locale.ROOT).contains(q)) continue;
            if (++count > 80) break;
            LinearLayout row = Ui.horizontal(this);
            row.setPadding(Ui.dp(this, 10), Ui.dp(this, 7), Ui.dp(this, 10), Ui.dp(this, 7));
            ImageView icon = new ImageView(this); icon.setImageDrawable(e.icon);
            row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
            Ui.hgap(row, this, 12);
            TextView name = Ui.text(this, e.label, 14, Ui.TEXT, true);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            TextView mark = Ui.text(this, Prefs.pins(this).contains(e.pkg) ? "✓ Pinned" : "+ Pin",
                    12, Ui.ACCENT, true);
            row.addView(mark);
            row.setOnClickListener(v -> {
                Prefs.togglePin(this, e.pkg);
                mark.setText(Prefs.pins(this).contains(e.pkg) ? "✓ Pinned" : "+ Pin");
                if (OverlayService.isRunning && Settings.canDrawOverlays(this)) {
                    Intent refresh = new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_REFRESH);
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(refresh);
                    else startService(refresh);
                }
            });
            list.addView(row);
            Ui.gap(list, this, 3);
        }
        if (count == 0) list.addView(Ui.text(this, "No matching applications", 14, Ui.MUTED, false));
    }
    private String abbreviate(String s, int max) { return s.length() > max ? s.substring(0,max-1) + "…" : s; }
}
