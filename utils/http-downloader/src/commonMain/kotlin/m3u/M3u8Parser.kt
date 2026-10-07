package com.wynime.utils.httpdownloader.m3u

import com.wynime.utils.ktor.UrlHelpers

interface M3u8Parser {

    @Throws(M3uFormatException::class)
    fun parse(content: String, baseUrl: String): M3u8Playlist
}

class M3uFormatException(override val message: String?) : RuntimeException()

data class ResolvedMediaPlaylist(
    val sourceUrl: String,
    val rawContent: String,
    val playlist: M3u8Playlist.MediaPlaylist,
)

fun M3u8Parser.parseResolvedMediaPlaylist(content: String, baseUrl: String): ResolvedMediaPlaylist {
    val playlist = parse(content, baseUrl)
    if (playlist !is M3u8Playlist.MediaPlaylist) {
        throw M3uFormatException("Expected media playlist but parsed a non-media playlist")
    }
    return ResolvedMediaPlaylist(
        sourceUrl = baseUrl,
        rawContent = content,
        playlist = playlist,
    )
}

fun ResolvedMediaPlaylist.export(
    resolveSegmentUri: (segment: MediaSegment, index: Int) -> String,
    resolveKeyUri: (encryption: MediaSegmentEncryption) -> String,
): String {
    val segmentEncryptions = playlist.segments.mapNotNull { it.encryption }
    val orderedUniqueEncryptions = segmentEncryptions.distinctBy { listOf(it.method, it.uri, it.iv) }
    val encryptionLookup = orderedUniqueEncryptions.associateBy { listOf(it.method, it.uri, it.iv) }
    var segmentCursor = 0

    val exported = buildString {
        rawContent.lines().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("#EXT-X-KEY:") -> appendLine(
                    rewriteKeyLine(
                        line = line,
                        sourceUrl = sourceUrl,
                        encryptionLookup = encryptionLookup,
                        resolveKeyUri = resolveKeyUri,
                    ),
                )

                trimmed.isEmpty() || trimmed.startsWith("#") -> appendLine(line)
                else -> {
                    val segment = playlist.segments.getOrNull(segmentCursor)
                        ?: throw M3uFormatException(
                            "Found more media segment URI lines than parsed segments while exporting playlist",
                        )
                    appendLine(resolveSegmentUri(segment, segmentCursor))
                    segmentCursor++
                }
            }
        }
    }

    if (segmentCursor != playlist.segments.size) {
        throw M3uFormatException(
            "Found fewer media segment URI lines than parsed segments while exporting playlist: " +
                    "expected=${playlist.segments.size}, actual=$segmentCursor",
        )
    }

    return exported
}

sealed class M3u8Playlist {
    abstract val version: Int
    abstract val tags: Map<String, String>

    data class MasterPlaylist(
        override val version: Int = 3,
        val variants: List<VariantStream> = emptyList(),
        override val tags: Map<String, String> = emptyMap(),
    ) : M3u8Playlist()

    data class MediaPlaylist(
        override val version: Int = 3,
        val targetDuration: Int? = null,
        val mediaSequence: Int = 0,
        val segments: List<MediaSegment> = emptyList(),
        val isEndlist: Boolean = false,
        override val tags: Map<String, String> = emptyMap(),
    ) : M3u8Playlist()
}

data class ByteRange(
    val length: Long,
    val offset: Long? = null,
)

data class MediaSegment(
    val duration: Float,
    val uri: String,
    val title: String? = null,
    val isDiscontinuity: Boolean = false,
    val byteRange: ByteRange? = null,
    val encryption: MediaSegmentEncryption? = null,
    val tags: Map<String, String> = emptyMap(),
    val sourceRange: M3u8SourceRange? = null,
)

data class MediaSegmentEncryption(
    val method: String,
    val uri: String,
    val iv: String? = null,
    val keyFormat: String? = null,
    val keyFormatVersions: String? = null,
)

