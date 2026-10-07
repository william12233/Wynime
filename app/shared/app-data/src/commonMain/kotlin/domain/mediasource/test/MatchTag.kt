package com.wynime.app.domain.mediasource.test

import androidx.compose.runtime.Immutable

@Immutable
class MatchTag(
    val value: String,

    val isMissing: Boolean = false,

    val isMatch: Boolean? = null,
)

class MatchTagsBuilder
@PublishedApi
internal constructor() {
    private val list = mutableListOf<MatchTag>()

    fun emit(value: String, isMissing: Boolean = false, isMatch: Boolean? = null) {
        list.add(MatchTag(value, isMissing, isMatch))
    }

    @PublishedApi
    internal fun build(): List<MatchTag> = list
}

inline fun buildMatchTags(builder: MatchTagsBuilder.() -> Unit): List<MatchTag> =
    MatchTagsBuilder().apply(builder).build()
