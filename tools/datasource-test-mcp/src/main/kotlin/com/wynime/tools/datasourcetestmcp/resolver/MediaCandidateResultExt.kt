package com.wynime.tools.datasourcetestmcp.resolver

import com.wynime.datasources.api.source.MediaMatch

internal fun MediaMatch.toCandidateResult(): MediaCandidateResult {
    return MediaCandidateResult(
        mediaId = media.mediaId,
        mediaSourceId = media.mediaSourceId,
        originalTitle = media.originalTitle,
        originalUrl = media.originalUrl,
        downloadUri = media.download.uri,
        downloadType = media.download::class.simpleName ?: "unknown",
        kind = media.kind.toString(),
        matchKind = kind.toString(),
        episodeRange = media.episodeRange?.toString(),
    )
}
