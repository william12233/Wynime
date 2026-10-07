package com.wynime.app.ui.settings.framework.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@SettingsDsl
@Composable
fun SettingsScope.TextItem(
    modifier: Modifier = Modifier,
    description: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onClickEnabled: Boolean = true,
    title: @Composable () -> Unit,
) {
    Item(
        headlineContent = title,
        modifier
            .then(onClick?.let { Modifier.clickable(onClickEnabled, onClick = it) } ?: Modifier),
        supportingContent = description,
        leadingContent = icon?.let {
            {
                SettingsDefaults.ItemIcon {
                    icon()
                }
            }
        },
        trailingContent = action,
    )

}

