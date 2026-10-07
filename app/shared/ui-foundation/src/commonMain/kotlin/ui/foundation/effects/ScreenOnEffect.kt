package com.wynime.app.ui.foundation.effects

import androidx.compose.runtime.Composable

@Composable
fun ScreenOnEffect() = ScreenOnEffectImpl()

@Composable
expect fun ScreenOnEffectImpl()