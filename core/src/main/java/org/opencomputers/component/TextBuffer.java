package org.opencomputers.component;

import org.opencomputers.api.ComponentException;

import java.util.Arrays;

/**
 * Character cell buffer backing a bound screen/GPU pair, mirroring OC's
 * {@code TextBuffer}. Each cell stores a UTF-16 char plus foreground and
 * background colors packed as {@code (paletteFlag << 24) | value} — palette
 * entries resolve through the buffer's palette table at read time, so
 * {@code gpu.setPaletteColor} re-colors already-written cells like real OC.
 */
public final class TextBuffer {
    private static final int PALETTE_FLAG = 0x01000000;

    // OC default palette (indexes 0-15); indexes 16-255 are computed greyscale.
    private static final int[] DEFAULT_PALETTE = {
            0xFFFFFF, 0xFFCC33, 0xCC66CC, 0x6699FF, 0xFFFF33, 0x33CC33, 0xFF6699, 0x333333,
            0xCCCCCC, 0x336699, 0x9933CC, 0x333399, 0x663300, 0x336600, 0xFF3333, 0x000000,
    };

    private final int maxWidth;
    private final int maxHeight;
    private final int maxDepth;
    private int width;
    private int height;
    private int depth;
    private char[] chars;
    private int[] fg;
    private int[] bg;
    private int curFg;
    private int curBg;
    private final int[] palette = new int[256];

    public TextBuffer(int maxWidth, int maxHeight, int maxDepth) {
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.maxDepth = maxDepth;
        resetPalette();
        this.depth = maxDepth;
        this.curFg = pack(0xFFFFFF, false);
        this.curBg = pack(0x000000, false);
        setSize(maxWidth, maxHeight);
    }

    private void resetPalette() {
        System.arraycopy(DEFAULT_PALETTE, 0, palette, 0, DEFAULT_PALETTE.length);
        for (int i = 16; i < 256; i++) {
            // OC fills the upper range with a computed greyscale ramp.
            palette[i] = shadeFor(i);
        }
    }

    private static int shadeFor(int index) {
        // OC's 240-entry ramp: piecewise shades from 0x0F0F0F up to 0xFFFFFF.
        double t = (index - 16) / 239.0;
        int v = (int) Math.round(15 + t * 240);
        return (v << 16) | (v << 8) | v;
    }

    private static int pack(int value, boolean paletteIdx) {
        return (paletteIdx ? PALETTE_FLAG : 0) | (value & 0xFFFFFF);
    }

    public static boolean isPaletteIndex(int packed) {
        return (packed & PALETTE_FLAG) != 0;
    }

    public static int packedValue(int packed) {
        return packed & 0xFFFFFF;
    }

    public int resolveColor(int packed) {
        if (isPaletteIndex(packed)) {
            return palette[packedValue(packed) & 0xFF];
        }
        return packedValue(packed);
    }

    public int maxWidth() {
        return maxWidth;
    }

    public int maxHeight() {
        return maxHeight;
    }

    public int maxDepth() {
        return maxDepth;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int depth() {
        return depth;
    }

    /** True if the resize was applied (clamped to max like OC does). */
    public boolean setSize(int w, int h) {
        int nw = Math.min(w, maxWidth);
        int nh = Math.min(h, maxHeight);
        if (nw <= 0 || nh <= 0) {
            return false;
        }
        if (nw == width && nh == height && chars != null) {
            return true;
        }
        char[] nchars = new char[nw * nh];
        int[] nfg = new int[nw * nh];
        int[] nbg = new int[nw * nh];
        Arrays.fill(nchars, ' ');
        Arrays.fill(nfg, curFg);
        Arrays.fill(nbg, curBg);
        if (chars != null) {
            int copyW = Math.min(width, nw);
            int copyH = Math.min(height, nh);
            for (int y = 0; y < copyH; y++) {
                System.arraycopy(chars, y * width, nchars, y * nw, copyW);
                System.arraycopy(fg, y * width, nfg, y * nw, copyW);
                System.arraycopy(bg, y * width, nbg, y * nw, copyW);
            }
        }
        width = nw;
        height = nh;
        chars = nchars;
        fg = nfg;
        bg = nbg;
        return true;
    }

    public boolean setDepth(int d) {
        if (d != 1 && d != 4 && d != 8) {
            throw new ComponentException("unsupported depth");
        }
        if (d > maxDepth) {
            return false;
        }
        depth = d;
        return true;
    }

    public void setForeground(int value, boolean paletteIdx) {
        curFg = pack(value, paletteIdx);
    }

    public void setBackground(int value, boolean paletteIdx) {
        curBg = pack(value, paletteIdx);
    }

    public int foreground() {
        return curFg;
    }

    public int background() {
        return curBg;
    }

    public int paletteColor(int index) {
        checkPalette(index);
        return palette[index];
    }

    public void setPaletteColor(int index, int color) {
        checkPalette(index);
        palette[index] = color & 0xFFFFFF;
    }

    private static void checkPalette(int index) {
        if (index < 0 || index > 255) {
            throw new ComponentException("invalid palette index");
        }
    }

    private int cell(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return -1;
        }
        return y * width + x;
    }

