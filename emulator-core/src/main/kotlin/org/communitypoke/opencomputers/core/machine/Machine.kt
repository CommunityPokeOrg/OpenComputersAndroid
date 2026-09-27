package org.communitypoke.opencomputers.core.machine

import org.communitypoke.opencomputers.core.api.Component
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An emulated OpenComputers machine: owns the component registry, the signal
 * queue and the VM thread. The VM (see [Architecture]) runs user code on a
 * dedicated daemon thread; components are ticked on a ~20 Hz scheduler.
 *
 * Threading model: every component `@Callback` invoked from Lua runs on the
 * machine thread, so component implementations are effectively single-threaded
 * during calls. `update()` runs on the tick thread; implementations that share
 * mutable state between the two should synchronize on themselves.
 */
class Machine(
    private val architectureFactory: (Machine) -> Architecture,
    /** Maximum queued signals; additional signals are dropped, as in OC. */
    signalQueueSize: Int = 256,
) {
    enum class State { STOPPED, STARTING, RUNNING, ERROR }

    val id: String = UUID.randomUUID().toString()
    val components = ComponentRegistry()
    private val signals = ArrayBlockingQueue<Signal>(signalQueueSize)

    @Volatile
    var state: State = State.STOPPED
        private set

    @Volatile
    var crashReason: String? = null
        private set

    /** Called on state transitions, on whatever thread triggered them. */
    var onStateChanged: ((State) -> Unit)? = null

    /** Called once when machine code throws (Lua error, halt, ...). */
    var onCrashed: ((String) -> Unit)? = null

    @Volatile
    private var startedAtMillis: Long = 0L
    private var thread: Thread? = null
    private var ticker: ScheduledExecutorService? = null
    private var architecture: Architecture? = null
    private val shutdownRequested = AtomicBoolean(false)

    val isRunning: Boolean get() = state == State.RUNNING || state == State.STARTING
    val uptimeSeconds: Double get() = (System.currentTimeMillis() - startedAtMillis) / 1000.0

    fun addComponent(component: Component) {
        components.add(component)
        if (component is MachineAware) component.attach(this)
        pushSignal("component_added", component.address, component.type)
    }

    fun removeComponent(address: String) {
        val removed = components.remove(address) ?: return
        pushSignal("component_removed", removed.address, removed.type)
    }

    /** Queue a signal for machine code (`computer.pullSignal`). Drops silently when full. */
    fun pushSignal(name: String, vararg args: Any?) {
        if (state == State.STARTING || state == State.RUNNING) {
            signals.offer(Signal(name, args.toList()))
        }
    }

    /** Blocking pull used by `computer.pullSignal` on the machine thread. */
    fun pullSignal(timeoutMillis: Long): Signal? =
        if (timeoutMillis <= 0) signals.poll()
        else signals.poll(timeoutMillis, TimeUnit.MILLISECONDS)

    /** `component.invoke` entry point used by the architecture layer. */
    fun invokeComponent(address: String, method: String, args: Array<Any?>): Array<Any?> {
        val component = components.get(address)
            ?: throw ComponentException("no component with address '$address'")
        return components.invoke(component, method, args)
    }

    fun requestShutdown(reboot: Boolean = false) {
        throw MachineShutdownException(reboot)
    }

    @Synchronized
    fun start(): Boolean {
        if (state != State.STOPPED) return false
        crashReason = null
        shutdownRequested.set(false)
        setState(State.STARTING)
        thread = Thread({ runMachine() }, "oc-machine-${id.take(8)}").apply {
            isDaemon = true
            start()
        }
        return true
    }

    fun stop() {
        shutdownRequested.set(true)
        val t = thread ?: return
        t.interrupt()
        runCatching { t.join(5000) }
    }

    private fun runMachine() {
        try {
            startTicking()
            val arch = architectureFactory(this).also { architecture = it }
            if (!arch.initialize()) throw MachineHaltedException("no bootable medium found")
            startedAtMillis = System.currentTimeMillis()
            setState(State.RUNNING)
            arch.run()
            setState(State.STOPPED)
        } catch (e: MachineShutdownException) {
            setState(State.STOPPED)
        } catch (e: InterruptedException) {
            setState(State.STOPPED)
        } catch (t: Throwable) {
            val benignShutdown = shutdownRequested.get() ||
                generateSequence(t as Throwable?) { it.cause }
                    .any { it is MachineShutdownException || it is InterruptedException }
            if (benignShutdown) {
                setState(State.STOPPED)
            } else {
                crashReason = t.message ?: t.toString()
                setState(State.ERROR)
                onCrashed?.invoke(crashReason!!)
            }
        } finally {
            ticker?.shutdown()
            runCatching { architecture?.close() }
            components.closeAll()
        }
    }

    private fun startTicking() {
        ticker = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "oc-tick-${id.take(8)}").apply { isDaemon = true }
        }.also { exec ->
            exec.scheduleAtFixedRate(
                { runCatching { components.updateAll() } },
                50L, 50L, TimeUnit.MILLISECONDS,
            )
        }
    }

    private fun setState(next: State) {
        state = next
        onStateChanged?.invoke(next)
    }
}
