/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource

/**
 * 播放器自動選線使用的來源優先級型別。
 *
 * 來源外掛透過通用 MediaSource 適配器提供優先級；此型別不包含舊的來源設定編碼或訂閱資料。
 */
typealias MediaSourceTier = me.him188.ani.datasources.api.source.MediaSourceTier
