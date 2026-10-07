package com.wynime.app.ui.settings.framework.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.wynime.app.ui.settings.SettingsTab

@SettingsDsl
@Composable
fun SettingsScope.SwitchItem(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    description: @Composable (() -> Unit)? = null,
    switch: @Composable () -> Unit,
) {
    Item(
        headlineContent = title,
        modifier = modifier,
        supportingContent = description,
        trailingContent = switch,
    )

}

@SettingsDsl
@Composable
fun SettingsScope.SwitchItem(
    onClick: () -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    description: @Composable (() -> Unit)? = null,
    switch: @Composable () -> Unit,
) {
    SwitchItem(
        title, modifier.clickable(onClick = onClick), description, switch,
    )
}

@SettingsDsl
@Composable
fun SettingsScope.SwitchItem(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    description: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val itemModifier = modifier.then(
        if (enabled) Modifier else Modifier.semantics { disabled() },
    )
    SwitchItem(
        { if (enabled) onCheckedChange(!checked) },
        title,
        itemModifier,
        description,
    ) {
        Switch(
            checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Preview
@Composable
private fun PreviewPreferenceScope() {
    SettingsTab {
        SwitchItem(
            checked = true,
            onCheckedChange = {},
            title = {
                Text("Test")
            },
            description = {
                Text(text = "Test description")
            },
        )
    }
}

