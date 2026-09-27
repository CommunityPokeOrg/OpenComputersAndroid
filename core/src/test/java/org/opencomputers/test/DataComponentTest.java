package org.opencomputers.test;

import org.opencomputers.component.DataComponent;

import java.nio.charset.StandardCharsets;

import static org.opencomputers.test.Assert.assertEquals;
import static org.opencomputers.test.Assert.assertThrows;

/** DataComponent: hash/base64/deflate round-trips. */
public class DataComponentTest {

    private static Object first(Object[] out) {
        return out[0];
    }

    public void testSha256() {
        DataComponent data = new DataComponent(3);
        Object[] out = data.sha256(new Object[]{"abc"});
        byte[] hash = first(out).toString().getBytes(StandardCharsets.ISO_8859_1);
        assertEquals(32, hash.length);
        // Known SHA-256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                hex.toString());
    }

    public void testMd5() {
        DataComponent data = new DataComponent(3);
        byte[] hash = first(data.md5(new Object[]{"abc"}))
                .toString().getBytes(StandardCharsets.ISO_8859_1);
        assertEquals(16, hash.length);
    }

    public void testBase64Roundtrip() {
        DataComponent data = new DataComponent(3);
        String encoded = (String) first(data.encode64(new Object[]{"hello"}));
        assertEquals("aGVsbG8=", encoded);
        String decoded = (String) first(data.decode64(new Object[]{encoded}));
        assertEquals("hello", decoded);
    }

    public void testDeflateInflateRoundtrip() {
        DataComponent data = new DataComponent(3);
        String input = "the quick brown fox jumps over the lazy dog the quick brown fox";
        String packed = (String) first(data.deflate(new Object[]{input}));
        String unpacked = (String) first(data.inflate(new Object[]{packed}));
        assertEquals(input, unpacked);
    }

    public void testDeflateRequiresTier3() {
        DataComponent data = new DataComponent(1);
        assertThrows(RuntimeException.class, () -> data.deflate(new Object[]{"x"}));
    }
}
