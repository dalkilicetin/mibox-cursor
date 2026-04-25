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

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (navigating) {
                Log.e(TAG, "Window changed → cancel navigation");
                navigating = false;
            }
            mainHandler.postDelayed(this::refreshNodeCache, 200);
        }

        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
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

    // Akıllı hedef seçimi — cursor'a yakın ve mevcut focus ile aynı satırda öncelik
    private NodeInfo pickBestTarget(int cx, int cy) {
        NodeInfo best = null;
        double bestScore = Double.MAX_VALUE;
        int fx = currentFocusX;
        int fy = currentFocusY;

        synchronized (nodeCache) {
            for (NodeInfo n : nodeCache) {
                if (!n.clickable) continue;
                double dist = Math.hypot(n.cx - cx, n.cy - cy);
                double rowPenalty = Math.abs(n.cy - fy) * 2.0;
                double colPenalty = Math.abs(n.cx - fx) * 0.3;
                double score = dist + rowPenalty + colPenalty;
                if (score < bestScore) { bestScore = score; best = n; }
            }
        }
        return best;
    }

    // Hedefe en çok yaklaştıran DPAD yönünü seç
    private int chooseBestDirection() {
        int fx = currentFocusX;
        int fy = currentFocusY;
        double bestScore = Double.MAX_VALUE;
        int bestKey = -1;

        synchronized (nodeCache) {
            for (NodeInfo n : nodeCache) {
                int dx = n.cx - fx;
                int dy = n.cy - fy;
                if (Math.abs(dx) < 20 && Math.abs(dy) < 20) continue;
                // Farklı row'a gitmeyi engelle
                if (Math.abs(n.cy - fy) > 250) continue;

                int key;
                if (Math.abs(dx) > Math.abs(dy)) {
                    key = dx > 0 ? 22 : 21;
                } else {
                    key = dy > 0 ? 20 : 19;
                }

                if (key == 22 && dx <= 0) continue;
                if (key == 21 && dx >= 0) continue;
                if (key == 20 && dy <= 0) continue;
                if (key == 19 && dy >= 0) continue;

                double before = Math.hypot(targetX - fx, targetY - fy);
                double after  = Math.hypot(targetX - n.cx, targetY - n.cy);
                double improvement = before - after;
                if (improvement <= 0) continue;

                double score = -improvement;
                if (score < bestScore) { bestScore = score; bestKey = key; }
            }
        }
        return bestKey;
    }

    public void tap() {
        navigating = false;  // önceki navigasyonu iptal et

        int cx = (int) cursorX;
        int cy = (int) cursorY;
        NodeInfo target = pickBestTarget(cx, cy);

        mainHandler.post(() -> { if (cursorView != null) cursorView.showClick(); });

        if (target == null) {
            Log.e(TAG, "Tap → no target → CENTER");
            injectKey(23);
            return;
        }

        double dist = Math.hypot(target.cx - cx, target.cy - cy);
        if (dist > 600) {
            Log.e(TAG, "Tap → too far → ignore");
            return;
        }

        targetX = target.cx;
        targetY = target.cy;
        navigating = true;
        Log.e(TAG, "Target: " + target.label + " (" + targetX + "," + targetY + ")");

        // Timeout — sadece dur, yanlış yere click atma
        mainHandler.postDelayed(() -> {
            if (navigating) {
                Log.e(TAG, "Timeout → cancel");
                navigating = false;
            }
        }, 2000);

        stepNavigate();
    }

    private void stepNavigate() {
        if (!navigating) return;

        int fx = currentFocusX;
        int fy = currentFocusY;

        if (fx < 0 || fy < 0) {
            Log.e(TAG, "No focus → cancel");
            navigating = false;
            return;
        }

        int dx = targetX - fx;
        int dy = targetY - fy;

        if (Math.abs(dx) < 60 && Math.abs(dy) < 60) {
            Log.e(TAG, "✅ Reached → CENTER");
            navigating = false;
            injectKey(23);
            return;
        }

        int key = chooseBestDirection();
        if (key == -1) {
            Log.e(TAG, "No valid direction → cancel");
            navigating = false;
            return;
        }

        Log.e(TAG, "stepNavigate focus:(" + fx + "," + fy + ") target:(" + targetX + "," + targetY + ") key=" + key);
        injectKey(key);
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