data class M3u8SourceRange(
    val startLine: Int,
    val endLine: Int,
)

data class VariantStream(

    val uri: String,
    val bandwidth: Int,
    val averageBandwidth: Int? = null,
    val codecs: String? = null,
    val resolution: String? = null,
    val frameRate: Float? = null,
    val audio: String? = null,
    val video: String? = null,
    val subtitles: String? = null,
    val closedCaptions: String? = null,
    val attributes: Map<String, String> = emptyMap(),
)

object DefaultM3u8Parser : M3u8Parser {
    override fun parse(content: String, baseUrl: String): M3u8Playlist {
        val lines = content.lines()
            .mapIndexed { index, text -> M3u8SourceLine(index + 1, text) }
            .filter { it.text.isNotBlank() }
        if (lines.isEmpty() || !lines[0].text.trimStart().startsWith("#EXTM3U")) {
            throw M3uFormatException("Invalid M3U8 format, must start with #EXTM3U")
        }

        var version = 3
        var targetDuration: Int? = null
        var mediaSequence = 0
        var isEndlist = false
        val segments = mutableListOf<MediaSegment>()
        val variants = mutableListOf<VariantStream>()
        val tags = mutableMapOf<String, String>()

        var i = 1

        var currentSegmentDuration: Float? = null
        var currentSegmentTitle: String? = null
        var currentSegmentDiscontinuity = false
        var currentSegmentByteRange: ByteRange? = null
        val currentSegmentTags = mutableMapOf<String, String>()
        var currentSegmentEncryption: MediaSegmentEncryption? = null
        var currentSegmentSourceStartLine: Int? = null

        var currentVariantAttributes = mutableMapOf<String, String>()

        while (i < lines.size) {
            val sourceLine = lines[i++]
            val line = sourceLine.text.trim()

            if (line.startsWith("#")) {

                when {
                    line.startsWith("#EXT-X-VERSION:") -> {
                        version = line.substringAfter(":").trim().toInt()
                    }

                    line.startsWith("#EXT-X-TARGETDURATION:") -> {
                        targetDuration = line.substringAfter(":").trim().toInt()
                    }

                    line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                        mediaSequence = line.substringAfter(":").trim().toInt()
                    }

                    line.startsWith("#EXT-X-ENDLIST") -> {
                        isEndlist = true
                    }

                    line.startsWith("#EXTINF:") -> {

                        val valueStr = line.substringAfter(":")
                        currentSegmentDuration = valueStr.substringBefore(",").toFloat()
                        currentSegmentSourceStartLine = currentSegmentSourceStartLine ?: sourceLine.number
                        if (valueStr.contains(",")) {
                            currentSegmentTitle = valueStr.substringAfter(",").trim()
                        }
                    }

                    line == "#EXT-X-DISCONTINUITY" -> {
                        currentSegmentDiscontinuity = true
                        currentSegmentSourceStartLine = currentSegmentSourceStartLine ?: sourceLine.number
                    }

                    line.startsWith("#EXT-X-BYTERANGE:") -> {
                        currentSegmentByteRange = parseByteRange(line.substringAfter(":").trim())
                        currentSegmentSourceStartLine = currentSegmentSourceStartLine ?: sourceLine.number
                    }

                    line.startsWith("#EXT-X-KEY:") -> {
                        val keyAttributes = parseAttributes(line.substringAfter(":").trim())
                        val method = keyAttributes["METHOD"]?.trim().orEmpty()
                        currentSegmentEncryption = when {
                            method.isEmpty() -> throw M3uFormatException("Invalid EXT-X-KEY tag: missing METHOD")
                            method.equals("NONE", ignoreCase = true) -> null
                            else -> {
                                val keyUri = keyAttributes["URI"]
                                    ?: throw M3uFormatException("Invalid EXT-X-KEY tag: missing URI")
                                MediaSegmentEncryption(
                                    method = method,
                                    uri = UrlHelpers.computeAbsoluteUrl(baseUrl, keyUri),
                                    iv = keyAttributes["IV"],
                                    keyFormat = keyAttributes["KEYFORMAT"],
                                    keyFormatVersions = keyAttributes["KEYFORMATVERSIONS"],
                                )
                            }
                        }
                    }

                    line.startsWith("#EXT-X-STREAM-INF:") -> {
                        currentVariantAttributes = parseAttributes(line.substringAfter(":").trim())
                    }

                    else -> {

                        if (line.contains(":")) {
                            val tagName = line.substringBefore(":")
                            val tagValue = line.substringAfter(":")

                            if (currentSegmentDuration != null) {

                                currentSegmentTags[tagName] = tagValue
                                currentSegmentSourceStartLine = currentSegmentSourceStartLine ?: sourceLine.number
                            } else {

                                tags[tagName] = tagValue
                            }
                        } else {
                            if (currentSegmentDuration != null) {
                                currentSegmentTags[line] = ""
                                currentSegmentSourceStartLine = currentSegmentSourceStartLine ?: sourceLine.number
                            } else {
                                tags[line] = ""
                            }
                        }
                    }
                }
            } else {

                val uri = line

                if (currentVariantAttributes.isNotEmpty()) {

                    val absoluteUri = UrlHelpers.computeAbsoluteUrl(baseUrl, uri)
                    variants.add(
                        VariantStream(
                            uri = absoluteUri,
                            bandwidth = currentVariantAttributes["BANDWIDTH"]?.toIntOrNull() ?: 0,
                            averageBandwidth = currentVariantAttributes["AVERAGE-BANDWIDTH"]?.toIntOrNull(),
                            codecs = currentVariantAttributes["CODECS"]?.trim('"'),
                            resolution = currentVariantAttributes["RESOLUTION"],
                            frameRate = currentVariantAttributes["FRAME-RATE"]?.toFloatOrNull(),
                            audio = currentVariantAttributes["AUDIO"],
                            video = currentVariantAttributes["VIDEO"],
                            subtitles = currentVariantAttributes["SUBTITLES"],
                            closedCaptions = currentVariantAttributes["CLOSED-CAPTIONS"],
                            attributes = currentVariantAttributes.toMap(),
                        ),
                    )

                    currentVariantAttributes.clear()
                } else if (currentSegmentDuration != null) {

                    val absoluteUri = UrlHelpers.computeAbsoluteUrl(baseUrl, uri)
                    segments.add(
                        MediaSegment(
                            duration = currentSegmentDuration,
                            uri = absoluteUri,
                            title = currentSegmentTitle,
                            isDiscontinuity = currentSegmentDiscontinuity,
                            byteRange = currentSegmentByteRange,
                            encryption = currentSegmentEncryption,
                            tags = currentSegmentTags.toMap(),
                            sourceRange = M3u8SourceRange(
                                startLine = currentSegmentSourceStartLine ?: sourceLine.number,
                                endLine = sourceLine.number,
                            ),
                        ),
                    )

                    currentSegmentDuration = null
                    currentSegmentTitle = null
                    currentSegmentDiscontinuity = false
                    currentSegmentByteRange = null
                    currentSegmentTags.clear()
                    currentSegmentSourceStartLine = null
                }
            }
        }

