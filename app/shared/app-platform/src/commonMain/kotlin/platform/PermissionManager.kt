package com.wynime.app.platform

interface PermissionManager {

    fun checkNotificationPermission(context: ContextMP): Boolean

    suspend fun requestNotificationPermission(context: ContextMP): Boolean

    suspend fun requestWriteExternalStoragePermission(context: ContextMP): Boolean

    fun openSystemNotificationSettings(context: ContextMP)

    suspend fun requestExternalDocumentTree(context: ContextMP): String?
}

object GrantedPermissionManager : PermissionManager {
    override fun checkNotificationPermission(context: ContextMP): Boolean {
        return true
    }

    override suspend fun requestNotificationPermission(context: ContextMP): Boolean {
        return true
    }

    override suspend fun requestWriteExternalStoragePermission(context: ContextMP): Boolean {
        return true
    }

    override fun openSystemNotificationSettings(context: ContextMP) {

    }

    override suspend fun requestExternalDocumentTree(context: ContextMP): String? {
        return null
    }
}
