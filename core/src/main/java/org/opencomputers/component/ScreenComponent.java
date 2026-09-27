package org.opencomputers.component;

import org.opencomputers.api.AbstractComponent;
import org.opencomputers.api.Callback;
import org.opencomputers.api.ComponentException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OC screen block: a tiered display that a GPU binds to. Holds the text buffer
 * the GPU renders into and reports power/aspect state. Keyboards attached to
 * the same machine are reported via {@code getKeyboards()}.
 */
public class ScreenComponent extends AbstractComponent {
    private final int tier;
    private final TextBuffer buffer;
    private boolean on = true;
    private boolean precise;

    public ScreenComponent(int tier) {
        super("screen");
        this.tier = Math.max(1, Math.min(3, tier));
        int[] maxRes = maxResolutionFor(this.tier);
        this.buffer = new TextBuffer(maxRes[0], maxRes[1], this.tier == 3 ? 8 : this.tier == 2 ? 4 : 1);
        this.precise = this.tier == 3;
        // OC screens boot at a lower-than-max default resolution.
        this.buffer.setSize(Math.min(maxRes[0], 80), Math.min(maxRes[1], 25));
    }

    private static int[] maxResolutionFor(int tier) {
        return switch (tier) {
            case 1 -> new int[]{50, 16};
            case 2 -> new int[]{80, 25};
            default -> new int[]{160, 50};
        };
    }

    public int tier() {
        return tier;
    }

    public TextBuffer buffer() {
        return buffer;
    }

    @Callback(doc = "function():boolean -- Returns whether the screen is currently on.")
    public Object[] isOn(Object[] args) {
        return new Object[]{on};
    }

    @Callback(doc = "function():boolean -- Turns the screen on. Returns true if it was off.")
    public Object[] turnOn(Object[] args) {
        boolean wasOff = !on;
        on = true;
        return new Object[]{wasOff};
    }

    @Callback(doc = "function():boolean -- Turns the screen off. Returns true if it was on.")
    public Object[] turnOff(Object[] args) {
        boolean wasOn = on;
        on = false;
        return new Object[]{wasOn};
    }

    @Callback(doc = "function():number, number -- The aspect ratio of the screen. For multi-block screens this is the number of blocks, horizontal and vertical.")
    public Object[] getAspectRatio(Object[] args) {
        return new Object[]{1.0, 1.0};
    }

    @Callback(doc = "function():table -- The list of keyboards attached to the screen.")
    public Object[] getKeyboards(Object[] args) {
        Map<Integer, Object> table = new LinkedHashMap<>();
        if (context != null) {
            int i = 1;
            for (Map.Entry<String, String> e : context.components()) {
                if (e.getValue().equals("keyboard")) {
                    table.put(i++, e.getKey());
                }
            }
        }
        return new Object[]{table};
    }

    @Callback(doc = "function():boolean -- Returns whether the screen is in high precision mode (sub-pixel mouse events).")
    public Object[] isPrecise(Object[] args) {
        return new Object[]{precise};
    }

    @Callback(doc = "function(enabled:boolean):boolean -- Set whether to use high precision mode. Requires a tier 3 screen.")
    public Object[] setPrecise(Object[] args) {
        if (boolArg(args, 0) && tier < 3) {
            throw new ComponentException("unsupported precision mode");
        }
        boolean old = precise;
        precise = boolArg(args, 0);
        return new Object[]{old};
    }
}
