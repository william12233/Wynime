package com.wynime.app.domain.media.fetch

import com.wynime.datasources.api.source.MediaSourceInfo

data class MediaSourceInfoWithId(
    val instanceId: String,
    val mediaSourceId: String,
    val info: MediaSourceInfo,
)
