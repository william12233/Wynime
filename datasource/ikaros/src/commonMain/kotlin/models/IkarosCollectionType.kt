package com.wynime.datasources.ikaros.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(IkarosCollectionType.AsIntSerializer::class)
enum class IkarosCollectionType(val id: Int) {

    WISH(1),

    DOING(2),

    DONE(3),

    SHELVE(4),

    DISCARD(5);
    ;

    internal object AsIntSerializer : KSerializer<IkarosCollectionType> {
        override val descriptor: SerialDescriptor = Int.serializer().descriptor

        override fun deserialize(decoder: Decoder): IkarosCollectionType {
            val raw = Int.serializer().deserialize(decoder)
            return entries.firstOrNull { it.id == raw }
                ?: throw IllegalStateException("Unknown IkarosCollectionType: $raw")
        }

        override fun serialize(encoder: Encoder, value: IkarosCollectionType) {
            return Int.serializer().serialize(encoder, value.id)
        }
    }
}