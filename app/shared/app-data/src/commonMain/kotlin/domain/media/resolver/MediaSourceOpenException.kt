package com.wynime.app.domain.media.resolver

import com.wynime.app.domain.media.player.data.MediaDataProvider

enum class OpenFailures {

    NO_MATCHING_FILE,

    UNSUPPORTED_VIDEO_SOURCE,

    ENGINE_DISABLED,
}

class MediaSourceOpenException(
    val reason: OpenFailures,
    extraMessage: String = "",
    override val cause: Throwable? = null,
) : Exception("Failed to open video due to $reason. $extraMessage", cause)
