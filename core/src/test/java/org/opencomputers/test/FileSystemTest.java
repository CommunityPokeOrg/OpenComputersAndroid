package org.opencomputers.test;

import org.opencomputers.component.fs.MemFileSystem;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.opencomputers.test.Assert.assertEquals;
import static org.opencomputers.test.Assert.assertFalse;
import static org.opencomputers.test.Assert.assertNotNull;
import static org.opencomputers.test.Assert.assertNull;
import static org.opencomputers.test.Assert.assertThrows;
import static org.opencomputers.test.Assert.assertTrue;

/** MemFileSystem + open-handle semantics. */
public class FileSystemTest {

    public void testWriteReadRoundtrip() {
        MemFileSystem fs = new MemFileSystem("rootfs", 1 << 20, false);
        fs.writeFile("/hello.txt", "hello world".getBytes(StandardCharsets.ISO_8859_1));
        byte[] back = fs.readFile("/hello.txt");
        assertNotNull(back);
        assertEquals("hello world", new String(back, StandardCharsets.ISO_8859_1));
    }

    public void testListDirectory() {
        MemFileSystem fs = new MemFileSystem("rootfs", 1 << 20, false);
        fs.makeDirectory("/dir");
        fs.writeFile("/dir/a.txt", new byte[]{65});
        fs.writeFile("/b.txt", new byte[]{66});
        List<String> root = fs.list("/");
        assertTrue(root.contains("dir/"));
        assertTrue(root.contains("b.txt"));
        assertEquals(List.of("a.txt"), fs.list("/dir"));
    }

    public void testRemoveAndRename() {
        MemFileSystem fs = new MemFileSystem("rootfs", 1 << 20, false);
        fs.writeFile("/a", new byte[]{1});
        fs.rename("/a", "/b");
        assertFalse(fs.exists("/a"));
        assertTrue(fs.exists("/b"));
        assertTrue(fs.remove("/b"));
        assertFalse(fs.exists("/b"));
    }

    public void testReadOnly() {
        MemFileSystem fs = new MemFileSystem("rom", 1 << 20, true);
        assertThrows(RuntimeException.class, () -> fs.writeFile("/x", new byte[]{1}));
        assertThrows(RuntimeException.class, () -> fs.makeDirectory("/d"));
    }

    public void testSeek() {
        MemFileSystem fs = new MemFileSystem("rootfs", 1 << 20, false);
        fs.writeFile("/f", "0123456789".getBytes(StandardCharsets.ISO_8859_1));
        String h = fs.open("/f", "r");
        assertNotNull(h);
        fs.seek(h, "set", 4);
        byte[] d = fs.read(h, 2);
        assertEquals("45", new String(d, StandardCharsets.ISO_8859_1));
        fs.seek(h, "end", -1);
        assertEquals("9", new String(fs.read(h, 1), StandardCharsets.ISO_8859_1));
        assertNull(fs.read(h, 1)); // EOF
        fs.close(h);
    }

    public void testCapacityEnforced() {
        MemFileSystem fs = new MemFileSystem("tiny", 8, false);
        assertThrows(RuntimeException.class,
                () -> fs.writeFile("/big", "0123456789abcdef".getBytes(StandardCharsets.ISO_8859_1)));
    }

    public void testEofReturnsNull() {
        MemFileSystem fs = new MemFileSystem("rootfs", 1 << 20, false);
        fs.writeFile("/e", new byte[]{65});
        String h = fs.open("/e", "r");
        fs.read(h, 10);
        assertNull(fs.read(h, 10));
    }
}
