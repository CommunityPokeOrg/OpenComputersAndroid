package org.communitypoke.opencomputers.core.machine

/** Thrown from machine code to request a clean shutdown (`computer.shutdown`). */
class MachineShutdownException(val reboot: Boolean = false) : RuntimeException("machine shutdown requested")

/** A machine was asked to run but nothing bootable was found. */
class MachineHaltedException(message: String) : RuntimeException(message)

/** Components that emit signals implement this to receive their machine handle. */
interface MachineAware {
    fun attach(machine: Machine)
}

/**
 * An execution architecture, i.e. the language VM the machine boots into.
 * In OpenComputers terms this is the `Architecture` API (Lua 5.2/5.3, etc.).
 *
 * Lifecycle: [initialize] then [run] on the machine thread; [close] on teardown.
 */
interface Architecture {
    /** Set up the VM and locate boot code. Return false if there is nothing to boot. */
    fun initialize(): Boolean

    /**
     * Execute machine code. Blocking; returns on `computer.shutdown` or when
     * the machine thread is interrupted. Throw to signal a crash.
     */
    fun run()

    fun close() {}
}
