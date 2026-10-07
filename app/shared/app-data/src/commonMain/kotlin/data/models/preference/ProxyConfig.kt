package com.wynime.app.data.models.preference

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import com.wynime.utils.platform.annotations.SerializationOnly

@Serializable
data class ProxySettings(

    val default: MediaSourceProxySettings = MediaSourceProxySettings.Default,
    @Suppress("PropertyName") @Transient val _placeHolder: Int = 0,
) {
    companion object {
        val Default = ProxySettings()

        val Disabled = ProxySettings(default = MediaSourceProxySettings(mode = ProxyMode.DISABLED))
    }
}

@Serializable
data class MediaSourceProxySettings @SerializationOnly constructor(

    @Deprecated(message = "For compatibility", level = DeprecationLevel.ERROR)
    val enabled: Boolean = false,

    @Suppress("DEPRECATION_ERROR")
    val mode: ProxyMode = if (enabled) ProxyMode.CUSTOM else ProxyMode.SYSTEM,

    @SerialName("config")
    val customConfig: ProxyConfig = ProxyConfig.Default,
) {
    @OptIn(SerializationOnly::class)
    constructor (
        mode: ProxyMode = ProxyMode.SYSTEM,

        customConfig: ProxyConfig = ProxyConfig.Default,
    ) : this(enabled = mode != ProxyMode.DISABLED, mode = mode, customConfig = customConfig)

    companion object {
        val Default = MediaSourceProxySettings()
    }
}

@Serializable
enum class ProxyMode {
    DISABLED,
    SYSTEM,
    CUSTOM
}

@Serializable
data class ProxyConfig(
    val url: String = "http://127.0.0.1:7890",
    val authorization: ProxyAuthorization? = null,
) {
    companion object {
        val Default = ProxyConfig()
    }
}

@Serializable
data class ProxyAuthorization(
    val username: String,
    val password: String,
)
