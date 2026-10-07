package com.wynime.app.domain.foundation

interface GlobalHttpEvents {
    fun onVersionExpired(latestVersion: String?)
}

lateinit var GlobalHttpEventBus: GlobalHttpEvents
