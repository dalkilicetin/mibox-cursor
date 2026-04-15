package com.aircursor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Ekranda mouse cursor (ok işareti) çizen View.
 * WindowManager overlay olarak eklenir.
 */
public class CursorView extends View {

    private final Paint fillPaint;
    private final Paint strokePaint;
    private final Path arrowPath;
    private final float SIZE = 48f;  // cursor boyutu dp

    // Tıklama animasyonu
    private boolean clicking = false;
    private long clickStart = 0;

    public CursorView(Context context) {
        super(context);

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(Color.WHITE);
        fillPaint.setStyle(Paint.Style.FILL);

        strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setColor(Color.BLACK);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(3f);

        // Ok cursor path (0,0 noktasından itibaren)
        arrowPath = buildArrow(SIZE);
    }

    private Path buildArrow(float s) {
        Path p = new Path();
        // Klasik mouse cursor şekli
        p.moveTo(0, 0);
        p.lineTo(0, s * 0.85f);
        p.lineTo(s * 0.25f, s * 0.60f);
        p.lineTo(s * 0.45f, s);
        p.lineTo(s * 0.55f, s * 0.96f);
        p.lineTo(s * 0.35f, s * 0.56f);
        p.lineTo(s * 0.60f, s * 0.56f);
        p.close();
        return p;
    }

    public void showClick() {
        clicking = true;
        clickStart = System.currentTimeMillis();
        invalidate();
        postDelayed(() -> {
            clicking = false;
            invalidate();
        }, 200);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (clicking) {
            // Tıklama sırasında kırmızı göster
            fillPaint.setColor(Color.RED);
        } else {
            fillPaint.setColor(Color.WHITE);
        }

        canvas.drawPath(arrowPath, fillPaint);
        canvas.drawPath(arrowPath, strokePaint);
    }

    @Override
    protected void onMeasure(int w, int h) {
        // Cursor boyutu sabit
        int size = (int)(SIZE * 1.5f);
        setMeasuredDimension(size, size);
    }
}
