/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.login

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.navigation.rememberAsyncBrowserNavigator
import me.him188.ani.tv.ui.foundation.TvNavigationEffect
import me.him188.ani.tv.ui.foundation.TvNavigationEvent

@Composable
fun TvLoginRoute(
    viewModel: TvLoginViewModel,
    onNavigate: (TvNavigationEvent) -> Unit,
    modifier: Modifier = Modifier,
    navigationRailInsets: PaddingValues = PaddingValues(0.dp),
) {
    val state by viewModel.uiState.collectAsState()
    val browserNavigator = rememberAsyncBrowserNavigator()
    val context = LocalContext.current
    TvNavigationEffect(viewModel.navigationEvents, onNavigate)
    TvLoginScreen(
        uiState = state,
        onIntent = { intent ->
            when (intent) {
                TvLoginIntent.Authorize -> viewModel.startOAuth { url ->
                    browserNavigator.openBrowser(context, url)
                }
            }
        },
        modifier = modifier,
        navigationRailInsets = navigationRailInsets,
    )
}
