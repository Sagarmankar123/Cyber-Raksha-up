package com.cyberraksha.guardian;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class RadarView extends View {

    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweepPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float sweepAngle = 0f;
    private boolean running = false;
    private static final long FRAME_MS = 16; // 60fps

    // Random "blip" dots
    private final float[] dotAngles = {30f, 85f, 140f, 210f, 270f, 320f};
    private final float[] dotRadii  = {0.3f, 0.6f, 0.45f, 0.7f, 0.25f, 0.55f};
    private final boolean[] dotVisible = new boolean[6];

    public RadarView(Context ctx) { super(ctx); init(); }
    public RadarView(Context ctx, AttributeSet a) { super(ctx, a); init(); }

    private void init() {
        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setColor(Color.parseColor("#2200FF94"));
        circlePaint.setStrokeWidth(1.5f);

        sweepPaint.setStyle(Paint.Style.FILL);
        sweepPaint.setColor(Color.parseColor("#4400FF94"));

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(Color.parseColor("#FF005C"));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!running) return;
        int w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float maxR = Math.min(cx, cy) * 0.9f;

        // Draw concentric circles
        circlePaint.setAlpha(80);
        for (float r = maxR * 0.25f; r <= maxR; r += maxR * 0.25f) {
            canvas.drawCircle(cx, cy, r, circlePaint);
        }

        // Draw crosshairs
        circlePaint.setAlpha(40);
        canvas.drawLine(cx - maxR, cy, cx + maxR, cy, circlePaint);
        canvas.drawLine(cx, cy - maxR, cx, cy + maxR, circlePaint);

        // Draw sweep
        RectF oval = new RectF(cx - maxR, cy - maxR, cx + maxR, cy + maxR);
        sweepPaint.setAlpha(120);
        canvas.drawArc(oval, sweepAngle - 60f, 60f, true, sweepPaint);

        // Draw blips when sweep passes over them
        for (int i = 0; i < dotAngles.length; i++) {
            float diff = (sweepAngle - dotAngles[i] + 360) % 360;
            if (diff < 60) {
                dotVisible[i] = true;
                dotPaint.setAlpha((int)(255 * (1 - diff / 60f)));
            } else if (diff > 300) {
                dotVisible[i] = false;
            }
            if (dotVisible[i]) {
                double rad = Math.toRadians(dotAngles[i]);
                float r = maxR * dotRadii[i];
                float dx = cx + (float)(r * Math.cos(rad));
                float dy = cy + (float)(r * Math.sin(rad));
                canvas.drawCircle(dx, dy, 5f, dotPaint);
            }
        }

        // Advance sweep
        sweepAngle = (sweepAngle + 1.5f) % 360;
        postInvalidateDelayed(FRAME_MS);
    }

    public void start() { running = true; invalidate(); }
    public void stop()  { running = false; }
}
