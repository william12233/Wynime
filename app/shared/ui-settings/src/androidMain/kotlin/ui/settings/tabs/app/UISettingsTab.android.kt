package com.wynime.app.ui.settings.tabs.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.wynime.app.ui.foundation.LocalIsPreviewing
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings_app_background_running
import com.wynime.app.ui.lang.settings_app_background_running_description
import com.wynime.app.ui.lang.settings_app_ignore_battery_optimizations
import com.wynime.app.ui.lang.settings_app_ignore_battery_optimizations_description
import com.wynime.app.ui.lang.settings_app_notification_settings
import com.wynime.app.ui.lang.settings_app_open_settings
import com.wynime.app.ui.settings.framework.components.RowButtonItem
import com.wynime.app.ui.settings.framework.components.SettingsScope
import com.wynime.app.ui.settings.framework.components.SwitchItem
import org.jetbrains.compose.resources.stringResource

@SuppressLint("BatteryLife")
@Composable
internal actual fun SettingsScope.AppSettingsTabPlatform() {
    val context by rememberUpdatedState(newValue = LocalContext.current)
    val powerManager by remember {
        derivedStateOf {
            kotlin.runCatching {
                val manager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager?
                manager?.isIgnoringBatteryOptimizations(context.packageName)
                manager
            }.getOrNull()
        }
    }

    if (powerManager != null) {
        Group(
            title = { Text(stringResource(Lang.settings_app_background_running)) },
            description = { Text(text = stringResource(Lang.settings_app_background_running_description)) },
        ) {
            val isPreviewing = LocalIsPreviewing.current
            var isIgnoring by remember {
                if (isPreviewing) {
                    mutableStateOf(false)
                } else {
                    mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true)
                }
            }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                if (!isPreviewing) {
                    isIgnoring = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
                }
            }
            SwitchItem(
                checked = isIgnoring,
                onCheckedChange = {
                    if (!isIgnoring) {
                        kotlin.runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }
                    } else {
                        kotlin.runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
                                ),
                            )
                        }
                    }
                },
                title = { Text(stringResource(Lang.settings_app_ignore_battery_optimizations)) },
                description = { Text(stringResource(Lang.settings_app_ignore_battery_optimizations_description)) },
            )
        }
    }

    Group(
        title = { Text(stringResource(Lang.settings_app_notification_settings)) },
    ) {
        RowButtonItem(
            icon = { Icon(Icons.Rounded.ArrowOutward, contentDescription = null) },
            onClick = {
                kotlin.runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                        ).putExtra(
                            Settings.EXTRA_APP_PACKAGE,
                            context.packageName,
                        ),
                    )
                }.onFailure {
                    it.printStackTrace()
                }
            },
        ) { Text(text = stringResource(Lang.settings_app_open_settings)) }
    }
}
