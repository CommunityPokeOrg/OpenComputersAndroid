package org.opencomputers.component;

import org.opencomputers.api.AbstractComponent;
import org.opencomputers.api.Callback;
import org.opencomputers.api.ComponentException;

/**
 * OC redstone card: reads and drives per-side analog redstone levels (0-15).
 * Input levels are fed by the host ({@link #setExternalInput}); output changes
 * push {@code redstone_changed} signals to listening machines — as real OC does
 * when a neighbor's input changes.
 */
public class RedstoneComponent extends AbstractComponent {
    /** OC sides: 0=bottom, 1=top, 2=back, 3=front, 4=right, 5=left. */
    public static final int SIDES = 6;

    private final int[] input = new int[SIDES];
    private final int[] output = new int[SIDES];
    private final boolean wireless;

    public RedstoneComponent(boolean wireless) {
        super("redstone");
        this.wireless = wireless;
    }

    private static int side(Object[] args) {
        int s = AbstractComponent.intArg(args, 0, "number");
        if (s < 0 || s >= SIDES) {
            throw new ComponentException("invalid side");
        }
        return s;
    }

    private static int strength(int v) {
        if (v < 0 || v > 15) {
            throw new ComponentException("invalid strength");
        }
        return v;
    }

    /** Feed an external input level change; emits redstone_changed to the machine. */
    public void setExternalInput(int side, int level) {
        int prev = input[side];
        input[side] = Math.max(0, Math.min(15, level));
        if (context != null && prev != input[side]) {
            context.pushSignal("redstone_changed", address(), (double) side);
        }
    }

    @Callback(doc = "function(side:number):number -- Get the redstone input on the given side.")
    public Object[] getInput(Object[] args) {
        return new Object[]{(double) input[side(args)]};
    }

    @Callback(doc = "function():table -- Get the redstone input of all sides.")
    public Object[] getInputAll(Object[] args) {
        java.util.Map<Integer, Object> t = new java.util.LinkedHashMap<>();
        for (int i = 0; i < SIDES; i++) {
            t.put(i, (double) input[i]);
        }
        return new Object[]{t};
    }

    @Callback(doc = "function(side:number):number -- Get the currently set output on the given side.")
    public Object[] getOutput(Object[] args) {
        return new Object[]{(double) output[side(args)]};
    }

    @Callback(doc = "function(side:number, value:number):number -- Set the output on the given side. Returns the previous value.")
    public Object[] setOutput(Object[] args) {
        int s = side(args);
        int v = strength(intArg(args, 1, "number"));
        int prev = output[s];
        output[s] = v;
        return new Object[]{(double) prev};
    }

    @Callback(doc = "function():number -- The comparator input on the given side.")
    public Object[] getComparatorInput(Object[] args) {
        return new Object[]{0.0};
    }

    @Callback(doc = "function():boolean -- Whether the redstone card can emit/receive wireless signals.")
    public Object[] isWireless(Object[] args) {
        return new Object[]{wireless};
    }

    @Callback(doc = "function(side:number):string -- Get the bundled color input (stub: no bundled support).")
    public Object[] getBundledInput(Object[] args) {
        throw new ComponentException("no bundled redstone support");
    }

    @Callback(doc = "function():number -- The maximum wake threshold tier.")
    public Object[] getWakeThreshold(Object[] args) {
        return new Object[]{15.0};
    }
}
