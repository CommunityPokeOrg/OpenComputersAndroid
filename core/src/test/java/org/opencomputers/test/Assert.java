package org.opencomputers.test;

import java.util.Objects;

/** Minimal assertion helpers for the dependency-free harness. */
public final class Assert {
    private Assert() {
    }

    public static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            fail("expected true: " + msg);
        }
    }

    public static void assertTrue(boolean cond) {
        assertTrue(cond, "");
    }

    public static void assertFalse(boolean cond, String msg) {
        assertTrue(!cond, "expected false: " + msg);
    }

    public static void assertFalse(boolean cond) {
        assertFalse(cond, "");
    }

    public static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            fail("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void assertEquals(double expected, double actual, double eps) {
        if (Math.abs(expected - actual) > eps) {
            fail("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void assertNotNull(Object o) {
        if (o == null) {
            fail("expected non-null value");
        }
    }

    public static void assertNull(Object o) {
        if (o != null) {
            fail("expected null but was <" + o + ">");
        }
    }

    public static void fail(String msg) {
        throw new AssertionError(msg);
    }

    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    public static <T extends Throwable> T assertThrows(Class<T> type, ThrowingRunnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
            throw new AssertionError("expected " + type.getSimpleName() + " but threw " + t);
        }
        throw new AssertionError("expected " + type.getSimpleName() + " but nothing was thrown");
    }
}
