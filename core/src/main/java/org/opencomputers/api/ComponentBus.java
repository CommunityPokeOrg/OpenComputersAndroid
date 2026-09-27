package org.opencomputers.api;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry connecting a computer to its components. On attach it reflectively
 * scans {@link Callback}-annotated methods, so {@code component.methods(addr)}
 * and {@code component.invoke(addr, name, ...)} work with no per-component glue.
 *
 * Attach/detach push {@code component_added}/{@code component_removed} signals,
 * matching OC semantics for hot-plugged hardware.
 */
public final class ComponentBus {
    private final ComputerContext context;
    private final Map<String, Component> components = new LinkedHashMap<>();
    private final Map<String, Map<String, Method>> callbackCache = new LinkedHashMap<>();

    public ComponentBus(ComputerContext context) {
        this.context = context;
    }

    public void add(Component component) {
        String address = component.address();
        components.put(address, component);
        callbackCache.put(address, scan(component));
        component.onAttach(context);
        context.pushSignal("component_added", address, component.type());
    }

    public void remove(String address) {
        Component component = components.remove(address);
        if (component == null) {
            return;
        }
        callbackCache.remove(address);
        component.onDetach(context);
        context.pushSignal("component_removed", address, component.type());
    }

    public Component get(String address) {
        return components.get(address);
    }

    /** First component of the given type, or null — backs {@code component.list} iteration. */
    public Component firstOfType(String type) {
        for (Component c : components.values()) {
            if (c.type().equals(type)) {
                return c;
            }
        }
        return null;
    }

    /** Ordered (address, type) pairs — backs {@code component.list()}. */
    public List<Map.Entry<String, String>> entries() {
        List<Map.Entry<String, String>> out = new ArrayList<>(components.size());
        for (Component c : components.values()) {
            out.add(new java.util.AbstractMap.SimpleEntry<>(c.address(), c.type()));
        }
        return Collections.unmodifiableList(out);
    }

    /** Callback names and their OC-style doc signatures for one component. */
    public Map<String, String> methods(String address) {
        Map<String, Method> table = callbackCache.get(address);
        if (table == null) {
            return Collections.emptyMap();
        }
        Map<String, String> docs = new LinkedHashMap<>();
        for (Map.Entry<String, Method> e : table.entrySet()) {
            docs.put(e.getKey(), e.getValue().getAnnotation(Callback.class).doc());
        }
        return docs;
    }

    /**
     * Invoke a callback. {@code args} are machine-converted values. Returns the
     * raw result (void → empty array) for the machine layer to coerce to Lua.
     */
    public Object[] invoke(String address, String method, Object[] args) {
        Component component = components.get(address);
        if (component == null) {
            throw new ComponentException("no such component");
        }
        Map<String, Method> table = callbackCache.get(address);
        Method m = table == null ? null : table.get(method);
        if (m == null) {
            throw new ComponentException("no such method (" + method + ")");
        }
        try {
            Object result = m.invoke(component, new Object[]{args});
            if (result == null) {
                return new Object[0];
            }
            if (result instanceof Object[] arr) {
                return arr;
            }
            return new Object[]{result};
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ComponentException ce) {
                throw ce;
            }
            throw new ComponentException(cause == null ? "internal error" : cause.toString());
        } catch (IllegalAccessException e) {
            throw new ComponentException("inaccessible method");
        }
    }

    public void detachAll() {
        for (Component c : new ArrayList<>(components.values())) {
            remove(c.address());
        }
    }

    private static Map<String, Method> scan(Component component) {
        Map<String, Method> table = new LinkedHashMap<>();
        for (Method m : component.getClass().getMethods()) {
            Callback cb = m.getAnnotation(Callback.class);
            if (cb == null) {
                continue;
            }
            if (m.getParameterCount() != 1 || m.getParameterTypes()[0] != Object[].class) {
                throw new IllegalStateException(
                        "@Callback method " + m.getName() + " on " + component.type()
                                + " must take exactly one Object[] parameter");
            }
            // Method.trySetAccessible() doesn't exist on Android's ART runtime;
            // setAccessible is equivalent for the public callbacks we expose.
            try {
                m.setAccessible(true);
            } catch (SecurityException ignored) {
                // Public methods on public classes are invocable regardless.
            }
            table.put(m.getName(), m);
        }
        return table;
    }
}
