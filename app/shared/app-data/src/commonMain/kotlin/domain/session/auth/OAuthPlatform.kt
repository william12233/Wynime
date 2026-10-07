package com.wynime.app.domain.session.auth

enum class OAuthPlatform(

    val id: String,
    val displayName: String,
) {
    BANGUMI("bangumi", "Bangumi"),
    ;

    companion object {
        fun fromId(id: String): OAuthPlatform? = entries.firstOrNull { it.id == id }
    }
}
