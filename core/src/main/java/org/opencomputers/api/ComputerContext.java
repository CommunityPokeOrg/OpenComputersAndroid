package org.opencomputers.api;

/**
 * The services a {@link Computer} exposes to its components: posting signals to
 * the machine's signal queue and reaching sibling components on the bus.
 */
public interface ComputerContext {
    /**
     * Enqueue a signal for the Lua machine, e.g. {@code pushSignal("key_down",
     * keyboardAddress, charCode, keyCode)}. Follows OC's 256-signal queue cap;
     * returns false when the queue is full and the signal was dropped.
     */
    boolean pushSignal(String name, Object... args);

    /** Look up a sibling component by address, or null. */
    Component component(String address);

    /** All attached components as (address, type) entries, in attach order. */
    java.util.List<java.util.Map.Entry<String, String>> components();
}
