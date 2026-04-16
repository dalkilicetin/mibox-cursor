package com.aircursor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

public class CursorView extends View {

    private final Paint redPaint;
    private final Paint redFadePaint;
    private final Paint redFade2Paint;
    private final Paint whitePaint;

    private final float R  = 22f;   // ana çember yarıçapı
    private final float DOT = 4f;   // merkez nokta
    private final float GAP = 5f;   // çember ile çizgi arası boşluk
    private final float LINE = 8f;  // çizgi uzunluğu

    // Scroll modu: 0=normal, 1=dikey, 2=yatay
    private int scrollMode = 0;

    // Tıklama animasyonu
    private boolean clicking = false;

    public CursorView(Context context) {
        super(context);

        redPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        redPaint.setColor(0xFFFF3B30);
        redPaint.setStyle(Paint.Style.STROKE);
        redPaint.setStrokeWidth(2.5f);

        redFadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        redFadePaint.setColor(0x99FF3B30);
        redFadePaint.setStyle(Paint.Style.STROKE);
        redFadePaint.setStrokeWidth(1.5f);

        redFade2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        redFade2Paint.setColor(0x44FF3B30);
        redFade2Paint.setStyle(Paint.Style.STROKE);
        redFade2Paint.setStrokeWidth(1f);

        whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePaint.setColor(Color.WHITE);
        whitePaint.setStyle(Paint.Style.FILL);
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

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;

        if (clicking) {
            // Tıklama: patlama efekti - 3 iç içe daire
            Paint fillClick = new Paint(Paint.ANTI_ALIAS_FLAG);
            fillClick.setStyle(Paint.Style.FILL);
            fillClick.setColor(0xFFFF3B30);
            canvas.drawCircle(cx, cy, R * 0.7f, fillClick);

            canvas.drawCircle(cx, cy, R * 1.2f, redFadePaint);
            canvas.drawCircle(cx, cy, R * 1.7f, redFade2Paint);
            return;
        }

        // Ana çember
        canvas.drawCircle(cx, cy, R, redPaint);

        // Merkez nokta
        Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(0xFFFF3B30);
        dotPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, DOT, dotPaint);

        if (scrollMode == 0) {
            // Normal: 4 yönde kısa çizgi
            drawLines4(canvas, cx, cy);
        } else if (scrollMode == 1) {
            // Dikey scroll: yukarı ve aşağı ok
            drawLines4(canvas, cx, cy);
            drawArrowUp(canvas, cx, cy);
            drawArrowDown(canvas, cx, cy);
        } else if (scrollMode == 2) {
            // Yatay scroll: sağ ve sol ok
            drawLines4(canvas, cx, cy);
            drawArrowLeft(canvas, cx, cy);
            drawArrowRight(canvas, cx, cy);
        }
    }

    private void drawLines4(Canvas canvas, float cx, float cy) {
        // Sağ
        canvas.drawLine(cx + R + GAP, cy, cx + R + GAP + LINE, cy, redPaint);
        // Sol
        canvas.drawLine(cx - R - GAP, cy, cx - R - GAP - LINE, cy, redPaint);
        // Üst
        canvas.drawLine(cx, cy - R - GAP, cx, cy - R - GAP - LINE, redPaint);
        // Alt
        canvas.drawLine(cx, cy + R + GAP, cx, cy + R + GAP + LINE, redPaint);
    }

    private void drawArrowUp(Canvas canvas, float cx, float cy) {
        float base = cy - R - GAP - LINE - 4f;
        float hs = 7f;
        Path p = new Path();
        p.moveTo(cx, base - hs);
        p.lineTo(cx - hs, base);
        p.lineTo(cx + hs, base);
        p.close();
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(0xFFFF3B30);
        fill.setStyle(Paint.Style.FILL);
        canvas.drawPath(p, fill);
    }

    private void drawArrowDown(Canvas canvas, float cx, float cy) {
        float base = cy + R + GAP + LINE + 4f;
        float hs = 7f;
        Path p = new Path();
        p.moveTo(cx, base + hs);
        p.lineTo(cx - hs, base);
        p.lineTo(cx + hs, base);
        p.close();
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(0xFFFF3B30);
        fill.setStyle(Paint.Style.FILL);
        canvas.drawPath(p, fill);
    }

    private void drawArrowLeft(Canvas canvas, float cx, float cy) {
        float base = cx - R - GAP - LINE - 4f;
        float hs = 7f;
        Path p = new Path();
        p.moveTo(base - hs, cy);
        p.lineTo(base, cy - hs);
        p.lineTo(base, cy + hs);
        p.close();
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(0xFFFF3B30);
        fill.setStyle(Paint.Style.FILL);
        canvas.drawPath(p, fill);
    }

    private void drawArrowRight(Canvas canvas, float cx, float cy) {
        float base = cx + R + GAP + LINE + 4f;
        float hs = 7f;
        Path p = new Path();
        p.moveTo(base + hs, cy);
        p.lineTo(base, cy - hs);
        p.lineTo(base, cy + hs);
        p.close();
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(0xFFFF3B30);
        fill.setStyle(Paint.Style.FILL);
        canvas.drawPath(p, fill);
    }

    @Override
    protected void onMeasure(int w, int h) {
        int size = (int)((R + GAP + LINE + 14f) * 2f);
        setMeasuredDimension(size, size);
    }
}
