package org.communitypoke.opencomputers.core.machine

/** A queued message delivered to running machine code via `computer.pullSignal`. */
data class Signal(val name: String, val args: List<Any?>)
