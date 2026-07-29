package com.example.ohmyssh

import com.example.ohmyssh.fs.FileBrowserError
import com.example.ohmyssh.fs.FileBrowserState
import com.example.ohmyssh.fs.LocalSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FileCopyTest {
    private fun browser() =
        FileBrowserState(LocalSource(), CoroutineScope(SupervisorJob() + Dispatchers.Default))

    private fun tempDir(name: String): File =
        File(System.getProperty("java.io.tmpdir"), "ohmyssh-$name-${System.nanoTime()}").apply {
            mkdirs()
            deleteOnExit()
        }

    @Test
    fun copiesFilesAndWholeDirectoriesBetweenPanes() = runBlocking {
        val from = tempDir("from")
        val into = tempDir("into")
        File(from, "notes.txt").writeText("one\ntwo\n")
        File(from, "nested").mkdir()
        File(from, "nested/inner.bin").writeBytes(byteArrayOf(1, 2, 3, 4))

        val left = browser()
        val right = browser()
        left.listDir(from.path)
        right.listDir(into.path)

        val report = right.receive(left, left.entries)

        assertEquals(2, report.copied)
        assertEquals(0, report.skipped)
        assertEquals("one\ntwo\n", File(into, "notes.txt").readText())
        assertTrue(File(into, "nested/inner.bin").readBytes().contentEquals(byteArrayOf(1, 2, 3, 4)))
        assertEquals(listOf("nested", "notes.txt"), right.entries.map { it.name })
    }

    @Test
    fun refusesADropOntoTheDirectoryTheDragCameFrom() = runBlocking {
        val dir = tempDir("same")
        File(dir, "a.txt").writeText("a")

        val left = browser()
        val right = browser()
        left.listDir(dir.path)
        right.listDir(dir.path)

        assertFailsWith<FileBrowserError> { right.receive(left, left.entries) }
        Unit
    }

    @Test
    fun refusesToCopyADirectoryIntoItself() = runBlocking {
        val root = tempDir("self")
        File(root, "tree").mkdir()
        File(root, "tree/leaf.txt").writeText("leaf")

        val left = browser()
        val right = browser()
        left.listDir(root.path)
        right.listDir(File(root, "tree").path)

        assertFailsWith<FileBrowserError> {
            right.receive(left, left.entries.filter { it.name == "tree" })
        }
        assertEquals(listOf("leaf.txt"), right.entries.map { it.name })
    }
}
