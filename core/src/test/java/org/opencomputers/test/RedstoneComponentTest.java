package org.opencomputers.test;

import org.opencomputers.api.Component;
import org.opencomputers.api.ComputerContext;
import org.opencomputers.component.RedstoneComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.opencomputers.test.Assert.assertEquals;
import static org.opencomputers.test.Assert.assertThrows;
import static org.opencomputers.test.Assert.assertTrue;

/** RedstoneComponent: sided IO + change signals. */
public class RedstoneComponentTest {

    private static final class Ctx implements ComputerContext {
        final List<String> signals = new ArrayList<>();

        @Override
        public boolean pushSignal(String name, Object... args) {
            signals.add(name + ":" + args[1]);
            return true;
        }

        @Override
        public Component component(String address) {
            return null;
        }

        @Override
        public List<Map.Entry<String, String>> components() {
            return List.of();
        }
    }

    public void testOutputSetGet() {
        RedstoneComponent r = new RedstoneComponent(false);
        r.setOutput(new Object[]{0.0, 15.0});
        assertEquals(15.0, r.getOutput(new Object[]{0.0})[0]);
        assertEquals(0.0, r.getOutput(new Object[]{3.0})[0]);
    }

    public void testInputSignal() {
        Ctx ctx = new Ctx();
        RedstoneComponent r = new RedstoneComponent(false);
        r.onAttach(ctx);
        r.setExternalInput(2, 7);
        r.setExternalInput(2, 7); // no change → no signal
        assertEquals(7.0, r.getInput(new Object[]{2.0})[0]);
        assertEquals(1, ctx.signals.size());
        assertTrue(ctx.signals.get(0).startsWith("redstone_changed"));
    }

    public void testInvalidSide() {
        RedstoneComponent r = new RedstoneComponent(false);
        assertThrows(RuntimeException.class, () -> r.getInput(new Object[]{6.0}));
    }

    public void testWirelessFlag() {
        assertEquals(true, new RedstoneComponent(true).isWireless(new Object[0])[0]);
        assertEquals(false, new RedstoneComponent(false).isWireless(new Object[0])[0]);
    }
}
