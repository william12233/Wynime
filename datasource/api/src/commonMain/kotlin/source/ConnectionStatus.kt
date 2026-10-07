package com.wynime.datasources.api.source

enum class ConnectionStatus {
    SUCCESS,
    FAILED,
}

fun Boolean.toConnectionStatus() = if (this) ConnectionStatus.SUCCESS else ConnectionStatus.FAILED
