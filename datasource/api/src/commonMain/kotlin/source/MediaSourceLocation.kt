package com.wynime.datasources.api.source

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import com.wynime.datasources.api.Media

@Serializable(MediaSourceLocation.AsStringSerializer::class)
sealed class MediaSourceLocation {

    data object Online : MediaSourceLocation()

    data object Lan : MediaSourceLocation()

    data object Local : MediaSourceLocation()

    companion object {

        val entries get() = listOf(Online, Lan, Local)
    }

    internal object AsStringSerializer : KSerializer<MediaSourceLocation> {
        override val descriptor = String.serializer().descriptor

        override fun serialize(encoder: Encoder, value: MediaSourceLocation) {
            encoder.encodeString(
                getText(value),
            )
        }

        private fun getText(value: MediaSourceLocation) = when (value) {
            Online -> "ONLINE"
            Lan -> "LAN"
            Local -> "LOCAL"
        }

        override fun deserialize(decoder: Decoder): MediaSourceLocation {
            val string = decoder.decodeString()
            for (entry in entries) {
                if (getText(entry) == string) {
                    return entry
                }
            }
            throw SerializationException("Unknown MediaSourceLocation: $string")
        }
    }
}

fun MediaSourceLocation.isLowEffort(): Boolean = this is MediaSourceLocation.Lan || this is MediaSourceLocation.Local
