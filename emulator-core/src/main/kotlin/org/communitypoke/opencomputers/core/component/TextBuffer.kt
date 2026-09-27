package org.communitypoke.opencomputers.core.component

import org.communitypoke.opencomputers.core.machine.ComponentException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The character cell storage behind a `screen` component, equivalent to the
 * `TextBuffer` in OpenComputers' internals. Holds glyph, foreground and
 * background per cell plus the 240-color palette; a bound `gpu` writes through
 * it and a renderer (Android view or test code) reads it.
 *
 * Color storage: each cell keeps a raw value plus a flag telling whether the
 * value is a palette index or a packed RGB int — the same scheme OC uses, so
 * palette edits retroactively recolor existing cells.
 */
class TextBuffer(
    val maxWidth: Int = 160,
    val maxHeight: Int = 50,
    initialWidth: Int = 80,
    initialHeight: Int = 25,
    val maxDepth: Int = 8,
) {
    /** One addressable cell. Colors are resolved RGB when read through [cell]. */
    data class Cell(
        val char: Char,
        val foreground: Int,
        val background: Int,
    )

    var width: Int = initialWidth.coerceIn(1, maxWidth)
        private set
    var height: Int = initialHeight.coerceIn(1, maxHeight)
        private set
    var depth: Int = maxDepth
        private set

    private var chars = CharArray(width * height) { ' ' }
    private var fg = IntArray(width * height) { DEFAULT_FOREGROUND }
    private var bg = IntArray(width * height) { DEFAULT_BACKGROUND }
    private var fgPalette = BooleanArray(width * height)
    private var bgPalette = BooleanArray(width * height) { true }

    private val palette = IntArray(240).also { initDefaultPalette(it) }
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun addChangeListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    private fun changed() = listeners.forEach { it() }

    @Synchronized
    fun setResolution(w: Int, h: Int): Boolean {
        val nw = w.coerceIn(1, maxWidth)
        val nh = h.coerceIn(1, maxHeight)
        if (nw == width && nh == height) return false
        val nc = CharArray(nw * nh) { ' ' }
        val nfg = IntArray(nw * nh) { DEFAULT_FOREGROUND }
        val nbg = IntArray(nw * nh) { DEFAULT_BACKGROUND }
        val nfgp = BooleanArray(nw * nh)
        val nbgp = BooleanArray(nw * nh) { true }
        val cw = minOf(width, nw)
        val ch = minOf(height, nh)
        for (y in 0 until ch) for (x in 0 until cw) {
            val from = y * width + x
            val to = y * nw + x
            nc[to] = chars[from]; nfg[to] = fg[from]; nbg[to] = bg[from]
            nfgp[to] = fgPalette[from]; nbgp[to] = bgPalette[from]
        }
        width = nw; height = nh
        chars = nc; fg = nfg; bg = nbg; fgPalette = nfgp; bgPalette = nbgp
        changed()
        return true
    }

    @Synchronized
    fun cell(x: Int, y: Int): Cell? {
        if (x < 1 || y < 1 || x > width || y > height) return null
        val i = (y - 1) * width + (x - 1)
        return Cell(chars[i], resolveColor(fg[i], fgPalette[i]), resolveColor(bg[i], bgPalette[i]))
    }

    /** Raw row text (row is 1-based), for fast renderers/tests. */
    @Synchronized
    fun rowText(y: Int): String {
        if (y < 1 || y > height) return ""
        return String(chars, (y - 1) * width, width)
    }

    @Synchronized
    fun set(x: Int, y: Int, text: String, vertical: Boolean, fgColor: Int, fgIsPalette: Boolean, bgColor: Int, bgIsPalette: Boolean): Boolean {
        if (x < 1 || y < 1 || x > width || y > height || text.isEmpty()) return false
        var written = false
        text.forEachIndexed { i, ch ->
            val cx = if (vertical) x else x + i
            val cy = if (vertical) y + i else y
            if (cx in 1..width && cy in 1..height) {
                val idx = (cy - 1) * width + (cx - 1)
                chars[idx] = ch
                fg[idx] = fgColor; fgPalette[idx] = fgIsPalette
                bg[idx] = bgColor; bgPalette[idx] = bgIsPalette
                written = true
            }
        }
        if (written) changed()
        return written
    }

    @Synchronized
    fun fill(x: Int, y: Int, w: Int, h: Int, ch: Char, fgColor: Int, fgIsPalette: Boolean, bgColor: Int, bgIsPalette: Boolean): Boolean {
        if (w <= 0 || h <= 0) return false
        var written = false
        for (cy in y until y + h) for (cx in x until x + w) {
            if (cx in 1..width && cy in 1..height) {
                val i = (cy - 1) * width + (cx - 1)
                chars[i] = ch
                fg[i] = fgColor; fgPalette[i] = fgIsPalette
                bg[i] = bgColor; bgPalette[i] = bgIsPalette
                written = true
            }
        }
        if (written) changed()
        return written
    }

    @Synchronized
    fun copy(x: Int, y: Int, w: Int, h: Int, dx: Int, dy: Int): Boolean {
        if (w <= 0 || h <= 0) return false
        val snapshotChars = chars.copyOf()
        val snapshotFg = fg.copyOf()
        val snapshotBg = bg.copyOf()
        val snapshotFgP = fgPalette.copyOf()
        val snapshotBgP = bgPalette.copyOf()
        var written = false
        for (sy in 0 until h) for (sx in 0 until w) {
            val sx1 = x + sx; val sy1 = y + sy
            val dx1 = x + sx + dx; val dy1 = y + sy + dy
            if (sx1 in 1..width && sy1 in 1..height && dx1 in 1..width && dy1 in 1..height) {
                val from = (sy1 - 1) * width + (sx1 - 1)
                val to = (dy1 - 1) * width + (dx1 - 1)
                chars[to] = snapshotChars[from]
                fg[to] = snapshotFg[from]; bg[to] = snapshotBg[from]
                fgPalette[to] = snapshotFgP[from]; bgPalette[to] = snapshotBgP[from]
                written = true
            }
        }
        if (written) changed()
        return written
    }

    @Synchronized
    fun getPaletteColor(index: Int): Int =
        if (index in palette.indices) palette[index] else throw ComponentException("bad palette index")

    @Synchronized
    fun setPaletteColor(index: Int, rgb: Int): Int {
        if (index !in palette.indices) throw ComponentException("bad palette index")
        val old = palette[index]
        palette[index] = rgb and 0xFFFFFF
        changed()
        return old
    }

    @Synchronized
    fun resolveColor(value: Int, isPalette: Boolean): Int =
        if (isPalette) palette[value.coerceIn(0, palette.size - 1)] else value and 0xFFFFFF

    companion object {
        const val DEFAULT_FOREGROUND = 0xFFFFFF
        const val DEFAULT_BACKGROUND = 0x000000

        /**
         * Approximation of OC's tier-3 palette: the first 16 entries are the
         * standard console colors, the remaining 224 a grayscale ramp.
         * API-compatible (`getPaletteColor`/`setPaletteColor`); exact shades
         * differ slightly from upstream.
         */
        private fun initDefaultPalette(palette: IntArray) {
            val base16 = intArrayOf(
                0xFFFFFF, 0x99CC33, 0xCC66CC, 0x6699FF,
                0xFFFF33, 0x33CC33, 0xFF6699, 0x333333,
                0xCCCCCC, 0x336699, 0x9933CC, 0x333399,
                0x663300, 0x336600, 0xFF3333, 0x000000,
            )
            base16.copyInto(palette)
            for (i in 16 until palette.size) {
                val v = 0x0F + ((i - 16).toDouble() / (palette.size - 17) * 0xE1).toInt()
                palette[i] = (v shl 16) or (v shl 8) or v
            }
        }
    }
}
