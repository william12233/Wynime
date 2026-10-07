package com.wynime.app.ui.foundation.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BackNavigationIconButton(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBarActionButton(
        onNavigateBack,
        modifier,
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.ArrowBack,
            null,
        )
    }
}

@Composable
fun TopAppBarActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick,
        modifier,

    ) {
        Box(Modifier.size(24.dp)) {
            content()
        }
    }
}
