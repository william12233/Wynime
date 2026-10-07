package com.wynime.app.ui.settings.tabs.log

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import com.wynime.app.platform.LocalContext
import com.wynime.app.ui.foundation.setClipEntryText
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings_log_copy_today_log_content
import com.wynime.app.ui.lang.settings_log_share_file
import com.wynime.app.ui.lang.settings_log_share_today_log_file
import com.wynime.buildconfig.AndroidBuildConfig
import org.jetbrains.compose.resources.stringResource
import java.io.File

@Composable
internal actual fun ColumnScope.PlatformLoggingItems(listItemColors: ListItemColors) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val shareTodayLogFileText = stringResource(Lang.settings_log_share_today_log_file)
    val shareLogFileText = stringResource(Lang.settings_log_share_file)
    val copyTodayLogContentText = stringResource(Lang.settings_log_copy_today_log_content)

    ListItem(
        headlineContent = { Text(shareTodayLogFileText) },
        Modifier.clickable {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.setType("text/plain")
            shareIntent.putExtra(
                Intent.EXTRA_STREAM,
                FileProvider.getUriForFile(
                    context,
                    AndroidBuildConfig.APP_APPLICATION_ID + ".fileprovider",
                    context.getCurrentLogFile(),
                ),
            )
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(shareIntent, shareLogFileText))
        },
        colors = listItemColors,
    )

    ListItem(
        headlineContent = { Text(copyTodayLogContentText) },
        Modifier.clickable {
            scope.launch {
                clipboard.setClipEntryText(context.getCurrentLogFile().readText())
            }
        },
        colors = listItemColors,
    )
}

fun Context.getLogsDir(): File {

    val logs = applicationContext.filesDir.resolve("logs")
    if (!logs.exists()) {
        logs.mkdirs()
    }
    return logs
}

internal fun Context.getCurrentLogFile(): File {
    return getLogsDir().resolve("app.log")
}
