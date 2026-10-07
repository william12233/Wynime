package com.wynime.app.platform

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import com.wynime.utils.logging.logger

@SuppressLint("MissingPermission")
private class AndroidMeteredNetworkDetector(
    private val context: Context
) : MeteredNetworkDetector, BroadcastReceiver() {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val logger by lazy { logger<AndroidMeteredNetworkDetector>() }

    private val flow = MutableStateFlow(getCurrentIsMetered())
    override val isMeteredNetworkFlow: Flow<Boolean> get() = flow

    init {

        @Suppress("DEPRECATION")
        context.registerReceiver(this, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))

        flow.value = getCurrentIsMetered()
    }

    override fun onReceive(context: android.content.Context?, intent: Intent?) {
        flow.value = getCurrentIsMetered()
    }

    private fun getCurrentIsMetered(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val activeNetworkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        val isMetered = !activeNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        log { "getCurrentIsMetered: isMetered=$isMetered" }
        return isMetered
    }

    override fun dispose() {

        context.unregisterReceiver(this)
    }

    private inline fun log(message: () -> String) {
        if (currentWynimeBuildConfig.isDebug) {
            logger.debug(message())
        }
    }
}

actual fun createMeteredNetworkDetector(context: Context): MeteredNetworkDetector {
    return AndroidMeteredNetworkDetector(context)
}