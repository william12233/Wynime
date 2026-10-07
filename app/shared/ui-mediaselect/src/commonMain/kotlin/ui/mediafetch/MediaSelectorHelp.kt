package com.wynime.app.ui.mediafetch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.widgets.RichDialogLayout
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.cache_details_source_online
import com.wynime.app.ui.lang.media_selector_help_source_types
import com.wynime.app.ui.lang.media_selector_help_title
import com.wynime.app.ui.lang.media_selector_help_web_description
import com.wynime.app.ui.lang.subject_episode_close
import com.wynime.app.ui.settings.rendering.MediaSourceIcons
import org.jetbrains.compose.resources.stringResource

@Composable
fun MediaSelectorHelp(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val titleText = stringResource(Lang.media_selector_help_title)
    val closeText = stringResource(Lang.subject_episode_close)
    val sourceTypesText = stringResource(Lang.media_selector_help_source_types)
    val onlineText = stringResource(Lang.cache_details_source_online)
    val webDescriptionText = stringResource(Lang.media_selector_help_web_description)
    RichDialogLayout(
        title = { Text(titleText) },
        buttons = {
            TextButton(onDismissRequest) {
                Text(closeText)
            }
        },
        modifier,
    ) {
        Text(sourceTypesText, style = MaterialTheme.typography.titleMedium)

        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExplainerCard(
                title = { Text(onlineText) },
                Modifier.weight(1f),
                icon = {
                    Icon(MediaSourceIcons.KindWeb, null)
                },
            ) {
                Text(webDescriptionText)
            }
        }
    }
}

@Composable
fun ExplainerCard(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    OutlinedCard(modifier) {
        Column(Modifier.padding(all = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(
                Modifier.align(Alignment.CenterHorizontally),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                icon?.let {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        it()
                    }
                }
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    title()
                }
            }

            ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                content()
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewMediaSelectorHelp() {
    ProvideCompositionLocalsForPreview {
        MediaSelectorHelp({})
    }
}
