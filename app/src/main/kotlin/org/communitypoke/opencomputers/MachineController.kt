package org.communitypoke.opencomputers

import org.communitypoke.opencomputers.core.Machines
import org.communitypoke.opencomputers.core.component.KeyboardComponent
import org.communitypoke.opencomputers.core.component.ScreenComponent
import org.communitypoke.opencomputers.core.component.TextBuffer
import org.communitypoke.opencomputers.core.machine.Machine
import java.io.File

/**
 * Host-side owner of a [Machine] for the app: builds the demo machine, wires
 * screen-buffer change notifications, and forwards UI key events into the
 * emulated keyboard component.
 */
class MachineController(filesDir: File) {

    val machine: Machine = Machines.demo(File(filesDir, "oc-rootfs"))

    private val screen: ScreenComponent? get() =
        machine.components.byType("screen").filterIsInstance<ScreenComponent>().firstOrNull()

    private val keyboard: KeyboardComponent? get() =
        machine.components.byType("keyboard").filterIsInstance<KeyboardComponent>().firstOrNull()

    val buffer: TextBuffer? get() = screen?.buffer

    fun isPowered(): Boolean = machine.isRunning

    fun setBufferChangedListener(listener: () -> Unit) {
        screen?.buffer?.addChangeListener(listener)
    }

    fun power() {
        if (machine.isRunning) machine.stop() else machine.start()
    }

    fun onKeyChar(char: Int) {
        keyboard?.injectKeyPress(char, 0)
    }
}
