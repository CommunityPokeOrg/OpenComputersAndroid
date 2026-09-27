package com.communitypoke.ocandroid;

import org.opencomputers.Computer;
import org.opencomputers.bios.BootResources;
import org.opencomputers.component.DataComponent;
import org.opencomputers.component.EepromComponent;
import org.opencomputers.component.FilesystemComponent;
import org.opencomputers.component.GpuComponent;
import org.opencomputers.component.KeyboardComponent;
import org.opencomputers.component.RedstoneComponent;
import org.opencomputers.component.ScreenComponent;
import org.opencomputers.component.SpeakerComponent;
import org.opencomputers.component.TextBuffer;

/**
 * Host-side lifecycle for one emulated computer: assembles a tier-3-ish rig,
 * runs the tick loop on a background thread at ~20 Hz, and drains speaker
 * beep events into audio output.
 */
public final class EmulatorRuntime {
    /** OC ticks at 20/second. */
    private static final long TICK_MS = 50;

    private final Computer computer;
    private final ScreenComponent screen;
    private final GpuComponent gpu;
    private final KeyboardComponent keyboard;
    private final FilesystemComponent filesystem;
    private final RedstoneComponent redstone;
    private final SpeakerComponent speaker;
    private final BeepPlayer beepPlayer = new BeepPlayer();

    private Thread tickThread;
    private volatile boolean ticking;

    public interface StateListener {
        void onStateChanged(Computer.State state, String error);
    }

    private volatile StateListener stateListener;

    public EmulatorRuntime() {
        computer = new Computer(384 * 1024, 500);
        screen = new ScreenComponent(3);
        gpu = new GpuComponent(3);
        keyboard = new KeyboardComponent();
        filesystem = new FilesystemComponent("rootfs", 1 << 20, false);
        redstone = new RedstoneComponent(true);
        speaker = new SpeakerComponent();

        filesystem.fs().writeFile("/init.lua", BootResources.initLua());

        computer.addComponent(screen);
        computer.addComponent(keyboard);
        computer.addComponent(gpu);
        computer.addComponent(filesystem);
        computer.addComponent(redstone);
        computer.addComponent(speaker);
        computer.addComponent(new DataComponent(3));
        computer.addComponent(new EepromComponent(BootResources.eepromBios()));

        computer.setListener(c -> {
            StateListener l = stateListener;
            if (l != null) {
                l.onStateChanged(c.state(), c.machine().lastError());
            }
        });
    }

    public Computer computer() {
        return computer;
    }

    public ScreenComponent screen() {
        return screen;
    }

    public GpuComponent gpu() {
        return gpu;
    }

    public KeyboardComponent keyboard() {
        return keyboard;
    }

    public RedstoneComponent redstone() {
        return redstone;
    }

    public void setStateListener(StateListener l) {
        stateListener = l;
    }

    /** Power on and start the tick loop. */
    public synchronized void powerOn() {
        if (ticking) {
            return;
        }
        computer.start();
        ticking = true;
        tickThread = new Thread(this::tickLoop, "oc-tick");
        tickThread.setDaemon(true);
        tickThread.start();
        notifyState();
    }

    /** Power off: stops ticking and the machine. */
    public synchronized void powerOff() {
        ticking = false;
        if (tickThread != null) {
            try {
                tickThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            tickThread = null;
        }
        computer.stop();
        notifyState();
    }

    /** Hard reset: power off then on, keeping component wiring. */
    public synchronized void reset() {
        powerOff();
        powerOn();
    }

    private void tickLoop() {
        while (ticking) {
            computer.tick();
            // Creative power: keep the buffer topped up; energy modelling is
            // informational only in v1 (see README limitations).
            computer.setEnergy(computer.maxEnergy());
            for (SpeakerComponent.Beep b : speaker.drainBeeps()) {
                beepPlayer.play(b.frequency(), b.durationTicks() * TICK_MS);
            }
            try {
                Thread.sleep(TICK_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void notifyState() {
        StateListener l = stateListener;
        if (l != null) {
            l.onStateChanged(computer.state(), computer.machine().lastError());
        }
    }

    /** The currently bound text buffer, or null if the GPU isn't bound yet. */
    public TextBuffer textBuffer() {
        return gpu.boundBuffer();
    }
}
