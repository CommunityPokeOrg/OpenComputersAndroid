package org.opencomputers.component;

import org.opencomputers.api.AbstractComponent;
import org.opencomputers.api.Callback;
import org.opencomputers.api.Component;
import org.opencomputers.api.ComponentException;

/**
 * OC graphics card: renders into a bound {@link ScreenComponent}'s
 * {@link TextBuffer}. Tier controls max resolution and color depth
 * (t1: 50x16 mono, t2: 80x25 16-color, t3: 160x50 256-color).
 *
 * Coordinates are 1-based like the OC API. All drawing callbacks return
 * {@code true} on change, matching the mod's boolean contracts where defined.
 */
public class GpuComponent extends AbstractComponent {
    private final int tier;
    private ScreenComponent screen;
    private String boundAddress;

    public GpuComponent(int tier) {
        super("gpu");
        this.tier = Math.max(1, Math.min(3, tier));
    }

    public int tier() {
        return tier;
    }

    /** Direct access for the app's renderer; null until bound. */
    public TextBuffer boundBuffer() {
        return screen == null ? null : screen.buffer();
    }

    public ScreenComponent boundScreen() {
        return screen;
    }

    private TextBuffer buffer() {
        if (screen == null) {
            throw new ComponentException("no screen bound");
        }
        return screen.buffer();
    }

    @Callback(doc = "function(address:string[, reset:boolean=true]):boolean[, string] -- Binds the GPU to a screen. Returns the bound screen's address, or nil + reason.")
    public Object[] bind(Object[] args) {
        String address = arg(args, 0, "string");
        Component target = context == null ? null : context.component(address);
        if (!(target instanceof ScreenComponent s)) {
            return new Object[]{null, "not a screen"};
        }
        boolean reset = args.length < 2 || boolArg(args, 1);
        screen = s;
        boundAddress = s.address();
        if (reset) {
            s.buffer().setSize(Math.min(s.buffer().maxWidth(), 80), Math.min(s.buffer().maxHeight(), 25));
            s.buffer().fill(0, 0, s.buffer().width(), s.buffer().height(), ' ');
        }
        return new Object[]{s.address()};
    }

    @Callback(doc = "function():string, boolean -- Get the address of the screen the GPU is bound to.")
    public Object[] getScreen(Object[] args) {
        return new Object[]{boundAddress};
    }

    @Callback(doc = "function():number, boolean -- Get the current background color and whether it's a palette index.")
    public Object[] getBackground(Object[] args) {
        TextBuffer b = buffer();
        return new Object[]{(double) TextBuffer.packedValue(b.background()), TextBuffer.isPaletteIndex(b.background())};
    }

    @Callback(doc = "function(value:number[, palette:boolean]):number -- Set the background color. Returns the previous color.")
    public Object[] setBackground(Object[] args) {
        TextBuffer b = buffer();
        int prev = TextBuffer.packedValue(b.background());
        b.setBackground(intArg(args, 0, "number"), args.length > 1 && boolArg(args, 1));
        return new Object[]{(double) prev};
    }

    @Callback(doc = "function():number, boolean -- Get the current foreground color and whether it's a palette index.")
    public Object[] getForeground(Object[] args) {
        TextBuffer b = buffer();
        return new Object[]{(double) TextBuffer.packedValue(b.foreground()), TextBuffer.isPaletteIndex(b.foreground())};
    }

    @Callback(doc = "function(value:number[, palette:boolean]):number -- Set the foreground color. Returns the previous color.")
    public Object[] setForeground(Object[] args) {
        TextBuffer b = buffer();
        int prev = TextBuffer.packedValue(b.foreground());
        b.setForeground(intArg(args, 0, "number"), args.length > 1 && boolArg(args, 1));
        return new Object[]{(double) prev};
    }

    @Callback(doc = "function(index:number):number -- Get the palette color at the given index.")
    public Object[] getPaletteColor(Object[] args) {
        return new Object[]{(double) buffer().paletteColor(intArg(args, 0, "number"))};
    }

    @Callback(doc = "function(index:number, color:number):number -- Set the palette color at the given index. Returns the previous color.")
    public Object[] setPaletteColor(Object[] args) {
        TextBuffer b = buffer();
        int idx = intArg(args, 0, "number");
        int prev = b.paletteColor(idx);
        b.setPaletteColor(idx, intArg(args, 1, "number"));
        return new Object[]{(double) prev};
    }

    @Callback(doc = "function():number, number -- Get the maximum resolution supported by the GPU and bound screen.")
    public Object[] maxResolution(Object[] args) {
        TextBuffer b = buffer();
        return new Object[]{(double) b.maxWidth(), (double) b.maxHeight()};
    }

