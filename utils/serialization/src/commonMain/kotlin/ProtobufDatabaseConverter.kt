package com.wynime.utils.serialization

import kotlinx.serialization.builtins.IntArraySerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.protobuf.ProtoBuf

val DatabaseProtoBuf
    get() = ProtoBuf {
        serializersModule = SerializersModule {
            contextual(IntArray::class, IntArraySerializer())
        }
    }