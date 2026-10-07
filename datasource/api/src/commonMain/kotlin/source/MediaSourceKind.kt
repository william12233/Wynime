package com.wynime.datasources.api.source

import kotlinx.serialization.Serializable

@Serializable
enum class MediaSourceKind {

    WEB,

    LocalCache;

    companion object {

        val selectableEntries = listOf(WEB)
    }
}
