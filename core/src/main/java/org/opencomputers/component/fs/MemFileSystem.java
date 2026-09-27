package org.opencomputers.component.fs;

import org.opencomputers.api.ComponentException;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * In-memory filesystem mirroring OC's filesystem component semantics: a tree of
 * directories and byte-content files, a space quota, and a set of open handles
 * with position pointers.
 */
public final class MemFileSystem {
    private final String label;
    private final long capacity;
    private final boolean readOnly;
    private final Node root = new Node(true);
    private final Map<String, Handle> handles = new LinkedHashMap<>();
    private int nextHandle = 0;
    private long lastModified = System.currentTimeMillis() / 1000L;

    private static final class Node {
        final boolean dir;
        final Map<String, Node> children;
        final ByteArrayOutputStream data;

        Node(boolean dir) {
            this.dir = dir;
            this.children = dir ? new LinkedHashMap<>() : null;
            this.data = dir ? null : new ByteArrayOutputStream();
        }
    }

    /** Open file handle with a position pointer, as in OC. */
    public static final class Handle {
        final Node node;
        final String mode;
        int position;

        Handle(Node node, String mode) {
            this.node = node;
            this.mode = mode;
        }
    }

    public MemFileSystem(String label, long capacity, boolean readOnly) {
        this.label = label;
        this.capacity = capacity;
        this.readOnly = readOnly;
    }

    public String label() {
        return label;
    }

    public long capacity() {
        return capacity;
    }

    public boolean readOnly() {
        return readOnly;
    }

    public long spaceUsed() {
        return spaceUsed(root);
    }

    private static long spaceUsed(Node n) {
        if (!n.dir) {
            return n.data.size();
        }
        long total = 0;
        for (Node child : n.children.values()) {
            total += spaceUsed(child);
        }
        return total;
    }

    public long lastModified() {
        return lastModified;
    }

    private void touch() {
        lastModified = System.currentTimeMillis() / 1000L;
    }

    private void checkWritable() {
        if (readOnly) {
            throw new ComponentException("filesystem is read-only");
        }
    }

    private static String[] segments(String path) {
        String normalized = path.replace('\\', '/');
        List<String> out = new ArrayList<>();
        for (String seg : normalized.split("/")) {
            if (!seg.isEmpty() && !seg.equals(".")) {
                if (seg.equals("..")) {
                    if (!out.isEmpty()) {
                        out.remove(out.size() - 1);
                    }
                } else {
                    out.add(seg);
                }
            }
        }
        return out.toArray(new String[0]);
    }

    private Node find(String path, boolean mustExist) {
        Node node = root;
        for (String seg : segments(path)) {
            if (!node.dir) {
                if (mustExist) {
                    throw new ComponentException("not a directory");
                }
                return null;
            }
            node = node.children.get(seg);
            if (node == null) {
                if (mustExist) {
                    throw new ComponentException("no such file or directory");
                }
                return null;
            }
        }
        return node;
    }

    private Node parentOf(String path) {
        String[] segs = segments(path);
        if (segs.length == 0) {
            return null;
        }
        Node node = root;
        for (int i = 0; i < segs.length - 1; i++) {
            node = node.children.get(segs[i]);
            if (node == null || !node.dir) {
                return null;
            }
        }
        return node;
    }

    private static String basename(String path) {
        String[] segs = segments(path);
        return segs.length == 0 ? "" : segs[segs.length - 1];
    }

    public boolean exists(String path) {
        return find(path, false) != null;
    }

    public boolean isDirectory(String path) {
        Node n = find(path, false);
        return n != null && n.dir;
    }

    public long size(String path) {
        Node n = find(path, true);
        return n.dir ? 0 : n.data.size();
    }

    /** OC list(): child names, directories with a trailing slash. */
    public List<String> list(String path) {
        Node n = find(path, true);
        if (!n.dir) {
            throw new ComponentException("not a directory");
        }
        List<String> out = new ArrayList<>(new TreeSet<>(n.children.keySet()));
        for (int i = 0; i < out.size(); i++) {
            if (n.children.get(out.get(i)).dir) {
                out.set(i, out.get(i) + "/");
            }
        }
        return out;
    }

    public boolean makeDirectory(String path) {
        checkWritable();
        if (exists(path)) {
            return false;
        }
        Node parent = parentOf(path);
        if (parent == null) {
            throw new ComponentException("no such directory");
        }
        parent.children.put(basename(path), new Node(true));
        touch();
        return true;
    }

    public boolean remove(String path) {
        checkWritable();
        Node parent = parentOf(path);
        if (parent == null) {
            return false;
        }
        boolean removed = parent.children.remove(basename(path)) != null;
        if (removed) {
            touch();
        }
        return removed;
    }

