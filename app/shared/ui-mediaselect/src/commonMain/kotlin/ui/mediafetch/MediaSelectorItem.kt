package com.wynime.app.ui.mediafetch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wynime.app.domain.media.selector.MediaExclusionReason
import com.wynime.app.domain.media.selector.UnsafeOriginalMediaAccess
import com.wynime.app.platform.currentWynimeBuildConfig
import com.wynime.app.tools.formatDateTime
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.cache_unknown
import com.wynime.app.ui.lang.media_selector_item_episode_mismatch
import com.wynime.app.ui.lang.media_selector_item_no_subtitle
import com.wynime.app.ui.lang.media_selector_item_season_mismatch
import com.wynime.app.ui.lang.media_selector_item_single_episode_resource
import com.wynime.app.ui.lang.media_selector_item_subject_title_mismatch
import com.wynime.app.ui.lang.media_selector_item_unsupported_playback
import com.wynime.app.ui.media.rememberMediaDetailsStrings
import com.wynime.app.ui.media.renderSubtitleLanguage
import com.wynime.app.ui.settings.rendering.MediaSourceIcon
import com.wynime.app.ui.settings.rendering.MediaSourceIcons
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.topic.FileSize
import org.jetbrains.compose.resources.stringResource

@OptIn(UnsafeOriginalMediaAccess::class)
@Composable
internal fun MediaSelectorItem(
    group: MediaGroup,
    groupState: MediaGroupState,
    mediaSourceInfoProvider: MediaSourceInfoProvider,
    selected: Boolean,
    onSelect: (Media) -> Unit,
    preferredResolution: () -> String?,
    onPreferResolution: (String) -> Unit,
    preferredSubtitleLanguageId: () -> String?,
    onPreferSubtitleLanguageId: (String) -> Unit,
    modifier: Modifier = Modifier,
) {

    val media: Media = group.first.original
    val mediaDetailsStrings = rememberMediaDetailsStrings()
    val noSubtitleText = stringResource(Lang.media_selector_item_no_subtitle)
    val singleEpisodeResourceText = stringResource(Lang.media_selector_item_single_episode_resource)
    val unsupportedPlaybackText = stringResource(Lang.media_selector_item_unsupported_playback)
    val seasonMismatchText = stringResource(Lang.media_selector_item_season_mismatch)
    val subjectTitleMismatchText = stringResource(Lang.media_selector_item_subject_title_mismatch)
    val episodeMismatchText = stringResource(Lang.media_selector_item_episode_mismatch)

    val reasonText = group.exclusionReason?.let { reason ->
        if (currentWynimeBuildConfig.isDebug) {
            reason.toString()
        } else {
            when (reason) {
                is MediaExclusionReason.EpisodeMismatch -> episodeMismatchText
                MediaExclusionReason.MediaWithoutSubtitle -> noSubtitleText
                is MediaExclusionReason.SingleEpisodeForCompleteSubject -> singleEpisodeResourceText
                MediaExclusionReason.UnsupportedByPlatformPlayer -> unsupportedPlaybackText
                MediaExclusionReason.FromSequelSeason -> seasonMismatchText
                MediaExclusionReason.FromSeriesSeason -> seasonMismatchText
                MediaExclusionReason.SubjectNameMismatch -> subjectTitleMismatchText
            }
        }
    }

    MediaSelectorItemLayout(
        selected = selected,
        onClick = { onSelect(media) },
        title = { Text(media.originalTitle) },
        labels = {

            if (media.properties.size != FileSize.Zero && media.properties.size != FileSize.Unspecified) {
                InputChip(
                    selected = false,
                    onClick = {             },
                    label = { Text(media.properties.size.toString()) },
                )
            }

            InputChip(
                selected = false,
                onClick = { onPreferResolution(media.properties.resolution) },
                label = { Text(media.properties.resolution) },
                enabled = preferredResolution() != media.properties.resolution,
            )

            media.properties.subtitleLanguageIds.forEach { languageId ->
                InputChip(
                    selected = false,
                    onClick = { onPreferSubtitleLanguageId(languageId) },
                    label = { Text(renderSubtitleLanguage(languageId, mediaDetailsStrings)) },
                    enabled = preferredSubtitleLanguageId() != languageId,
                )
            }

            reasonText?.let {
                InputChip(
                    selected = false,
                    onClick = {             },
                    label = { Text(it) },
                    colors = InputChipDefaults.inputChipColors(
                        labelColor = MaterialTheme.colorScheme.error,
                    ),
                    border = InputChipDefaults.inputChipBorder(
                        enabled = true,
                        selected = false,
                        borderColor = MaterialTheme.colorScheme.error,
                    ),
                )
            }
        },

        exposedMediaSourceMenu = {
            ExposedMediaSourceMenu(
                group = group,
                groupState = groupState,
                mediaSourceInfoProvider = mediaSourceInfoProvider,
                onSelect = onSelect,
            )
        },
        alliance = {
            Text(
                media.properties.alliance,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        },
        publishedTime = {
            Text(
                formatDateTime(media.publishedTime, showTime = false),
                maxLines = 1,
                softWrap = false,
            )
        },
        modifier = modifier,
    )
}

@Composable
fun MediaSelectorItemLayout(
    selected: Boolean,
    onClick: () -> Unit,
    title: @Composable () -> Unit,
    labels: @Composable RowScope.() -> Unit,
    exposedMediaSourceMenu: @Composable () -> Unit,
    alliance: @Composable () -> Unit,
    publishedTime: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.width(IntrinsicSize.Min)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = if (!selected) BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline,
        ) else null,
    ) {
        val horizontalPadding = 16.dp

        Column(
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        ) {

            ProvideTextStyle(MaterialTheme.typography.titleSmall) {
                Row(Modifier.padding(horizontal = horizontalPadding)) {
                    title()
                }
            }

            FlowRow(
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = labels,
            )

            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                Row(
                    modifier = Modifier
                        .padding(
                            start = horizontalPadding - 8.dp,
                            end = horizontalPadding,
                        )
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        exposedMediaSourceMenu()
                        Box(
                            Modifier.weight(1f, fill = false),
                            contentAlignment = Alignment.Center,
                        ) {
                            alliance()
                        }
                    }

                    Box(Modifier.padding(start = 16.dp)) {
                        publishedTime()
                    }
                }
            }
        }
    }
}

