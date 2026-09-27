package com.communitypoke.ocandroid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import org.opencomputers.component.TextBuffer;

/**
 * Renders an OC screen's {@link TextBuffer}: per-cell background fill plus a
 * monospace glyph for the character. Refreshes on a UI timer — the buffer is
 * owned by the emulator thread, so drawing tolerates torn reads.
 */
public class ScreenView extends View {
    private final Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cellPaint = new Paint();
    private final Rect cellRect = new Rect();

    private EmulatorRuntime runtime;
    private float cellW;
    private float cellH;

    private final Runnable refreshTick = new Runnable() {
        @Override
        public void run() {
            invalidate();
            postDelayed(this, 66); // ~15 fps refresh
        }
    };

    public ScreenView(Context context) {
        super(context);
        init();
    }

    public ScreenView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setFocusable(true);
        setFocusableInTouchMode(true);
        glyphPaint.setTypeface(Typeface.MONOSPACE);
        glyphPaint.setTextAlign(Paint.Align.LEFT);
        setBackgroundColor(0xFF000000);
        postDelayed(refreshTick, 66);
    }

    public void attach(EmulatorRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (runtime == null) {
            return;
        }
        TextBuffer buf = runtime.textBuffer();
        if (buf == null) {
            drawCentered(canvas, "gpu not bound — booting?");
            return;
        }
        if (!runtime.screen().buffer().equals(buf) || !screenOn()) {
            drawCentered(canvas, "screen off");
            return;
        }
        int w = buf.width();
        int h = buf.height();
        if (w <= 0 || h <= 0) {
            return;
        }
        // Fit cells to the view, preserving ~2:1 character aspect.
        float glyphW = glyphPaint.measureText("W");
        cellW = (float) getWidth() / w;
        cellH = Math.min((float) getHeight() / h, cellW * 2.4f);
        glyphPaint.setTextSize(cellH * 0.78f);
        float baselinePad = glyphPaint.getFontMetrics().descent;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float left = x * cellW;
                float top = y * cellH;
                cellRect.set((int) left, (int) top,
                        (int) (left + cellW + 1), (int) (top + cellH + 1));
                cellPaint.setColor(0xFF000000 | buf.rawBg(x, y));
                canvas.drawRect(cellRect, cellPaint);
                char c = buf.rawChar(x, y);
                if (c != ' ') {
                    glyphPaint.setColor(0xFF000000 | buf.rawFg(x, y));
                    canvas.drawText(new char[]{c}, 0, 1,
                            left, top + cellH - baselinePad, glyphPaint);
                }
            }
        }
    }

    private boolean screenOn() {
        try {
            return Boolean.TRUE.equals(
                    runtime.screen().isOn(new Object[0])[0]);
        } catch (RuntimeException e) {
            return true;
        }
    }

    private void drawCentered(Canvas canvas, String text) {
        glyphPaint.setColor(0xFF555555);
        glyphPaint.setTextSize(getHeight() / 20f);
        float tw = glyphPaint.measureText(text);
        canvas.drawText(text, (getWidth() - tw) / 2f, getHeight() / 2f, glyphPaint);
    }
}
