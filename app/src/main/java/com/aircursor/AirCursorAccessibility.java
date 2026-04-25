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

        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,  // izin gerektirmez
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );

        params.x = (int) cursorX;
        params.y = (int) cursorY;

        mainHandler.post(() -> {
            wm.addView(cursorView, params);
            Log.i(TAG, "Overlay added: " + screenW + "x" + screenH);
        });
    }

    public void moveCursor(int dx, int dy) {
        cursorX = Math.max(0, Math.min(cursorX + dx, screenW - 60));
        cursorY = Math.max(0, Math.min(cursorY + dy, screenH - 60));
        final int nx = (int) cursorX;
        final int ny = (int) cursorY;
        mainHandler.post(() -> {
            if (params != null && wm != null && cursorView != null) {
                params.x = nx;
                params.y = ny;
                wm.updateViewLayout(cursorView, params);
            }
        });
    }

    public void tap() {
        final int x = (int) cursorX;
        final int y = (int) cursorY;
        Log.d(TAG, "tap → " + x + "," + y);
        mainHandler.post(() -> {
            if (cursorView != null) cursorView.showClick();
            performTap(x, y);   // UI thread'den çağır
        });
    }

    public void performTap(int x, int y) {
        // dispatchGesture UI thread'den çağrılmalı
        mainHandler.post(() -> {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 50))
                .build();
            dispatchGesture(gesture, new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription g) {
                    Log.d(TAG, "✅ Tap ok: " + x + "," + y);
                }
                @Override public void onCancelled(GestureDescription g) {
                    Log.e(TAG, "❌ Tap cancelled: " + x + "," + y);
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
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onDestroy() {
        instance = null;
        if (cursorView != null && wm != null) {
            try { mainHandler.post(() -> wm.removeView(cursorView)); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}
