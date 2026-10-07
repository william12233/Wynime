package com.wynime.app.ui.comment

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.comment_block_user
import com.wynime.app.ui.lang.comment_copy_content
import com.wynime.app.ui.lang.comment_open_in_bangumi
import com.wynime.app.ui.lang.comment_report
import org.jetbrains.compose.resources.stringResource

object CommentContextMenuTestTags {
    const val CopyContent = "CommentContextMenu:copy"
    const val OpenOriginal = "CommentContextMenu:openOriginal"
    const val BlockAuthor = "CommentContextMenu:block"
    const val Report = "CommentContextMenu:report"
}

@Composable
fun CommentContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onCopyContent: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenOriginal: (() -> Unit)? = null,
    onBlockAuthor: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    offset: DpOffset = DpOffset.Zero,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        shape = MaterialTheme.shapes.medium,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(Lang.comment_copy_content)) },
            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
            modifier = Modifier.testTag(CommentContextMenuTestTags.CopyContent),
            onClick = {
                onDismissRequest()
                onCopyContent()
            },
        )
        if (onOpenOriginal != null) {
            DropdownMenuItem(
                text = { Text(stringResource(Lang.comment_open_in_bangumi)) },
                leadingIcon = { Icon(Icons.Outlined.Public, contentDescription = null) },
                modifier = Modifier.testTag(CommentContextMenuTestTags.OpenOriginal),
                onClick = {
                    onDismissRequest()
                    onOpenOriginal()
                },
            )
        }
        if (onBlockAuthor != null || onReport != null) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
        }
        if (onBlockAuthor != null) {
            DropdownMenuItem(
                text = { Text(stringResource(Lang.comment_block_user)) },
                leadingIcon = { Icon(Icons.Outlined.Block, contentDescription = null) },
                modifier = Modifier.testTag(CommentContextMenuTestTags.BlockAuthor),
                onClick = {
                    onDismissRequest()
                    onBlockAuthor()
                },
            )
        }
        if (onReport != null) {
            DropdownMenuItem(
                text = { Text(stringResource(Lang.comment_report)) },
                leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
                modifier = Modifier.testTag(CommentContextMenuTestTags.Report),
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error,
                ),
                onClick = {
                    onDismissRequest()
                    onReport()
                },
            )
        }
    }
}
