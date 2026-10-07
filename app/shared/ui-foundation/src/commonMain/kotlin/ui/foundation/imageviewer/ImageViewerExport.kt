package com.wynime.app.ui.foundation.imageviewer

import com.github.panpf.sketch.Sketch
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.util.DownloadData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wynime.utils.coroutines.IO_
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.createDirectories
import com.wynime.utils.io.exists
import com.wynime.utils.io.resolve
import com.wynime.utils.io.writeBytes

class ImageViewerExportedFile(
    val path: SystemPath,

    val baseName: String,

    val extension: String,
) {
    val fileName: String get() = "$baseName.$extension"
}

suspend fun Sketch.exportImageForViewer(
    model: String,
    directory: SystemPath,
): ImageViewerExportedFile = withContext(Dispatchers.IO_) {
    val data = executeDownload(ImageRequest(context, model)).getOrThrow()
    val bytes = when (data) {
        is DownloadData.Bytes -> data.bytes
        is DownloadData.Cache -> data.fileSystem.read(data.path) { readByteArray() }
    }
    val (baseName, extension) = deriveImageFileName(model, bytes)
    val target = directory.resolve(model.hashCode().toUInt().toString(16))
        .apply { createDirectories() }
        .resolve("$baseName.$extension")
    if (!target.exists()) {
        target.writeBytes(bytes)
    }
    ImageViewerExportedFile(target, baseName, extension)
}

fun deriveImageFileName(model: String, bytes: ByteArray): Pair<String, String> {
    val withoutQuery = model.substringBefore('#').substringBefore('?')

    val path = withoutQuery.substringAfter("://", "").substringAfter('/', "")
        .ifEmpty { if ("://" in withoutQuery) "" else withoutQuery }
    val segments = path.trimEnd('/').split('/').filter { it.isNotEmpty() }
    val lastSegment = segments.lastOrNull().orEmpty()
    val urlExtension = lastSegment.substringAfterLast('.', "").lowercase()
    val hasKnownUrlExtension = urlExtension in KNOWN_IMAGE_EXTENSIONS
    val rawBaseName = when {
        hasKnownUrlExtension -> lastSegment.substringBeforeLast('.')

        lastSegment.lowercase() in GENERIC_SIZE_SEGMENTS && segments.size >= 2 ->
            segments[segments.size - 2] + "_" + lastSegment

        else -> lastSegment
    }
    val baseName = rawBaseName
        .replace(INVALID_FILE_NAME_CHARS, "_")
        .trim { it == '.' || it == ' ' || it == '_' }
        .take(MAX_BASE_NAME_LENGTH)
        .ifEmpty { "image" }
    val extension = sniffImageExtension(bytes)
        ?: urlExtension.takeIf { hasKnownUrlExtension }
        ?: "jpg"
    return baseName to extension
}

private val GENERIC_SIZE_SEGMENTS = setOf("large", "medium", "small", "grid", "common", "original", "cover", "image", "img", "thumb")
private val KNOWN_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "avif", "heic")
private val INVALID_FILE_NAME_CHARS = Regex("""[\\/:*?"<>| ]""")
private const val MAX_BASE_NAME_LENGTH = 80

private fun sniffImageExtension(bytes: ByteArray): String? {
    fun startsWith(vararg prefix: Int, offset: Int = 0): Boolean {
        if (bytes.size < offset + prefix.size) return false
        return prefix.withIndex().all { (i, b) -> bytes[offset + i] == b.toByte() }
    }
    return when {
        startsWith(0x89, 0x50, 0x4E, 0x47) -> "png"
        startsWith(0xFF, 0xD8, 0xFF) -> "jpg"
        startsWith('G'.code, 'I'.code, 'F'.code, '8'.code) -> "gif"
        startsWith('R'.code, 'I'.code, 'F'.code, 'F'.code) &&
                startsWith('W'.code, 'E'.code, 'B'.code, 'P'.code, offset = 8) -> "webp"

        startsWith('B'.code, 'M'.code) -> "bmp"
        else -> null
    }
}
