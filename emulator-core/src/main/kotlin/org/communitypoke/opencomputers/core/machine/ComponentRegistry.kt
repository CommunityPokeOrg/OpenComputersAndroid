package org.communitypoke.opencomputers.core.machine

import org.communitypoke.opencomputers.core.api.Callback
import org.communitypoke.opencomputers.core.api.Component
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/** Raised when a Lua-facing component call fails; the message is surfaced to Lua code. */
class ComponentException(message: String) : Exception(message)

/**
 * Holds the components attached to a [Machine] and dispatches `@Callback`
 * invocations coming from machine code, equivalent to OpenComputers'
 * component network + `component.invoke` plumbing.
 */
class ComponentRegistry {
    private val components = LinkedHashMap<String, Component>()
    private val methodCache = ConcurrentHashMap<Class<*>, Map<String, Method>>()

    @Synchronized
    fun add(component: Component) {
        components[component.address] = component
    }

    @Synchronized
    fun remove(address: String): Component? = components.remove(address)

    @Synchronized
    fun get(address: String): Component? = components[address]

    @Synchronized
    fun byType(type: String): List<Component> =
        components.values.filter { it.type == type }

    /** `component.list`: address -> type, with optional substring/exact type filter. */
    @Synchronized
    fun list(filter: String? = null, exact: Boolean = false): Map<String, String> =
        components.values
            .filter { filter == null || (if (exact) it.type == filter else filter in it.type) }
            .associate { it.address to it.type }

    fun typeOf(address: String): String? = get(address)?.type

    /** `component.methods`: callback name -> `direct` flag. */
    fun methodsOf(address: String): Map<String, Boolean>? {
        val component = get(address) ?: return null
        return callbackMethods(component.javaClass)
            .mapValues { (_, m) -> m.getAnnotation(Callback::class.java)!!.direct }
    }

    /**
     * `component.invoke`: call a `@Callback` method, coercing arguments to the
     * declared Kotlin parameter types. A trailing `vararg rest: Any?` parameter
     * receives any remaining arguments unconverted.
     *
     * Returns the flattened result list: a method returning `Array<Any?>`
     * produces multiple Lua return values; `Unit` produces none.
     */
    fun invoke(component: Component, name: String, args: Array<Any?>): Array<Any?> {
        val method = callbackMethods(component.javaClass)[name]
            ?: throw ComponentException("no such method")
        val result = callMethod(component, method, args)
        return when {
            method.returnType == Void.TYPE -> emptyArray()
            result is Array<*> -> result.map { it }.toTypedArray()
            else -> arrayOf(result)
        }
    }

    @Synchronized
    fun updateAll() {
        components.values.forEach { it.update() }
    }

    fun closeAll() {
        val all: List<Component>
        synchronized(this) { all = components.values.toList() }
        all.forEach { runCatching { it.close() } }
    }

    private fun callbackMethods(clazz: Class<*>): Map<String, Method> =
        methodCache.getOrPut(clazz) {
            clazz.methods
                .filter { Modifier.isPublic(it.modifiers) }
                .mapNotNull { m ->
                    val cb = m.getAnnotation(Callback::class.java) ?: return@mapNotNull null
                    (cb.name.ifEmpty { m.name }) to m
                }
                .toMap()
        }

    private fun callMethod(component: Component, method: Method, args: Array<Any?>): Any? {
        val params = method.parameterTypes.toList()
        val varargTail = params.isNotEmpty() && params.last() == Array<Any?>::class.java
        val fixed = if (varargTail) params.size - 1 else params.size
        if (args.size < fixed) {
            val name = methodName(method)
            throw ComponentException(
                "bad argument #${args.size + 1} to '$name' (expected ${params.size}, got ${args.size})"
            )
        }
        val finalArgs = arrayOfNulls<Any?>(params.size)
        for (i in 0 until fixed) {
            finalArgs[i] = coerce(args[i], params[i], i + 1)
        }
        if (varargTail) {
            finalArgs[fixed] = args.copyOfRange(fixed, args.size)
        }
        return try {
            method.invoke(component, *finalArgs)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            val cause = e.cause ?: e
            throw if (cause is ComponentException) cause
            else ComponentException(cause.message ?: cause.toString())
        } catch (e: IllegalArgumentException) {
            throw ComponentException(e.message ?: "bad arguments to '${method.name}'")
        }
    }

    private fun methodName(method: Method): String =
        method.getAnnotation(Callback::class.java)?.name?.ifEmpty { method.name } ?: method.name

    private fun coerce(value: Any?, type: Class<*>, index: Int): Any? {
        fun fail(): Nothing =
            throw ComponentException("bad argument #$index (${type.simpleName} expected, got ${luaTypeName(value)})")
        return when (type) {
            String::class.java -> when (value) {
                null -> null
                is ByteArray -> String(value, Charsets.UTF_8)
                is Double -> if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
                else -> value.toString()
            }
            java.lang.Integer.TYPE, java.lang.Integer::class.java -> when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull() ?: fail()
                else -> fail()
            }
            java.lang.Long.TYPE, java.lang.Long::class.java -> when (value) {
                is Number -> value.toLong()
                is String -> value.toLongOrNull() ?: fail()
                else -> fail()
            }
            java.lang.Double.TYPE, java.lang.Double::class.java -> when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull() ?: fail()
                else -> fail()
            }
            java.lang.Float.TYPE, java.lang.Float::class.java -> when (value) {
                is Number -> value.toFloat()
                is String -> value.toFloatOrNull() ?: fail()
                else -> fail()
            }
            java.lang.Number::class.java -> when (value) {
                is Number -> value
                is String -> value.toDoubleOrNull() ?: fail()
                else -> fail()
            }
            java.lang.Boolean.TYPE, java.lang.Boolean::class.java -> when (value) {
                is Boolean -> value
                null -> false
                else -> fail()
            }
            ByteArray::class.java -> when (value) {
                is ByteArray -> value
                is String -> value.toByteArray(Charsets.UTF_8)
                else -> fail()
            }
            Map::class.java -> value as? Map<*, *> ?: fail()
            else -> value ?: fail()
        }
    }

    private fun luaTypeName(value: Any?): String = when (value) {
        null -> "nil"
        is Boolean -> "boolean"
        is Number -> "number"
        is String -> "string"
        is Map<*, *> -> "table"
        else -> value.javaClass.simpleName
    }
}
