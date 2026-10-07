package com.wynime.app.domain.foundation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface VersionExpiryService {
    val state: StateFlow<VersionExpiryState?>
    fun onVersionExpired(latestVersion: String?)
}

data class VersionExpiryState(
    val latestVersion: String?,
)

class DefaultVersionExpiryService : VersionExpiryService {
    private val _state = MutableStateFlow<VersionExpiryState?>(null)
    override val state: StateFlow<VersionExpiryState?> = _state.asStateFlow()

    override fun onVersionExpired(latestVersion: String?) {
        _state.value = VersionExpiryState(latestVersion)
    }
}

