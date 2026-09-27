package org.communitypoke.opencomputers.core.component

import org.communitypoke.opencomputers.core.api.Component
import org.communitypoke.opencomputers.core.machine.Machine
import org.communitypoke.opencomputers.core.machine.MachineAware
import java.util.UUID

/**
 * A `keyboard` component. Like the real OC keyboard it exposes no component
 * methods — it is a pure signal source, generating `key_down`/`key_up` signals
 * carrying `(keyboardAddress, char, code)` for the host to feed from real input.
 */
class KeyboardComponent(
    override val address: String = UUID.randomUUID().toString(),
) : Component, MachineAware {

    override val type = "keyboard"

    /** The screen this keyboard is attached to; set via [ScreenComponent.attachKeyboard]. */
    var screen: ScreenComponent? = null

    private var machine: Machine? = null

    override fun attach(machine: Machine) {
        this.machine = machine
    }

    /**
     * Host-side input injection: queues a `key_down` followed by `key_up`.
     * [char] is the Unicode code point produced by the key, [code] the raw
     * keycode (0 when the host can't provide one).
     */
    fun injectKeyPress(char: Int, code: Int = 0) {
        machine?.pushSignal("key_down", address, char, code)
        machine?.pushSignal("key_up", address, char, code)
    }

    fun injectText(text: String) {
        text.codePoints().forEach { injectKeyPress(it, 0) }
    }
}
