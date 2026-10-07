package com.wynime.utils.io

import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlin.jvm.JvmInline

fun Path.resolve(vararg parts: String): Path = Path(this, parts = parts)

fun Path.resolve(part: String): Path = Path(this, part)

fun Path.resolveSibling(part: String): Path = parent?.resolve(part) ?: Path(part)

fun Path.resolveSibling(part: String, vararg parts: String): Path = parent?.resolve(part, *parts) ?: Path(part, *parts)

val Path.extension: String get() = name.substringAfterLast('.', "")
val SystemPath.extension: String get() = path.extension

val Path.nameWithoutExtension: String get() = name.substringBeforeLast('.', "")
val SystemPath.nameWithoutExtension: String get() = path.nameWithoutExtension

inline val Path.inSystem get() = SystemPath(this)

@JvmInline
value class SystemPath @PublishedApi internal constructor(
    val path: Path
) {
    override fun toString(): String = path.toString()
}

val SystemPath.name get() = path.name

expect fun SystemPath.length(): Long

expect fun SystemPath.isDirectory(): Boolean

expect fun SystemPath.isRegularFile(): Boolean

fun SystemPath.resolve(part: String) = path.resolve(part).inSystem

fun SystemPath.resolve(vararg parts: String) = path.resolve(*parts).inSystem

fun SystemPath.resolveSibling(part: String) = path.resolveSibling(part).inSystem

fun SystemPath.resolveSibling(part: String, vararg parts: String) = path.resolveSibling(part, *parts).inSystem

fun SystemPath.exists(): Boolean = SystemFileSystem.exists(path)

fun SystemPath.delete(mustExist: Boolean = false) = SystemFileSystem.delete(path, mustExist)

@Deprecated("For migration. Use delete() instead", ReplaceWith("this.delete()"), level = DeprecationLevel.ERROR)
fun SystemPath.deleteIfExists() = delete()

fun SystemPath.actualSize(): Long {
    return if (isRegularFile()) {
        length()
    } else {
        useDirectoryEntries { seq ->
            seq.sumOf { it.actualSize() }
        }
    }
}

fun SystemPath.deleteRecursively(mustExist: Boolean = false) {
    if (isDirectory()) {
        useDirectoryEntries { seq ->
            seq.forEach { it.deleteRecursively(mustExist) }
        }
    }
    delete(mustExist)
}

fun FileSystem.deleteRecursively(path: Path, mustExist: Boolean = false) {
    if (SystemFileSystem.metadataOrNull(path)?.isDirectory == true) {
        SystemFileSystem.list(path).asSequence().forEach { path ->
            deleteRecursively(path, mustExist)
        }
    }
    delete(path, mustExist)
}

fun SystemPath.list(): Collection<Path> = SystemFileSystem.list(path)

@Deprecated("For migration. Use list() instead", ReplaceWith("this.list()"), level = DeprecationLevel.ERROR)
fun SystemPath.listFiles(): Collection<Path> = SystemFileSystem.list(path)

fun SystemPath.createDirectories(mustCreate: Boolean = false): Unit =
    SystemFileSystem.createDirectories(path, mustCreate)

fun SystemPath.moveTo(target: Path): Unit = SystemFileSystem.atomicMove(path, target)

fun SystemPath.moveTo(target: SystemPath): Unit = moveTo(target.path)

fun SystemPath.source() = SystemFileSystem.source(path)

fun SystemPath.bufferedSource() = this.source().buffered()

fun SystemPath.sink(append: Boolean = false) = SystemFileSystem.sink(path, append)

fun SystemPath.bufferedSink(append: Boolean = false) = sink(append).buffered()

fun SystemPath.metadataOrNull() = SystemFileSystem.metadataOrNull(path)

fun SystemPath.resolveToAbsolute() = SystemFileSystem.resolve(path)

expect val SystemPath.absolutePath: String

expect inline fun <T> SystemPath.useDirectoryEntries(block: (Sequence<SystemPath>) -> T): T

expect fun SystemPath.moveDirectoryRecursively(target: SystemPath, onBeforeMove: ((SystemPath) -> Unit)? = null)

fun SystemPath.readText(): String {
    return this.bufferedSource().use { source ->
        source.readString()
    }
}

fun SystemPath.readBytes(): ByteArray {
    return this.bufferedSource().use {
        it.readByteArray()
    }
}

fun SystemPath.writeText(string: String, startIndex: Int = 0, endIndex: Int = string.length) {
    this.bufferedSink(append = false).use {
        it.writeString(string, startIndex, endIndex)
    }
}

fun SystemPath.appendText(string: String, startIndex: Int = 0, endIndex: Int = string.length) {
    this.bufferedSink(append = true).use {
        it.writeString(string, startIndex, endIndex)
    }
}

fun SystemPath.writeBytes(array: ByteArray, startIndex: Int = 0, endIndex: Int = array.size) {
    this.bufferedSink(append = false).use {
        it.write(array, startIndex, endIndex)
    }
}

fun SystemPath.appendBytes(array: ByteArray, startIndex: Int = 0, endIndex: Int = array.size) {
    this.bufferedSink(append = true).use {
        it.write(array, startIndex, endIndex)
    }
}

fun SystemPath.copyTo(target: SystemPath) {
    this.bufferedSource().use { source ->
        target.bufferedSink(append = false).use { sink ->
            source.transferTo(sink)
        }
    }
}

object SystemPaths

expect fun SystemPaths.createTempDirectory(prefix: String = "tmp"): SystemPath
expect fun SystemPaths.createTempFile(prefix: String = "tmp", suffix: String = ""): SystemPath

