package org.communitypoke.opencomputers.core

import org.communitypoke.opencomputers.core.component.FileSystemComponent
import org.communitypoke.opencomputers.core.machine.ComponentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileSystemComponentTest {

    @TempDir
    lateinit var tmpDir: File

    private fun fs() = FileSystemComponent(tmpDir.toPath())

    @Test
    fun `write then read roundtrip`() {
        val fs = fs()
        val h = fs.open("/a/b.txt", "w")
        assertTrue(fs.write(h, "hello oc".toByteArray()))
        fs.close(h)

        val r = fs.open("/a/b.txt", "r")
        val data = fs.read(r, 8192.0) as ByteArray
        assertEquals("hello oc", String(data))
        fs.close(r)
    }

    @Test
    fun `path traversal is blocked`() {
        val fs = fs()
        assertFailsWith<ComponentException> { fs.exists("../escape") }
        assertFailsWith<ComponentException> { fs.open("/../../etc/passwd", "r") }
    }

    @Test
    fun `list and directories`() {
        val fs = fs()
        assertTrue(fs.makeDirectory("/dir"))
        assertTrue(fs.isDirectory("/dir"))
        assertFalse(fs.makeDirectory("/dir"))
        val h = fs.open("/dir/f.txt", "w")
        fs.close(h)
        assertEquals(listOf("f.txt"), fs.list("/dir"))
    }

    @Test
    fun `handle limit enforced`() {
        val fs = FileSystemComponent(tmpDir.toPath(), maxOpenHandles = 2)
        fs.open("/x", "w"); fs.open("/y", "w")
        assertFailsWith<ComponentException> { fs.open("/z", "w") }
    }
}
