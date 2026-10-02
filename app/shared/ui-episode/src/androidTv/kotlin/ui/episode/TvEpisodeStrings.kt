/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.episode

import androidx.compose.runtime.Composable
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_episode_info_failed
import me.him188.ani.app.ui.lang.video_player_operation_failed
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TvPlayerError.text(): String = stringResource(when (this) {
    TvPlayerError.SourceInfoUnavailable -> Lang.media_selector_episode_info_failed
})

@Composable
internal fun TvPlayerMessage.text(): String = when (this) {
    TvPlayerMessage.OperationFailed -> stringResource(Lang.video_player_operation_failed)
}