        return if (variants.isNotEmpty()) {
            M3u8Playlist.MasterPlaylist(
                version = version,
                variants = variants,
                tags = tags,
            )
        } else {
            M3u8Playlist.MediaPlaylist(
                version = version,
                targetDuration = targetDuration,
                mediaSequence = mediaSequence,
                segments = segments,
                isEndlist = isEndlist,
                tags = tags,
            )
        }
    }

    private fun parseAttributes(attributesString: String): MutableMap<String, String> {
        val attributes = mutableMapOf<String, String>()
        var remaining = attributesString

        while (remaining.isNotEmpty()) {

            var inQuotes = false
            var commaPos = -1

            for (i in remaining.indices) {
                val c = remaining[i]
                if (c == '"') {
                    inQuotes = !inQuotes
                } else if (c == ',' && !inQuotes) {
                    commaPos = i
                    break
                }
            }

            val attribute = if (commaPos >= 0) {
                val attr = remaining.substring(0, commaPos)
                remaining = remaining.substring(commaPos + 1).trim()
                attr
            } else {
                val attr = remaining
                remaining = ""
                attr
            }

            val eqPos = attribute.indexOf('=')
            if (eqPos > 0) {
                val key = attribute.substring(0, eqPos).trim()
                val value = attribute.substring(eqPos + 1).trim()

                val cleanValue = if (value.startsWith("\"") && value.endsWith("\"") && value.length >= 2) {
                    value.substring(1, value.length - 1)
                } else {
                    value
                }

                attributes[key] = cleanValue
            }
        }

        return attributes
    }

    private fun parseByteRange(rangeValue: String): ByteRange {
        val parts = rangeValue.split("@")
        val length = parts[0].toLongOrNull() ?: throw M3uFormatException("Invalid byte range length: ${parts[0]}")
        val offset = if (parts.size > 1) {
            parts[1].toLongOrNull() ?: throw M3uFormatException("Invalid byte range offset: ${parts[1]}")
        } else {
            null
        }
        return ByteRange(length, offset)
    }
}

