package org.communitypoke.opencomputers.core.component

import org.communitypoke.opencomputers.core.api.Callback
import org.communitypoke.opencomputers.core.api.Component
import org.communitypoke.opencomputers.core.machine.ComponentException
import org.communitypoke.opencomputers.core.machine.Machine
import org.communitypoke.opencomputers.core.machine.MachineAware
import java.util.UUID

/**
 * A `gpu` component (tier-3 equivalent): renders text into the [TextBuffer] of
 * a bound screen. Mirrors the OC GPU component API — `bind`, `set`, `get`,
 * `fill`, `copy`, resolution and color/palette management.
 */
class GpuComponent(
    override val address: String = UUID.randomUUID().toString(),
) : Component, MachineAware {

    override val type = "gpu"

    private var machine: Machine? = null
    private var screen: ScreenComponent? = null

    private var foreground = TextBuffer.DEFAULT_FOREGROUND
    private var foregroundIsPalette = false
    private var background = TextBuffer.DEFAULT_BACKGROUND
    private var backgroundIsPalette = true
    private var depth = 8

    override fun attach(machine: Machine) {
        this.machine = machine
    }

    private fun buffer(): TextBuffer =
        screen?.buffer ?: throw ComponentException("no screen bound")

    private fun checkDepth(color: Int): Int =
        when {
            depth >= 8 -> color
            depth >= 4 -> color and 0x0F0F0F
            else -> if (color != 0) 0xFFFFFF else 0x000000
        }

    @Callback
    fun bind(address: String, vararg rest: Any?) {
        val target = machine?.components?.get(address) as? ScreenComponent
            ?: throw ComponentException("invalid address")
        screen = target
        if (rest.isNotEmpty() && rest[0] == true) {
            target.buffer.fill(1, 1, target.buffer.width, target.buffer.height, ' ', foreground, foregroundIsPalette, background, backgroundIsPalette)
        }
    }

    @Callback
    fun getScreen(): Any? = screen?.address

    @Callback
    fun set(x: Int, y: Int, value: String, vararg rest: Any?): Boolean {
        val vertical = rest.firstOrNull() == true
        return buffer().set(x, y, value, vertical, foreground, foregroundIsPalette, background, backgroundIsPalette)
    }

    @Callback
    fun get(x: Int, y: Int): Array<Any?> {
        val cell = buffer().cell(x, y) ?: return arrayOf(null)
        return arrayOf(cell.char.toString(), cell.foreground, cell.background)
    }

    @Callback
    fun fill(x: Int, y: Int, w: Int, h: Int, char: String): Boolean {
        val ch = char.firstOrNull() ?: ' '
        return buffer().fill(x, y, w, h, ch, foreground, foregroundIsPalette, background, backgroundIsPalette)
    }

    @Callback
    fun copy(x: Int, y: Int, w: Int, h: Int, dx: Int, dy: Int): Boolean =
        buffer().copy(x, y, w, h, dx, dy)

    @Callback
    fun getResolution(): Array<Any?> = buffer().let { arrayOf(it.width, it.height) }

    @Callback
    fun setResolution(w: Int, h: Int): Boolean {
        val b = buffer()
        if (w > b.maxWidth || h > b.maxHeight) {
            throw ComponentException("unsupported resolution")
        }
        return b.setResolution(w, h)
    }

    @Callback
    fun maxResolution(): Array<Any?> = buffer().let { arrayOf(it.maxWidth, it.maxHeight) }

    @Callback
    fun getViewport(): Array<Any?> = getResolution()

    @Callback
    fun setViewport(w: Int, h: Int): Boolean = setResolution(w, h)

    @Callback
    fun getBackground(): Array<Any?> =
        if (backgroundIsPalette) arrayOf(resolve(background), background)
        else arrayOf(background)

    @Callback
    fun setBackground(value: Int, vararg rest: Any?): Array<Any?> {
        val old: Array<Any?> = if (backgroundIsPalette)
            arrayOf(resolve(background), background)
        else
            arrayOf(background)
        backgroundIsPalette = rest.firstOrNull() == true
        background = if (backgroundIsPalette) value else checkDepth(value and 0xFFFFFF)
        return old
    }

    @Callback
    fun getForeground(): Array<Any?> =
        if (foregroundIsPalette) arrayOf(resolve(foreground), foreground)
        else arrayOf(foreground)

    @Callback
    fun setForeground(value: Int, vararg rest: Any?): Array<Any?> {
        val old: Array<Any?> = if (foregroundIsPalette)
            arrayOf(resolve(foreground), foreground)
        else
            arrayOf(foreground)
        foregroundIsPalette = rest.firstOrNull() == true
        foreground = if (foregroundIsPalette) value else checkDepth(value and 0xFFFFFF)
        return old
    }

    @Callback
    fun getDepth(): Int = depth

    @Callback
    fun maxDepth(): Int = buffer().maxDepth

    @Callback
    fun setDepth(d: Int): Int {
        val b = buffer()
        if (d > b.maxDepth) throw ComponentException("unsupported depth")
        val old = depth
        depth = d
        return old
    }

    @Callback
    fun getPaletteColor(index: Int): Int = buffer().getPaletteColor(index)

    @Callback
    fun setPaletteColor(index: Int, rgb: Int): Int = buffer().setPaletteColor(index, rgb)

    @Callback
    fun getSize(): Array<Any?> = getResolution()

    private fun resolve(value: Int): Int = buffer().resolveColor(value, true)
}
