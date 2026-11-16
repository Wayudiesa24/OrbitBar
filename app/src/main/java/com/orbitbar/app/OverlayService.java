package com.orbitbar.app;

import android.app.ActivityOptions;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Foreground, user-initiated taskbar overlay. No accessibility permission is used. */
public class OverlayService extends Service {
    static final String ACTION_REFRESH = "com.orbitbar.app.REFRESH";
    private static final String ACTION_STOP = "com.orbitbar.app.STOP";
    private static final String CHANNEL = "orbitbar_taskbar";
    static volatile boolean isRunning = false;
    private WindowManager windowManager;
    private WindowManager.LayoutParams windowParams;
    private View overlay;
    private boolean expanded = false;
    private boolean manuallyHidden = false;
    private boolean phoneControls = false;
    private List<AppRepository.Entry> apps = new ArrayList<>();
    private TextView mediaText, playButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateMedia();
            handler.postDelayed(this, 4000);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        isRunning = true;
        windowManager = (WindowManager)getSystemService(WINDOW_SERVICE);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf(); return START_NOT_STICKY;
        }
        startForegroundNow();
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "OrbitBar needs overlay permission", Toast.LENGTH_LONG).show();
            stopSelf(); return START_NOT_STICKY;
        }
        apps = AppRepository.installed(this);
        Prefs.ensureInitialPins(this, apps);
        render();
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, 1000);
        return START_STICKY;
    }
    private void startForegroundNow() {
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                    "OrbitBar running", NotificationManager.IMPORTANCE_LOW));
        }
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, OverlayService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification note = new Notification.Builder(this, CHANNEL)
                .setContentTitle("OrbitBar is running")
                .setContentText("Tap for settings · taskbar is active")
                .setSmallIcon(R.drawable.ic_orbitbar)
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop).build())
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(101, note,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else startForeground(101, note);
    }
    @Override public void onDestroy() {
        handler.removeCallbacks(tick);
        removeOverlay();
        isRunning = false;
        super.onDestroy();
    }
    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        render();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    private void removeOverlay() {
        if (overlay != null) {
            try { windowManager.removeViewImmediate(overlay); }
            catch (Exception ignored) { }
            overlay = null;
        }
    }
    private void render() {
        removeOverlay();
        if (!Settings.canDrawOverlays(this)) return;
        String mode = Prefs.mode(this);
        String hideStyle = Prefs.hideStyle(this);
        boolean edge = mode.equals("edge");
        boolean sideHandle = !expanded && (manuallyHidden ? hideStyle.equals("side") : edge);
        boolean sideAnchor = expanded ? edge : sideHandle;
        int width = expanded ? (sideAnchor ? Math.min(screenWidth() - Ui.dp(this, 20), Ui.dp(this, 326))
                        : Math.min(screenWidth() - Ui.dp(this, 20), Ui.dp(this, 510)))
                : (manuallyHidden || edge) ? WindowManager.LayoutParams.WRAP_CONTENT
                : mode.equals("taskbar") ? WindowManager.LayoutParams.MATCH_PARENT
                : Math.min(screenWidth() - Ui.dp(this, 20), Ui.dp(this, 368));
        int gravity = sideAnchor ? (Gravity.END | Gravity.CENTER_VERTICAL)
                : (Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        int flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        if (!expanded) flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        windowParams = new WindowManager.LayoutParams(width,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags, PixelFormat.TRANSLUCENT);
        windowParams.gravity = gravity;
        int edgeLimit = Math.max(0, getResources().getDisplayMetrics().heightPixels / 2 - Ui.dp(this, 68));
        windowParams.y = sideAnchor ? Math.max(-edgeLimit, Math.min(edgeLimit, Prefs.edgeOffset(this))) : Ui.dp(this, 24);
        windowParams.x = sideAnchor ? Ui.dp(this, 4) : 0;
        windowParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        windowParams.setTitle("OrbitBar taskbar");

        LinearLayout panel = Ui.vertical(this);
        panel.setPadding(Ui.dp(this, expanded ? 13 : 8), Ui.dp(this, expanded ? 12 : 7),
                Ui.dp(this, expanded ? 13 : 8), Ui.dp(this, expanded ? 12 : 7));
        GradientDrawable bg = Ui.shape(0xF21A2035, expanded ? 23 : 19, this);
        bg.setStroke(Ui.dp(this, 1), 0xFF3F4C70);
        panel.setBackground(bg);
        panel.setElevation(Ui.dp(this, 12));
        if (expanded) buildExpanded(panel);
        else if (sideHandle) buildEdgeHandle(panel);
        else if (manuallyHidden) buildMiniHandle(panel, hideStyle.equals("dot"));
        else buildCollapsed(panel, mode.equals("taskbar"));
        overlay = panel;
        try { windowManager.addView(overlay, windowParams); }
        catch (Exception e) {
            overlay = null;
            Toast.makeText(this, "Unable to display taskbar", Toast.LENGTH_SHORT).show();
        }
    }
    private int screenWidth() { return getResources().getDisplayMetrics().widthPixels; }
    private TextView button(String label, Runnable action) {
        TextView v = Ui.pill(this, label, action);
        v.setTextSize(12);
        v.setPadding(Ui.dp(this, 11), Ui.dp(this, 9), Ui.dp(this, 11), Ui.dp(this, 9));
        return v;
    }
    private void buildMiniHandle(LinearLayout panel, boolean dot) {
        TextView handle = Ui.text(this, dot ? "◈" : "◈   OrbitBar", dot ? 23 : 13, Ui.ACCENT, true);
        handle.setGravity(Gravity.CENTER);
        handle.setContentDescription("Open OrbitBar");
        handle.setOnClickListener(v -> {
            expanded = true;
            manuallyHidden = false;
            phoneControls = false;
            render();
        });
        panel.addView(handle, new LinearLayout.LayoutParams(
                Ui.dp(this, dot ? 44 : 105), Ui.dp(this, 43)));
    }
    private void buildEdgeHandle(LinearLayout panel) {
        TextView icon = Ui.text(this, "◈", 26, Ui.ACCENT, true);
        icon.setGravity(Gravity.CENTER);
        icon.setContentDescription("Tap to open OrbitBar; drag vertically to move it");
        icon.setOnClickListener(v -> {
            expanded = true;
            manuallyHidden = false;
            phoneControls = false;
            render();
        });
        icon.setOnTouchListener(new View.OnTouchListener() {
            private float startRawY;
            private int startOffset;
            private boolean dragged;
            @Override public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startRawY = event.getRawY();
                        startOffset = windowParams == null ? 0 : windowParams.y;
                        dragged = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float movement = event.getRawY() - startRawY;
                        if (Math.abs(movement) > Ui.dp(OverlayService.this, 8)) dragged = true;
                        if (dragged && overlay != null && windowParams != null) {
                            int limit = Math.max(0,
                                    getResources().getDisplayMetrics().heightPixels / 2 -
                                    Ui.dp(OverlayService.this, 68));
                            windowParams.y = Math.max(-limit, Math.min(limit, startOffset + Math.round(movement)));
                            try { windowManager.updateViewLayout(overlay, windowParams); }
                            catch (IllegalArgumentException ignored) { }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (dragged && windowParams != null) Prefs.setEdgeOffset(OverlayService.this, windowParams.y);
                        else view.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        if (dragged && windowParams != null) Prefs.setEdgeOffset(OverlayService.this, windowParams.y);
                        return true;
                    default:
                        return false;
                }
            }
        });
        panel.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 43), Ui.dp(this, 55)));
    }
    private void buildCollapsed(LinearLayout panel, boolean taskbar) {
        LinearLayout row = Ui.horizontal(this);
        if (taskbar) {
            TextView symbol = Ui.text(this, "◈", 25, Ui.ACCENT, true);
            symbol.setPadding(Ui.dp(this, 9), 0, Ui.dp(this, 10), 0);
            symbol.setOnClickListener(v -> { expanded = true; render(); });
            row.addView(symbol);
        }
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout iconRow = Ui.horizontal(this);
        List<AppRepository.Entry> pins = AppRepository.select(apps, Prefs.pins(this), taskbar ? 5 : 5);
        List<AppRepository.Entry> recents = AppRepository.recent(this, apps, 3);
        Set<String> shown = new HashSet<>();
        for (AppRepository.Entry e : pins) { addIcon(iconRow, e, false); shown.add(e.pkg); }
        if (taskbar) for (AppRepository.Entry e : recents) {
            if (!shown.contains(e.pkg)) { addIcon(iconRow, e, false); shown.add(e.pkg); }
        }
        scroll.addView(iconRow);
        row.addView(scroll, new LinearLayout.LayoutParams(0, Ui.dp(this, 51), 1));
        row.addView(button("⌕", () -> { expanded = true; render(); }),
                new LinearLayout.LayoutParams(Ui.dp(this, 45), Ui.dp(this, 43)));
        Ui.hgap(row, this, 4);
        row.addView(button("☰", () -> { expanded = true; render(); }),
                new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 43)));
        Ui.hgap(row, this, 4);
        row.addView(button("Hide", () -> {
            expanded = false;
            manuallyHidden = true;
            phoneControls = false;
            render();
        }), new LinearLayout.LayoutParams(Ui.dp(this, 50), Ui.dp(this, 43)));
        panel.addView(row);
    }
    private void addIcon(LinearLayout row, AppRepository.Entry e, boolean compact) {
        ImageView image = new ImageView(this);
        image.setImageDrawable(e.icon);
        int dp = compact ? 35 : 41;
        image.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Ui.dp(this, dp), Ui.dp(this, dp));
        p.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
        row.addView(image, p);
        image.setContentDescription(e.label);
        image.setOnClickListener(v -> launch(e));
        image.setOnLongClickListener(v -> {
            Prefs.togglePin(this, e.pkg);
            Toast.makeText(this, e.label + (Prefs.pins(this).contains(e.pkg) ? " pinned" : " unpinned"),
                    Toast.LENGTH_SHORT).show();
            render(); return true;
        });
    }
    private void buildExpanded(LinearLayout panel) {
        LinearLayout header = Ui.horizontal(this);
        TextView logo = Ui.text(this, "◈  OrbitBar", 17, Ui.TEXT, true);
        header.addView(logo, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1));
        header.addView(button("⚙", this::openSettings));
        Ui.hgap(header, this, 5);
        header.addView(button("Hide", () -> {
            expanded = false;
            manuallyHidden = true;
            phoneControls = false;
            render();
        }));
        Ui.hgap(header, this, 5);
        header.addView(button("▾", () -> {
            expanded = false;
            manuallyHidden = false;
            phoneControls = false;
            render();
        }));
        panel.addView(header);
        Ui.gap(panel, this, 8);
        if (phoneControls) {
            buildPhoneControls(panel);
            return;
        }
        LinearLayout modes = Ui.horizontal(this);
        for (String m : new String[]{"dock", "taskbar", "edge"}) {
            String label = m.equals("dock") ? "Dock" : m.equals("taskbar") ? "Taskbar" : "Edge";
            TextView v = button(label, () -> { Prefs.setMode(this, m); render(); });
            if (Prefs.mode(this).equals(m)) v.setBackground(Ui.shape(0xFF4B5C9D, 12, this));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 38), 1);
            lp.setMargins(0,0,Ui.dp(this,5),0);
            modes.addView(v, lp);
        }
        panel.addView(modes);
        Ui.gap(panel, this, 12);

        LinearLayout launchModes = Ui.horizontal(this);
        for (String m : new String[]{"normal", "split", "float"}) {
            String label = m.equals("normal") ? "Open" : m.equals("split") ? "Split" : "Float";
            TextView b = button(label, () -> { Prefs.setLaunchMode(this, m); render(); });
            if (Prefs.launchMode(this).equals(m)) b.setBackground(Ui.shape(0xFF4B5C9D, 12, this));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 35), 1);
            lp.setMargins(0,0,Ui.dp(this,5),0);
            launchModes.addView(b, lp);
        }
        panel.addView(launchModes);
        Ui.gap(panel, this, 7);
        panel.addView(Ui.text(this, "Choose how the next app opens (device-dependent).", 11, Ui.MUTED, false));
        Ui.gap(panel, this, 10);

        EditText search = new EditText(this);
        search.setTextColor(Ui.TEXT);
        search.setHintTextColor(Ui.MUTED);
        search.setTextSize(14);
        search.setSingleLine(true);
        search.setHint("⌕  Find an app...");
        search.setPadding(Ui.dp(this, 13), Ui.dp(this, 8), Ui.dp(this, 13), Ui.dp(this, 8));
        search.setBackground(Ui.shape(Ui.SURFACE2, 13, this));
        panel.addView(search, new LinearLayout.LayoutParams(-1, Ui.dp(this, 43)));
        Ui.gap(panel, this, 9);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout results = Ui.vertical(this);
        scroll.addView(results);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, Ui.dp(this, 205)));
        populateResults(results, "");
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                populateResults(results, s.toString());
                scroll.scrollTo(0,0);
            }
            @Override public void afterTextChanged(Editable e) { }
        });
        Ui.gap(panel, this, 9);
        addMedia(panel);
        Ui.gap(panel, this, 8);
        addControls(panel);
    }
    private void populateResults(LinearLayout results, String query) {
        results.removeAllViews();
        List<AppRepository.Entry> found = new ArrayList<>();
        if (query.trim().isEmpty()) {
            results.addView(Ui.text(this, "FAVOURITES", 11, Ui.ACCENT, true));
            Ui.gap(results, this, 6);
            found.addAll(AppRepository.select(apps, Prefs.pins(this), 9));
            List<AppRepository.Entry> recent = AppRepository.recent(this, apps, 8);
            for (AppRepository.Entry e : recent) {
                boolean duplicate = false;
                for (AppRepository.Entry f : found) if (f.pkg.equals(e.pkg)) { duplicate = true; break; }
                if (!duplicate) found.add(e);
            }
        } else {
            String lower = query.toLowerCase(Locale.ROOT).trim();
            for (AppRepository.Entry e : apps) {
                if (e.label.toLowerCase(Locale.ROOT).contains(lower)) found.add(e);
                if (found.size() >= 30) break;
            }
        }
        for (AppRepository.Entry e : found) addResultRow(results, e);
        if (found.isEmpty()) {
            results.addView(Ui.text(this, query.isEmpty() ? "No apps found" : "No matching apps",
                    13, Ui.MUTED, false));
        }
    }
    private void addResultRow(LinearLayout results, AppRepository.Entry e) {
        LinearLayout row = Ui.horizontal(this);
        row.setPadding(Ui.dp(this, 9), Ui.dp(this, 5), Ui.dp(this, 8), Ui.dp(this, 5));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(e.icon);
        row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30)));
        Ui.hgap(row, this, 10);
        TextView name = Ui.text(this, e.label, 13, Ui.TEXT, false);
        name.setMaxLines(1);
        row.addView(name, new LinearLayout.LayoutParams(0, Ui.dp(this, 36), 1));
        if (Prefs.pins(this).contains(e.pkg)) row.addView(Ui.text(this, "★", 15, Ui.ACCENT, true));
        row.setOnClickListener(v -> launch(e));
        row.setOnLongClickListener(v -> {
            Prefs.togglePin(this, e.pkg);
            Toast.makeText(this, e.label + " favourite changed", Toast.LENGTH_SHORT).show();
            render(); return true;
        });
        results.addView(row);
    }
    private void addMedia(LinearLayout panel) {
        LinearLayout media = Ui.horizontal(this);
        media.setPadding(Ui.dp(this, 7), Ui.dp(this, 6), Ui.dp(this, 7), Ui.dp(this, 6));
        media.setBackground(Ui.shape(Ui.SURFACE2, 13, this));
        mediaText = Ui.text(this, MediaHelper.label(MediaHelper.current(this)), 11, Ui.TEXT, false);
        mediaText.setSingleLine(true);
        mediaText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        media.addView(mediaText, new LinearLayout.LayoutParams(0, Ui.dp(this, 32), 1));
        media.addView(button("⏮", () -> MediaHelper.mediaAction(this, "prev")));
        playButton = button("⏯", () -> {
            if (MediaHelper.current(this) == null) openSystem(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            else { MediaHelper.mediaAction(this, "play"); handler.postDelayed(this::updateMedia, 350); }
        });
        media.addView(playButton);
        media.addView(button("⏭", () -> MediaHelper.mediaAction(this, "next")));
        panel.addView(media);
        updateMedia();
    }
    private void updateMedia() {
        if (mediaText == null || overlay == null) return;
        MediaController m = MediaHelper.current(this);
        mediaText.setText(MediaHelper.label(m));
        if (playButton != null) playButton.setText(m == null ? "♫" :
                (MediaHelper.isPlaying(m) ? "Ⅱ" : "▶"));
    }
    private void addControls(LinearLayout panel) {
        LinearLayout controls = Ui.horizontal(this);
        TextView internet = button("◉ Internet", () -> {
            if (Build.VERSION.SDK_INT >= 29) openSystem(Settings.Panel.ACTION_INTERNET_CONNECTIVITY);
            else openSystem(Settings.ACTION_WIFI_SETTINGS);
        });
        controls.addView(internet, new LinearLayout.LayoutParams(0, Ui.dp(this, 38), 1.5f));
        Ui.hgap(controls, this, 4);
        controls.addView(button("ᛒ", () -> openSystem(Settings.ACTION_BLUETOOTH_SETTINGS)),
                new LinearLayout.LayoutParams(0, Ui.dp(this, 38), .7f));
        Ui.hgap(controls, this, 4);
        controls.addView(button("☼", () -> openSystem(Settings.ACTION_DISPLAY_SETTINGS)),
                new LinearLayout.LayoutParams(0, Ui.dp(this, 38), .7f));
        Ui.hgap(controls, this, 4);
        controls.addView(button("♫+", () -> {
            AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
            if (am != null) am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
        }), new LinearLayout.LayoutParams(0, Ui.dp(this, 38), .7f));
        panel.addView(controls);
        Ui.gap(panel, this, 8);
        panel.addView(button("⚡  More phone controls   ›", () -> {
            phoneControls = true;
            render();
        }), new LinearLayout.LayoutParams(-1, Ui.dp(this, 38)));
    }
    private void addControlPair(LinearLayout panel, String leftLabel, Runnable leftAction,
                                String rightLabel, Runnable rightAction) {
        LinearLayout row = Ui.horizontal(this);
        row.addView(button(leftLabel, leftAction), new LinearLayout.LayoutParams(0, Ui.dp(this, 39), 1));
        Ui.hgap(row, this, 7);
        row.addView(button(rightLabel, rightAction), new LinearLayout.LayoutParams(0, Ui.dp(this, 39), 1));
        panel.addView(row);
        Ui.gap(panel, this, 7);
    }
    private void buildPhoneControls(LinearLayout panel) {
        panel.addView(button("‹ Back to apps", () -> {
            phoneControls = false;
            render();
        }), new LinearLayout.LayoutParams(-1, Ui.dp(this, 38)));
        Ui.gap(panel, this, 13);
        panel.addView(Ui.text(this, "PHONE CONTROLS", 12, Ui.ACCENT, true));
        Ui.gap(panel, this, 5);
        panel.addView(Ui.text(this,
                "Settings buttons open Android's own controls. Volume buttons act immediately.",
                11, Ui.MUTED, false));
        Ui.gap(panel, this, 12);

        addControlPair(panel, "◉ Internet", () -> {
            if (Build.VERSION.SDK_INT >= 29) openSystem(Settings.Panel.ACTION_INTERNET_CONNECTIVITY);
            else openSystem(Settings.ACTION_WIFI_SETTINGS);
        }, "ᛒ Bluetooth", () -> openSystem(Settings.ACTION_BLUETOOTH_SETTINGS));
        addControlPair(panel, "☼ Display", () -> openSystem(Settings.ACTION_DISPLAY_SETTINGS),
                "♫ Sound", () -> openSystem(Settings.ACTION_SOUND_SETTINGS));
        addControlPair(panel, "⟳ Rotation", () -> {
            if (Build.VERSION.SDK_INT >= 31) openSystemWithFallback(
                    Settings.ACTION_AUTO_ROTATE_SETTINGS, Settings.ACTION_DISPLAY_SETTINGS);
            else openSystem(Settings.ACTION_DISPLAY_SETTINGS);
        }, "☾ Do Not Disturb", () -> openSystemWithFallback(
                "android.settings.ZEN_MODE_SETTINGS", Settings.ACTION_SETTINGS));
        addControlPair(panel, "▣ Battery saver", () -> openSystemWithFallback(
                Settings.ACTION_BATTERY_SAVER_SETTINGS, Settings.ACTION_SETTINGS),
                "⌖ Location", () -> openSystem(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        addControlPair(panel, "◐ Night light", () -> openSystemWithFallback(
                Settings.ACTION_NIGHT_DISPLAY_SETTINGS, Settings.ACTION_DISPLAY_SETTINGS),
                "◈ NFC", () -> openSystemWithFallback(
                        Settings.ACTION_NFC_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS));

        LinearLayout volume = Ui.horizontal(this);
        volume.addView(button("Vol −", () -> changeMusicVolume(AudioManager.ADJUST_LOWER)),
                new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1));
        Ui.hgap(volume, this, 7);
        volume.addView(button("Mute", () -> changeMusicVolume(AudioManager.ADJUST_TOGGLE_MUTE)),
                new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1));
        Ui.hgap(volume, this, 7);
        volume.addView(button("Vol +", () -> changeMusicVolume(AudioManager.ADJUST_RAISE)),
                new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1));
        panel.addView(volume);
        Ui.gap(panel, this, 13);
        addMedia(panel);
    }
    private void changeMusicVolume(int direction) {
        try {
            AudioManager audio = (AudioManager)getSystemService(AUDIO_SERVICE);
            if (audio != null) audio.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
        } catch (SecurityException e) {
            Toast.makeText(this, "Volume control unavailable", Toast.LENGTH_SHORT).show();
        }
    }
    private void openSystemWithFallback(String primary, String fallback) {
        try {
            Intent i = new Intent(primary).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            expanded = false;
            phoneControls = false;
            render();
        } catch (Exception ignored) {
            openSystem(fallback);
        }
    }
    private void launch(AppRepository.Entry e) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(e.pkg);
        if (intent == null) {
            Toast.makeText(this, "Cannot open " + e.label, Toast.LENGTH_SHORT).show(); return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String mode = Prefs.launchMode(this);
        try {
            if ("split".equals(mode) && Build.VERSION.SDK_INT >= 24) {
                intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
                startActivity(intent);
            } else if ("float".equals(mode) && Build.VERSION.SDK_INT >= 26) {
                // Launch bounds only take effect in compatible desktop/freeform modes.
                ActivityOptions options = ActivityOptions.makeBasic();
                int width = screenWidth();
                int h = getResources().getDisplayMetrics().heightPixels;
                options.setLaunchBounds(new Rect(Ui.dp(this, 28), Ui.dp(this, 70),
                        Math.max(Ui.dp(this, 280), width - Ui.dp(this, 28)),
                        Math.max(Ui.dp(this, 400), (int)(h * .78f))));
                startActivity(intent, options.toBundle());
            } else startActivity(intent);
            Prefs.recordLaunch(this, e.pkg);
            expanded = false;
            handler.postDelayed(this::render, 160);
        } catch (Exception ex) {
            Toast.makeText(this, "App launch blocked by Android", Toast.LENGTH_SHORT).show();
        }
    }
    private void openSettings() {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i); expanded = false; phoneControls = false; render();
        } catch (Exception ignored) { }
    }
    private void openSystem(String action) {
        try {
            Intent i = new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            expanded = false;
            phoneControls = false;
            render();
        } catch (Exception ex) {
            Toast.makeText(this, "System setting unavailable", Toast.LENGTH_SHORT).show();
        }
    }
}
