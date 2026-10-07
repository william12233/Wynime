package com.wynime.utils.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathMoveTest {

    @Test
    fun testMoveDirectoryRecursively() {
        val tempDir = SystemPaths.createTempDirectory("moveTest")
        val sourceDir = tempDir.resolve("source")
        val targetDir = tempDir.resolve("target")

        try {

            sourceDir.createDirectories()
            sourceDir.resolve("file1.txt").writeText("content1")
            sourceDir.resolve("file2.txt").writeText("content2")

            val subDir = sourceDir.resolve("subdir")
            subDir.createDirectories()
            subDir.resolve("subfile1.txt").writeText("subcontent1")

            val emptySubDir = sourceDir.resolve("emptysubdir")
            emptySubDir.createDirectories()

            val visitedFiles = mutableListOf<SystemPath>()

            sourceDir.moveDirectoryRecursively(targetDir) { visitedFiles.add(it) }

            assertFalse(sourceDir.exists())

            assertTrue(targetDir.exists())
            assertTrue(targetDir.isDirectory())
            assertTrue(targetDir.resolve("file1.txt").exists())
            assertTrue(targetDir.resolve("file2.txt").exists())
            assertTrue(targetDir.resolve("subdir").exists())
            assertTrue(targetDir.resolve("subdir/subfile1.txt").exists())
            assertTrue(targetDir.resolve("emptysubdir").exists())

            assertEquals("content1", targetDir.resolve("file1.txt").readText())
            assertEquals("content2", targetDir.resolve("file2.txt").readText())
            assertEquals("subcontent1", targetDir.resolve("subdir/subfile1.txt").readText())

            assertEquals(3, visitedFiles.size)
            assertTrue(visitedFiles.any { it.toString().endsWith("file1.txt") })
            assertTrue(visitedFiles.any { it.toString().endsWith("file2.txt") })
            assertTrue(visitedFiles.any { it.toString().endsWith("subfile1.txt") })
        } finally {

            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testMoveDirectoryRecursively_TargetExists() {
        val tempDir = SystemPaths.createTempDirectory("moveTest")
        val sourceDir = tempDir.resolve("source")
        val targetDir = tempDir.resolve("target")

        try {

            sourceDir.createDirectories()
            sourceDir.resolve("file1.txt").writeText("source content")

            targetDir.createDirectories()
            targetDir.resolve("file1.txt").writeText("target content")

            sourceDir.moveDirectoryRecursively(targetDir)

            assertFalse(sourceDir.exists())

            assertTrue(targetDir.exists())
            assertEquals("source content", targetDir.resolve("file1.txt").readText())
        } finally {

            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testMoveDirectoryRecursively_EmptySource() {
        val tempDir = SystemPaths.createTempDirectory("moveTest")
        val sourceDir = tempDir.resolve("source")
        val targetDir = tempDir.resolve("target")

        try {

            sourceDir.createDirectories()

            sourceDir.moveDirectoryRecursively(targetDir)

            assertFalse(sourceDir.exists())

            assertTrue(targetDir.exists())
            assertTrue(targetDir.isDirectory())
            assertEquals(0, targetDir.list().size)
        } finally {

            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testMoveDirectoryRecursively_SourceNotExists() {
        val tempDir = SystemPaths.createTempDirectory("moveTest")
        val sourceDir = tempDir.resolve("nonexistent")
        val targetDir = tempDir.resolve("target")

        try {

            assertFailsWith(NoSuchFileException::class) {
                sourceDir.moveDirectoryRecursively(targetDir)
            }

            assertFalse(targetDir.exists())
        } finally {

            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testMoveDirectoryRecursively_SourceIsFile() {
        val tempDir = SystemPaths.createTempDirectory("moveTest")
        val sourceFile = tempDir.resolve("sourcefile.txt")
        val targetDir = tempDir.resolve("target")

        try {

            sourceFile.writeText("content")

            assertFailsWith(IllegalArgumentException::class) {
                sourceFile.moveDirectoryRecursively(targetDir)
            }

            assertTrue(sourceFile.exists())
            assertFalse(targetDir.exists())
        } finally {

            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testMoveDirectoryRecursively_WithoutVisitor() {
        val tempDir = SystemPaths.createTempDirectory("moveTest")
        val sourceDir = tempDir.resolve("source")
        val targetDir = tempDir.resolve("target")

        try {

            sourceDir.createDirectories()
            sourceDir.resolve("file1.txt").writeText("content")

            sourceDir.moveDirectoryRecursively(targetDir, null)

            assertFalse(sourceDir.exists())
            assertTrue(targetDir.resolve("file1.txt").exists())
        } finally {

            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testMoveDirectoryRecursively_NestedStructure() {
        val tempDir = SystemPaths.createTempDirectory("moveTest")
        val sourceDir = tempDir.resolve("source")
        val targetDir = tempDir.resolve("target")

        try {

            sourceDir.createDirectories()
            sourceDir.resolve("level1").createDirectories()
            sourceDir.resolve("level1/level2").createDirectories()
            sourceDir.resolve("level1/level2/level3").createDirectories()
            sourceDir.resolve("level1/file1.txt").writeText("level1")
            sourceDir.resolve("level1/level2/file2.txt").writeText("level2")
            sourceDir.resolve("level1/level2/level3/file3.txt").writeText("level3")

            sourceDir.moveDirectoryRecursively(targetDir)

            assertTrue(targetDir.exists())
            assertTrue(targetDir.resolve("level1").exists())
            assertTrue(targetDir.resolve("level1/level2").exists())
            assertTrue(targetDir.resolve("level1/level2/level3").exists())
            assertEquals("level1", targetDir.resolve("level1/file1.txt").readText())
            assertEquals("level2", targetDir.resolve("level1/level2/file2.txt").readText())
            assertEquals("level3", targetDir.resolve("level1/level2/level3/file3.txt").readText())
        } finally {

            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testMoveDirectoryRecursively_SpecialFileNames() {
        val tempDir = SystemPaths.createTempDirectory("moveTest")
        val sourceDir = tempDir.resolve("source")
        val targetDir = tempDir.resolve("target")

        try {

            sourceDir.createDirectories()
            sourceDir.resolve("file with spaces.txt").writeText("spaces")
            sourceDir.resolve("file-with-dashes.txt").writeText("dashes")
            sourceDir.resolve("file_with_underscores.txt").writeText("underscores")
            sourceDir.resolve("file.with.dots.txt").writeText("dots")

            sourceDir.moveDirectoryRecursively(targetDir)

            assertEquals("spaces", targetDir.resolve("file with spaces.txt").readText())
            assertEquals("dashes", targetDir.resolve("file-with-dashes.txt").readText())
            assertEquals("underscores", targetDir.resolve("file_with_underscores.txt").readText())
            assertEquals("dots", targetDir.resolve("file.with.dots.txt").readText())
        } finally {

            tempDir.deleteRecursively()
        }
    }
}