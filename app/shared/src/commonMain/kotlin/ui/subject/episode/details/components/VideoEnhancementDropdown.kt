package com.wynime.app.ui.subject.episode.details.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.video_player_off
import com.wynime.app.ui.lang.video_player_performance
import com.wynime.app.ui.lang.video_player_quality
import com.wynime.app.ui.lang.video_player_video_enhancement
import com.wynime.app.videoplayer.videoenhancement.VideoEnhancementController
import com.wynime.app.videoplayer.videoenhancement.VideoEnhancementMode
import org.jetbrains.compose.resources.stringResource

@Composable
fun VideoEnhancementDropdown(
    videoEnhancement: VideoEnhancementController,
    showDropdown: Boolean,
    onDismissRequest: () -> Unit,
) {
    val mode by videoEnhancement.mode.collectAsState()
    val title = stringResource(Lang.video_player_video_enhancement)
    val performanceText = stringResource(Lang.video_player_performance)
    val qualityText = stringResource(Lang.video_player_quality)
    val offText = stringResource(Lang.video_player_off)

    DropdownMenu(
        expanded = showDropdown,
        onDismissRequest = onDismissRequest,
    ) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleSmall,
        )
        listOf(
            VideoEnhancementMode.PERFORMANCE,
            VideoEnhancementMode.QUALITY,
            VideoEnhancementMode.OFF,
        ).forEach { item ->
            DropdownMenuItem(
                text = {
                    Text(
                        when (item) {
                            VideoEnhancementMode.OFF -> offText
                            VideoEnhancementMode.PERFORMANCE -> performanceText
                            VideoEnhancementMode.QUALITY -> qualityText
                        },
                    )
                },
                onClick = {
                    videoEnhancement.setMode(item)
                    onDismissRequest()
                },
                enabled = item != mode,
            )
        }
    }
}