    public void rename(String from, String to) {
        checkWritable();
        Node srcParent = parentOf(from);
        if (srcParent == null) {
            throw new ComponentException("no such file");
        }
        Node node = srcParent.children.remove(basename(from));
        if (node == null) {
            throw new ComponentException("no such file");
        }
        Node dstParent = parentOf(to);
        if (dstParent == null) {
            srcParent.children.put(basename(from), node); // restore
            throw new ComponentException("no such directory");
        }
        dstParent.children.put(basename(to), node);
        touch();
    }

    /**
     * Open a file; mode is "r", "w", "a" (plus optional "b"). Returns a handle
     * id usable with read/write/seek/close, or null when the file is missing.
     */
    public String open(String path, String mode) {
        String m = mode == null ? "r" : mode;
        boolean write = m.startsWith("w") || m.startsWith("a");
        boolean append = m.startsWith("a");
        Node node = find(path, false);
        if (node == null) {
            if (!write) {
                return null;
            }
            checkWritable();
            Node parent = parentOf(path);
            if (parent == null) {
                throw new ComponentException("no such directory");
            }
            node = new Node(false);
            parent.children.put(basename(path), node);
        } else if (node.dir) {
            throw new ComponentException("is a directory");
        } else if (m.startsWith("w")) {
            checkWritable();
            node.data.reset();
        } else {
            checkWritable();
        }
        if (write) {
            checkWritable();
        }
        Handle h = new Handle(node, m);
        h.position = append ? node.data.size() : 0;
        String id = String.valueOf(nextHandle++);
        handles.put(id, h);
        touch();
        return id;
    }

    private Handle handle(String id) {
        Handle h = handles.get(id);
        if (h == null) {
            throw new ComponentException("bad file descriptor");
        }
        return h;
    }

    /** Read up to n bytes at the position; null at EOF, like OC. */
    public byte[] read(String id, int n) {
        Handle h = handle(id);
        byte[] data = h.node.data.toByteArray();
        if (h.position >= data.length) {
            return null;
        }
        int count = Math.min(n, data.length - h.position);
        byte[] out = new byte[count];
        System.arraycopy(data, h.position, out, 0, count);
        h.position += count;
        return out;
    }

    public void write(String id, byte[] value) {
        checkWritable();
        Handle h = handle(id);
        if (!h.mode.startsWith("w") && !h.mode.startsWith("a")) {
            throw new ComponentException("file not opened for writing");
        }
        byte[] existing = h.node.data.toByteArray();
        ByteArrayOutputStream merged = new ByteArrayOutputStream();
        merged.write(existing, 0, Math.min(h.position, existing.length));
        // Zero-fill a seek gap.
        for (int i = Math.min(h.position, existing.length); i < h.position; i++) {
            merged.write(0);
        }
        merged.write(value, 0, value.length);
        int tail = h.position + value.length;
        if (tail < existing.length) {
            merged.write(existing, tail, existing.length - tail);
        }
        h.node.data.reset();
        h.node.data.write(merged.toByteArray(), 0, merged.size());
        h.position += value.length;
        if (spaceUsed() > capacity) {
            h.position -= value.length;
            throw new ComponentException("not enough space");
        }
        touch();
    }

    /** OC seek: whence "set"|"cur"|"end", offset. Returns new position. */
    public int seek(String id, String whence, int offset) {
        Handle h = handle(id);
        int size = h.node.data.size();
        int target = switch (whence == null ? "set" : whence) {
            case "set" -> offset;
            case "cur" -> h.position + offset;
            case "end" -> size + offset;
            default -> throw new ComponentException("invalid whence");
        };
        h.position = Math.max(0, Math.min(target, size));
        return h.position;
    }

    public void close(String id) {
        if (handles.remove(id) == null) {
            throw new ComponentException("bad file descriptor");
        }
    }

    /** Convenience for tooling/tests: write a whole file in one shot. */
    public void writeFile(String path, byte[] content) {
        String id = open(path, "wb");
        if (id == null) {
            throw new ComponentException("cannot open " + path);
        }
        try {
            write(id, content);
        } finally {
            close(id);
        }
    }

    /** Convenience for tooling/tests: read a whole file. */
    public byte[] readFile(String path) {
        String id = open(path, "r");
        if (id == null) {
            return null;
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk;
            while ((chunk = read(id, 8192)) != null) {
                out.write(chunk, 0, chunk.length);
            }
            return out.toByteArray();
        } finally {
            close(id);
        }
    }
}
