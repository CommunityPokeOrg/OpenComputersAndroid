package org.opencomputers.api;

/**
 * A hardware component attached to a {@link org.opencomputers.Computer}'s
 * component bus — the analogue of an OpenComputers expansion card or block.
 *
 * Implementations get a stable address (hex string) and a type name that the
 * {@code component} Lua library can enumerate. Lifecycle hooks let a component
 * react to (dis)connection, e.g. a GPU binding to its screen.
 */
public interface Component {
    /** Stable unique address, as in OC (e.g. "9f4c2d8e-..."). */
    String address();

    /** Component type name used by {@code component.list(type)}. */
    String type();

    /** Called when the component is connected to a computer's bus. */
    default void onAttach(ComputerContext context) {
    }

    /** Called when the component is removed from the bus or the computer stops. */
    default void onDetach(ComputerContext context) {
    }
}
