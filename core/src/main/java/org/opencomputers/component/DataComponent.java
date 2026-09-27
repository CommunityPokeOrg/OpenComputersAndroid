package org.opencomputers.component;

import org.opencomputers.api.AbstractComponent;
import org.opencomputers.api.Callback;
import org.opencomputers.api.ComponentException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * OC data card (tier 3): pure-compute helpers — hashes, checksums, base64, and
 * zlib deflate/inflate. All data crosses the boundary as ISO-8859-1 strings so
 * bytes round-trip losslessly.
 */
public class DataComponent extends AbstractComponent {
    private final int tier;

    public DataComponent(int tier) {
        super("data");
        this.tier = Math.max(1, Math.min(3, tier));
    }

    private static byte[] bytes(Object[] args, int index) {
        return AbstractComponent.arg(args, index, "string").getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String str(byte[] b) {
        return new String(b, StandardCharsets.ISO_8859_1);
    }

    @Callback(doc = "function(data:string):string -- Computes CRC-32 of the data. Result is binary bytes.")
    public Object[] crc32(Object[] args) {
        CRC32 crc = new CRC32();
        crc.update(bytes(args, 0));
        long v = crc.getValue();
        return new Object[]{str(new byte[]{
                (byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v})};
    }

    @Callback(doc = "function(data:string):string -- Computes MD5 of the data. Result is 16 binary bytes.")
    public Object[] md5(Object[] args) {
        return new Object[]{str(digest("MD5", bytes(args, 0)))};
    }

    @Callback(doc = "function(data:string):string -- Computes SHA-256 of the data. Result is 32 binary bytes.")
    public Object[] sha256(Object[] args) {
        return new Object[]{str(digest("SHA-256", bytes(args, 0)))};
    }

    private static byte[] digest(String algo, byte[] data) {
        try {
            return MessageDigest.getInstance(algo).digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new ComponentException("digest unavailable: " + algo);
        }
    }

    @Callback(doc = "function(data:string):string -- Applies base64 encoding to the data.")
    public Object[] encode64(Object[] args) {
        return new Object[]{Base64.getEncoder().encodeToString(bytes(args, 0))};
    }

    @Callback(doc = "function(data:string):string -- Applies base64 decoding to the data.")
    public Object[] decode64(Object[] args) {
        try {
            return new Object[]{str(Base64.getDecoder().decode(arg(args, 0, "string")))};
        } catch (IllegalArgumentException e) {
            throw new ComponentException("invalid base64 data");
        }
    }

    @Callback(doc = "function(data:string):string -- Applies deflate compression to the data.")
    public Object[] deflate(Object[] args) {
        if (tier < 3) {
            throw new ComponentException("deflate requires a tier 3 data card");
        }
        byte[] input = bytes(args, 0);
        Deflater d = new Deflater();
        d.setInput(input);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[512];
        while (!d.finished()) {
            out.write(buf, 0, d.deflate(buf));
        }
        d.end();
        return new Object[]{str(out.toByteArray())};
    }

    @Callback(doc = "function(data:string):string -- Applies inflate decompression to the data.")
    public Object[] inflate(Object[] args) {
        if (tier < 3) {
            throw new ComponentException("inflate requires a tier 3 data card");
        }
        byte[] input = bytes(args, 0);
        Inflater i = new Inflater();
        i.setInput(input);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[512];
        try {
            while (!i.finished()) {
                int n = i.inflate(buf);
                if (n == 0 && i.needsInput()) {
                    break;
                }
                out.write(buf, 0, n);
            }
        } catch (java.util.zip.DataFormatException e) {
            throw new ComponentException("invalid deflate data");
        } finally {
            i.end();
        }
        return new Object[]{str(out.toByteArray())};
    }

    @Callback(doc = "function():number -- The energy cost of the data card per operation.")
    public Object[] getLimit(Object[] args) {
        return new Object[]{256.0};
    }
}
