package com.wynime.app.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.text.ProvideTextStyleContentColor
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings_update_popup_auto_update
import com.wynime.app.ui.lang.settings_update_popup_close
import com.wynime.app.ui.lang.settings_update_popup_new_version
import com.wynime.app.ui.lang.settings_update_popup_see_details
import org.jetbrains.compose.resources.stringResource

@Composable
fun NewVersionPopupCard(
    version: String,
    changes: List<String>,
    onDetailsClick: () -> Unit,
    onAutoUpdateClick: () -> Unit,
    onDismissRequest: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    BasicNotificationPopupCard(
        title = { Text(stringResource(Lang.settings_update_popup_new_version)) },
        modifier,
        subtitle = { Text(version) },
        dismissButton = {
            onDismissRequest?.let {
                NotificationPopupDefaults.DismissButton(it)
            }
        },
        actions = {
            OutlinedButton(
                onClick = onDetailsClick,
                modifier = Modifier,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Launch,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Lang.settings_update_popup_see_details))
            }
            Spacer(Modifier.width(16.dp))
            Button(
                onClick = onAutoUpdateClick,
                modifier = Modifier,
            ) {
                Text(stringResource(Lang.settings_update_popup_auto_update))
            }
        },
        content = {
            Column {
                changes.forEach { change ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier,
                    ) {
                        Text("•")
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = change,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun BasicNotificationPopupCard(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: @Composable () -> Unit = {},
    dismissButton: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    secondaryActions: @Composable RowScope.() -> Unit = {},
    shape: CornerBasedShape = MaterialTheme.shapes.extraLarge,
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier,
        shape = shape,
        colors = colors,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .widthIn(min = 280.dp, max = 380.dp),
        ) {

            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                        title()
                    }

                    ProvideTextStyleContentColor(
                        MaterialTheme.typography.bodyMedium,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        subtitle()
                    }
                }
                dismissButton()
            }

            Spacer(Modifier.height(16.dp))

            Column {
                content()
            }

            Spacer(Modifier.height(16.dp))

            Row(

                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlowRow(Modifier.weight(1f), verticalArrangement = Arrangement.aligned(Alignment.CenterVertically)) {
                    secondaryActions()
                }
                actions()
            }
        }
    }
}

object NotificationPopupDefaults {
    @Composable
    fun DismissButton(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        IconButton(onClick = onClick, modifier) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(Lang.settings_update_popup_close),
            )
        }
    }
}

@Preview
@Composable
private fun NewVersionDialogPreview() {
    ProvideCompositionLocalsForPreview {
        Surface {
            NewVersionPopupCard(
                version = "4.8.0‑alpha01",
                changes = listOf("支持标签搜索", "支持缓存在线源"),
                onDetailsClick = {},
                onAutoUpdateClick = {},
                onDismissRequest = {},
            )
        }
    }
}
