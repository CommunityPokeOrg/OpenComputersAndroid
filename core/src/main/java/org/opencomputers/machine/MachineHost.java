package org.opencomputers.machine;

import org.opencomputers.api.ComponentException;
import org.opencomputers.api.Signal;

import java.util.List;
import java.util.Map;

/**
 * Services the {@link Machine} needs from its owning computer: the component
 * bus surface area used by the Lua {@code component} library, the signal queue
 * used by {@code computer.pullSignal}/{@code pushSignal}, and machine info.
 */
public interface MachineHost {
    /** Pop the next signal, or null. */
    Signal pollSignal();

    /** Enqueue a signal from inside the machine (computer.pushSignal). */
    boolean pushSignal(String name, Object... args);

    /** Invoke a component callback; throws ComponentException on failure. */
    Object[] invokeComponent(String address, String method, Object[] args) throws ComponentException;

    /** (address, type) pairs for component.list. */
    List<Map.Entry<String, String>> componentEntries();

    /** Component type for an address, or null. */
    String componentType(String address);

    /** Method name → doc signature for a component. */
    Map<String, String> componentMethods(String address);

    /** Uptime in seconds since the computer started. */
    double uptime();

    /** This computer's own component-like address. */
    String address();

    /** Current / max energy for computer.energy/maxEnergy. */
    double energy();

    double maxEnergy();

    /** Approximate RAM stats in bytes for computer.freeMemory/totalMemory. */
    long freeMemory();

    long totalMemory();

    /** Ask the host to power the machine off (computer.shutdown). */
    void requestShutdown(boolean reboot);
}
