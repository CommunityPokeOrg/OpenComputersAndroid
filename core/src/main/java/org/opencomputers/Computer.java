package org.opencomputers;

import org.opencomputers.api.Component;
import org.opencomputers.api.ComponentBus;
import org.opencomputers.api.ComponentException;
import org.opencomputers.api.ComputerContext;
import org.opencomputers.api.Signal;
import org.opencomputers.machine.LuaJMachine;
import org.opencomputers.machine.Machine;
import org.opencomputers.machine.MachineHost;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

/**
 * An OpenComputers computer case: owns the component bus, the signal queue, the
 * power buffer, and the machine architecture. The host drives it by calling
 * {@link #tick()} on a fixed cadence (OC uses 20 ticks/second).
 *
 * Synchronization: components and the UI may be touched from other threads;
 * {@link #pushSignal} is thread-safe. All component invocations happen on the
 * tick thread inside the machine.
 */
public class Computer implements ComputerContext, MachineHost {
    /** OC's signal queue bound; excess signals are dropped silently. */
    public static final int SIGNAL_QUEUE_SIZE = 256;

    public enum State {
        OFF, RUNNING, STOPPED, CRASHED
    }

    private final String address = UUID.randomUUID().toString();
    private final ComponentBus bus = new ComponentBus(this);
    private final Machine machine;
    private final Queue<Signal> signals = new ArrayDeque<>(SIGNAL_QUEUE_SIZE);
    private final long totalMemory;
    private final double maxEnergy;

    private volatile State state = State.OFF;
    private volatile double energy;
    private volatile long startedAt;
    private long tickCount;
    private boolean rebootRequested;
    private volatile boolean shutdownRequested;

    public interface Listener {
        /** Machine stopped or crashed; check machine().lastError(). */
        void onStopped(Computer computer);
    }

    private volatile Listener listener;

    /**
     * @param memoryBytes  RAM size in bytes (e.g. OC tier sizes are 192k/256k/384k... reported via computer.totalMemory)
     * @param energyBuffer energy buffer size; the host recharges via {@link #setEnergy}
     */
    public Computer(long memoryBytes, double energyBuffer) {
        this.totalMemory = memoryBytes;
        this.maxEnergy = energyBuffer;
        this.energy = energyBuffer;
        this.machine = new LuaJMachine(this);
    }

    public ComponentBus bus() {
        return bus;
    }

    public Machine machine() {
        return machine;
    }

    public State state() {
        return state;
    }

    public String address() {
        return address;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Attach a component before or while running (hot-plug, signals fire). */
    public void addComponent(Component c) {
        bus.add(c);
    }

    /** Power on: starts the machine (loads EEPROM code). */
    public synchronized void start() {
        if (state == State.RUNNING) {
            return;
        }
        state = State.RUNNING;
        startedAt = System.currentTimeMillis();
        machine.start();
        if (!machine.isRunning()) {
            state = machine.lastError() != null ? State.CRASHED : State.STOPPED;
        }
    }

    /** Power off: detaches nothing, stops the machine. */
    public synchronized void stop() {
        machine.stop();
        state = State.STOPPED;
    }

    /**
     * Run one machine tick (call at 20 Hz). Pumps the signal queue deadline
     * logic inside the machine and enforces the instruction budget.
     */
    public void tick() {
        if (state != State.RUNNING) {
            return;
        }
        tickCount++;
        machine.tick();
        if (!machine.isRunning()) {
            boolean crashed = machine.lastError() != null;
            state = crashed ? State.CRASHED : State.STOPPED;
            Listener l = listener;
            if (l != null) {
                l.onStopped(this);
            }
        }
        if (rebootRequested) {
            rebootRequested = false;
            stop();
            start();
        } else if (shutdownRequested) {
            shutdownRequested = false;
            stop();
        }
    }

    public long tickCount() {
        return tickCount;
    }

    // ---- ComputerContext ----

    @Override
    public boolean pushSignal(String name, Object... args) {
        synchronized (signals) {
            if (signals.size() >= SIGNAL_QUEUE_SIZE) {
                return false;
            }
            return signals.add(new Signal(name, args));
        }
    }

    @Override
    public Component component(String address) {
        return bus.get(address);
    }

    @Override
    public List<Map.Entry<String, String>> components() {
        return bus.entries();
    }

    // ---- MachineHost ----

    @Override
    public Signal pollSignal() {
        synchronized (signals) {
            return signals.poll();
        }
    }

    @Override
    public Object[] invokeComponent(String address, String method, Object[] args)
            throws ComponentException {
        return bus.invoke(address, method, args);
    }

    @Override
    public List<Map.Entry<String, String>> componentEntries() {
        return bus.entries();
    }

    @Override
    public String componentType(String address) {
        Component c = bus.get(address);
        return c == null ? null : c.type();
    }

    @Override
    public Map<String, String> componentMethods(String address) {
        return bus.methods(address);
    }

    @Override
    public double uptime() {
        if (state == State.OFF) {
            return 0;
        }
        return (System.currentTimeMillis() - startedAt) / 1000.0;
    }

    @Override
    public double energy() {
        return energy;
    }

    @Override
    public double maxEnergy() {
        return maxEnergy;
    }

    /** Host-driven recharge/drain. */
    public void setEnergy(double energy) {
        this.energy = Math.max(0, Math.min(maxEnergy, energy));
    }

    @Override
    public long freeMemory() {
        return Math.max(0, totalMemory - usedMemoryEstimate());
    }

    @Override
    public long totalMemory() {
        return totalMemory;
    }

    private long usedMemoryEstimate() {
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        return Math.min(used, totalMemory);
    }

    @Override
    public void requestShutdown(boolean reboot) {
        // Deferred to the next tick so in-flight Lua calls unwind cleanly
        // before the VM is torn down.
        if (reboot) {
            rebootRequested = true;
        } else {
            shutdownRequested = true;
        }
    }
}
