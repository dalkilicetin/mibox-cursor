package com.aircursor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

public class CursorView extends View {

    private float x = 500;
    private float y = 500;
    private Paint paint;

    public CursorView(Context context) {
        super(context);

        paint = new Paint();
        paint.setColor(Color.RED);
        paint.setAntiAlias(true);
    }

    public void update(float nx, float ny) {
        x = nx;
        y = ny;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawCircle(x, y, 12, paint);
    }
}