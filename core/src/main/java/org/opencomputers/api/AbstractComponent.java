package org.opencomputers.api;

import java.util.UUID;

/**
 * Base class for components: assigns an OC-style UUID address and stores the
 * computer context set on attach.
 */
public abstract class AbstractComponent implements Component {
    private final String address;
    private final String type;
    protected ComputerContext context;

    protected AbstractComponent(String type) {
        this(type, UUID.randomUUID().toString());
    }

    protected AbstractComponent(String type, String address) {
        this.type = type;
        this.address = address;
    }

    @Override
    public String address() {
        return address;
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public void onAttach(ComputerContext context) {
        this.context = context;
    }

    @Override
    public void onDetach(ComputerContext context) {
        this.context = null;
    }

    protected static String arg(Object[] args, int index, String name) {
        if (index >= args.length || args[index] == null) {
            throw new ComponentException("bad argument #" + (index + 1) + " (" + name + " expected, got no value)");
        }
        return args[index].toString();
    }

    protected static int intArg(Object[] args, int index, String name) {
        Object v = index < args.length ? args[index] : null;
        if (v instanceof Number n) {
            return n.intValue();
        }
        throw new ComponentException("bad argument #" + (index + 1) + " (" + name + " expected, got no value)");
    }

    protected static double doubleArg(Object[] args, int index, String name) {
        Object v = index < args.length ? args[index] : null;
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        throw new ComponentException("bad argument #" + (index + 1) + " (" + name + " expected, got no value)");
    }

    protected static boolean boolArg(Object[] args, int index) {
        Object v = index < args.length ? args[index] : null;
        return Boolean.TRUE.equals(v);
    }
}
