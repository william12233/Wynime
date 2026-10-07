package com.wynime.datasources.api.topic

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(Resolution.Serializer::class)
class Resolution private constructor(
    val id: String,
    val size: Int,
    private val otherNames: List<String>,
    val displayName: String = id,
) : Comparable<Resolution> {
    constructor(
        id: String,
        size: Int,
        vararg otherNames: String,
        displayName: String = id,
    ) : this(id, size, otherNames.toList(), displayName)

    override fun compareTo(other: Resolution): Int = this.size.compareTo(other.size)

    override fun toString(): String {
        return displayName
    }

    companion object {
        val R240P = Resolution("240P", 240, "x240")
        val R360P = Resolution("360P", 360, "x360")
        val R480P = Resolution("480P", 480, "x480")
        val R560P = Resolution("560P", 560, "x560")
        val R720P = Resolution("720P", 720, "x720")
        val R1080P = Resolution("1080P", 1080, "x1080")
        val R1440P = Resolution("1440P", 1440, "x1440", displayName = "2K")
        val R2160P = Resolution("2160P", 2160, "x2160", displayName = "4K")

        val entries = listOf(
            R240P, R360P, R480P, R560P, R720P, R1080P, R1440P, R2160P,
        )

        fun tryParse(text: String): Resolution? {
            for (entry in entries) {
                if (text.contains(entry.id, ignoreCase = true)
                    || entry.otherNames.any { text.contains(it, ignoreCase = true) }
                ) {
                    return entry
                }
            }
            return null
        }
    }

    internal object Serializer : KSerializer<Resolution> {
        override val descriptor: SerialDescriptor = String.serializer().descriptor

        override fun deserialize(decoder: Decoder): Resolution {
            return tryParse(String.serializer().deserialize(decoder)) ?: R240P
        }

        override fun serialize(encoder: Encoder, value: Resolution) {
            return String.serializer().serialize(encoder, value.id)
        }

    }
}