package com.car2hass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/** Minimal numeric sparkline: draws the recent values of a sensor as a line. */
public class SparklineView extends View {

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private float[] values = new float[0];

    public SparklineView(Context c) { this(c, null, 0); }

    public SparklineView(Context c, AttributeSet a) { this(c, a, 0); }

    public SparklineView(Context c, AttributeSet a, int defStyle) {
        super(c, a, defStyle);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(getResources().getDisplayMetrics().density * 2f);
        line.setColor(0xFF4CAF50);
    }

    public void setValues(float[] v) {
        this.values = v != null ? v : new float[0];
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int n = values.length;
        if (n < 2) return;
        float min = values[0], max = values[0];
        for (float v : values) {
            if (v < min) min = v;
            if (v > max) max = v;
        }
        float range = max - min;
        if (range <= 0) range = 1f;
        float w = getWidth(), h = getHeight();
        float pad = line.getStrokeWidth();
        path.reset();
        for (int i = 0; i < n; i++) {
            float x = pad + (w - 2 * pad) * i / (n - 1);
            float y = h - pad - (h - 2 * pad) * (values[i] - min) / range;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        canvas.drawPath(path, line);
    }
}
