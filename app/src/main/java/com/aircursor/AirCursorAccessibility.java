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

    // Node cache — event'te güncellenir, tap'te sadece lookup
    private final List<NodeInfo> nodeCache = new ArrayList<>();
    private int currentFocusX = -1;
    private int currentFocusY = -1;

    static class NodeInfo {
        String label;
        int cx, cy;
        boolean clickable;
        NodeInfo(String label, int cx, int cy, boolean clickable) {
            this.label = label; this.cx = cx; this.cy = cy; this.clickable = clickable;
        }
    }

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
        Log.e(TAG, "✅ Accessibility connected");
        setupOverlay();
        // İlk yüklemede tree'yi cache'le
        mainHandler.postDelayed(this::refreshNodeCache, 1000);
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
        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        params.x = 0; params.y = 0;
        mainHandler.post(() -> {
            wm.addView(cursorView, params);
            Log.e(TAG, "Overlay added: " + screenW + "x" + screenH);
        });
    }

    // Node cache güncelle — background thread'den çağrılabilir
    private void refreshNodeCache() {
        new Thread(() -> {
            try {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root == null) { Log.e(TAG, "root=null"); return; }
                List<NodeInfo> nodes = new ArrayList<>();
                traverseNodes(root, nodes);
                root.recycle();
                synchronized (nodeCache) {
                    nodeCache.clear();
                    nodeCache.addAll(nodes);
                }
                Log.e(TAG, "Cache updated: " + nodes.size() + " nodes");
            } catch (Exception e) {
                Log.e(TAG, "refreshNodeCache: " + e.getMessage());
            }
        }).start();
    }

    private void traverseNodes(AccessibilityNodeInfo node, List<NodeInfo> out) {
        if (node == null) return;
        if (node.isClickable() || node.isFocusable()) {
            Rect r = new Rect();
            node.getBoundsInScreen(r);
            if (r.width() > 0 && r.height() > 0) {
                String label = "";
                if (node.getContentDescription() != null)
                    label = node.getContentDescription().toString();
                else if (node.getText() != null)
                    label = node.getText().toString();
                out.add(new NodeInfo(label, r.centerX(), r.centerY(), node.isClickable()));
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
        Log.e(TAG, "Event: " + type + " pkg=" + event.getPackageName());

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            // UI değişti — cache'i yenile
            mainHandler.postDelayed(this::refreshNodeCache, 200);
        }

        if (type == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            AccessibilityNodeInfo node = event.getSource();
            if (node != null) {
                Rect r = new Rect();
                node.getBoundsInScreen(r);
                currentFocusX = r.centerX();
                currentFocusY = r.centerY();
                Log.e(TAG, "Focus at: " + currentFocusX + "," + currentFocusY
                    + " label=" + node.getContentDescription());
                node.recycle();

                // Event-driven navigation — yeni focus gelince bir sonraki adımı hesapla
                if (navigating) {
                    stepNavigate();
                }
            }
        }
    }

    // Cursor'a en yakın node'u bul — O(N) ama cache'den, tap anında
    public NodeInfo findNearest(int cx, int cy) {
        NodeInfo best = null;
        double minDist = Double.MAX_VALUE;
        synchronized (nodeCache) {
            for (NodeInfo n : nodeCache) {
                double d = Math.hypot(n.cx - cx, n.cy - cy);
                if (d < minDist) { minDist = d; best = n; }
            }
        }
        return best;
    }

    public int getCurrentFocusX() { return currentFocusX; }
    public int getCurrentFocusY() { return currentFocusY; }
    public int getNodeCacheSize() { return nodeCache.size(); }

    // Cursor hareketi
    public void moveCursor(int dx, int dy) {
        cursorX = Math.max(0, Math.min(cursorX + dx, screenW - 1));
        cursorY = Math.max(0, Math.min(cursorY + dy, screenH - 1));
        final float nx = cursorX, ny = cursorY;
        mainHandler.post(() -> {
            if (cursorView != null) cursorView.updatePosition(nx, ny);
        });
    }

    // Navigation state
    private volatile boolean navigating = false;
    private volatile int targetX = -1;
    private volatile int targetY = -1;

    // Tap = nearest node'a event-driven navigate et
    public void tap() {
        if (navigating) return;  // navigation lock

        int cx = (int) cursorX;
        int cy = (int) cursorY;
        NodeInfo nearest = findNearest(cx, cy);

        mainHandler.post(() -> {
            if (cursorView != null) cursorView.showClick();
        });

        if (nearest == null) {
            Log.e(TAG, "Tap → no node → CENTER");
            injectKey(23);
            return;
        }

        // Çok uzak node'u ignore et
        double dist = Math.hypot(nearest.cx - cx, nearest.cy - cy);
        if (dist > 600) {
            Log.e(TAG, "Tap → too far (" + (int)dist + "px) → CENTER");
            injectKey(23);
            return;
        }

        targetX = nearest.cx;
        targetY = nearest.cy;
        navigating = true;
        Log.e(TAG, "Tap → navigate to: " + nearest.label + " (" + targetX + "," + targetY + ")");

        // Timeout — 2sn içinde ulaşamazsa iptal
        mainHandler.postDelayed(() -> {
            if (navigating) {
                Log.e(TAG, "Navigation timeout");
                navigating = false;
                injectKey(23);
            }
        }, 2000);

        stepNavigate();
    }

    // Event-driven navigation step
    private void stepNavigate() {
        if (!navigating) return;

        int fx = currentFocusX;
        int fy = currentFocusY;

        if (fx < 0 || fy < 0) {
            Log.e(TAG, "stepNavigate → no focus → CENTER");
            navigating = false;
            injectKey(23);
            return;
        }

        int dx = targetX - fx;
        int dy = targetY - fy;

        Log.e(TAG, "stepNavigate → focus:(" + fx + "," + fy +
            ") target:(" + targetX + "," + targetY + ") d:(" + dx + "," + dy + ")");

        // Hedefe ulaştık mı?
        if (Math.abs(dx) < 80 && Math.abs(dy) < 80) {
            Log.e(TAG, "✅ Reached → CENTER");
            navigating = false;
            injectKey(23);
            return;
        }

        // Hangi yönde DPAD
        int keyCode;
        if (Math.abs(dx) > Math.abs(dy)) {
            keyCode = dx > 0 ? 22 : 21;  // RIGHT : LEFT
        } else {
            keyCode = dy > 0 ? 20 : 19;  // DOWN : UP
        }
        injectKey(keyCode);
        // Bir sonraki adım TYPE_VIEW_FOCUSED event'i tetikleyince gelecek
    }

    public void performSwipe(int x1, int y1, int x2, int y2, int durationMs) {
        mainHandler.post(() -> {
            Path path = new Path();
            path.moveTo(x1, y1); path.lineTo(x2, y2);
            GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, durationMs))
                .build();
            dispatchGesture(gesture, null, null);
        });
    }

    public void setCursorVisible(boolean visible) {
        mainHandler.post(() -> {
            if (cursorView != null)
                cursorView.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        });
    }

    public void setScrollMode(int mode) {
        mainHandler.post(() -> { if (cursorView != null) cursorView.setScrollMode(mode); });
    }

    public void injectText(String text) {
        new Thread(() -> {
            try { Runtime.getRuntime().exec(new String[]{"input", "text", text}); }
            catch (Exception e) { Log.e(TAG, "Text: " + e.getMessage()); }
        }).start();
    }

    public void injectKey(int keyCode) {
        new Thread(() -> {
            try { Runtime.getRuntime().exec(new String[]{"input", "keyevent", String.valueOf(keyCode)}); }
            catch (Exception e) { Log.e(TAG, "Key: " + e.getMessage()); }
        }).start();
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        instance = null;
        if (cursorView != null && wm != null) {
            try { mainHandler.post(() -> wm.removeView(cursorView)); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}
