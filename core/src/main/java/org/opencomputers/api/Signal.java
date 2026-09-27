package org.opencomputers.api;

import java.util.Arrays;

/** A queued machine signal: a name plus positional arguments, as in OC. */
public final class Signal {
    private final String name;
    private final Object[] args;

    public Signal(String name, Object... args) {
        this.name = name;
        this.args = args == null ? new Object[0] : args;
    }

    public String name() {
        return name;
    }

    public Object[] args() {
        return args;
    }

    @Override
    public String toString() {
        return name + Arrays.toString(args);
    }
}
