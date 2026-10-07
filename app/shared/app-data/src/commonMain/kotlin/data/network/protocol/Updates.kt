package com.wynime.app.data.network.protocol

import kotlinx.serialization.Serializable

@Serializable
data class ReleaseUpdatesResponse(
    val versions: List<String>
)

@Serializable
data class ReleaseUpdatesDetailedResponse(
    val updates: List<UpdateInfo>
)

@Serializable
data class UpdateInfo(
    val version: String,
    val downloadUrlAlternatives: List<String>,
    val publishTime: Long,
    val description: String,
)

@Serializable
enum class ReleaseClass {

    ALPHA,

    BETA,

    RC,

    STABLE;

    override fun toString(): String {
        return this.name.lowercase()
    }

    fun moreStableThan(other: ReleaseClass): Boolean {
        return this.ordinal >= other.ordinal
    }

    companion object {

        val enabledEntries by lazy {
            entries.filter { it != RC }.sortedDescending()
        }

        fun fromStringOrNull(value: String): ReleaseClass? {
            return value.let {
                entries.firstOrNull { it.toString() == value.lowercase() }
            }
        }
    }
}