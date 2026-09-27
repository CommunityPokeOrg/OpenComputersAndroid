package org.communitypoke.opencomputers.core.machine

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs

/**
 * LuaJ <-> Kotlin value conversion used when crossing the machine boundary in
 * either direction (component call arguments/results, signal payloads).
 *
 * Tables become `List<Any?>` when their keys form the integer range 1..n,
 * otherwise `Map<Any?, Any?>`. Numbers always arrive as `Double`; the registry
 * coerces them to declared parameter types.
 */
object LuaInterop {

    fun toLua(value: Any?): LuaValue = when (value) {
        null -> LuaValue.NIL
        is LuaValue -> value
        is String -> LuaValue.valueOf(value)
        is Boolean -> LuaValue.valueOf(value)
        is Int -> LuaValue.valueOf(value)
        is Long -> LuaValue.valueOf(value.toDouble())
        is Double -> LuaValue.valueOf(value)
        is Number -> LuaValue.valueOf(value.toDouble())
        is ByteArray -> LuaValue.valueOf(value)
        is Map<*, *> -> {
            val t = LuaTable()
            for ((k, v) in value) t.set(toLua(k), toLua(v))
            t
        }
        is List<*> -> {
            val t = LuaTable()
            value.forEachIndexed { i, e -> t.set(i + 1, toLua(e)) }
            t
        }
        is Array<*> -> {
            val t = LuaTable()
            value.forEachIndexed { i, e -> t.set(i + 1, toLua(e)) }
            t
        }
        else -> LuaValue.valueOf(value.toString())
    }

    fun toVarargs(results: Array<Any?>): Varargs {
        val values = results.map { toLua(it) }.toTypedArray()
        return LuaValue.varargsOf(values)
    }

    fun fromLua(value: LuaValue): Any? = when {
        value.isnil() -> null
        value.isboolean() -> value.toboolean()
        value.isnumber() -> value.todouble()
        value.istable() -> tableToKotlin(value.checktable())
        value.isstring() -> value.tojstring()
        else -> value.tojstring()
    }

    /** Converts `args.arg(fromIndex) .. args.arg(narg())` into a Kotlin array. */
    fun argsFromLua(args: Varargs, fromIndex: Int = 1): Array<Any?> =
        (fromIndex..args.narg()).map { fromLua(args.arg(it)) }.toTypedArray()

    private fun tableToKotlin(t: LuaTable): Any {
        val keys = t.keys()
        val allSequentialInts = keys.isNotEmpty() && keys.all { it.isint() } &&
            keys.map { it.toint() }.sorted() == (1..keys.size).toList()
        return if (keys.isEmpty() || allSequentialInts) {
            (1..t.length()).map { fromLua(t.get(it)) }
        } else {
            keys.associate { k -> fromLua(k) to fromLua(t.get(k)) }
        }
    }
}
