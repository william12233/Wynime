package com.wynime.app.ui.settings.tabs.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wynime.app.platform.DesktopContext
import com.wynime.app.platform.LocalContext
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings_log_open_directory
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop

@Composable
internal actual fun ColumnScope.PlatformLoggingItems(listItemColors: ListItemColors) {
    val context = LocalContext.current
    ListItem(
        {
            Text(stringResource(Lang.settings_log_open_directory))
        },
        Modifier.clickable {
            Desktop.getDesktop().open((context as DesktopContext).logsDir)
        },
        colors = listItemColors,
    )
}
