package com.wynime.app.ui.foundation.effects

import androidx.compose.runtime.Composable

@Composable
fun ScreenRotationEffect(onChange: (isLandscape: Boolean) -> Unit) =
    ScreenRotationEffectImpl(onChange)

@Composable
expect fun ScreenRotationEffectImpl(onChange: (isLandscape: Boolean) -> Unit)
