package com.wynime.app.ui.subject.collection.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.lang.*
import com.wynime.datasources.api.topic.UnifiedCollectionType
import org.jetbrains.compose.resources.*

object SubjectCollectionTypeButtonDefaults {
    @Composable
    fun collectedButtonColors() = ButtonDefaults.outlinedButtonColors(
        contentColor = MaterialTheme.colorScheme.onSurface,
    )

    @Composable
    fun collectedBorder() = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
}

@Composable
fun SubjectCollectionTypeButton(
    type: UnifiedCollectionType,
    onEdit: (newType: UnifiedCollectionType) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val action = remember(type) {
        SubjectCollectionActionsForCollect.find { it.type == type }
    }

    Box(modifier, propagateMinConstraints = true) {
        var showDropdown by rememberSaveable { mutableStateOf(false) }
        val onClick = remember {
            {
                showDropdown = true
            }
        }
        if (type != UnifiedCollectionType.NOT_COLLECTED) {
            OutlinedButton(
                onClick = onClick,
                enabled = enabled,

            ) {
                if (action != null) {
                    action.icon()
                    Row(Modifier.padding(start = 8.dp)) {
                        Text(renderCollectionTypeAsCurrent(type))
                    }
                } else {
                    Text("Loading")
                }
            }
        } else {
            Button(
                onClick = onClick,
                enabled = enabled,
            ) {
                if (action != null) {
                    action.icon()
                    Row(Modifier.padding(start = 8.dp)) {
                        action.title()
                    }
                } else {
                    Text("Loading")
                }
            }

        }
        EditCollectionTypeDropDown(
            currentType = type,
            expanded = showDropdown,
            onDismissRequest = { showDropdown = false },
            onClick = {
                showDropdown = false
                onEdit(it.type)
            },
        )
    }
}

@Composable
@Stable
private fun renderCollectionTypeAsCurrent(type: UnifiedCollectionType): String {
    return when (type) {
        UnifiedCollectionType.WISH -> stringResource(Lang.subject_collection_current_wish)
        UnifiedCollectionType.DOING -> stringResource(Lang.subject_collection_current_doing)
        UnifiedCollectionType.DONE -> stringResource(Lang.subject_collection_current_done)
        UnifiedCollectionType.ON_HOLD -> stringResource(Lang.subject_collection_current_on_hold)
        UnifiedCollectionType.DROPPED -> stringResource(Lang.subject_collection_current_dropped)
        UnifiedCollectionType.NOT_COLLECTED -> stringResource(Lang.subject_collection_not_collected)
    }
}

@Composable
@Preview
fun PreviewCollectionActionButton() = ProvideCompositionLocalsForPreview {
    Surface {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                for (entry in UnifiedCollectionType.entries) {
                    SubjectCollectionTypeButton(
                        type = entry,
                        onEdit = {},
                    )
                }
            }
        }
    }
}
