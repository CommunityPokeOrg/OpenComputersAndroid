package org.communitypoke.opencomputers.core.component

import org.communitypoke.opencomputers.core.api.Callback
import org.communitypoke.opencomputers.core.api.Component
import org.communitypoke.opencomputers.core.machine.ComponentException
import java.security.MessageDigest
import java.util.UUID

/**
 * An `eeprom` component: stores the boot code a machine runs on power-up,
 * mirroring the OC EEPROM chip. [codeString] is what [org.communitypoke.opencomputers.core.machine.LuaArchitecture]
 * loads at `initialize()`.
 */
class EepromComponent(
    code: ByteArray = ByteArray(0),
    data: ByteArray = ByteArray(0),
    private val codeSize: Int = 4096,
    private val dataSize: Int = 4096,
    override val address: String = UUID.randomUUID().toString(),
) : Component {

    override val type = "eeprom"

    private var code = code
    private var data = data
    private var readonly = false
    private var label: String? = null

    val codeString: String get() = String(code, Charsets.UTF_8)

    @Callback
    fun get(): ByteArray = code

    @Callback
    fun set(newCode: ByteArray, vararg rest: Any?) {
        if (readonly) throw ComponentException("storage is readonly")
        if (newCode.size > codeSize) throw ComponentException("not enough space")
        code = newCode
        label = (rest.firstOrNull() as? String) ?: label
    }

    @Callback
    fun getData(): ByteArray = data

    @Callback
    fun setData(newData: ByteArray, vararg rest: Any?) {
        if (readonly) throw ComponentException("storage is readonly")
        if (newData.size > dataSize) throw ComponentException("not enough space")
        data = newData
        label = (rest.firstOrNull() as? String) ?: label
    }

    @Callback
    fun getSize(): Int = codeSize

    @Callback
    fun getDataSize(): Int = dataSize

    @Callback
    fun getChecksum(): String =
        MessageDigest.getInstance("SHA-256").digest(code).joinToString("") { "%02x".format(it) }

    @Callback
    fun getLabel(): Any? = label

    @Callback
    fun setLabel(value: String): Any? {
        if (readonly) throw ComponentException("storage is readonly")
        val old = label
        label = value.take(32)
        return old
    }

    @Callback
    fun isReadonly(): Boolean = readonly

    @Callback
    fun makeReadonly(checksum: String): Any? {
        if (checksum != getChecksum()) return null
        readonly = true
        return true
    }
}
