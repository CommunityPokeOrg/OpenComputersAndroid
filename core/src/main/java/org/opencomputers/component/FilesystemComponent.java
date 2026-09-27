package org.opencomputers.component;

import org.opencomputers.api.AbstractComponent;
import org.opencomputers.api.Callback;
import org.opencomputers.api.ComponentException;
import org.opencomputers.component.fs.MemFileSystem;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OC filesystem component over an in-memory {@link MemFileSystem}.
 * Byte payloads cross the Lua boundary as strings (Lua strings are byte-safe).
 */
public class FilesystemComponent extends AbstractComponent {
    private final MemFileSystem fs;

    public FilesystemComponent(String label, long capacityBytes, boolean readOnly) {
        super("filesystem");
        this.fs = new MemFileSystem(label, capacityBytes, readOnly);
    }

    /** Default 1 MiB writable volume. */
    public FilesystemComponent() {
        this("rootfs", 1024 * 1024, false);
    }

    public MemFileSystem fs() {
        return fs;
    }

    @Callback(doc = "function():number -- The currently used capacity of the file system, in bytes.")
    public Object[] spaceUsed(Object[] args) {
        return new Object[]{(double) fs.spaceUsed()};
    }

    @Callback(doc = "function():number -- The overall capacity of the file system, in bytes.")
    public Object[] spaceTotal(Object[] args) {
        return new Object[]{(double) fs.capacity()};
    }

    @Callback(doc = "function():boolean -- Whether the file system is read-only.")
    public Object[] isReadOnly(Object[] args) {
        return new Object[]{fs.readOnly()};
    }

    @Callback(doc = "function():string -- The label of the file system.")
    public Object[] getLabel(Object[] args) {
        return new Object[]{fs.label()};
    }

    @Callback(doc = "function(label:string) -- Sets the label of the file system.")
    public Object[] setLabel(Object[] args) {
        throw new ComponentException("filesystem is read-only");
    }

    @Callback(doc = "function(path:string):table -- Table of entries in the directory; directories end in '/'.")
    public Object[] list(Object[] args) {
        List<String> entries = fs.list(arg(args, 0, "string"));
        Map<Integer, Object> table = new LinkedHashMap<>();
        int i = 1;
        for (String e : entries) {
            table.put(i++, e);
        }
        return new Object[]{table};
    }

    @Callback(doc = "function(path:string):boolean -- Whether the path exists.")
    public Object[] exists(Object[] args) {
        return new Object[]{fs.exists(arg(args, 0, "string"))};
    }

    @Callback(doc = "function(path:string):boolean -- Whether the path is a directory.")
    public Object[] isDirectory(Object[] args) {
        return new Object[]{fs.isDirectory(arg(args, 0, "string"))};
    }

    @Callback(doc = "function(path:string):number -- Size of the file in bytes; 0 for directories.")
    public Object[] size(Object[] args) {
        return new Object[]{(double) fs.size(arg(args, 0, "string"))};
    }

    @Callback(doc = "function(path:string):number -- Unix timestamp of last modification.")
    public Object[] lastModified(Object[] args) {
        return new Object[]{(double) fs.lastModified()};
    }

    @Callback(doc = "function(path:string):boolean -- Create a directory; true on success.")
    public Object[] makeDirectory(Object[] args) {
        return new Object[]{fs.makeDirectory(arg(args, 0, "string"))};
    }

    @Callback(doc = "function(path:string):boolean -- Remove a file or empty directory.")
    public Object[] remove(Object[] args) {
        return new Object[]{fs.remove(arg(args, 0, "string"))};
    }

    @Callback(doc = "function(from:string, to:string) -- Rename/move a file.")
    public Object[] rename(Object[] args) {
        fs.rename(arg(args, 0, "string"), arg(args, 1, "string"));
        return new Object[]{};
    }

    @Callback(doc = "function(path:string[, mode:string='r']):string -- Open a file; returns a handle or nil.")
    public Object[] open(Object[] args) {
        String mode = args.length > 1 && args[1] != null ? args[1].toString() : "r";
        String handle = fs.open(arg(args, 0, "string"), mode);
        return new Object[]{handle};
    }

    @Callback(doc = "function(handle:string, n:number):string or nil -- Read up to n bytes; nil at EOF.")
    public Object[] read(Object[] args) {
        byte[] data = fs.read(arg(args, 0, "string"), intArg(args, 1, "number"));
        if (data == null) {
            return new Object[]{null};
        }
        return new Object[]{new String(data, StandardCharsets.ISO_8859_1)};
    }

    @Callback(doc = "function(handle:string, value:string) -- Write bytes to the file.")
    public Object[] write(Object[] args) {
        String value = arg(args, 1, "string");
        fs.write(arg(args, 0, "string"), value.getBytes(StandardCharsets.ISO_8859_1));
        return new Object[]{true};
    }

    @Callback(doc = "function(handle:string, whence:string='cur', offset:number=0):number -- Seek; returns new position.")
    public Object[] seek(Object[] args) {
        String whence = args.length > 1 && args[1] != null ? args[1].toString() : "cur";
        int offset = args.length > 2 && args[2] != null ? intArg(args, 2, "number") : 0;
        return new Object[]{(double) fs.seek(arg(args, 0, "string"), whence, offset)};
    }

    @Callback(doc = "function(handle:string) -- Close the file handle.")
    public Object[] close(Object[] args) {
        fs.close(arg(args, 0, "string"));
        return new Object[]{};
    }
}
