package com.wynime.utils.ktor

object HttpTokenChecker {

    fun isValidToken(token: String): Boolean {

        val allowedCharsRegex = Regex("^[A-Za-z0-9\\-_.~]*\$")

        if (token.isBlank() || !allowedCharsRegex.matches(token)) {
            return false
        }

        if (token.length > 255) {
            return false
        }

        return true
    }
}