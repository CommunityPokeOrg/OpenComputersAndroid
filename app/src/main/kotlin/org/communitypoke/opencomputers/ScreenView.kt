package org.communitypoke.opencomputers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import org.communitypoke.opencomputers.core.component.TextBuffer

/**
 * Renders an emulated screen's [TextBuffer] as a grid of monospace cells with
 * per-cell foreground/background — the visual counterpart of an OC screen.
 */
class ScreenView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var buffer: TextBuffer? = null
        set(value) {
            if (field === value) return
            field = value
            invalidate()
        }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
    }
    private val fillPaint = Paint()

    /** Call from any thread; schedules a redraw on the UI thread. */
    fun onBufferChanged() = postInvalidate()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val b = buffer ?: run {
            canvas.drawColor(0xFF101418.toInt())
            return
        }
        canvas.drawColor(0xFF101418.toInt())

        val cols = b.width
        val rows = b.height
        if (cols <= 0 || rows <= 0) return

        val cellW = width.toFloat() / cols
        val cellH = height.toFloat() / rows
        if (cellW <= 0f || cellH <= 0f) return

        // Fit the monospace glyph width into a cell.
        textPaint.textSize = cellH
        val measured = textPaint.measureText("M")
        if (measured > 0) {
            textPaint.textSize = cellH * (cellW / measured).coerceAtMost(1.6f)
        }
        val baseline = -textPaint.fontMetrics.ascent

        for (y in 0 until rows) {
            val top = y * cellH
            // Background runs.
            var x = 0
            while (x < cols) {
                val cell = b.cell(x + 1, y + 1) ?: break
                val bg = cell.background or 0xFF000000.toInt()
                var end = x + 1
                while (end < cols && ((b.cell(end + 1, y + 1)?.background ?: -1) or 0xFF000000.toInt()) == bg) {
                    end++
                }
                fillPaint.color = bg
                canvas.drawRect(x * cellW, top, end * cellW, top + cellH, fillPaint)
                x = end
            }
            // Foreground runs of identical color.
            x = 0
            while (x < cols) {
                val cell = b.cell(x + 1, y + 1) ?: break
                val fg = cell.foreground or 0xFF000000.toInt()
                var end = x + 1
                while (end < cols && ((b.cell(end + 1, y + 1)?.foreground ?: -1) or 0xFF000000.toInt()) == fg) {
                    end++
                }
                textPaint.color = fg
                val row = b.rowText(y + 1)
                canvas.drawText(row.substring(x, end), x * cellW, top + baseline, textPaint)
                x = end
            }
        }
    }
}
