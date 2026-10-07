package com.wynime.app.platform

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.wynime.utils.logging.info
import com.wynime.utils.logging.logger

class AndroidPermissionManager : PermissionManager {
    private val logger = logger<AndroidPermissionManager>()

    override fun checkNotificationPermission(context: ContextMP): Boolean {
        val activity = context.findActivity() as? WynimeComponentActivity ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else {

            NotificationManagerCompat.from(activity).areNotificationsEnabled()
        }
    }

    override suspend fun requestNotificationPermission(context: ContextMP): Boolean {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val activity = context.findActivity() as? WynimeComponentActivity ?: return false
        return activity.requestPermission(Manifest.permission.POST_NOTIFICATIONS)
    }

    override suspend fun requestWriteExternalStoragePermission(context: ContextMP): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true
        val activity = context.findActivity() as? WynimeComponentActivity ?: return false
        if (
            ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        return activity.requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    override fun openSystemNotificationSettings(context: ContextMP) {
        val openSystemNotificationIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        context.startActivity(openSystemNotificationIntent)
    }

    override suspend fun requestExternalDocumentTree(context: ContextMP): String? {
        val activity = context.findActivity() as? WynimeComponentActivity ?: return null
        val result = activity.requestExternalDocumentTree()
        logger.info { "request external document tree result: $result" }
        return result
    }
}