private data class M3u8SourceLine(
    val number: Int,
    val text: String,
)

private fun rewriteKeyLine(
    line: String,
    sourceUrl: String,
    encryptionLookup: Map<List<String?>, MediaSegmentEncryption>,
    resolveKeyUri: (encryption: MediaSegmentEncryption) -> String,
): String {
    val match = KEY_URI_ATTRIBUTE_PATTERN.find(line) ?: return line
    val rawUri = match.groupValues[2].ifEmpty { match.groupValues[3] }.trim()
    val attributes = parseAttributesPublic(line.substringAfter(":").trim())
    val method = attributes["METHOD"]?.trim().orEmpty()
    if (method.equals("NONE", ignoreCase = true)) {
        return line
    }

    val absoluteUri = UrlHelpers.computeAbsoluteUrl(sourceUrl, rawUri)
    val encryption = encryptionLookup[listOf(method, absoluteUri, attributes["IV"])]
        ?: throw M3uFormatException("Unable to map EXT-X-KEY line to parsed encryption: $line")
    return line.replaceRange(match.range, "URI=\"${resolveKeyUri(encryption)}\"")
}

private val KEY_URI_ATTRIBUTE_PATTERN = Regex("""URI=("([^"]*)"|([^,]+))""")

private fun parseAttributesPublic(attributesString: String): MutableMap<String, String> {
    val attributes = mutableMapOf<String, String>()
    var remaining = attributesString

    while (remaining.isNotEmpty()) {
        var inQuotes = false
        var commaPos = -1

        for (i in remaining.indices) {
            val c = remaining[i]
            if (c == '"') {
                inQuotes = !inQuotes
            } else if (c == ',' && !inQuotes) {
                commaPos = i
                break
            }
        }

        val attribute = if (commaPos >= 0) {
            val attr = remaining.substring(0, commaPos)
            remaining = remaining.substring(commaPos + 1).trim()
            attr
        } else {
            val attr = remaining
            remaining = ""
            attr
        }

        val eqPos = attribute.indexOf('=')
        if (eqPos > 0) {
            val key = attribute.substring(0, eqPos).trim()
            val value = attribute.substring(eqPos + 1).trim()
            val cleanValue = if (value.startsWith("\"") && value.endsWith("\"") && value.length >= 2) {
                value.substring(1, value.length - 1)
            } else {
                value
            }
            attributes[key] = cleanValue
        }
    }

    return attributes
}
