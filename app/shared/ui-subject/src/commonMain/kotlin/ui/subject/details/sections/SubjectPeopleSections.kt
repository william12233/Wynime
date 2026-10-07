package com.wynime.app.ui.subject.details.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.wynime.app.data.models.subject.RatingInfo
import com.wynime.app.data.models.subject.RelatedCharacterInfo
import com.wynime.app.data.models.subject.RelatedPersonInfo
import com.wynime.app.data.models.subject.SubjectCollectionStats
import com.wynime.app.data.models.subject.nameCn
import com.wynime.app.ui.foundation.avatar.AvatarImage
import com.wynime.app.ui.foundation.ifThen
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.subject_details_characters
import com.wynime.app.ui.lang.subject_details_characters_with_count
import com.wynime.app.ui.lang.subject_details_rating_summary
import com.wynime.app.ui.lang.subject_details_staff
import com.wynime.app.ui.lang.subject_details_staff_with_count
import com.wynime.app.ui.lang.subject_details_stat_collected
import com.wynime.app.ui.lang.subject_details_stat_watching
import com.wynime.app.ui.lang.subject_details_stat_wish
import com.wynime.app.ui.lang.subject_details_view_all
import com.wynime.app.ui.rating.FiveRatingStars
import com.wynime.app.ui.rating.renderScore
import com.wynime.app.ui.subject.details.components.PersonCard
import com.wynime.app.ui.subject.person.PeoplePreviewTarget
import com.wynime.app.ui.subject.person.rememberPeopleClickHandler
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
fun SubjectCollectionStatsRow(
    stats: SubjectCollectionStats,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCell(stats.collect, stringResource(Lang.subject_details_stat_collected), Modifier.weight(1f))
        StatCell(stats.doing, stringResource(Lang.subject_details_stat_watching), Modifier.weight(1f))
        StatCell(stats.wish, stringResource(Lang.subject_details_stat_wish), Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(count: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            remember(count) { groupThousands(count) },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

internal fun groupThousands(n: Int): String {
    val s = n.toString()
    val neg = s.startsWith("-")
    val digits = if (neg) s.substring(1) else s
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return if (neg) "-$grouped" else grouped
}

@Composable
fun SubjectRatingSummary(
    ratingInfo: RatingInfo,
    modifier: Modifier = Modifier,
    scoreStyle: TextStyle = MaterialTheme.typography.displaySmall,
    starSize: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .clip(MaterialTheme.shapes.small)
            .ifThen(onClick != null) { clickable(onClick = checkNotNull(onClick)) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            remember(ratingInfo.score) { renderScore(ratingInfo.score) },
            style = scoreStyle,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FiveRatingStars(
                remember(ratingInfo.score) { ratingInfo.scoreFloat.roundToInt() },
                starSize = starSize,
            )
            Text(
                stringResource(
                    Lang.subject_details_rating_summary,
                    ratingInfo.rank.toString(),
                    remember(ratingInfo.total) { groupThousands(ratingInfo.total) },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun CharactersSection(
    exposedCharacters: LazyPagingItems<RelatedCharacterInfo>,
    allCharacters: LazyPagingItems<RelatedCharacterInfo>,
    totalCharactersCount: Int?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemWidth: Dp = 76.dp,
    avatarSize: Dp = 76.dp,
    itemSpacing: Dp = 12.dp,
) {
    if (exposedCharacters.itemCount == 0) return
    var showAll by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            stringResource(Lang.subject_details_characters),
            actionLabel = stringResource(Lang.subject_details_view_all),
            onAction = { showAll = true },
            modifier = Modifier.padding(contentPadding),
        )
        val onClickCharacter = rememberPeopleClickHandler()
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            contentPadding = contentPadding,
        ) {
            items(exposedCharacters.itemCount) { i ->
                val item = exposedCharacters[i] ?: return@items
                CharacterAvatarCell(
                    item, itemWidth, avatarSize,
                    onClick = { onClickCharacter(PeoplePreviewTarget.Character(item.character.id)) },
                )
            }
        }
    }
    if (showAll) {
        val onClickCharacter = rememberPeopleClickHandler()
        ViewAllSheet(
            title = totalCharactersCount?.let { stringResource(Lang.subject_details_characters_with_count, it) }
                ?: stringResource(Lang.subject_details_characters),
            items = allCharacters,
            onDismissRequest = { showAll = false },
        ) {
            PersonCard(
                it,
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable {
                        showAll = false
                        onClickCharacter(PeoplePreviewTarget.Character(it.character.id))
                    },
            )
        }
    }
}

@Composable
private fun CharacterAvatarCell(
    info: RelatedCharacterInfo,
    itemWidth: Dp,
    avatarSize: Dp,
    onClick: () -> Unit,
) {
    val cv = remember(info) { info.character.actors.firstOrNull()?.displayName }
    Column(
        Modifier
            .width(itemWidth)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {

        Box(Modifier.size(avatarSize).clip(CircleShape)) {
            AvatarImage(
                info.character.imageMedium,
                Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
            )
        }
        Text(
            info.character.displayName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            cv ?: info.role.nameCn,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun StaffSection(
    exposedStaff: LazyPagingItems<RelatedPersonInfo>,
    allStaff: LazyPagingItems<RelatedPersonInfo>,
    totalStaffCount: Int?,
    modifier: Modifier = Modifier,
    gridColumns: Int? = null,
    maxItems: Int = if (gridColumns != null) 6 else 10,
) {
    if (exposedStaff.itemCount == 0) return
    var showAll by rememberSaveable { mutableStateOf(false) }
    val onClickPerson = rememberPeopleClickHandler()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            stringResource(Lang.subject_details_staff),
            actionLabel = stringResource(Lang.subject_details_view_all),
            onAction = { showAll = true },
        )
        if (gridColumns != null) {
            StaffGrid(
                exposedStaff, columns = gridColumns, maxItems = maxItems,
                onClick = { onClickPerson(PeoplePreviewTarget.Person(it.personInfo.id)) },
            )
        } else {
            StaffKeyValueList(
                exposedStaff, maxItems = maxItems,
                onClick = { onClickPerson(PeoplePreviewTarget.Person(it.personInfo.id)) },
            )
        }
    }
    if (showAll) {
        ViewAllSheet(
            title = totalStaffCount?.let { stringResource(Lang.subject_details_staff_with_count, it) }
                ?: stringResource(Lang.subject_details_staff),
            items = allStaff,
            onDismissRequest = { showAll = false },
        ) {
            PersonCard(
                it,
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable {
                        showAll = false
                        onClickPerson(PeoplePreviewTarget.Person(it.personInfo.id))
                    },
            )
        }
    }
}

@Composable
private fun StaffGrid(
    staff: LazyPagingItems<RelatedPersonInfo>,
    columns: Int,
    maxItems: Int,
    onClick: (RelatedPersonInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        maxItemsInEachRow = columns,
    ) {
        val count = minOf(staff.itemCount, maxItems)
        for (i in 0 until count) {
            val person = staff[i] ?: continue
            Column(
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onClick(person) },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    person.position.nameCn ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    person.personInfo.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        val remainder = count % columns
        if (remainder != 0) {
            repeat(columns - remainder) { Box(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun StaffKeyValueList(
    staff: LazyPagingItems<RelatedPersonInfo>,
    maxItems: Int,
    onClick: (RelatedPersonInfo) -> Unit,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 78.dp,
    rowSpacing: Dp = 12.dp,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
        for (i in 0 until minOf(staff.itemCount, maxItems)) {
            val person = staff[i] ?: continue
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onClick(person) },
            ) {
                Text(
                    person.position.nameCn ?: "",
                    Modifier.width(labelWidth),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    person.personInfo.displayName,
                    Modifier.weight(1f).padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
