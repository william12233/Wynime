package com.wynime.app.data.network.protocol

import kotlinx.serialization.Serializable

@Serializable
data class WynimeUser(
    val id: String,
    val nickname: String,
    val smallAvatar: String,
    val mediumAvatar: String,
    val largeAvatar: String,
    val registerTime: Long,
    val lastLoginTime: Long,
    val clientVersion: String? = null,
    val clientPlatforms: Set<String> = emptySet(),
)
