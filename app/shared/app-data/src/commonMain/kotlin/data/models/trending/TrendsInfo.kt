/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.trending

import me.him188.ani.client.models.AniTrendingSubject
import kotlinx.datetime.LocalDate

data class TrendsInfo(
    val subjects: List<TrendingSubjectInfo>,
    val total: Int = subjects.size,
)

/**
 * @see AniTrendingSubject
 */
data class TrendingSubjectInfo(
    val bangumiId: Int,
    val nameCn: String,
    val imageLarge: String,
    val name: String = nameCn,
    val nsfw: Boolean = false,
    val score: Double = 0.0,
    val scoreCount: Int = 0,
    val rank: Int = 0,
    val tags: List<String> = emptyList(),
    val airDate: LocalDate? = null,
    val trendingCount: Int = 0,
)
