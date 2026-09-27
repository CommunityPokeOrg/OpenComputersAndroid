package org.communitypoke.opencomputers.core.component

import org.communitypoke.opencomputers.core.api.Callback
import org.communitypoke.opencomputers.core.api.Component
import java.util.UUID

/**
 * A `screen` component: owns the [TextBuffer] that a bound `gpu` renders into,
 * plus power state. Attached [KeyboardComponent]s are how `key_down`/`key_up`
 * signals know which screen they belong to.
 */
class ScreenComponent(
    val buffer: TextBuffer = TextBuffer(),
    override val address: String = UUID.randomUUID().toString(),
) : Component {

    override val type = "screen"

    @Volatile
    private var poweredOn = true

    private val keyboards = mutableListOf<KeyboardComponent>()

    /** Host-side wiring; not exposed to Lua. */
    fun attachKeyboard(keyboard: KeyboardComponent) {
        if (keyboard !in keyboards) keyboards.add(keyboard)
        keyboard.screen = this
    }

    @Callback
    fun isOn(): Boolean = poweredOn

    @Callback
    fun turnOff(): Boolean {
        val changed = poweredOn
        poweredOn = false
        return changed
    }

    @Callback
    fun turnOn(): Boolean {
        val changed = !poweredOn
        poweredOn = true
        return changed
    }

    @Callback
    fun isPrecise(): Boolean = false

    @Callback
    fun getPrecise(): Boolean = false

    @Callback
    fun getAspectRatio(): Array<Any?> = arrayOf(1.0, 1.0)

    @Callback
    fun getKeyboards(): List<Any?> = keyboards.map { it.address }
}
