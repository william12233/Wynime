package com.wynime.app.data.persistent

import kotlinx.atomicfu.locks.SynchronizedObject
import com.wynime.app.platform.Context
import kotlin.concurrent.Volatile

expect fun Context.createPlatformDataStoreManager(): PlatformDataStoreManager

val Context.dataStores: PlatformDataStoreManager get() = PlatformDataStoreManagerHolder.get(this)

private object PlatformDataStoreManagerHolder : SynchronizedObject() {
    private class Initialized(
        val contextHashCode: Int,
        val instance: PlatformDataStoreManager,
    )

    @Volatile
    private var initialized: Initialized? = null

    fun get(context: Context): PlatformDataStoreManager {
        getInitialized()?.let {
            return it
        }
        kotlinx.atomicfu.locks.synchronized(this) {
            getInitialized()?.let {
                return it
            }
            return context.createPlatformDataStoreManager().also {
                initialized = Initialized(
                    context.hashCode(),
                    it,
                )
            }
        }
    }

    private fun getInitialized(): PlatformDataStoreManager? {
        initialized?.let { return it.instance }
        return null
    }
}
