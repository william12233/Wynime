package com.wynime.app.ui.exploration.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.wynime.app.data.models.preference.NsfwMode
import com.wynime.app.data.models.subject.CanonicalTagKind
import com.wynime.app.data.models.subject.RatingCounts
import com.wynime.app.data.models.subject.RatingInfo
import com.wynime.app.data.models.subject.SubjectAiringInfo
import com.wynime.app.data.models.subject.SubjectAiringKind
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.models.subject.kind
import com.wynime.app.data.models.subject.nameCnOrName
import com.wynime.app.data.models.subject.nameOrNameCn
import com.wynime.app.data.network.LightRelatedCharacterInfo
import com.wynime.app.data.network.LightRelatedPersonInfo
import com.wynime.app.ui.foundation.LocalSubjectAppearanceSettings
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.paneVerticalPadding
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.exploration_search_staff_prefix
import com.wynime.app.ui.lang.subject_airing_total_episodes_completed
import com.wynime.app.ui.lang.subject_airing_total_episodes_scheduled
import com.wynime.app.ui.rating.RatingText
import com.wynime.app.ui.subject.getSubjectSeasonText
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString

@Immutable
class SubjectPreviewItemInfo(
    val subjectId: Int,
    val imageUrl: String,
    val title: String,
    val tags: String,
    val staff: String?,
    val actors: String?,
    val rating: RatingInfo,
    val nsfw: Boolean,

    val nsfwMode: NsfwMode,

    val hide: Boolean = false,

    val originalTitle: String = title,
) {
    companion object {

        suspend fun compute(
            subjectInfo: SubjectInfo,
            mainEpisodeCount: Int,
            nsfwModeSettings: NsfwMode,
            relatedPersonList: List<LightRelatedPersonInfo>?,
            characters: List<LightRelatedCharacterInfo>?,
            roleSet: RoleSet = RoleSet.Default,
            hide: Boolean = false,
        ): SubjectPreviewItemInfo {
            val airingInfo = SubjectAiringInfo.computeFromSubjectInfo(subjectInfo, mainEpisodeCount)
            val tags = buildString {
                if (subjectInfo.airDate.isValid) {
                    append(getSubjectSeasonText(subjectInfo.airDate))
                    append(" · ")
                }
                renderTotalEpisodesText(airingInfo)?.let {
                    append(it)
                    append(" · ")
                }

                val sourceTag = subjectInfo.tags
                    .firstOrNull { it.kind == CanonicalTagKind.Source }
                    ?.let { sequenceOf(it) }.orEmpty()

                val genreTags = subjectInfo.tags
                    .filterTo(ArrayList(10)) { it.kind == CanonicalTagKind.Genre }
                    .apply { sortByDescending { it.count } }
                    .asSequence()

                append(
                    (sourceTag + genreTags)
                        .take(3)
                        .joinToString(" / ") { it.name },
                )
            }
            val staff = relatedPersonList?.let {
                val persons = relatedPersonList.asSequence()
                    .filterByRoleSet(roleSet)
                    .sortedWithRoleSet(roleSet)
                    .take(4)
                    .toList()

                if (persons.isEmpty()) return@let null

                buildString {
                    append(getString(Lang.exploration_search_staff_prefix))
                    persons.forEachIndexed { index, relatedPersonInfo ->
                        append(relatedPersonInfo.name)
                        if (index != persons.lastIndex) {
                            append(" · ")
                        }
                    }
                }
            }

            val actors = null

            return SubjectPreviewItemInfo(
                subjectId = subjectInfo.subjectId,
                subjectInfo.imageLarge,
                subjectInfo.nameCnOrName,
                tags,
                staff,
                actors,
                rating = subjectInfo.ratingInfo,
                nsfw = subjectInfo.nsfw,
                nsfwMode = if (subjectInfo.nsfw) nsfwModeSettings else NsfwMode.DISPLAY,
                hide = hide,
                originalTitle = subjectInfo.nameOrNameCn,
            )
        }

        private suspend fun renderTotalEpisodesText(airingInfo: SubjectAiringInfo): String? {
            if (airingInfo.kind == SubjectAiringKind.UPCOMING && airingInfo.mainEpisodeCount == 0) {
                return null
            }
            return when (airingInfo.kind) {
                SubjectAiringKind.COMPLETED ->
                    getString(Lang.subject_airing_total_episodes_completed, airingInfo.mainEpisodeCount.toString())

                SubjectAiringKind.UPCOMING,
                SubjectAiringKind.ON_AIR,
                    ->
                    getString(Lang.subject_airing_total_episodes_scheduled, airingInfo.mainEpisodeCount.toString())
            }
        }
    }
}

