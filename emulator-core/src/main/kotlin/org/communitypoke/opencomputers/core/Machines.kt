package org.communitypoke.opencomputers.core

import org.communitypoke.opencomputers.core.component.EepromComponent
import org.communitypoke.opencomputers.core.component.FileSystemComponent
import org.communitypoke.opencomputers.core.component.GpuComponent
import org.communitypoke.opencomputers.core.component.KeyboardComponent
import org.communitypoke.opencomputers.core.component.ScreenComponent
import org.communitypoke.opencomputers.core.machine.LuaArchitecture
import org.communitypoke.opencomputers.core.machine.Machine
import java.io.File

/** Factory helpers for ready-to-run machine configurations. */
object Machines {

    /**
     * A machine with a tier-3 screen + GPU, a keyboard, a sandboxed tmp
     * filesystem rooted at [filesystemRoot] and an EEPROM flashed with the
     * bundled demo BIOS (`bios/bios.lua`).
     */
    fun demo(filesystemRoot: File): Machine {
        val screen = ScreenComponent()
        val keyboard = KeyboardComponent()
        screen.attachKeyboard(keyboard)

        val eeprom = EepromComponent(code = defaultBios().toByteArray(Charsets.UTF_8))

        return Machine(::LuaArchitecture).apply {
            addComponent(screen)
            addComponent(GpuComponent())
            addComponent(keyboard)
            addComponent(
                FileSystemComponent(
                    root = filesystemRoot.toPath(),
                    quotaBytes = FileSystemComponent.DEFAULT_QUOTA,
                    isTemporary = true,
                )
            )
            addComponent(eeprom)
        }
    }

    fun defaultBios(): String {
        val stream = Machines::class.java.getResourceAsStream("/bios/bios.lua")
            ?: error("bundled bios resource missing: /bios/bios.lua")
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
