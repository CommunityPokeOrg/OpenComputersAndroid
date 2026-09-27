package org.communitypoke.opencomputers.core.api

/** How far a component's address propagates on the component network. */
enum class Visibility { None, Neighbors, Network }

/**
 * A piece of emulated hardware attached to a machine, equivalent to a
 * ManagedEnvironment/driver pair in OpenComputers. Methods annotated with
 * [Callback] form the Lua-facing API of the component.
 */
interface Component {
    /** UUID-style address, e.g. `"3b4b1f4a-...."`, stable per component instance. */
    val address: String

    /** Component type name used in `component.list`, e.g. `"gpu"`, `"screen"`. */
    val type: String

    val visibility: Visibility
        get() = Visibility.Network

    /** Called once per machine tick (~50 ms) while the machine runs. */
    fun update() {}

    /** Releases resources when the machine stops. */
    fun close() {}
}
