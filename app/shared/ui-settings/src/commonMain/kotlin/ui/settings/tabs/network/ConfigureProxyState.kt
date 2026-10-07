package com.wynime.app.ui.settings.tabs.network

import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.Flow
import com.wynime.app.data.models.preference.MediaSourceProxySettings
import com.wynime.app.data.models.preference.ProxyAuthorization
import com.wynime.app.data.models.preference.ProxyConfig
import com.wynime.app.data.models.preference.ProxyMode
import com.wynime.app.data.models.preference.ProxySettings
import com.wynime.app.ui.foundation.icons.BangumiNext
import com.wynime.app.ui.foundation.icons.BangumiNextIconColor

@Stable
class ConfigureProxyState(
    val state: Flow<ConfigureProxyUIState>,
    private val onUpdateConfig: (ProxyUIConfig) -> Unit,
    val onRequestReTest: () -> Unit,
) {
    fun updateConfig(
        currentConfig: ProxyUIConfig,
        newConfig: ProxyUIConfig,
        currentSystemProxy: SystemProxyPresentation
    ) {
        if (shouldRerunProxyTestManually(currentConfig, newConfig, currentSystemProxy)) {
            onRequestReTest()
        }
        onUpdateConfig(newConfig)
    }

    private fun shouldRerunProxyTestManually(
        prev: ProxyUIConfig,
        curr: ProxyUIConfig,
        systemProxy: SystemProxyPresentation
    ): Boolean {
        if (prev == curr) return true

        val prevMode = prev.mode
        val currMode = curr.mode
        val noSystemProxy = systemProxy is SystemProxyPresentation.NotDetected

        if (prevMode == ProxyUIMode.SYSTEM && currMode == ProxyUIMode.DISABLED && noSystemProxy) {
            return true
        }
        if (prevMode == ProxyUIMode.DISABLED && currMode == ProxyUIMode.SYSTEM && noSystemProxy) {
            return true
        }
        return false
    }
}

@Immutable
sealed class SystemProxyPresentation {
    @Immutable
    data object Detecting : SystemProxyPresentation()

    @Immutable
    data class Detected(val proxyConfig: ProxyConfig) : SystemProxyPresentation()

    @Immutable
    data object NotDetected : SystemProxyPresentation()
}

@Immutable
enum class ProxyTestCaseEnums {
    BANGUMI,
    BANGUMI_NEXT,
}

@Immutable
sealed class ProxyTestCase(
    val name: ProxyTestCaseEnums,
    val icon: ImageVector,
    val color: Color
) {
    data object BangumiApi : ProxyTestCase(
        name = ProxyTestCaseEnums.BANGUMI,
        icon = Icons.Default.BangumiNext,
        color = BangumiNextIconColor,
    )

    data object BangumiNextApi : ProxyTestCase(
        name = ProxyTestCaseEnums.BANGUMI_NEXT,
        icon = Icons.Default.BangumiNext,
        color = BangumiNextIconColor,
    )
}

fun ProxyMode.toUIMode(): ProxyUIMode {
    return when (this) {
        ProxyMode.DISABLED -> ProxyUIMode.DISABLED
        ProxyMode.SYSTEM -> ProxyUIMode.SYSTEM
        ProxyMode.CUSTOM -> ProxyUIMode.CUSTOM
    }
}

fun ProxyUIMode.toDataMode(): ProxyMode {
    return when (this) {
        ProxyUIMode.DISABLED -> ProxyMode.DISABLED
        ProxyUIMode.SYSTEM -> ProxyMode.SYSTEM
        ProxyUIMode.CUSTOM -> ProxyMode.CUSTOM
    }
}

fun ProxySettings.toUIConfig(): ProxyUIConfig {
    return ProxyUIConfig(
        mode = default.mode.toUIMode(),
        manualUrl = default.customConfig.url,
        manualUsername = default.customConfig.authorization?.username,
        manualPassword = default.customConfig.authorization?.password,
    )
}

fun ProxyUIConfig.toDataSettings(): ProxySettings {
    return ProxySettings(
        default = MediaSourceProxySettings(
            mode = mode.toDataMode(),
            customConfig = MediaSourceProxySettings.Default.customConfig.copy(
                url = manualUrl,
                authorization = if (manualUsername != null && manualPassword != null) {
                    ProxyAuthorization(manualUsername, manualPassword)
                } else null,
            ),
        ),
    )
}

