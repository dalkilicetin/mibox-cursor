package com.aircursor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

public class CursorView extends View {

    // %30 kucultuldu: 28 -> 20
    private final float R = 20f;

    private Paint ballPaint;
    private Paint highlightPaint;
    private Paint edgePaint;
    private Paint arrowPaint;

    private float posX = 960;
    private float posY = 540;

    public void updatePosition(float x, float y) {
        posX = x;
        posY = y;
        invalidate();
    }

    public CursorView(Context context) {
        super(context);

        ballPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ballPaint.setStyle(Paint.Style.FILL);

        highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlightPaint.setStyle(Paint.Style.FILL);
        highlightPaint.setColor(0x55FFFFFF);

        edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(1f);
        edgePaint.setColor(0x22000000);

        arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrowPaint.setStyle(Paint.Style.FILL);
        arrowPaint.setColor(0xFF333333);
    }

    public void setScrollMode(int mode) {
        this.scrollMode = mode;
        invalidate();
    }

    public void showClick() {
        clicking = true;
        invalidate();
        postDelayed(() -> {
            clicking = false;
            invalidate();
        }, 200);
    }

    private void setupBallGradient(Canvas canvas, float cx, float cy, boolean isClick) {
        RadialGradient gradient;
        if (isClick) {
            gradient = new RadialGradient(
                cx - R * 0.3f, cy - R * 0.3f, R * 1.2f,
                new int[]{0xFFFF8888, 0xFFDD3333, 0xFFBB1111},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
            );
        } else {
            gradient = new RadialGradient(
                cx - R * 0.3f, cy - R * 0.3f, R * 1.2f,
                new int[]{0xFFFFFFFF, 0xFFDDDDDD, 0xFF999999},
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP
            );
        }
        ballPaint.setShader(gradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = posX;
        float cy = posY;

        setupBallGradient(canvas, cx, cy, clicking);

        // Top
        canvas.drawCircle(cx, cy, R, ballPaint);
        canvas.drawCircle(cx, cy, R, edgePaint);

        // Highlight (sol üst parlaklık)
        canvas.drawCircle(cx - R * 0.28f, cy - R * 0.28f, R * 0.45f, highlightPaint);

        if (!clicking) {
            if (scrollMode == 1) {
                drawVerticalArrows(canvas, cx, cy);
            } else if (scrollMode == 2) {
                drawHorizontalArrows(canvas, cx, cy);
            }
        }
    }

    private void drawVerticalArrows(Canvas canvas, float cx, float cy) {
        float as = R * 0.38f; // ok boyutu
        float gap = R * 0.18f;

        // Yukari ok
        Path up = new Path();
        up.moveTo(cx, cy - gap - as);
        up.lineTo(cx - as * 0.7f, cy - gap);
        up.lineTo(cx + as * 0.7f, cy - gap);
        up.close();
        canvas.drawPath(up, arrowPaint);

        // Asagi ok
        Path down = new Path();
        down.moveTo(cx, cy + gap + as);
        down.lineTo(cx - as * 0.7f, cy + gap);
        down.lineTo(cx + as * 0.7f, cy + gap);
        down.close();
        canvas.drawPath(down, arrowPaint);

        // Orta cizgi
        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(0x88333333);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(1.5f);
        canvas.drawLine(cx, cy - gap + 2, cx, cy + gap - 2, linePaint);
    }

    private void drawHorizontalArrows(Canvas canvas, float cx, float cy) {
        float as = R * 0.38f;
        float gap = R * 0.18f;

        // Sol ok
        Path left = new Path();
        left.moveTo(cx - gap - as, cy);
        left.lineTo(cx - gap, cy - as * 0.7f);
        left.lineTo(cx - gap, cy + as * 0.7f);
        left.close();
        canvas.drawPath(left, arrowPaint);

        // Sag ok
        Path right = new Path();
        right.moveTo(cx + gap + as, cy);
        right.lineTo(cx + gap, cy - as * 0.7f);
        right.lineTo(cx + gap, cy + as * 0.7f);
        right.close();
        canvas.drawPath(right, arrowPaint);

        // Orta cizgi
        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(0x88333333);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(1.5f);
        canvas.drawLine(cx - gap + 2, cy, cx + gap - 2, cy, linePaint);
    }

    @Override
    protected void onMeasure(int w, int h) {
        // MATCH_PARENT overlay — tüm ekranı kapla
        setMeasuredDimension(
            MeasureSpec.getSize(w),
            MeasureSpec.getSize(h)
        );
    }
}
