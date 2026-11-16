package com.orbitbar.app;

import android.app.ActivityOptions;
import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Color;
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
    static final String ACTION_SHOW = "com.orbitbar.app.SHOW";
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
        if (intent != null && ACTION_SHOW.equals(intent.getAction())) {
            expanded = true;
            manuallyHidden = false;
            phoneControls = false;
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
        PendingIntent show = PendingIntent.getService(this, 2,
                new Intent(this, OverlayService.class).setAction(ACTION_SHOW),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification note = new Notification.Builder(this, CHANNEL)
                .setContentTitle("OrbitBar is running")
                .setContentText(manuallyHidden && Prefs.hideStyle(this).equals("off")
                        ? "Taskbar hidden — tap Show to restore"
                        : "Taskbar active — tap Show to expand")
                .setSmallIcon(R.drawable.ic_orbitbar)
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_view, "Show", show).build())
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
        if (manuallyHidden && "off".equals(hideStyle)) return;
        boolean edge = mode.equals("edge");
        boolean bubble = mode.equals("bubble");
        boolean freeBubble = !expanded && !manuallyHidden && bubble;
        boolean freeMini = !expanded && manuallyHidden && !hideStyle.equals("side");
        boolean freePosition = freeBubble || freeMini;
        boolean sideHandle = !expanded && !freeBubble &&
                (manuallyHidden ? hideStyle.equals("side") : edge);
        boolean sideAnchor = expanded ? (edge || bubble) : sideHandle;
        int width = expanded ? (sideAnchor ? Math.min(screenWidth() - Ui.dp(this, 20), Ui.dp(this, 326))
                        : Math.min(screenWidth() - Ui.dp(this, 20), Ui.dp(this, 510)))
                : (manuallyHidden || edge || bubble) ? WindowManager.LayoutParams.WRAP_CONTENT
                : mode.equals("taskbar") ? WindowManager.LayoutParams.MATCH_PARENT
                : Math.min(screenWidth() - Ui.dp(this, 20), Ui.dp(this, 368));
        int gravity = freePosition ? (Gravity.TOP | Gravity.START)
                : sideAnchor ? (Gravity.END | Gravity.CENTER_VERTICAL)
                : (Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        int flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        if (!expanded) flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        windowParams = new WindowManager.LayoutParams(width,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags, PixelFormat.TRANSLUCENT);
        windowParams.gravity = gravity;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int edgeLimit = Math.max(0, screenH / 2 - Ui.dp(this, 68));
        if (freeBubble) {
            int diameter = Ui.dp(this, Prefs.bubbleSize(this) + 10);
            int defaultX = screenWidth() - diameter - Ui.dp(this, 8);
            windowParams.x = clamp(Prefs.bubbleX(this) < 0 ? defaultX : Prefs.bubbleX(this),
                    0, Math.max(0, screenWidth() - diameter));
            windowParams.y = clamp(Prefs.bubbleY(this) < 0 ? screenH / 3 : Prefs.bubbleY(this),
                    Ui.dp(this, 24), Math.max(Ui.dp(this, 24), screenH - diameter - Ui.dp(this, 35)));
        } else if (freeMini) {
            int miniW = Ui.dp(this, (hideStyle.equals("dot") ? 44 : 105) + 16);
            int miniH = Ui.dp(this, 58);
            windowParams.x = clamp(Prefs.miniX(this) < 0 ? (screenWidth() - miniW) / 2 : Prefs.miniX(this),
                    0, Math.max(0, screenWidth() - miniW));
            windowParams.y = clamp(Prefs.miniY(this) < 0 ? screenH - miniH - Ui.dp(this, 42) : Prefs.miniY(this),
                    Ui.dp(this, 24), Math.max(Ui.dp(this, 24), screenH - miniH - Ui.dp(this, 35)));
        } else {
            windowParams.y = sideAnchor
                    ? clamp(Prefs.edgeOffset(this), -edgeLimit, edgeLimit) : Ui.dp(this, 24);
            windowParams.x = sideAnchor ? Ui.dp(this, 4) : 0;
        }
        windowParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        windowParams.setTitle("OrbitBar taskbar");

        LinearLayout panel = Ui.vertical(this);
        int pad = freeBubble ? 5 : expanded ? 13 : 8;
        panel.setPadding(Ui.dp(this, pad), Ui.dp(this, freeBubble ? 5 : expanded ? 12 : 7),
                Ui.dp(this, pad), Ui.dp(this, freeBubble ? 5 : expanded ? 12 : 7));
        GradientDrawable bg = Ui.shape(
                Color.argb((int)(255f * Prefs.opacity(this) / 100f), 26, 32, 53),
                freeBubble ? 90 : expanded ? 23 : 19, this);
        bg.setStroke(Ui.dp(this, 1), 0xFF3F4C70);
        panel.setBackground(bg);
        panel.setElevation(Ui.dp(this, 12));
        if (expanded) buildExpanded(panel);
        else if (freeBubble) buildBubbleHandle(panel);
        else if (sideHandle) buildEdgeHandle(panel);
        else if (manuallyHidden) buildMiniHandle(panel, hideStyle.equals("dot"));
        else buildCollapsed(panel, mode.equals("taskbar"));
        overlay = panel;
        try {
            panel.setAlpha(0f);
            panel.setScaleX(.94f);
            panel.setScaleY(.94f);
            windowManager.addView(overlay, windowParams);
            panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start();
        } catch (Exception e) {
            overlay = null;
            Toast.makeText(this, "Unable to display taskbar", Toast.LENGTH_SHORT).show();
        }
    }
    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
    private void hideBar() {
        expanded = false;
        manuallyHidden = true;
        phoneControls = false;
        render();
        startForegroundNow();
    }
    private void collapseBar() {
        expanded = false;
        manuallyHidden = false;
        phoneControls = false;
        render();
        startForegroundNow();
    }
    private void showBar() {
        expanded = true;
        manuallyHidden = false;
        phoneControls = false;
        render();
        startForegroundNow();
    }
    private void attachFreeDrag(View handle, boolean bubble) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            private float rawX, rawY;
            private int originX, originY;
            private boolean moved;
            private long touchedAt;
            @Override public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        rawX = event.getRawX(); rawY = event.getRawY();
                        touchedAt = event.getEventTime();
                        originX = windowParams == null ? 0 : windowParams.x;
                        originY = windowParams == null ? 0 : windowParams.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - rawX;
                        float dy = event.getRawY() - rawY;
                        if (Math.abs(dx) > Ui.dp(OverlayService.this, 7) ||
                                Math.abs(dy) > Ui.dp(OverlayService.this, 7)) moved = true;
                        if (moved && windowParams != null && overlay != null) {
                            int screenH = getResources().getDisplayMetrics().heightPixels;
                            int sizeW = Ui.dp(OverlayService.this,
                                    bubble ? Prefs.bubbleSize(OverlayService.this) + 10 : 121);
                            int sizeH = Ui.dp(OverlayService.this, bubble ? Prefs.bubbleSize(OverlayService.this) + 10 : 58);
                            windowParams.x = clamp(originX + Math.round(dx),
                                    0, Math.max(0, screenWidth() - sizeW));
                            windowParams.y = clamp(originY + Math.round(dy), Ui.dp(OverlayService.this, 24),
                                    Math.max(Ui.dp(OverlayService.this, 24), screenH - sizeH - Ui.dp(OverlayService.this, 35)));
                            try { windowManager.updateViewLayout(overlay, windowParams); }
                            catch (IllegalArgumentException ignored) { }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (moved && windowParams != null) {
                            if (bubble) {
                                int diameter = Ui.dp(OverlayService.this, Prefs.bubbleSize(OverlayService.this) + 10);
                                int edgeX = windowParams.x < screenWidth() / 2
                                        ? Ui.dp(OverlayService.this, 6)
                                        : Math.max(0, screenWidth() - diameter - Ui.dp(OverlayService.this, 6));
                                final int y = windowParams.y;
                                ValueAnimator snap = ValueAnimator.ofInt(windowParams.x, edgeX);
                                snap.setDuration(170);
                                snap.addUpdateListener(a -> {
                                    if (windowParams != null && overlay != null) {
                                        windowParams.x = (Integer)a.getAnimatedValue();
                                        try { windowManager.updateViewLayout(overlay, windowParams); }
                                        catch (IllegalArgumentException ignored) { }
                                    }
                                });
                                snap.addListener(new android.animation.AnimatorListenerAdapter() {
                                    @Override public void onAnimationEnd(android.animation.Animator animation) {
                                        Prefs.setBubblePosition(OverlayService.this, edgeX, y);
                                    }
                                });
                                snap.start();
                            } else Prefs.setMiniPosition(OverlayService.this, windowParams.x, windowParams.y);
                        } else if (!bubble && event.getEventTime() - touchedAt >=
                                android.view.ViewConfiguration.getLongPressTimeout()) {
                            Prefs.setHideStyle(OverlayService.this, "off");
                            hideBar();
                            Toast.makeText(OverlayService.this,
                                    "OrbitBar hidden. Restore from the notification or app.",
                                    Toast.LENGTH_LONG).show();
                        } else view.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        if (moved && windowParams != null) {
                            if (bubble) Prefs.setBubblePosition(OverlayService.this, windowParams.x, windowParams.y);
                            else Prefs.setMiniPosition(OverlayService.this, windowParams.x, windowParams.y);
                        }
                        return true;
                    default: return false;
                }
            }
        });
    }
    private void buildBubbleHandle(LinearLayout panel) {
        TextView handle = Ui.text(this, "◈", 30, Ui.ACCENT, true);
        handle.setGravity(Gravity.CENTER);
        handle.setContentDescription("Move OrbitBar bubble by dragging; tap to open launcher");
        int size = Ui.dp(this, Prefs.bubbleSize(this));
        GradientDrawable circle = Ui.shape(0xFF354266, 90, this);
        circle.setStroke(Ui.dp(this, 1), Ui.ACCENT);
        handle.setBackground(circle);
        handle.setOnClickListener(v -> showBar());
        attachFreeDrag(handle, true);
        panel.addView(handle, new LinearLayout.LayoutParams(size, size));
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
        handle.setContentDescription("Drag to move; tap to expand; long press to hide completely");
        handle.setOnClickListener(v -> showBar());
        attachFreeDrag(handle, false);
        panel.addView(handle, new LinearLayout.LayoutParams(
                Ui.dp(this, dot ? 44 : 105), Ui.dp(this, 43)));
    }
    private void buildEdgeHandle(LinearLayout panel) {
        TextView icon = Ui.text(this, "◈", 26, Ui.ACCENT, true);
        icon.setGravity(Gravity.CENTER);
        icon.setContentDescription("Tap to open OrbitBar; drag vertically to move it");
        icon.setOnClickListener(v -> showBar());
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
        row.addView(button("Hide", this::hideBar), new LinearLayout.LayoutParams(Ui.dp(this, 50), Ui.dp(this, 43)));
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
        header.addView(button("Hide", this::hideBar));
        Ui.hgap(header, this, 5);
        header.addView(button("▾", this::collapseBar));
        panel.addView(header);
        Ui.gap(panel, this, 8);
        if (phoneControls) {
            buildPhoneControls(panel);
            return;
        }
        LinearLayout modes = Ui.horizontal(this);
        for (String m : new String[]{"dock", "taskbar", "edge", "bubble"}) {
            String label = m.equals("dock") ? "Dock" : m.equals("taskbar") ? "Taskbar" : m.equals("edge") ? "Edge" : "Bubble";
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
            String label = m.equals("normal") ? "Open" : m.equals("split") ? "Split" : "Pixel Float";
            TextView b = button(label, () -> { Prefs.setLaunchMode(this, m); render(); });
            if (Prefs.launchMode(this).equals(m)) b.setBackground(Ui.shape(0xFF4B5C9D, 12, this));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 35), 1);
            lp.setMargins(0,0,Ui.dp(this,5),0);
            launchModes.addView(b, lp);
        }
        panel.addView(launchModes);
        Ui.gap(panel, this, 6);
        TextView orbitMode = button("◉  Bubble in Orbit companion app", () -> {
            Prefs.setLaunchMode(this, "orbit");
            render();
        });
        if ("orbit".equals(Prefs.launchMode(this))) {
            orbitMode.setBackground(Ui.shape(0xFF4B5C9D, 12, this));
        }
        panel.addView(orbitMode, new LinearLayout.LayoutParams(-1, Ui.dp(this, 39)));
        Ui.gap(panel, this, 7);
        if (Prefs.pairReady(this)) {
            panel.addView(button("◧  Launch two-app workspace", this::launchWorkspacePair),
                    new LinearLayout.LayoutParams(-1, Ui.dp(this, 39)));
            Ui.gap(panel, this, 7);
        }
        if ("float".equals(Prefs.launchMode(this))) {
            buildFreeformPositionControls(panel);
            Ui.gap(panel, this, 6);
        }
        panel.addView(Ui.text(this,
                "Pixel Float uses your saved app positions. Tap Save beside an app to remember " +
                "the current size and position. Two-app layouts are configured in settings.",
                11, Ui.MUTED, false));
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
    private void buildFreeformPositionControls(LinearLayout panel) {
        LinearLayout labelRow = Ui.horizontal(this);
        labelRow.addView(Ui.text(this, "FREEFORM START POSITION", 10, Ui.ACCENT, true),
                new LinearLayout.LayoutParams(0, Ui.dp(this, 34), 1));
        labelRow.addView(button("Size: " + Prefs.windowSizeMode(this), this::openSettings));
        panel.addView(labelRow);
        LinearLayout choices = Ui.horizontal(this);
        final String[] labels = {"↖", "↗", "⊙", "↙", "↘"};
        final int[] xValues = {0, 100, 50, 0, 100};
        final int[] yValues = {0, 0, 50, 100, 100};
        for (int i = 0; i < labels.length; i++) {
            final int x = xValues[i];
            final int y = yValues[i];
            TextView choice = button(labels[i], () -> {
                Prefs.setFreeformPosition(this, x, y);
                render();
            });
            choice.setContentDescription("Start window at " +
                    (i == 0 ? "top left" : i == 1 ? "top right" : i == 2
                            ? "center" : i == 3 ? "bottom left" : "bottom right"));
            if (Prefs.freeformX(this) == x && Prefs.freeformY(this) == y) {
                choice.setBackground(Ui.shape(0xFF4B5C9D, 12, this));
            }
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Ui.dp(this, 35), 1);
            p.setMargins(0, 0, Ui.dp(this, 4), 0);
            choices.addView(choice, p);
        }
        panel.addView(choices);
        Ui.gap(panel, this, 6);
        LinearLayout sizes = Ui.horizontal(this);
        for (String size : new String[]{"small","medium","large","custom"}) {
            final String choice = size;
            TextView b = button(size.substring(0,1).toUpperCase(Locale.ROOT)
                    + size.substring(1), () -> {
                Prefs.setWindowSizeMode(this,choice);
                if ("custom".equals(choice)) openSettings();
                else render();
            });
            if (Prefs.windowSizeMode(this).equals(choice))
                b.setBackground(Ui.shape(0xFF4B5C9D, 12, this));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 34), 1);
            lp.setMargins(0, 0, Ui.dp(this, 4), 0);
            sizes.addView(b, lp);
        }
        panel.addView(sizes);
    }
    private void launchWorkspacePair() {
        if (!Prefs.pairReady(this)) {
            Toast.makeText(this, "Choose two apps in OrbitBar settings", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(this, PixelFreeformActivity.class);
        i.putExtra(PixelFreeformActivity.EXTRA_PACKAGE, Prefs.pairFirst(this));
        i.putExtra(PixelFreeformActivity.EXTRA_SECOND, Prefs.pairSecond(this));
        i.putExtra(PixelFreeformActivity.EXTRA_LAYOUT, Prefs.pairLayout(this));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
            expanded = false;
            handler.postDelayed(this::render, 160);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to launch workspace", Toast.LENGTH_SHORT).show();
        }
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
        if (Prefs.pins(this).contains(e.pkg))
            row.addView(Ui.text(this, "★", 15, Ui.ACCENT, true));
        if ("float".equals(Prefs.launchMode(this))) {
            boolean saved = Prefs.hasPreset(this, e.pkg);
            TextView save = button(saved ? "Reset" : "Save", () -> {
                if (Prefs.hasPreset(this,e.pkg)) Prefs.clearPreset(this,e.pkg);
                else Prefs.savePreset(this,e.pkg);
                Toast.makeText(this, e.label + (saved ? " preset removed" : " preset saved"),
                        Toast.LENGTH_SHORT).show();
                render();
            });
            save.setContentDescription(saved ? "Remove window preset for " + e.label
                    : "Save current window preset for " + e.label);
            row.addView(save, new LinearLayout.LayoutParams(Ui.dp(this, 57), Ui.dp(this, 33)));
        }
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
                // Launch through a real Activity, as in Orbit-AI-Launcher.
                Intent request = new Intent(this, PixelFreeformActivity.class);
                request.putExtra(PixelFreeformActivity.EXTRA_PACKAGE, e.pkg);
                request.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(request);
            } else if ("orbit".equals(mode)) {
                String error = OrbitBubbleBridge.launch(this, e.pkg);
                if (error != null) {
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                    return;
                }
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
