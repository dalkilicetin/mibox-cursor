package com.aircursor;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

public class AirCursorAccessibility extends AccessibilityService {

    private static final String TAG = "A11Y";
    private static AirCursorAccessibility instance;

    private CursorView cursorView;
    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private Handler mainHandler;

    private float cursorX = 960;
    private float cursorY = 540;
    private int screenW = 1920;
    private int screenH = 1080;

    private final List<NodeInfo> nodeCache = new ArrayList<>();
    private int currentFocusX = -1;
    private int currentFocusY = -1;

    // 🔥 PLAN STATE
    private final List<Integer> keyPlan = new ArrayList<>();
    private int planIndex = 0;

    static class NodeInfo {
        String label;
        int cx, cy;
        boolean clickable;
        NodeInfo(String label, int cx, int cy, boolean clickable) {
            this.label = label;
            this.cx = cx;
            this.cy = cy;
            this.clickable = clickable;
        }
    }

    public static AirCursorAccessibility getInstance() { return instance; }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        mainHandler = new Handler(Looper.getMainLooper());
        setupOverlay();
        mainHandler.postDelayed(this::refreshNodeCache, 1000);
    }

    private void setupOverlay() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;

        cursorView = new CursorView(this);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
        );

        mainHandler.post(() -> wm.addView(cursorView, params));
    }

    private void refreshNodeCache() {
        new Thread(() -> {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;

            List<NodeInfo> nodes = new ArrayList<>();
            traverseNodes(root, nodes);
            root.recycle();

            synchronized (nodeCache) {
                nodeCache.clear();
                nodeCache.addAll(nodes);
            }
        }).start();
    }

    private void traverseNodes(AccessibilityNodeInfo node, List<NodeInfo> out) {
        if (node == null) return;

        if (node.isClickable() || node.isFocusable()) {
            Rect r = new Rect();
            node.getBoundsInScreen(r);

            if (r.width() > 0 && r.height() > 0) {
                out.add(new NodeInfo("", r.centerX(), r.centerY(), node.isClickable()));
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            traverseNodes(child, out);
            if (child != null) child.recycle();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

        int type = event.getEventType();

        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            mainHandler.postDelayed(this::refreshNodeCache, 200);
        }

        if (type == AccessibilityEvent.TYPE_VIEW_FOCUSED) {

            AccessibilityNodeInfo node = event.getSource();
            if (node != null) {

                Rect r = new Rect();
                node.getBoundsInScreen(r);

                currentFocusX = r.centerX();
                currentFocusY = r.centerY();

                node.recycle();

                if (navigating) {

                    // 🔥 PLAN YÜRÜT
                    if (planIndex < keyPlan.size()) {
                        injectKey(keyPlan.get(planIndex++));
                    } else {
                        navigating = false;
                        injectKey(23);
                    }
                }
            }
        }
    }

    @Override
    public void onInterrupt() {}

    public NodeInfo findNearest(int cx, int cy) {
        NodeInfo best = null;
        double minDist = Double.MAX_VALUE;

        synchronized (nodeCache) {
            for (NodeInfo n : nodeCache) {
                double d = Math.hypot(n.cx - cx, n.cy - cy);
                if (d < minDist) {
                    minDist = d;
                    best = n;
                }
            }
        }
        return best;
    }

    private volatile boolean navigating = false;
    private volatile int targetX = -1;
    private volatile int targetY = -1;

    public void tap() {

        navigating = false;

        int cx = (int) cursorX;
        int cy = (int) cursorY;

        NodeInfo target = findNearest(cx, cy);

        if (target == null) {
            injectKey(23);
            return;
        }

        targetX = target.cx;
        targetY = target.cy;

        // 🔥 PLAN
        keyPlan.clear();
        planIndex = 0;
        buildPlan();

        if (keyPlan.isEmpty()) {
            injectKey(23);
            return;
        }

        navigating = true;
        injectKey(keyPlan.get(planIndex++));

        mainHandler.postDelayed(() -> navigating = false, 2000);
    }

    // 🔥 PLAN BUILDER
    private void buildPlan() {

        int fx = currentFocusX;
        int fy = currentFocusY;

        int dx = targetX - fx;
        int dy = targetY - fy;

        int step = 120;

        if (Math.abs(dy) > 120) {
            int count = Math.abs(dy) / step;
            int key = dy > 0 ? 20 : 19;
            for (int i = 0; i < count; i++) keyPlan.add(key);
        }

        if (Math.abs(dx) > 80) {
            int count = Math.abs(dx) / step;
            int key = dx > 0 ? 22 : 21;
            for (int i = 0; i < count; i++) keyPlan.add(key);
        }

        Log.e(TAG, "Plan size: " + keyPlan.size());
    }

    public void injectKey(int keyCode) {
        new Thread(() -> {
            try {
                Runtime.getRuntime().exec(new String[]{"input", "keyevent", String.valueOf(keyCode)});
            } catch (Exception e) {
                Log.e(TAG, "Key error");
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        instance = null;
        if (cursorView != null && wm != null) {
            try {
                mainHandler.post(() -> wm.removeView(cursorView));
            } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}