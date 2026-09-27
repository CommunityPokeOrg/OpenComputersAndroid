package org.opencomputers.test;

import org.opencomputers.component.TextBuffer;

import static org.opencomputers.test.Assert.assertEquals;
import static org.opencomputers.test.Assert.assertTrue;

/** TextBuffer: resolution, drawing ops, palette resolution. */
public class TextBufferTest {

    public void testSetAndGet() {
        TextBuffer b = new TextBuffer(160, 50, 8);
        b.setSize(10, 4);
        b.set(1, 1, "hello", false);
        assertEquals("h", b.get(1, 1)[0]);
        assertEquals("o", b.get(5, 1)[0]);
        assertEquals(" ", String.valueOf(b.rawChar(6, 1)));
    }

    public void testVerticalSet() {
        TextBuffer b = new TextBuffer(160, 50, 8);
        b.setSize(10, 10);
        b.set(2, 2, "abc", true);
        assertEquals('a', b.rawChar(2, 2));
        assertEquals('b', b.rawChar(2, 3));
        assertEquals('c', b.rawChar(2, 4));
    }

    public void testFillAndCopy() {
        TextBuffer b = new TextBuffer(160, 50, 8);
        b.setSize(10, 5);
        b.fill(0, 0, 10, 5, '.');
        b.set(0, 0, "X", false);
        b.copy(0, 0, 3, 2, 0, 2);
        assertEquals('X', b.rawChar(0, 2));
        assertEquals('.', b.rawChar(2, 3));
    }

    public void testClampedResize() {
        TextBuffer b = new TextBuffer(50, 16, 1);
        assertTrue(b.setSize(500, 500));
        assertEquals(50, b.width());
        assertEquals(16, b.height());
    }

    public void testResizePreservesContent() {
        TextBuffer b = new TextBuffer(160, 50, 8);
        b.setSize(10, 5);
        b.set(0, 0, "K", false);
        b.setSize(20, 10);
        assertEquals('K', b.rawChar(0, 0));
        assertEquals(20, b.width());
    }

    public void testPaletteResolution() {
        TextBuffer b = new TextBuffer(160, 50, 8);
        b.setForeground(3, true); // palette index 3
        b.set(0, 0, "Z", false);
        int before = b.rawFg(0, 0);
        b.setPaletteColor(3, 0x123456);
        assertTrue(b.rawFg(0, 0) == 0x123456, "palette change should recolor cell");
        assertTrue(before != b.rawFg(0, 0));
    }

    public void testOutOfBoundsGet() {
        TextBuffer b = new TextBuffer(160, 50, 8);
        assertEquals(null, b.get(-1, 0));
        assertEquals(null, b.get(9999, 9999));
    }

    public void testWriteClippedAtEdge() {
        TextBuffer b = new TextBuffer(160, 50, 8);
        b.setSize(5, 3);
        int written = b.set(3, 1, "abcdef", false);
        assertEquals(2, written); // cells 3,4 — rest clipped
        assertEquals('b', b.rawChar(4, 1));
    }
}
