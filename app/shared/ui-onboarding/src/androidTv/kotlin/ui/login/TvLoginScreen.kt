/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusHotkey
import me.him188.ani.tv.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.tv.ui.foundation.widgets.TvHeroButton
import me.him188.ani.tv.ui.foundation.widgets.tvHeroContentColor
import me.him188.ani.tv.ui.foundation.widgets.tvHeroSecondaryContentColor

/** TV 登入頁焦點錨點（統一焦點框架，見 ui-foundation-tv/focus）。 */
private enum class TvLoginFocus : TvFocusKey {
    Authorize,
}

/** Bangumi OAuth entry point for TV. */
@Composable
fun TvLoginScreen(
    uiState: TvLoginUiState,
    onIntent: (TvLoginIntent) -> Unit,
    modifier: Modifier = Modifier,
    navigationRailInsets: PaddingValues = PaddingValues(0.dp),
) {
    val focus = rememberTvFocusScope()
    focus.Resolver()
    LaunchedEffect(Unit) { focus.request(TvLoginFocus.Authorize) }

    Column(
        modifier
            .fillMaxSize()
            .tvFocusNavSignal(focus)
            .padding(navigationRailInsets)
            .padding(horizontal = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text("登入 Bangumi", style = MaterialTheme.typography.displaySmall, color = tvHeroContentColor())
        Text(
            "使用 Bangumi 帳號授權，登入後即可同步收藏與播放進度。",
            Modifier.padding(top = 8.dp, bottom = 24.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = tvHeroSecondaryContentColor(),
        )
        TvHeroButton(
            text = if (uiState.busy) "等待 Bangumi 授權…" else "使用 Bangumi 登入",
            icon = Icons.Rounded.Person,
            filled = true,
            onClick = { if (!uiState.busy) onIntent(TvLoginIntent.Authorize) },
            onFocused = {},
            modifier = Modifier
                .fillMaxWidth(0.45f)
                .tvFocusAnchor(focus, TvLoginFocus.Authorize)
                .tvFocusHotkey(focus, Key.DirectionDown to TvLoginFocus.Authorize),
        )
        uiState.error?.let {
            Text(
                it,
                Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * 登录页骨架: 左侧是邮箱登录的步骤区块 ([content], 垂直居中列, slot 模式对齐手机 EmailLoginScreenLayout),
 * 右侧常驻 [qrPanel]. 两种登录方式同时可用, 扫码不需要遥控器操作, 所以焦点默认留在左侧.
 */
