package com.wynime.app.data.models.user

import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class SelfInfo(
    val id: Uuid,
    val nickname: String,
    val email: String?,
    val hasPassword: Boolean,
    val avatarUrl: String?,
    val bangumiUsername: String?,
    val isBangumiSessionValid: Boolean? = null,

    val externalAccounts: List<ExternalAccount> = emptyList(),
)

@Serializable
data class ExternalAccount(

    val provider: String,
    val username: String?,
)

fun SelfInfo.externalAccount(provider: String): ExternalAccount? = externalAccounts.firstOrNull { it.provider == provider }

data class SelfInfoDisplay(
    val title: String,
    val subtitle: String,
)

@Stable
fun SelfInfo?.calculateDisplay(): SelfInfoDisplay {
    val selfInfo = this
    if (selfInfo == null) {
        return SelfInfoDisplay("加载中...", "")
    }

    if (selfInfo.nickname.isNotEmpty()) {
        return SelfInfoDisplay(selfInfo.nickname, selfInfo.email ?: "")
    }

    return (selfInfo.email ?: selfInfo.bangumiUsername)?.let { SelfInfoDisplay(it, "") }
        ?: SelfInfoDisplay(selfInfo.id.toString(), "")
}
