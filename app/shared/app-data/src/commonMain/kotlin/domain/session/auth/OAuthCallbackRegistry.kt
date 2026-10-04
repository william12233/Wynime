/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session.auth

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Android App Link / custom scheme callback 的进程内交接点。
 *
 * callback 只保存短期 ticket 或错误状态；access token 永远不经过 URI，也不会写入日志。
 */
object OAuthCallbackRegistry {
    data class Callback(
        val state: String,
        val ticket: String?,
        val error: String?,
    )

    private val mutex = Mutex()
    private val callbacks = mutableMapOf<String, Callback>()

    suspend fun publish(state: String, ticket: String?, error: String?) {
        require(state.isNotBlank()) { "OAuth callback state must not be blank" }
        require(!ticket.isNullOrBlank() || !error.isNullOrBlank()) {
            "OAuth callback must contain a ticket or an error"
        }
        mutex.withLock {
            callbacks[state] = Callback(
                state = state,
                ticket = ticket?.takeIf(String::isNotBlank),
                error = error?.takeIf(String::isNotBlank),
            )
        }
    }

    /** 读取并删除 callback，确保一次 callback 只能驱动一次本地交换。 */
    suspend fun take(state: String): Callback? = mutex.withLock {
        callbacks.remove(state)
    }
}
