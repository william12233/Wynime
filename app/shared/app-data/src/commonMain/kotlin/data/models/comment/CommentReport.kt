package com.wynime.app.data.models.comment

enum class CommentReportTargetType {
    EPISODE_COMMENT,
    SUBJECT_REVIEW,

    PERSON_COMMENT,

    CHARACTER_COMMENT,
}

enum class CommentReportReason {
    SPAM,
    HARASSMENT,
    SPOILER,
    NSFW,
    ILLEGAL,
    OTHER,
}
