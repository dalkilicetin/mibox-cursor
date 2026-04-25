package com.aircursor;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.WindowManager;

public class CursorOverlay {

    private static CursorView cursorView;
    private static WindowManager wm;

    public static float cursorX = 500;
    public static float cursorY = 500;

    public static void init(Context context) {

        wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        cursorView = new CursorView(context);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
        );

        wm.addView(cursorView, params);
    }

    public static void move(float dx, float dy) {
        cursorX += dx;
        cursorY += dy;

        cursorX = Math.max(0, Math.min(cursorX, 1920));
        cursorY = Math.max(0, Math.min(cursorY, 1080));

        if (cursorView != null) {
            cursorView.update(cursorX, cursorY);
        }
    }
}