package com.wynime.app.domain.media.resolver

import com.wynime.datasources.api.MediaExtraFiles

internal fun MediaExtraFiles.toMediampMediaExtraFiles(): org.openani.mediamp.source.MediaExtraFiles {
    return org.openani.mediamp.source.MediaExtraFiles(
        subtitles = subtitles.map {
            org.openani.mediamp.source.Subtitle(
                uri = it.uri,
                mimeType = it.mimeType,
                language = it.language,
            )
        },
    )
}

