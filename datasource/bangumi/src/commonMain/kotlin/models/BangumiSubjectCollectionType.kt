package com.wynime.datasources.bangumi.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable(AsInt::class)
enum class BangumiSubjectCollectionType(val value: Int) {

    @SerialName(value = "1")
    Wish(1),

    @SerialName(value = "2")
    Done(2),

    @SerialName(value = "3")
    Doing(3),

    @SerialName(value = "4")
    OnHold(4),

    @SerialName(value = "5")
    Dropped(5);

    override fun toString(): String = value.toString()
}

private object AsInt : EnumValueSerializer<BangumiSubjectCollectionType>(
    "BangumiSubjectCollectionType", BangumiSubjectCollectionType.entries, { it.value },
)
