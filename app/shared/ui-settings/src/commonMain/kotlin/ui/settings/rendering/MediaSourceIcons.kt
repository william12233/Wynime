@file:Suppress("MemberVisibilityCanBePrivate")

package com.wynime.app.ui.settings.rendering

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.runtime.Stable
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import com.wynime.datasources.api.source.MediaSourceInfo
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.datasources.api.source.MediaSourceLocation

object MediaSourceIcons {

    inline val KindWeb get() = Icons.Rounded.Public
    inline val KindLocal get() = Icons.Rounded.DownloadDone

    @Stable
    fun kind(kind: MediaSourceKind) = when (kind) {
        MediaSourceKind.WEB -> KindWeb
        MediaSourceKind.LocalCache -> KindLocal
    }

    inline val LocationLocal get() = Icons.Rounded.DownloadDone
    inline val LocationLan get() = Icons.Rounded.Radar
    inline val LocationOnline get() = Icons.Rounded.Public

    @Stable
    fun location(location: MediaSourceLocation, kind: MediaSourceKind) = when (location) {
        MediaSourceLocation.Local -> LocationLocal
        MediaSourceLocation.Lan -> LocationLan
        MediaSourceLocation.Online -> kind(kind)
    }

    @Stable
    fun getDefaultIconUrl(info: MediaSourceInfo): String {
        return URLBuilder().apply {
            takeFrom("https://ui-avatars.com/api")
            parameters.append("name", info.displayName)
        }.buildString()
    }
}
