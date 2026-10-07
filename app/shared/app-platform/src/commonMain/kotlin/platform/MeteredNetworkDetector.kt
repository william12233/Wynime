package com.wynime.app.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface MeteredNetworkDetector {
    val isMeteredNetworkFlow: Flow<Boolean>

    fun dispose()
}

object NoopMeteredNetworkDetector : MeteredNetworkDetector {
    override val isMeteredNetworkFlow: Flow<Boolean> = flowOf(false)

    override fun dispose() {
    }
}

expect fun createMeteredNetworkDetector(context: ContextMP): MeteredNetworkDetector