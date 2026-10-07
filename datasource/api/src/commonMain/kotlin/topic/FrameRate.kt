package com.wynime.datasources.api.topic

import kotlinx.serialization.Serializable

@Serializable
data class FrameRate(
    val value: Int,
) {
    companion object {
        val F60 = FrameRate(60)

        fun tryParse(text: String): FrameRate? {

            if (text.contains("@60")) {
                return F60
            }
            if (text.contains("1080P60")) {
                return F60
            }
            if (text.contains("2160P60")) {
                return F60
            }
            if (text.contains("60FPS")) {
                return F60
            }
            if (text.contains("60 FPS")) {
                return F60
            }
            return null
        }
    }
}