package com.wynime.app.data.repository

import androidx.compose.ui.unit.Dp
import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

sealed class WindowStateRepository : Repository() {
    abstract val flow: Flow<SavedWindowState?>
    abstract suspend fun update(states: SavedWindowState)
}

@Serializable
data class SavedWindowState(
    val x: @Serializable(DpSerializer::class) Dp,
    val y: @Serializable(DpSerializer::class) Dp,
    val width: @Serializable(DpSerializer::class) Dp,
    val height: @Serializable(DpSerializer::class) Dp,
) {
    fun hasUnspecified(): Boolean =
        x == Dp.Unspecified || y == Dp.Unspecified || width == Dp.Unspecified || height == Dp.Unspecified
}

private object DpSerializer : KSerializer<Dp> {
    override val descriptor = Float.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Dp) {
        encoder.encodeFloat(value.value)
    }

    override fun deserialize(decoder: Decoder): Dp = Dp(decoder.decodeFloat())
}

class WindowStateRepositoryImpl(
    private val store: DataStore<SavedWindowState?>,
) : WindowStateRepository() {
    override val flow: Flow<SavedWindowState?> = store.data

    override suspend fun update(states: SavedWindowState) {
        store.updateData {
            states
        }
    }

}