    @Callback(doc = "function():number, number -- Get the current resolution.")
    public Object[] getResolution(Object[] args) {
        TextBuffer b = buffer();
        return new Object[]{(double) b.width(), (double) b.height()};
    }

    @Callback(doc = "function(width:number, height:number):boolean -- Set the resolution. Returns true if the resolution changed.")
    public Object[] setResolution(Object[] args) {
        TextBuffer b = buffer();
        int w = intArg(args, 0, "number"), h = intArg(args, 1, "number");
        if (w <= 0 || h <= 0 || w > b.maxWidth() || h > b.maxHeight()) {
            return new Object[]{null, "unsupported resolution"};
        }
        boolean changed = w != b.width() || h != b.height();
        b.setSize(w, h);
        return new Object[]{changed};
    }

    @Callback(doc = "function():number, number -- Get the current viewport resolution.")
    public Object[] getViewport(Object[] args) {
        return getResolution(args);
    }

    @Callback(doc = "function(width:number, height:number):boolean -- Set the viewport. Returns true if it changed.")
    public Object[] setViewport(Object[] args) {
        return setResolution(args);
    }

    @Callback(doc = "function():number -- Get the current color depth in bits.")
    public Object[] getDepth(Object[] args) {
        return new Object[]{(double) buffer().depth()};
    }

    @Callback(doc = "function(depth:number):boolean -- Set the color depth (1, 4, or 8 bit; tier 3 required for 8).")
    public Object[] setDepth(Object[] args) {
        TextBuffer b = buffer();
        boolean ok = b.setDepth(intArg(args, 0, "number"));
        if (!ok) {
            return new Object[]{null, "unsupported depth"};
        }
        return new Object[]{true};
    }

    @Callback(doc = "function(x:number, y:number):string, number, number, number, number -- Get the char and colors at the given position.")
    public Object[] get(Object[] args) {
        TextBuffer b = buffer();
        Object[] cell = b.get(intArg(args, 0, "number") - 1, intArg(args, 1, "number") - 1);
        if (cell == null) {
            return new Object[]{null, "index out of bounds"};
        }
        // char, fg, bg, fgIsPalette, bgIsPalette
        return new Object[]{cell[0], cell[1], cell[2]};
    }

    @Callback(doc = "function(x:number, y:number, value:string[, vertical:boolean]):boolean -- Write a string at the given position.")
    public Object[] set(Object[] args) {
        TextBuffer b = buffer();
        int x = intArg(args, 0, "number") - 1;
        int y = intArg(args, 1, "number") - 1;
        String s = arg(args, 2, "string");
        boolean vertical = args.length > 3 && boolArg(args, 3);
        b.set(x, y, s, vertical);
        return new Object[]{true};
    }

    @Callback(doc = "function(x:number, y:number, width:number, height:number, tx:number, ty:number):boolean -- Copy a region.")
    public Object[] copy(Object[] args) {
        buffer().copy(
                intArg(args, 0, "number") - 1, intArg(args, 1, "number") - 1,
                intArg(args, 2, "number"), intArg(args, 3, "number"),
                intArg(args, 4, "number"), intArg(args, 5, "number"));
        return new Object[]{true};
    }

    @Callback(doc = "function(x:number, y:number, width:number, height:number, char:string):boolean -- Fill a region with a character.")
    public Object[] fill(Object[] args) {
        String s = arg(args, 4, "string");
        if (s.isEmpty()) {
            throw new ComponentException("invalid fill character");
        }
        buffer().fill(
                intArg(args, 0, "number") - 1, intArg(args, 1, "number") - 1,
                intArg(args, 2, "number"), intArg(args, 3, "number"),
                s.charAt(0));
        return new Object[]{true};
    }

    @Callback(doc = "function():number -- Free VRAM, in bytes (emulated as proportional to buffer size).")
    public Object[] freeMemory(Object[] args) {
        TextBuffer b = buffer();
        return new Object[]{(double) (totalVram() - b.width() * b.height() * cellBytes())};
    }

    @Callback(doc = "function():number -- Total VRAM, in bytes.")
    public Object[] totalMemory(Object[] args) {
        return new Object[]{(double) totalVram()};
    }

    private int totalVram() {
        TextBuffer b = buffer();
        // OC approximates VRAM by tier; use max cells x bytes-per-cell.
        return b.maxWidth() * b.maxHeight() * cellBytes();
    }

    private int cellBytes() {
        return 5; // char + fg + bg + flags, matching OC's accounting scale
    }
}
