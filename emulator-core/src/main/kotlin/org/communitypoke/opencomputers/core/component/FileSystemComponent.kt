package org.communitypoke.opencomputers.core.component

import org.communitypoke.opencomputers.core.api.Callback
import org.communitypoke.opencomputers.core.api.Component
import org.communitypoke.opencomputers.core.machine.ComponentException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A `filesystem` component backed by a host directory, sandboxed so absolute
 * paths and `..` segments cannot escape [root]. Implements the OC filesystem
 * component API: `open`/`read`/`write`/`seek`/`close` handles plus directory
 * and metadata operations.
 */
class FileSystemComponent(
    private val root: Path,
    private val quotaBytes: Long = DEFAULT_QUOTA,
    /** Marks this as the machine's tmpfs (`computer.tmpAddress`). */
    val isTemporary: Boolean = false,
    override val address: String = UUID.randomUUID().toString(),
    private val maxOpenHandles: Int = 16,
) : Component {

    override val type = "filesystem"

    private class Handle(val file: RandomAccessFile, val writable: Boolean)

    private val handles = ConcurrentHashMap<Int, Handle>()
    private var nextHandle = 1

    @Volatile
    private var label: String? = null

    private val normalizedRoot: Path by lazy {
        root.toAbsolutePath().normalize().also { Files.createDirectories(it) }
    }

    private fun resolve(path: String): Path {
        val rel = path.removePrefix("/")
        val resolved = normalizedRoot.resolve(rel).normalize()
        if (!resolved.startsWith(normalizedRoot)) {
            throw ComponentException("invalid path")
        }
        return resolved
    }

    private fun handle(id: Int): Handle =
        handles[id] ?: throw ComponentException("bad file descriptor")

    private fun usedBytes(): Long =
        Files.walk(normalizedRoot).use { stream ->
            stream.filter { Files.isRegularFile(it) }.mapToLong { Files.size(it) }.sum()
        }

    private fun listEntries(dir: Path): List<Path> =
        Files.newDirectoryStream(dir).use { it.toList() }

    @Callback
    fun isReadOnly(): Boolean = false

    @Callback
    fun getLabel(): Any? = label

    @Callback
    fun setLabel(value: String): Any? {
        val old = label
        label = value.take(32)
        return old
    }

    @Callback
    fun list(path: String, vararg rest: Any?): List<Any?> {
        val dir = resolve(path)
        if (!Files.exists(dir)) return emptyList()
        if (!Files.isDirectory(dir)) throw ComponentException("not a directory")
        return listEntries(dir).map { entry ->
            val name = entry.fileName.toString()
            if (Files.isDirectory(entry)) "$name/" else name
        }.sorted()
    }

    @Callback
    fun exists(path: String): Boolean = Files.exists(resolve(path))

    @Callback
    fun isDirectory(path: String): Boolean = Files.isDirectory(resolve(path))

    @Callback
    fun size(path: String): Long {
        val p = resolve(path)
        if (!Files.exists(p) || Files.isDirectory(p)) return 0L
        return Files.size(p)
    }

    @Callback
    fun lastModified(path: String): Long {
        val p = resolve(path)
        if (!Files.exists(p)) return 0L
        return Files.getLastModifiedTime(p).toMillis()
    }

    @Callback
    fun makeDirectory(path: String): Boolean {
        val p = resolve(path)
        if (Files.exists(p)) return false
        Files.createDirectories(p)
        return true
    }

    @Callback
    fun remove(path: String): Boolean {
        val p = resolve(path)
        if (!Files.exists(p)) return false
        if (Files.isDirectory(p)) {
            listEntries(p).forEach { entry ->
                remove("/" + normalizedRoot.relativize(entry).toString().replace('\\', '/'))
            }
        }
        return Files.deleteIfExists(p)
    }

    @Callback
    fun rename(from: String, to: String): Boolean {
        val src = resolve(from)
        val dst = resolve(to)
        if (!Files.exists(src)) return false
        dst.parent?.let { Files.createDirectories(it) }
        return runCatching { Files.move(src, dst); true }.getOrDefault(false)
    }

    @Callback
    fun spaceUsed(): Long = usedBytes()

    @Callback
    fun spaceTotal(): Long = quotaBytes

    @Callback
    fun open(path: String, vararg rest: Any?): Int {
        if (handles.size >= maxOpenHandles) throw ComponentException("too many open files")
        val mode = (rest.firstOrNull() as? String) ?: "r"
        val p = resolve(path)
        return when (mode.removeSuffix("b")) {
            "r" -> {
                if (!Files.exists(p) || Files.isDirectory(p)) throw ComponentException("file not found")
                openHandle(RandomAccessFile(p.toFile(), "r"), writable = false)
            }
            "w" -> {
                p.parent?.let { Files.createDirectories(it) }
                val f = RandomAccessFile(p.toFile(), "rw")
                f.setLength(0)
                openHandle(f, writable = true)
            }
            "a" -> {
                p.parent?.let { Files.createDirectories(it) }
                val f = RandomAccessFile(p.toFile(), "rw")
                f.seek(f.length())
                openHandle(f, writable = true)
            }
            else -> throw ComponentException("unsupported mode '$mode'")
        }
    }

    private fun openHandle(file: RandomAccessFile, writable: Boolean): Int {
        val id = nextHandle++
        handles[id] = Handle(file, writable)
        return id
    }

    @Callback
    fun read(id: Int, n: Double): Any? {
        val h = handle(id)
        val count = when {
            n < 0 || n.isInfinite() || n.isNaN() -> 8192
            else -> n.toInt().coerceIn(0, 8192)
        }
        if (count == 0) return null
        val buf = ByteArray(count)
        val read = h.file.read(buf)
        return if (read <= 0) null else buf.copyOf(read)
    }

    @Callback
    fun write(id: Int, value: ByteArray): Boolean {
        val h = handle(id)
        if (!h.writable) throw ComponentException("file is read-only")
        if (usedBytes() + value.size > quotaBytes) return false
        h.file.write(value)
        return true
    }

    @Callback
    fun seek(id: Int, vararg rest: Any?): Long {
        val h = handle(id)
        val whence = (rest.getOrNull(0) as? String) ?: "cur"
        val offset = (rest.getOrNull(1) as? Number)?.toLong() ?: 0L
        val target = when (whence) {
            "set" -> offset
            "cur" -> h.file.filePointer + offset
            "end" -> h.file.length() + offset
            else -> throw ComponentException("invalid whence '$whence'")
        }.coerceIn(0, h.file.length())
        h.file.seek(target)
        return h.file.filePointer
    }

    @Callback
    fun close(id: Int): Boolean {
        val h = handles.remove(id) ?: return false
        runCatching { h.file.close() }
        return true
    }

    override fun close() {
        handles.values.forEach { runCatching { it.file.close() } }
        handles.clear()
    }

    companion object {
        const val DEFAULT_QUOTA: Long = 4L * 1024 * 1024
    }
}
