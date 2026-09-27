package org.opencomputers.component;

import org.opencomputers.api.AbstractComponent;
import org.opencomputers.api.Callback;

import java.nio.charset.StandardCharsets;

/**
 * OC EEPROM: a small read-mostly store holding the machine's boot code plus a
 * read-only-ish data area. The machine loads {@link #code} at startup.
 */
public class EepromComponent extends AbstractComponent {
    public static final int CODE_SIZE = 4096;
    public static final int DATA_SIZE = 256;

    private byte[] code = new byte[0];
    private byte[] data = new byte[0];
    private String label = "EEPROM";
    private boolean readOnly;

    public EepromComponent() {
        super("eeprom");
    }

    public EepromComponent(byte[] code) {
        this();
        setCode(code);
    }

    public byte[] code() {
        return code;
    }

    public void setCode(byte[] code) {
        if (code.length > CODE_SIZE) {
            throw new IllegalArgumentException("eeprom code too large: " + code.length);
        }
        this.code = code.clone();
    }

    @Callback(doc = "function():string -- Get the currently stored byte array (the boot code).")
    public Object[] get(Object[] args) {
        return new Object[]{new String(code, StandardCharsets.ISO_8859_1)};
    }

    @Callback(doc = "function(data:string) -- Overwrite the currently stored byte array.")
    public Object[] set(Object[] args) {
        if (readOnly) {
            throw new org.opencomputers.api.ComponentException("eeprom is read-only");
        }
        byte[] b = arg(args, 0, "string").getBytes(StandardCharsets.ISO_8859_1);
        if (b.length > CODE_SIZE) {
            throw new org.opencomputers.api.ComponentException("not enough space");
        }
        code = b;
        return new Object[]{};
    }

    @Callback(doc = "function():string -- Get the label of the EEPROM.")
    public Object[] getLabel(Object[] args) {
        return new Object[]{label};
    }

    @Callback(doc = "function(label:string):string -- Set the label of the EEPROM.")
    public Object[] setLabel(Object[] args) {
        String newLabel = arg(args, 0, "string");
        label = newLabel.length() > 24 ? newLabel.substring(0, 24) : newLabel;
        return new Object[]{label};
    }

    @Callback(doc = "function():number -- Storage size of the EEPROM code area.")
    public Object[] getSize(Object[] args) {
        return new Object[]{(double) CODE_SIZE};
    }

    @Callback(doc = "function():number -- Data storage size of the EEPROM.")
    public Object[] getDataSize(Object[] args) {
        return new Object[]{(double) DATA_SIZE};
    }

    @Callback(doc = "function():string -- Get the stored data area.")
    public Object[] getData(Object[] args) {
        return new Object[]{new String(data, StandardCharsets.ISO_8859_1)};
    }

    @Callback(doc = "function(data:string) -- Store the data area.")
    public Object[] setData(Object[] args) {
        byte[] b = arg(args, 0, "string").getBytes(StandardCharsets.ISO_8859_1);
        if (b.length > DATA_SIZE) {
            throw new org.opencomputers.api.ComponentException("not enough space");
        }
        data = b;
        return new Object[]{};
    }

    @Callback(doc = "function():boolean -- Whether the EEPROM is read-only.")
    public Object[] isReadOnly(Object[] args) {
        return new Object[]{readOnly};
    }

    @Callback(doc = "function(readonly:boolean):boolean -- Makes the EEPROM read-only if true. This cannot be undone in-session.")
    public Object[] makeReadonly(Object[] args) {
        readOnly = readOnly || boolArg(args, 0);
        return new Object[]{readOnly};
    }

    @Callback(doc = "function():string -- Checksum of the code area (for OC parity; computed as a simple hash).")
    public Object[] getChecksum(Object[] args) {
        int h = 0;
        for (byte b : code) {
            h = 31 * h + b;
        }
        return new Object[]{String.format("%08x", h)};
    }
}
