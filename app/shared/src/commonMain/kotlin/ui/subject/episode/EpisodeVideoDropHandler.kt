package com.wynime.app.ui.subject.episode

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.wynime.app.domain.media.DroppedFileMedia
import com.wynime.app.ui.foundation.DragAndDropContent
import com.wynime.app.ui.foundation.WindowDropCardContent
import com.wynime.app.ui.foundation.WindowDropHandler
import com.wynime.app.ui.foundation.WindowDropPreview
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.episode_drop_video_description
import com.wynime.app.ui.lang.episode_drop_video_supported_hint
import com.wynime.app.ui.lang.episode_drop_video_title
import com.wynime.app.ui.lang.episode_drop_video_unknown_name
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.name
import org.jetbrains.compose.resources.stringResource

class EpisodeVideoDropHandler(
    private val onPlay: (SystemPath) -> Unit,
) : WindowDropHandler {
    override fun onDragStarted(content: DragAndDropContent?): WindowDropPreview? {
        val file = when (content) {
            null -> null
            is DragAndDropContent.FileList -> DroppedFileMedia.findVideoFile(content.files) ?: return null
            is DragAndDropContent.PlainText, DragAndDropContent.Unsupported -> return null
        }
        return WindowDropPreview { EpisodeVideoDropCard(file) }
    }

    override fun onDrop(content: DragAndDropContent): Boolean {
        if (content !is DragAndDropContent.FileList) return false
        val file = DroppedFileMedia.findVideoFile(content.files) ?: return false
        onPlay(file)
        return true
    }

    @Composable
    override fun supportedHint(): String = stringResource(Lang.episode_drop_video_supported_hint)
}

@Composable
fun rememberEpisodeVideoDropHandler(onPlay: (SystemPath) -> Unit): EpisodeVideoDropHandler {
    val onPlayUpdated by rememberUpdatedState(onPlay)
    return remember { EpisodeVideoDropHandler { onPlayUpdated(it) } }
}

@Composable
private fun EpisodeVideoDropCard(file: SystemPath?) {
    WindowDropCardContent(
        icon = Icons.Rounded.PlayCircle,
        title = stringResource(Lang.episode_drop_video_title),
        subtitle = file?.name ?: stringResource(Lang.episode_drop_video_unknown_name),
        description = stringResource(Lang.episode_drop_video_description),
    )
}
