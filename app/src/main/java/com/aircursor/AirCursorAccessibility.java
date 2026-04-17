package com.aircursor;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class AirCursorAccessibility extends AccessibilityService {

    private static final String TAG = "AirCursorA11y";
    private static AirCursorAccessibility instance;

    public static AirCursorAccessibility getInstance() {
        return instance;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "Accessibility Service connected");
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    // Tap - verilen koordinata tikla
    public void performTap(int x, int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        try {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 50);
            GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();
            dispatchGesture(gesture, null, null);
            Log.i(TAG, "Tap: " + x + "," + y);
        } catch (Exception e) {
            Log.w(TAG, "Tap error: " + e.getMessage());
        }
    }

    // Swipe - x1,y1'den x2,y2'ye kaydir
    public void performSwipe(int x1, int y1, int x2, int y2, int durationMs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        try {
            Path path = new Path();
            path.moveTo(x1, y1);
            path.lineTo(x2, y2);
            GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, durationMs);
            GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();
            dispatchGesture(gesture, null, null);
            Log.i(TAG, "Swipe: " + x1 + "," + y1 + " -> " + x2 + "," + y2);
        } catch (Exception e) {
            Log.w(TAG, "Swipe error: " + e.getMessage());
        }
    }
}