    /** Sets cells along +x (or +y when vertical). Returns chars actually written. */
    public int set(int x, int y, String s, boolean vertical) {
        if (vertical) {
            if (x < 0 || x >= width || y >= height) {
                return 0;
            }
            int n = 0;
            for (int i = 0; i < s.length() && y + i < height; i++) {
                if (y + i < 0) {
                    continue;
                }
                int c = cell(x, y + i);
                chars[c] = s.charAt(i);
                fg[c] = curFg;
                bg[c] = curBg;
                n++;
            }
            return n;
        }
        if (y < 0 || y >= height || x >= width) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < s.length() && x + i < width; i++) {
            if (x + i < 0) {
                continue;
            }
            int c = cell(x + i, y);
            chars[c] = s.charAt(i);
            fg[c] = curFg;
            bg[c] = curBg;
            n++;
        }
        return n;
    }

    public void fill(int x, int y, int w, int h, char ch) {
        int x0 = Math.max(x, 0), y0 = Math.max(y, 0);
        int x1 = Math.min(x + w, width), y1 = Math.min(y + h, height);
        for (int yy = y0; yy < y1; yy++) {
            int row = yy * width;
            for (int xx = x0; xx < x1; xx++) {
                int c = row + xx;
                chars[c] = ch;
                fg[c] = curFg;
                bg[c] = curBg;
            }
        }
    }

    public void copy(int x, int y, int w, int h, int tx, int ty) {
        int x0 = Math.max(x, 0), y0 = Math.max(y, 0);
        int x1 = Math.min(x + w, width), y1 = Math.min(y + h, height);
        int cw = x1 - x0, ch = y1 - y0;
        if (cw <= 0 || ch <= 0) {
            return;
        }
        char[] tmpChars = new char[cw * ch];
        int[] tmpFg = new int[cw * ch];
        int[] tmpBg = new int[cw * ch];
        for (int yy = 0; yy < ch; yy++) {
            int src = (y0 + yy) * width + x0;
            System.arraycopy(chars, src, tmpChars, yy * cw, cw);
            System.arraycopy(fg, src, tmpFg, yy * cw, cw);
            System.arraycopy(bg, src, tmpBg, yy * cw, cw);
        }
        for (int yy = 0; yy < ch; yy++) {
            int dy = y0 + ty + yy;
            if (dy < 0 || dy >= height) {
                continue;
            }
            for (int xx = 0; xx < cw; xx++) {
                int dx = x0 + tx + xx;
                if (dx < 0 || dx >= width) {
                    continue;
                }
                int c = dy * width + dx;
                chars[c] = tmpChars[yy * cw + xx];
                fg[c] = tmpFg[yy * cw + xx];
                bg[c] = tmpBg[yy * cw + xx];
            }
        }
    }

    /** OC gpu.get: {char, foreground, background}. Null off-screen. */
    public Object[] get(int x, int y) {
        int c = cell(x, y);
        if (c < 0) {
            return null;
        }
        return new Object[]{
                String.valueOf(chars[c]),
                (double) packedValue(fg[c]),
                (double) packedValue(bg[c]),
                isPaletteIndex(fg[c]),
                isPaletteIndex(bg[c]),
        };
    }

    /** Raw packed cell for renderers. */
    public char rawChar(int x, int y) {
        int c = cell(x, y);
        return c < 0 ? ' ' : chars[c];
    }

    public int rawFg(int x, int y) {
        int c = cell(x, y);
        return c < 0 ? 0xFFFFFF : resolveColor(fg[c]);
    }

    public int rawBg(int x, int y) {
        int c = cell(x, y);
        return c < 0 ? 0x000000 : resolveColor(bg[c]);
    }

    /** Whole buffer as text lines, for tests/debugging. */
    public String dump() {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                sb.append(rawChar(x, y));
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
