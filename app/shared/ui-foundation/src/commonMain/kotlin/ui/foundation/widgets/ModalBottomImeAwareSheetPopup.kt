package com.wynime.app.ui.foundation.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

@Composable
expect fun ModalBottomImeAwareSheetPopup(
    popupPositionProvider: PopupPositionProvider,
    onDismissRequest: () -> Unit,
    properties: PopupProperties,
    content: @Composable () -> Unit
)