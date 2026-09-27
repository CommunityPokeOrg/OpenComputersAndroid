package org.communitypoke.opencomputers.core

import org.communitypoke.opencomputers.core.component.FileSystemComponent
import org.communitypoke.opencomputers.core.component.KeyboardComponent
import org.communitypoke.opencomputers.core.component.ScreenComponent
import org.communitypoke.opencomputers.core.machine.LuaArchitecture
import org.communitypoke.opencomputers.core.machine.Machine
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MachineIntegrationTest {

    @TempDir
    lateinit var tmpDir: File

    private fun screenText(screen: ScreenComponent): String =
        (1..screen.buffer.height).joinToString("\n") { screen.buffer.rowText(it) }

    @Test
    fun `demo machine boots, prints banner, echoes key input`() {
        val machine = Machines.demo(tmpDir)
        machine.start()
        try {
            val screen = machine.components.byType("screen").first() as ScreenComponent
            val deadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < deadline) {
                if (screenText(screen).contains("OpenComputersAndroid")) break
                if (machine.state == Machine.State.ERROR) break
                Thread.sleep(50)
            }
            assertEquals(Machine.State.RUNNING, machine.state)
            assertTrue(screenText(screen).contains("OpenComputersAndroid"),
                "boot banner should be visible on the emulated screen")

            val keyboard = machine.components.byType("keyboard").first() as KeyboardComponent
            keyboard.injectText("hi")
            val echoDeadline = System.currentTimeMillis() + 10_000
            var echoed = false
            while (System.currentTimeMillis() < echoDeadline) {
                if (screenText(screen).contains("hi")) { echoed = true; break }
                Thread.sleep(50)
            }
            assertTrue(echoed, "key_down signal should be echoed to the screen")
        } finally {
            machine.stop()
        }
        assertEquals(Machine.State.STOPPED, machine.state)
        assertNull(machine.crashReason)
    }

    @Test
    fun `machine without eeprom reports no bootable medium`() {
        val machine = Machine(::LuaArchitecture)
        var crash: String? = null
        machine.onCrashed = { crash = it }
        machine.start()
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && machine.state != Machine.State.ERROR) {
            Thread.sleep(50)
        }
        assertEquals(Machine.State.ERROR, machine.state)
        assertEquals("no bootable medium found", crash)
    }

    @Test
    fun `component registry dispatches callbacks like component dot invoke`() {
        val machine = Machines.demo(tmpDir)
        val gpu = machine.components.byType("gpu").first()
        val screen = machine.components.byType("screen").first()

        machine.invokeComponent(gpu.address, "bind", arrayOf(screen.address))
        machine.invokeComponent(gpu.address, "set", arrayOf(1.0, 1.0, "AB"))
        val cell = (screen as ScreenComponent).buffer.cell(2, 1)
        assertNotNull(cell)
        assertEquals('B', cell.char)

        val methods = machine.components.methodsOf(gpu.address)!!
        assertTrue("set" in methods && "bind" in methods && "fill" in methods)

        val fs = machine.components.byType("filesystem").first() as FileSystemComponent
        val handle = machine.invokeComponent(fs.address, "open", arrayOf("/hello.txt", "w"))[0] as Int
        assertEquals(true, machine.invokeComponent(fs.address, "write", arrayOf(handle, "world".toByteArray()))[0])
        machine.invokeComponent(fs.address, "close", arrayOf(handle))
        assertTrue(File(tmpDir, "hello.txt").readText() == "world")
    }
}
