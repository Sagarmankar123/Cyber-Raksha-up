package com.cyberraksha.guardian;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * CyberWebView — Spider web / network grid animation
 * Draws animated glowing nodes connected by neon lines.
 * GPU-friendly, lightweight — runs smooth on Redmi/Realme.
 */
public class CyberWebView extends View {

    private static final int NODE_COUNT    = 18;
    private static final int LINE_DISTANCE = 250;
    private static final long FRAME_MS     = 33; // ~30fps — battery friendly

    private final List<Node> nodes = new ArrayList<>();
    private final Paint linePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rng       = new Random();

    private boolean running = false;
    private float scanPulse = 0f;        // 0..1 expanding pulse
    private int   pulseAlpha = 200;

    private final Paint pulsePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CyberWebView(Context ctx) { super(ctx); init(); }
    public CyberWebView(Context ctx, AttributeSet attrs) { super(ctx, attrs); init(); }

    private void init() {
        linePaint.setColor(Color.parseColor("#2200FFFF"));
        linePaint.setStrokeWidth(1f);
        linePaint.setStyle(Paint.Style.STROKE);

        nodePaint.setColor(Color.parseColor("#0099FF"));
        nodePaint.setStyle(Paint.Style.FILL);

        glowPaint.setColor(Color.parseColor("#440099FF"));
        glowPaint.setStyle(Paint.Style.FILL);

        pulsePaint.setStyle(Paint.Style.STROKE);
        pulsePaint.setStrokeWidth(2f);
        pulsePaint.setColor(Color.parseColor("#44B400FF"));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        nodes.clear();
        for (int i = 0; i < NODE_COUNT; i++) {
            nodes.add(new Node(rng.nextFloat() * w, rng.nextFloat() * h,
                    (rng.nextFloat() - 0.5f) * 1.2f,
                    (rng.nextFloat() - 0.5f) * 1.2f));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!running) return;

        int w = getWidth(), h = getHeight();

        // Update node positions
        for (Node n : nodes) {
            n.x += n.vx;
            n.y += n.vy;
            if (n.x < 0 || n.x > w) n.vx = -n.vx;
            if (n.y < 0 || n.y > h) n.vy = -n.vy;
        }

        // Draw connecting lines
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                float dx = nodes.get(i).x - nodes.get(j).x;
                float dy = nodes.get(i).y - nodes.get(j).y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist < LINE_DISTANCE) {
                    int alpha = (int) (180 * (1 - dist / LINE_DISTANCE));
                    linePaint.setAlpha(alpha);
                    canvas.drawLine(nodes.get(i).x, nodes.get(i).y,
                            nodes.get(j).x, nodes.get(j).y, linePaint);
                }
            }
        }

        // Draw nodes
        for (Node n : nodes) {
            glowPaint.setAlpha(60);
            canvas.drawCircle(n.x, n.y, 12f, glowPaint);
            nodePaint.setAlpha(200);
            canvas.drawCircle(n.x, n.y, 4f, nodePaint);
        }

        // Draw scan pulse
        scanPulse += 0.008f;
        if (scanPulse > 1f) { scanPulse = 0f; pulseAlpha = 180; }
        else pulseAlpha = Math.max(0, pulseAlpha - 3);

        float pulseRadius = scanPulse * Math.max(w, h) * 0.9f;
        pulsePaint.setAlpha(pulseAlpha);
        canvas.drawCircle(w / 2f, h / 2f, pulseRadius, pulsePaint);

        postInvalidateDelayed(FRAME_MS);
    }

    public void start() {
        running = true;
        invalidate();
    }

    public void stop() {
        running = false;
    }

    static class Node {
        float x, y, vx, vy;
        Node(float x, float y, float vx, float vy) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
        }
    }
}