@TestOnly
@Stable
internal val TestSubjectPreviewItemInfos
    get() = listOf(
        SubjectPreviewItemInfo(
            subjectId = 1,
            imageUrl = "https://example.com/image.jpg",
            title = "关于我转生变成史莱姆这档事 第三季",
            tags = "2024 年 10 月 · 全 24 话 · 奇幻 / 战斗",
            staff = "制作:  8bit · 中山敦史 · 泽野弘之",
            actors = "配音:  岡咲美保 · 前野智昭 · 古川慎",
            rating = RatingInfo(
                rank = 123,
                total = 100,
                count = RatingCounts.Zero,
                score = "6.7",
            ),
            nsfw = false,
            nsfwMode = NsfwMode.DISPLAY,
        ),
        SubjectPreviewItemInfo(
            subjectId = 2,
            imageUrl = "https://example.com/image.jpg",
            title = "关于我转生变成史莱姆这档事 第三季",
            tags = "2024 年 10 月 · 全 24 话 · 奇幻 / 战斗",
            staff = "制作:  8bit · 中山敦史 · 泽野弘之",
            actors = "配音:  岡咲美保 · 前野智昭 · 古川慎",
            rating = RatingInfo(
                rank = 123,
                total = 100,
                count = RatingCounts.Zero,
                score = "6.7",
            ),
            nsfw = true,
            nsfwMode = NsfwMode.BLUR,
        ),
    )

@Composable
fun SubjectPreviewItem(
    selected: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    info: SubjectPreviewItemInfo,
    modifier: Modifier = Modifier,
    image: @Composable () -> Unit = {
        SubjectItemDefaults.Image(info.imageUrl)
    },
    title: @Composable (Int) -> Unit = { maxLines ->
        val useOriginalTitle = LocalSubjectAppearanceSettings.current.useOriginalTitle
        Text(if (useOriginalTitle) info.originalTitle else info.title, maxLines = maxLines)
    },
) {
    SubjectItemLayout(
        selected = selected,
        onClick = onClick,
        image = image,
        title = title,
        tags = {
            Text(info.tags, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        extraInfo = {
            info.staff?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            info.actors?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        },
        rating = {
            RatingText(info.rating)
        },
        actions = {

        },
        modifier,
    )
}

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewSubjectPreviewItem() {
    val info = TestSubjectPreviewItemInfos[0]
    SubjectPreviewItem(
        selected = false,
        onClick = { },
        onPlay = { },
        info = info,
        Modifier
            .fillMaxWidth()
            .padding(vertical = currentWindowAdaptiveInfo1().windowSizeClass.paneVerticalPadding / 2),
        image = {
            SubjectItemDefaults.Image(
                info.imageUrl,
            )
        },
        title = { maxLines ->
            Text(
                info.title,
                maxLines = maxLines,
            )
        },
    )
}

@Composable
@PreviewLightDark
private fun PreviewSubjectItemLayout() = ProvideCompositionLocalsForPreview {
    SubjectItemLayout(
        selected = false,
        {},
        image = { SubjectItemDefaults.Image("a", Modifier.fillMaxSize(), null) },
        title = { maxLines ->
            Text("关于我转生变成史莱姆这档事 第三季", maxLines = maxLines)
        },
        tags = { Text("2024 年 10 月 · 全 24 话 · 奇幻 / 战斗") },
        extraInfo = {
            Text("配音:  岡咲美保 · 前野智昭 ·  古川慎")
            Text("制作:  8bit · 中山敦史 · 泽野弘之 ")
        },
        rating = {},
        actions = {
            SubjectItemDefaults.ActionPlay({})
        },
    )
}
