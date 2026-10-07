package com.wynime.app.ui.comment

import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.LocalIsPreviewing
import com.wynime.app.ui.foundation.avatar.AvatarImage
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.comment_empty_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

object CommentDefaults {
    @Composable
    fun Avatar(url: String?, modifier: Modifier = Modifier) {
        AvatarImage(
            url = url,
            modifier = modifier.size(36.dp),
        )
    }

    @Composable
    fun ReactionPicker(
        onClickItem: (reactionValue: String) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val previewing = LocalIsPreviewing.current
        FlowRow(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BangumiCommentSticker.map { (id, drawableRes) -> id to drawableRes }.forEach { (id, drawableRes) ->
                Surface(
                    onClick = { onClickItem("bgm$id") },
                    shape = CircleShape,
                    color = Color.Transparent,
                ) {
                    if (previewing) {
                        Icon(
                            imageVector = Icons.Rounded.Face,
                            contentDescription = null,
                            modifier = Modifier.padding(4.dp).size(22.dp),
                        )
                    } else {
                        Image(
                            painter = painterResource(drawableRes),
                            contentDescription = null,
                            modifier = Modifier.padding(4.dp).size(22.dp),
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun EmptyPlaceholder(modifier: Modifier = Modifier) {
        Text(
            stringResource(Lang.comment_empty_title),
            modifier = modifier.padding(16.dp),
        )
    }
}
