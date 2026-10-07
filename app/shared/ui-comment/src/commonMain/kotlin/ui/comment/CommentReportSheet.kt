package com.wynime.app.ui.comment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.isWidthCompact
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.comment_report_cancel
import com.wynime.app.ui.lang.comment_report_detail_hint
import com.wynime.app.ui.lang.comment_report_reason_harassment
import com.wynime.app.ui.lang.comment_report_reason_illegal
import com.wynime.app.ui.lang.comment_report_reason_nsfw
import com.wynime.app.ui.lang.comment_report_reason_other
import com.wynime.app.ui.lang.comment_report_reason_spam
import com.wynime.app.ui.lang.comment_report_reason_spoiler
import com.wynime.app.ui.lang.comment_report_submit
import com.wynime.app.ui.lang.comment_report_subtitle
import com.wynime.app.ui.lang.comment_report_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class CommentReportReason {

    SPAM,

    HARASSMENT,

    SPOILER,

    NSFW,

    ILLEGAL,

    OTHER,
}

private val CommentReportReason.titleRes: StringResource
    get() = when (this) {
        CommentReportReason.SPAM -> Lang.comment_report_reason_spam
        CommentReportReason.HARASSMENT -> Lang.comment_report_reason_harassment
        CommentReportReason.SPOILER -> Lang.comment_report_reason_spoiler
        CommentReportReason.NSFW -> Lang.comment_report_reason_nsfw
        CommentReportReason.ILLEGAL -> Lang.comment_report_reason_illegal
        CommentReportReason.OTHER -> Lang.comment_report_reason_other
    }

@Composable
fun CommentReportSheet(
    snapshotText: String,
    onSubmit: (reason: CommentReportReason, detail: String) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (currentWindowAdaptiveInfo1().isWidthCompact) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
        ) {
            CommentReportSheetContent(
                snapshotText = snapshotText,
                onSubmit = onSubmit,
                onCancel = onDismissRequest,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 20.dp),
            )
        }
    } else {
        Dialog(onDismissRequest = onDismissRequest) {
            Surface(
                modifier = modifier.widthIn(max = 400.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                CommentReportSheetContent(
                    snapshotText = snapshotText,
                    onSubmit = onSubmit,
                    onCancel = onDismissRequest,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    }
}

@Composable
internal fun CommentReportSheetContent(
    snapshotText: String,
    onSubmit: (reason: CommentReportReason, detail: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedReason by rememberSaveable { mutableStateOf<CommentReportReason?>(null) }
    var detail by rememberSaveable { mutableStateOf("") }

    Column(modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = stringResource(Lang.comment_report_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(Lang.comment_report_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = snapshotText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(6.dp))

        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
        ) {
            CommentReportReason.entries.forEach { reason ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason },
                            role = Role.RadioButton,
                        )
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedReason == reason,
                        onClick = null,
                    )
                    Text(
                        text = stringResource(reason.titleRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))

        Row(Modifier.fillMaxWidth().padding(start = 30.dp)) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                BasicTextField(
                    value = detail,
                    onValueChange = { detail = it.take(1000) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    decorationBox = { innerTextField ->
                        if (detail.isEmpty()) {
                            Text(
                                text = stringResource(Lang.comment_report_detail_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    },
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(Lang.comment_report_cancel))
            }
            Button(
                onClick = {
                    selectedReason?.let { onSubmit(it, detail.trim()) }
                },
                enabled = selectedReason != null,
            ) {
                Text(stringResource(Lang.comment_report_submit))
            }
        }
    }
}