@OptIn(UnsafeOriginalMediaAccess::class)
@Composable
private fun ExposedMediaSourceMenu(
    group: MediaGroup,
    groupState: MediaGroupState,
    mediaSourceInfoProvider: MediaSourceInfoProvider,
    onSelect: (Media) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }
    val unknownText = stringResource(Lang.cache_unknown)
    ExposedDropdownMenuBox(showMenu, { showMenu = it }, modifier) {
        val currentItem = groupState.selectedItem ?: group.first.original
        val currentSourceInfo by mediaSourceInfoProvider.rememberMediaSourceInfo(currentItem.mediaSourceId)
        TextField(
            value = currentSourceInfo?.displayName ?: unknownText,
            onValueChange = {},
            Modifier
                .widthIn(min = 48.dp)
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            maxLines = 1,
            leadingIcon = {
                Icon(MediaSourceIcons.location(currentItem.location, currentItem.kind), null)
            },
            trailingIcon = if (group.list.size > 1) {
                {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = showMenu,
                        Modifier.menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable),
                    )
                }
            } else null,
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
            ),
        )
        ExposedDropdownMenu(showMenu, { showMenu = false }) {
            for (maybeExcluded in group.list) {
                val item = maybeExcluded.original
                val sourceInfo by mediaSourceInfoProvider.rememberMediaSourceInfo(item.mediaSourceId)
                DropdownMenuItem(
                    text = { Text(sourceInfo?.displayName ?: unknownText) },
                    leadingIcon = { MediaSourceIcon(sourceInfo, Modifier.size(24.dp)) },
                    onClick = {
                        groupState.selectedItem = item
                        onSelect(item)
                        showMenu = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
