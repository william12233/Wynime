package com.wynime.app.ui.settings.framework.components

import androidx.annotation.UiThread
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings_cancel
import com.wynime.app.ui.lang.settings_confirm
import org.jetbrains.compose.resources.stringResource

@Stable
class SingleSelectionElement<T>(
    val value: T,
    val enabled: Boolean
)

@Stable
private class Item<T>(
    val item: SingleSelectionElement<T>,
    val selected: Boolean,
)

private class SingleSelectionState<T>(
    private val items: List<SingleSelectionElement<T>>,
    initialSelectedItem: Int,
) {
    private var previousSelected: Int? = null
    var currentSelected by mutableStateOf(initialSelectedItem)
        private set
    val presentation: List<Item<T>> by derivedStateOf {
        items.mapIndexed { i, e -> Item(e, i == currentSelected) }
    }

    @UiThread
    fun setSelection(index: Int) {
        if (index !in items.indices) {
            return
        }
        previousSelected = currentSelected
        currentSelected = index
    }

    @UiThread
    fun rollbackSelection() {
        if (previousSelected != null) {
            currentSelected = previousSelected as Int
            previousSelected = null
        }
    }
}

@Composable
private fun <T> rememberSingleSelectionState(
    items: List<SingleSelectionElement<T>>,
    initialSelectedItem: Int
): SingleSelectionState<T> {
    return remember(items) {
        SingleSelectionState(items, initialSelectedItem)
    }
}

@SettingsDsl
@Composable
fun <T> SettingsScope.SingleSelectionItem(
    items: List<SingleSelectionElement<T>>,
    selected: Int,
    key: (T) -> Any,
    icon: @Composable (() -> Unit)? = null,
    description: @Composable (T?) -> Unit,
    listItem: @Composable ColumnScope.(T) -> Unit,
    onConfirm: (T?) -> Unit,
    modifier: Modifier = Modifier,
    dialogIcon: @Composable (() -> Unit)? = null,
    dialogDescription: @Composable (() -> Unit)? = null,
    onOpenDialog: (() -> Unit)? = null,
    onSelectItem: suspend (T) -> Boolean = { true },
    title: @Composable () -> Unit,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    TextItem(
        modifier = modifier,
        description = { description(items.getOrNull(selected)?.value) },
        icon = icon,
        action = {
            IconButton(
                {
                    showDialog = true
                    onOpenDialog?.invoke()
                },
            ) {
                Icon(Icons.AutoMirrored.Default.ArrowRightAlt, null)
            }
        },
        onClick = {
            showDialog = true
            onOpenDialog?.invoke()
        },
        title = { title() },
    )

    if (showDialog) {
        val selectionState = rememberSingleSelectionState(items, selected)
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { title() },
            icon = dialogIcon,
            confirmButton = {
                Button(
                    {
                        showDialog = false
                        onConfirm(items.getOrNull(selectionState.currentSelected)?.value)
                    },
                ) { Text(stringResource(Lang.settings_confirm)) }
            },
            dismissButton = {
                TextButton({ showDialog = false }) {
                    Text(stringResource(Lang.settings_cancel))
                }
            },
            text = {
                Column {
                    dialogDescription?.let {
                        Row(
                            Modifier.padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium, it)
                        }
                    }
                    Spacer(Modifier.fillMaxWidth().height(16.dp))
                    LazyColumn {
                        itemsIndexed(selectionState.presentation, { _, item -> key(item.item.value) }) { i, item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    scope.launch {
                                        selectionState.setSelection(i)
                                        if (!onSelectItem(item.item.value)) {
                                            selectionState.rollbackSelection()
                                        }
                                    }
                                },
                            ) {
                                RadioButton(
                                    selected = item.selected,
                                    enabled = item.item.enabled,
                                    onClick = {
                                        scope.launch {
                                            selectionState.setSelection(i)
                                            if (!onSelectItem(item.item.value)) {
                                                selectionState.rollbackSelection()
                                            }
                                        }
                                    },
                                )
                                Column {
                                    listItem(item.item.value)
                                }
                            }
                        }
                    }
                }
            },
        )
    }
}
