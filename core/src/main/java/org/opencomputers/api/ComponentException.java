package org.opencomputers.api;

/**
 * Error thrown by component {@link Callback} methods. The message is surfaced to
 * Lua as the second return value of a pcall'd invoke, mirroring OC behavior
 * where callbacks fail with a descriptive reason string.
 */
public class ComponentException extends RuntimeException {
    public ComponentException(String message) {
        super(message);
    }
}
