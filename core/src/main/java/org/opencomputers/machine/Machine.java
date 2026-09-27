package org.opencomputers.machine;

/**
 * A sandboxed execution architecture — the analogue of OC's Machine
 * (Lua 5.2/5.3 architecture running on a VM with a call budget).
 *
 * The machine is cooperative: {@link #tick()} runs the architecture for one
 * game tick (50ms), which may resume a suspended Lua thread, honor a pending
 * {@code computer.pullSignal}, or run queued work. All component calls happen
 * synchronously inside the tick.
 */
public interface Machine {
    /** Load boot code (from the EEPROM component) and begin execution. */
    void start();

    /** Run one tick of machine work. Called on the computer's tick thread. */
    void tick();

    /** Stop execution and release the VM. */
    void stop();

    /** Whether the architecture is executing. */
    boolean isRunning();

    /** If the machine crashed, the error message; else null. */
    String lastError();
}
