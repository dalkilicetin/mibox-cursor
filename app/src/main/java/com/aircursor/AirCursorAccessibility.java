package com.aircursor;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class AirCursorAccessibility extends AccessibilityService {

    private static AirCursorAccessibility instance;

    public static AirCursorAccessibility getInstance() {
        return instance;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d("A11Y", "✅ Accessibility connected");

        CursorOverlay.init(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    // 🔥 TAP
    public void performTap(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 50);

        GestureDescription gesture =
                new GestureDescription.Builder().addStroke(stroke).build();

        dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                Log.d("A11Y", "✅ Tap success");
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                Log.e("A11Y", "❌ Tap cancelled");
            }
        }, null);
    }
}