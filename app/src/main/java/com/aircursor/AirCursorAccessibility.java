package com.aircursor;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

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

    public static AirCursorAccessibility getInstance() { return instance; }
    public float getCursorX() { return cursorX; }
    public float getCursorY() { return cursorY; }
    public int getScreenW() { return screenW; }
    public int getScreenH() { return screenH; }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        mainHandler = new Handler(Looper.getMainLooper());
        Log.i(TAG, "✅ Accessibility connected");
        setupOverlay();
    }

    private void setupOverlay() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        cursorX = screenW / 2f;
        cursorY = screenH / 2f;

        cursorView = new CursorView(this);

        // MATCH_PARENT: gesture koordinatları screen absolute olmalı
        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );

        params.x = 0;
        params.y = 0;

        mainHandler.post(() -> {
            wm.addView(cursorView, params);
            Log.i(TAG, "Overlay added: " + screenW + "x" + screenH);
        });
    }

    public void moveCursor(int dx, int dy) {
        cursorX = Math.max(0, Math.min(cursorX + dx, screenW - 1));
        cursorY = Math.max(0, Math.min(cursorY + dy, screenH - 1));
        final float nx = cursorX;
        final float ny = cursorY;
        mainHandler.post(() -> {
            if (cursorView != null) cursorView.updatePosition(nx, ny);
        });
    }

    public void tap() {
        final int x = (int) cursorX;
        final int y = (int) cursorY;
        Log.d(TAG, "tap → " + x + "," + y);
        mainHandler.post(() -> {
            if (cursorView != null) cursorView.showClick();
        });
        // sh -c input tap: ADB ile aynı yetki seviyesi
        new Thread(() -> {
            try {
                Process p = Runtime.getRuntime().exec(
                    new String[]{"sh", "-c", "input tap " + x + " " + y}
                );
                int exit = p.waitFor();
                Log.d(TAG, exit == 0 ? "✅ Tap ok (sh): " + x + "," + y
                                     : "⚠️ Tap sh exit=" + exit + ": " + x + "," + y);
            } catch (Exception e) {
                Log.e(TAG, "Tap error: " + e.getMessage());
                // fallback: dispatchGesture
                performTap(x, y);
            }
        }).start();
    }

    public void performTap(int x, int y) {
        mainHandler.post(() -> {
            float rx = Math.max(1, Math.min(x, screenW - 1));
            float ry = Math.max(1, Math.min(y, screenH - 1));

            Path path = new Path();
            path.moveTo(rx, ry);

            GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 100))  // 100ms
                .build();

            dispatchGesture(gesture, new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription g) {
                    Log.d(TAG, "✅ Tap ok: " + rx + "," + ry);
                }
                @Override public void onCancelled(GestureDescription g) {
                    Log.e(TAG, "❌ Tap cancelled: " + rx + "," + ry);
                }
            }, null);
        });
    }

    public void performSwipe(int x1, int y1, int x2, int y2, int durationMs) {
        mainHandler.post(() -> {
            Path path = new Path();
            path.moveTo(x1, y1);
            path.lineTo(x2, y2);
            GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, durationMs))
                .build();
            dispatchGesture(gesture, null, null);
        });
    }

    public void setCursorVisible(boolean visible) {
        mainHandler.post(() -> {
            if (cursorView != null)
                cursorView.setVisibility(visible ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
        });
    }

    public void setScrollMode(int mode) {
        mainHandler.post(() -> {
            if (cursorView != null) cursorView.setScrollMode(mode);
        });
    }

    public void injectText(String text) {
        new Thread(() -> {
            try {
                String escaped = text.replace(" ", "%s");
                Runtime.getRuntime().exec(new String[]{"input", "text", escaped});
            } catch (Exception e) {
                Log.w(TAG, "Text error: " + e.getMessage());
            }
        }).start();
    }

    public void injectKey(int keyCode) {
        new Thread(() -> {
            try {
                Runtime.getRuntime().exec(new String[]{"input", "keyevent", String.valueOf(keyCode)});
            } catch (Exception e) {
                Log.w(TAG, "Key error: " + e.getMessage());
            }
        }).start();
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Event log - ne geliyor görelim
        Log.d(TAG, "Event type=" + event.getEventType() + " pkg=" + event.getPackageName());
    }

    // Tap gelince anlık UI tree oku - event beklemeden
    public String dumpNearestNode(int cursorX, int cursorY) {
        try {
            android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                Log.w(TAG, "getRootInActiveWindow = null");
                return null;
            }
            Log.d(TAG, "Root: " + root.getPackageName() + " children=" + root.getChildCount());

            // En yakın clickable node'u bul
            NodeResult best = findNearest(root, cursorX, cursorY);
            root.recycle();

            if (best != null) {
                Log.d(TAG, "Nearest: " + best.label + " at " + best.cx + "," + best.cy
                    + " dist=" + best.dist);
                return "{\"nearest\":\"" + best.label + "\",\"cx\":" + best.cx
                    + ",\"cy\":" + best.cy + "}";
            }
        } catch (Exception e) {
            Log.e(TAG, "dumpNearestNode error: " + e.getMessage());
        }
        return null;
    }

    private static class NodeResult {
        String label;
        int cx, cy;
        double dist;
        NodeResult(String label, int cx, int cy, double dist) {
            this.label = label; this.cx = cx; this.cy = cy; this.dist = dist;
        }
    }

    private NodeResult findNearest(android.view.accessibility.AccessibilityNodeInfo node,
                                   int cursorX, int cursorY) {
        if (node == null) return null;
        NodeResult best = null;

        if (node.isClickable() || node.isFocusable()) {
            android.graphics.Rect r = new android.graphics.Rect();
            node.getBoundsInScreen(r);
            if (r.width() > 0 && r.height() > 0) {
                double dist = Math.hypot(r.centerX() - cursorX, r.centerY() - cursorY);
                String label = node.getContentDescription() != null
                    ? node.getContentDescription().toString() : "";
                if (best == null || dist < best.dist) {
                    best = new NodeResult(label, r.centerX(), r.centerY(), dist);
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            android.view.accessibility.AccessibilityNodeInfo child = node.getChild(i);
            NodeResult childResult = findNearest(child, cursorX, cursorY);
            if (child != null) child.recycle();
            if (childResult != null && (best == null || childResult.dist < best.dist)) {
                best = childResult;
            }
        }
        return best;
    }

    @Override
    public void onDestroy() {
        instance = null;
        if (cursorView != null && wm != null) {
            try { mainHandler.post(() -> wm.removeView(cursorView)); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}
