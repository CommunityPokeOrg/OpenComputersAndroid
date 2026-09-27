package org.opencomputers.test;

import org.opencomputers.api.AbstractComponent;
import org.opencomputers.api.Callback;
import org.opencomputers.api.Component;
import org.opencomputers.api.ComponentBus;
import org.opencomputers.api.ComponentException;
import org.opencomputers.api.ComputerContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.opencomputers.test.Assert.assertEquals;
import static org.opencomputers.test.Assert.assertNotNull;
import static org.opencomputers.test.Assert.assertThrows;
import static org.opencomputers.test.Assert.assertTrue;

/** ComponentBus: reflective callback scan, invoke, add/remove signals. */
public class ComponentBusTest {

    private static final class FakeContext implements ComputerContext {
        final List<String> signals = new ArrayList<>();
        ComponentBus bus;

        @Override
        public boolean pushSignal(String name, Object... args) {
            signals.add(name);
            return true;
        }

        @Override
        public Component component(String address) {
            return bus == null ? null : bus.get(address);
        }

        @Override
        public List<Map.Entry<String, String>> components() {
            return List.of();
        }
    }

    private static final class EchoComponent extends AbstractComponent {
        EchoComponent() {
            super("echo");
        }

        @Callback(doc = "function(s:string):string")
        public Object[] echo(Object[] args) {
            return new Object[]{arg(args, 0, "string")};
        }

        @Callback(doc = "function(a:number, b:number):number")
        public Object[] add(Object[] args) {
            return new Object[]{intArg(args, 0, "number") + intArg(args, 1, "number")};
        }
    }

    public void testInvokeCallback() {
        ComponentBus bus = new ComponentBus(new FakeContext());
        EchoComponent echo = new EchoComponent();
        bus.add(echo);
        Object[] out = bus.invoke(echo.address(), "echo", new Object[]{"hi"});
        assertEquals("hi", out[0]);
    }

    public void testNumericArgs() {
        ComponentBus bus = new ComponentBus(new FakeContext());
        EchoComponent echo = new EchoComponent();
        bus.add(echo);
        assertEquals(7, bus.invoke(echo.address(), "add", new Object[]{3.0, 4.0})[0]);
    }

    public void testUnknownComponent() {
        ComponentBus bus = new ComponentBus(new FakeContext());
        ComponentException e = assertThrows(ComponentException.class,
                () -> bus.invoke("deadbeef", "x", new Object[0]));
        assertEquals("no such component", e.getMessage());
    }

    public void testUnknownMethod() {
        ComponentBus bus = new ComponentBus(new FakeContext());
        EchoComponent echo = new EchoComponent();
        bus.add(echo);
        assertThrows(ComponentException.class,
                () -> bus.invoke(echo.address(), "nope", new Object[0]));
    }

    public void testMethodsAndDocs() {
        ComponentBus bus = new ComponentBus(new FakeContext());
        EchoComponent echo = new EchoComponent();
        bus.add(echo);
        Map<String, String> methods = bus.methods(echo.address());
        assertTrue(methods.containsKey("echo"));
        assertTrue(methods.get("echo").contains("s:string"));
    }

    public void testAddRemoveSignals() {
        FakeContext ctx = new FakeContext();
        ComponentBus bus = new ComponentBus(ctx);
        EchoComponent echo = new EchoComponent();
        bus.add(echo);
        assertTrue(ctx.signals.contains("component_added"));
        bus.remove(echo.address());
        assertTrue(ctx.signals.contains("component_removed"));
        assertEquals(null, bus.get(echo.address()));
    }

    public void testEntries() {
        ComponentBus bus = new ComponentBus(new FakeContext());
        EchoComponent echo = new EchoComponent();
        bus.add(echo);
        assertEquals(1, bus.entries().size());
        assertEquals("echo", bus.entries().get(0).getValue());
        assertNotNull(bus.firstOfType("echo"));
        assertEquals(null, bus.firstOfType("gpu"));
    }

    /**
     * Regression for the v0.1.0 launch crash: the Android shell attaches the
     * whole production component set up front, and the reflective scan must
     * succeed (and produce invocable callbacks) for every one of them.
     */
    public void testProductionComponentsScan() {
        FakeContext ctx = new FakeContext();
        ComponentBus bus = new ComponentBus(ctx);
        ctx.bus = bus;
        Component[] rig = {
                new org.opencomputers.component.ScreenComponent(3),
                new org.opencomputers.component.GpuComponent(3),
                new org.opencomputers.component.KeyboardComponent(),
                new org.opencomputers.component.FilesystemComponent("rootfs", 1 << 20, false),
                new org.opencomputers.component.RedstoneComponent(true),
                new org.opencomputers.component.SpeakerComponent(),
                new org.opencomputers.component.DataComponent(3),
                new org.opencomputers.component.EepromComponent()
        };
        for (Component c : rig) {
            bus.add(c); // throws if the reflective scan fails for this component
            assertTrue(bus.methods(c.address()) != null,
                    "scan failed for " + c.type());
        }
        // Everything except the keyboard (a pure signal source) exposes callbacks.
        for (Component c : rig) {
            if ("keyboard".equals(c.type())) {
                continue;
            }
            assertTrue(!bus.methods(c.address()).isEmpty(),
                    "no callbacks scanned for " + c.type());
        }
        // Spot-check that scanned callbacks are actually invocable.
        assertNotNull(bus.invoke(rig[0].address(), "isOn", new Object[0]));
        bus.invoke(rig[1].address(), "bind", new Object[]{rig[0].address()});
        assertNotNull(bus.invoke(rig[1].address(), "maxResolution", new Object[0]));
        assertNotNull(bus.invoke(rig[3].address(), "spaceTotal", new Object[0]));
        assertNotNull(bus.invoke(rig[7].address(), "getLabel", new Object[0]));
    }
}
