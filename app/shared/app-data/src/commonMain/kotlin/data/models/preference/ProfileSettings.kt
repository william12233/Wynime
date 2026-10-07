package com.wynime.app.data.models.preference

import kotlinx.serialization.Serializable

@Serializable
class ProfileSettings {
    companion object {
        val Default = ProfileSettings()
    }
}
