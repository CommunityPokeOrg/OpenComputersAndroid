package org.opencomputers.test;

import org.opencomputers.Computer;
import org.opencomputers.bios.BootResources;
import org.opencomputers.component.EepromComponent;
import org.opencomputers.component.FilesystemComponent;
import org.opencomputers.component.GpuComponent;
import org.opencomputers.component.KeyboardComponent;
import org.opencomputers.component.ScreenComponent;
import org.opencomputers.component.TextBuffer;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.opencomputers.test.Assert.assertEquals;
import static org.opencomputers.test.Assert.assertFalse;
import static org.opencomputers.test.Assert.assertNotNull;
import static org.opencomputers.test.Assert.assertNull;
import static org.opencomputers.test.Assert.assertTrue;

/**
 * End-to-end: assemble a computer, boot the BIOS + init.lua through the real
 * LuaJ machine, drive signals, and check the screen buffer.
 */
public class MachineIntegrationTest {

    private static final class Rig {
        final Computer computer = new Computer(256 * 1024, 500);
        final ScreenComponent screen = new ScreenComponent(3);
        final GpuComponent gpu = new GpuComponent(3);
        final KeyboardComponent keyboard = new KeyboardComponent();
        final FilesystemComponent fs = new FilesystemComponent("rootfs", 1 << 20, false);
        final EepromComponent eeprom = new EepromComponent(BootResources.eepromBios());

        Rig() {
            fs.fs().writeFile("/init.lua", BootResources.initLua());
            computer.addComponent(screen);
            computer.addComponent(gpu);
            computer.addComponent(keyboard);
            computer.addComponent(fs);
            computer.addComponent(eeprom);
        }

        void ticks(int n) {
            for (int i = 0; i < n; i++) {
                computer.tick();
            }
        }
    }

    public void testBootsAndPrintsBanner() {
        Rig rig = new Rig();
        rig.computer.start();
        assertEquals(Computer.State.RUNNING, rig.computer.state());
        rig.ticks(10);
        TextBuffer buf = rig.screen.buffer();
        String line1 = line(buf, 0);
        assertTrue(line1.startsWith("OpenComputersAndroid"),
                "banner expected, got: " + line1);
    }

    public void testKeyEventEcho() {
        Rig rig = new Rig();
        rig.computer.start();
        rig.ticks(5);
        rig.keyboard.pressKey('x', 45);
        rig.ticks(5);
        String whole = rig.screen.buffer().dump();
        assertTrue(whole.contains("key: x (code 45.0)") || whole.contains("key: x (code 45)"),
                "expected key echo on screen, got:\n" + whole);
    }

    public void testPullSignalTimeoutResumes() {
        Computer c = new Computer(256 * 1024, 500);
        ScreenComponent screen = new ScreenComponent(3);
        GpuComponent gpu = new GpuComponent(3);
        EepromComponent eeprom = new EepromComponent((
                "local gpu\n" +
                        "for a in component.list('gpu') do gpu = component.proxy(a) break end\n" +
                        "for a in component.list('screen') do gpu.bind(a) break end\n" +
                        // component_added signals are already queued at boot — drain them
                        "while computer.pullSignal(0) do end\n" +
                        "local s = computer.pullSignal(0.05)\n" +
                        "gpu.set(1, 1, s == nil and 'timeout' or 'got')").getBytes(StandardCharsets.ISO_8859_1));
        c.addComponent(screen);
        c.addComponent(gpu);
        c.addComponent(eeprom);
        c.start();
        for (int i = 0; i < 40; i++) {
            c.tick();
            sleep(10);
        }
        assertTrue(line(screen.buffer(), 0).startsWith("timeout"),
                "expected timeout text, got: " + line(screen.buffer(), 0));
    }

    public void testCustomProgramComponentCalls() {
        Computer c = new Computer(256 * 1024, 500);
        ScreenComponent screen = new ScreenComponent(3);
        GpuComponent gpu = new GpuComponent(3);
        EepromComponent eeprom = new EepromComponent((
                "local gpu\n" +
                        "for a in component.list('gpu') do gpu = component.proxy(a) break end\n" +
                        "for a in component.list('screen') do gpu.bind(a) break end\n" +
                        "gpu.setResolution(20, 5)\n" +
                        "gpu.setForeground(0x00FF00)\n" +
                        "gpu.fill(1,1,20,5,'.') \n" +
                        "gpu.set(2, 2, 'hi')").getBytes(StandardCharsets.ISO_8859_1));
        c.addComponent(screen);
        c.addComponent(gpu);
        c.addComponent(eeprom);
        c.start();
        for (int i = 0; i < 10; i++) {
            c.tick();
        }
        assertEquals(20, screen.buffer().width());
        assertEquals('h', screen.buffer().rawChar(1, 1));
        assertEquals('i', screen.buffer().rawChar(2, 1));
        assertEquals('.', screen.buffer().rawChar(0, 0));
    }

    public void testCrashIsReported() {
        Computer c = new Computer(256 * 1024, 500);
        c.addComponent(new EepromComponent("error('boom')".getBytes(StandardCharsets.ISO_8859_1)));
        c.start();
        for (int i = 0; i < 5; i++) {
            c.tick();
        }
        assertEquals(Computer.State.CRASHED, c.state());
        assertNotNull(c.machine().lastError());
        assertTrue(c.machine().lastError().contains("boom"));
    }

    public void testEmptyEepromFailsCleanly() {
        Computer c = new Computer(256 * 1024, 500);
        c.addComponent(new EepromComponent());
        c.start();
        assertFalse(c.machine().isRunning());
        assertNotNull(c.machine().lastError());
    }

    public void testInstructionBudgetCrash() {
        Computer c = new Computer(256 * 1024, 500);
        c.addComponent(new EepromComponent("while true do end".getBytes(StandardCharsets.ISO_8859_1)));
        c.start();
        for (int i = 0; i < 3; i++) {
            c.tick();
        }
        assertEquals(Computer.State.CRASHED, c.state());
        assertNotNull(c.machine().lastError());
    }

    public void testShutdownRequest() {
        Rig rig = new Rig();
        rig.computer.start();
        rig.ticks(5);
        rig.computer.requestShutdown(false);
        rig.ticks(2);
        assertEquals(Computer.State.STOPPED, rig.computer.state());
    }

    private static String line(TextBuffer buf, int y) {
        StringBuilder sb = new StringBuilder();
        for (int x = 0; x < buf.width(); x++) {
            sb.append(buf.rawChar(x, y));
        }
        return sb.toString().trim();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
