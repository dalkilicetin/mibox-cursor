package com.aircursor;

import android.accessibilityservice.AccessibilityService;
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

/**
 * AirCursorAccessibility
 *
 * Sorumluluklar:
 *  1. Ekran üstü cursor overlay (TYPE_ACCESSIBILITY_OVERLAY)
 *  2. Node cache — window değişince refresh, tap anında lookup
 *  3. tap() → cursor altındaki en yakın node'un koordinatını döndür
 *     Navigation ve key inject YOK — bunlar ATV protokolü üzerinden iOS'tan yapılır.
 *  4. performAction(ACTION_CLICK) — clickable node'larda direkt tıkla
 */
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

    // Mevcut focus node koordinatı — TYPE_VIEW_FOCUSED event'inden güncellenir
    private int currentFocusX = -1;
    private int currentFocusY = -1;

    // Node cache — window event'lerinde background thread'de refresh edilir
    private final List<NodeInfo> nodeCache = new ArrayList<>();

    // ── Data types ─────────────────────────────────────────────────────────────

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

    /** tap() sonucu — CursorService JSON olarak iOS'a döner */
    public static class TapResult {
        public final int targetX;   // hedef node merkezi X (-1 = bulunamadı)
        public final int targetY;   // hedef node merkezi Y (-1 = bulunamadı)
        public final int focusX;    // mevcut focus X (iOS navigation için)
        public final int focusY;    // mevcut focus Y
        public final String label;  // hedef node label (debug)
        public final boolean clicked; // performAction başarılı olduysa true

        TapResult(int targetX, int targetY, int focusX, int focusY, String label, boolean clicked) {
            this.targetX = targetX;
            this.targetY = targetY;
            this.focusX  = focusX;
            this.focusY  = focusY;
            this.label   = label;
            this.clicked = clicked;
        }
    }

    // ── Singleton ──────────────────────────────────────────────────────────────

    public static AirCursorAccessibility getInstance() { return instance; }
    public float getCursorX()       { return cursorX; }
    public float getCursorY()       { return cursorY; }
    public int   getScreenW()       { return screenW; }
    public int   getScreenH()       { return screenH; }
    public int   getCurrentFocusX() { return currentFocusX; }
    public int   getCurrentFocusY() { return currentFocusY; }
    public int   getNodeCacheSize() { synchronized (nodeCache) { return nodeCache.size(); } }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        mainHandler = new Handler(Looper.getMainLooper());
        Log.i(TAG, "✅ Accessibility connected");
        setupOverlay();
        mainHandler.postDelayed(this::refreshNodeCache, 1000);
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

    // ── Overlay ────────────────────────────────────────────────────────────────

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
        params.x = 0;
        params.y = 0;
        mainHandler.post(() -> {
            wm.addView(cursorView, params);
            Log.i(TAG, "Overlay added: " + screenW + "x" + screenH);
        });
    }

    // ── Node cache ─────────────────────────────────────────────────────────────

    private void refreshNodeCache() {
        new Thread(() -> {
            try {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root == null) { Log.w(TAG, "refreshNodeCache: root=null"); return; }
                List<NodeInfo> nodes = new ArrayList<>();
                traverseNodes(root, nodes);
                root.recycle();
                synchronized (nodeCache) {
                    nodeCache.clear();
                    nodeCache.addAll(nodes);
                }
                Log.d(TAG, "Cache updated: " + nodes.size() + " nodes");
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
            if (r.width() > 0 && r.height() > 0 && node.isVisibleToUser()) {
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

    // ── Accessibility events ───────────────────────────────────────────────────

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int type = event.getEventType();

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            synchronized (nodeCache) { nodeCache.clear(); }
            mainHandler.postDelayed(this::refreshNodeCache, 150);
        }

        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            mainHandler.postDelayed(this::refreshNodeCache, 150);
        }

        if (type == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            AccessibilityNodeInfo node = event.getSource();
            if (node != null) {
                Rect r = new Rect();
                node.getBoundsInScreen(r);
                currentFocusX = r.centerX();
                currentFocusY = r.centerY();
                node.recycle();
                Log.d(TAG, "Focus → (" + currentFocusX + "," + currentFocusY + ")");
            }
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Cursor'ı dx,dy kadar hareket ettir */
    public void moveCursor(int dx, int dy) {
        cursorX = Math.max(0, Math.min(cursorX + dx, screenW - 1));
        cursorY = Math.max(0, Math.min(cursorY + dy, screenH - 1));
        final float nx = cursorX, ny = cursorY;
        mainHandler.post(() -> { if (cursorView != null) cursorView.updatePosition(nx, ny); });
    }

    /**
     * Tap komutu.
     *
     * Strateji:
     *  1. Cache'den cursor'a en yakın clickable node'u bul (O(N), hızlı)
     *  2. Uzaklık makul ise → live tree'den o koordinattaki node'u bul
     *     → performAction(ACTION_CLICK) dene
     *  3. TapResult dön:
     *     - clicked=true  → iOS'un yapacağı ek şey yok
     *     - clicked=false → iOS targetX/Y'ye göre ATV protokolüyle navigate eder + DPAD_CENTER
     *     - targetX=-1    → iOS direkt DPAD_CENTER gönderir (mevcut focus'a tıklar)
     */
    public TapResult tap() {
        mainHandler.post(() -> { if (cursorView != null) cursorView.showClick(); });

        int cx = (int) cursorX;
        int cy = (int) cursorY;

        NodeInfo target = findNearestClickable(cx, cy);

        if (target == null) {
            Log.i(TAG, "tap → no node in cache");
            return new TapResult(-1, -1, currentFocusX, currentFocusY, "", false);
        }

        double dist = Math.hypot(target.cx - cx, target.cy - cy);
        int threshold = Math.min(screenW, screenH) / 2;

        if (dist > threshold) {
            Log.i(TAG, "tap → too far (" + (int)dist + "px > " + threshold + ")");
            return new TapResult(-1, -1, currentFocusX, currentFocusY, "", false);
        }

        Log.i(TAG, "tap → '" + target.label + "' (" + target.cx + "," + target.cy
            + ") dist=" + (int)dist);

        // Live tree'den performAction(ACTION_CLICK) dene
        boolean clicked = tryPerformClick(target.cx, target.cy);

        return new TapResult(
            target.cx, target.cy,
            currentFocusX, currentFocusY,
            target.label,
            clicked
        );
    }

    /** Cursor visibility */
    public void setCursorVisible(boolean visible) {
        mainHandler.post(() -> {
            if (cursorView != null)
                cursorView.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        });
    }

    /** Scroll modu göstergesi */
    public void setScrollMode(int mode) {
        mainHandler.post(() -> { if (cursorView != null) cursorView.setScrollMode(mode); });
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Live accessibility tree'den verilen koordinatı bounds'u içine alan
     * en derin clickable node'u bul, performAction(ACTION_CLICK) uygula.
     */
    private boolean tryPerformClick(int targetX, int targetY) {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return false;
            AccessibilityNodeInfo node = findClickableNodeAt(root, targetX, targetY);
            root.recycle();
            if (node != null) {
                boolean result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                node.recycle();
                Log.i(TAG, "performAction(CLICK) → " + result);
                return result;
            }
        } catch (Exception e) {
            Log.e(TAG, "tryPerformClick: " + e.getMessage());
        }
        return false;
    }

    /**
     * Ağaçta en derin (en spesifik) clickable node'u döndürür.
     * Önce children'a iner, bulamazsa kendisini döner.
     */
    private AccessibilityNodeInfo findClickableNodeAt(AccessibilityNodeInfo node, int x, int y) {
        if (node == null) return null;
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        if (!r.contains(x, y)) return null;

        // Önce children
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo found = findClickableNodeAt(child, x, y);
            if (found != null) {
                if (child != found && child != null) child.recycle();
                return found;
            }
            if (child != null) child.recycle();
        }

        // Kendisi clickable mi?
        if (node.isClickable() && node.isVisibleToUser()) {
            return AccessibilityNodeInfo.obtain(node);
        }
        return null;
    }

    /** Cache'den en yakın clickable node */
    private NodeInfo findNearestClickable(int cx, int cy) {
        NodeInfo best = null;
        double minDist = Double.MAX_VALUE;
        synchronized (nodeCache) {
            for (NodeInfo n : nodeCache) {
                if (!n.clickable) continue;
                double d = Math.hypot(n.cx - cx, n.cy - cy);
                if (d < minDist) { minDist = d; best = n; }
            }
        }
        return best;
    }
}
