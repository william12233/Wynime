package com.wynime.app.ui.settings.framework.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.effects.defaultFocus
import com.wynime.app.ui.foundation.effects.onKey
import com.wynime.app.ui.foundation.text.ProvideTextStyleContentColor
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings_cancel
import com.wynime.app.ui.settings.SettingsTab
import org.jetbrains.compose.resources.stringResource

@SettingsDsl
@Composable
fun SettingsScope.TextFieldItem(
    value: String,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    description: @Composable ((value: String) -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    onValueChangeCompleted: (value: String) -> Unit = {},
    inverseTitleDescription: Boolean = false,
    isErrorProvider: (value: String) -> Boolean = { false },
    sanitizeValue: (value: String) -> String = { it },
    textFieldDescription: @Composable ((value: String) -> Unit)? = description,
    exposedItem: @Composable (value: String) -> Unit = { Text(it) },
    visualTransformation: VisualTransformation = VisualTransformation.None,

    showVisibilityToggle: Boolean = false,
    extra: @Composable ColumnScope.(editingValue: MutableState<String>) -> Unit = {}
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }

    val valueText = @Composable {
        if (placeholder != null && value.isEmpty()) {
            placeholder()
        } else {
            exposedItem(value)
        }
    }
    Box {
        Item(
            headlineContent = {
                if (inverseTitleDescription) {
                    valueText()
                } else {
                    title()
                }
            },
            modifier.clickable(onClick = { showDialog = true }),
            leadingContent = icon?.let {
                {
                    SettingsDefaults.ItemIcon {
                        it()
                    }
                }
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (inverseTitleDescription) {
                        title()
                    } else {
                        valueText()
                    }
                }
            },
            trailingContent = {
                IconButton({ showDialog = true }) {
                    Icon(Icons.Rounded.Edit, "编辑", tint = MaterialTheme.colorScheme.primary)
                }
            },
        )

        if (showDialog) {

            val editingValueState = rememberSaveable(value) {
                mutableStateOf(value)
            }
            var editingValue by editingValueState
            val error by remember(isErrorProvider) {
                derivedStateOf {
                    isErrorProvider(editingValue)
                }
            }
            val onConfirm = remember(onValueChangeCompleted) {
                {
                    onValueChangeCompleted(editingValue)
                    showDialog = false
                }
            }

            var revealMasked by rememberSaveable { mutableStateOf(false) }
            val effectiveTransformation = if (showVisibilityToggle && revealMasked) {
                VisualTransformation.None
            } else {
                visualTransformation
            }

            TextFieldDialog(
                onDismissRequest = { showDialog = false },
                onConfirm = onConfirm,
                title = title,
                confirmEnabled = !error,
                description = { textFieldDescription?.invoke(editingValue) },
                extra = { extra(editingValueState) },
            ) {
                OutlinedTextField(
                    value = editingValue,
                    onValueChange = { editingValue = sanitizeValue(it) },
                    visualTransformation = effectiveTransformation,
                    shape = MaterialTheme.shapes.medium,
                    keyboardActions = KeyboardActions {
                        if (!error) {
                            onConfirm()
                        }
                    },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Done,
                    ),
                    trailingIcon = if (showVisibilityToggle) {
                        {
                            IconButton({ revealMasked = !revealMasked }) {
                                Icon(
                                    if (revealMasked) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = null,
                                )
                            }
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                        .defaultFocus()
                        .onKey(Key.Enter) {
                            if (!error) {
                                onConfirm()
                            }
                        },
                    isError = error,
                )
            }
        }
    }
}

@Composable
internal fun SettingsScope.TextFieldDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    title: @Composable () -> Unit,
    confirmEnabled: Boolean = true,
    description: @Composable (() -> Unit)? = null,
    extra: @Composable (ColumnScope.() -> Unit) = {},
    textField: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = confirmEnabled,
            ) {
                Text("确认")
            }
        },
        title = title,
        text = {
            Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row {
                    textField()
                }

                extra()

                ProvideTextStyleContentColor(
                    MaterialTheme.typography.labelMedium,
                    LocalContentColor.current.copy(labelAlpha),
                ) {
                    description?.let {
                        Row(Modifier.padding(horizontal = 8.dp)) {
                            it()
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(Lang.settings_cancel)) }
        },
    )
}

@Composable
private fun PreviewTab(
    content: @Composable SettingsScope.() -> Unit,
) {
    SettingsTab {
        content()
    }
}

@Preview
@Composable
private fun PreviewTextFieldDialog() {
    PreviewTab {
        TextFieldDialog(
            onDismissRequest = {},
            onConfirm = {},
            title = { Text(text = "编辑") },
            description = {
                Text(
                    "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt" +
                            " ut labore et dolore magna aliqua.",
                )
            },
        ) {
            OutlinedTextField(
                value = "test",
                onValueChange = {},
                shape = MaterialTheme.shapes.medium,
            )
        }
    }
}
