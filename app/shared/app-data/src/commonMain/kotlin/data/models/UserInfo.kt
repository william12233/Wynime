package com.wynime.app.data.models

data class UserInfo(
    val id: String,

    val username: String?,
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val sign: String? = null
) {
    companion object {
        val EMPTY = UserInfo("", "")
    }
}
