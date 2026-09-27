package org.communitypoke.opencomputers.core.api

/**
 * Marks a component method as callable from machine code (Lua).
 *
 * Mirrors `li.cil.oc.api.machine.Callback` from the OpenComputers mod: only
 * functions carrying this annotation are visible through `component.invoke`,
 * `component.methods` and `component.proxy`.
 *
 * @property direct whether the call is safe to run outside the machine's
 *   synchronized execution context. Informational for now: every call currently
 *   runs on the machine thread, so the flag only documents intent and feeds
 *   `component.methods`.
 * @property limit maximum allowed invocations per machine tick. Not enforced
 *   yet; reserved for call-budget modeling.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Callback(
    val name: String = "",
    val direct: Boolean = false,
    val limit: Int = Int.MAX_VALUE,
)
