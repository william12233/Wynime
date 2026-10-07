package com.wynime.app.platform

import androidx.compose.runtime.Stable

@PublishedApi
@Stable
internal actual val currentWynimeBuildConfigImpl: WynimeBuildConfig
    get() = WynimeBuildConfigAndroid
