package com.wynime.app.videoplayer.ui.top

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import com.wynime.app.ui.foundation.LocalPlatform
import com.wynime.app.ui.foundation.ifThen
import com.wynime.app.ui.foundation.navigation.LocalBackDispatcher
import com.wynime.utils.platform.isDesktop

@Composable
fun PlayerTopBar(
    modifier: Modifier = Modifier,
    title: @Composable (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit) = {},
    color: Color = MaterialTheme.colorScheme.onBackground,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    TopAppBar(
        title = {
            CompositionLocalProvider(LocalContentColor provides color) {
                if (title != null) {
                    title()
                }
            }
        },
        modifier
            .fillMaxWidth(),
        navigationIcon = {
            val back = LocalBackDispatcher.current
            CompositionLocalProvider(LocalContentColor provides color) {
                val focusManager by rememberUpdatedState(LocalFocusManager.current)
                IconButton(
                    onClick = { back.onBackPressed() },
                    Modifier.ifThen(needWorkaroundForFocusManager) {
                        onFocusEvent {
                            if (it.hasFocus) {
                                focusManager.clearFocus()
                            }
                        }
                    },
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
        ),
        actions = {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                actions()
            }
        },
        windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
    )
}

val needWorkaroundForFocusManager: Boolean
    @Composable
    get() = LocalPlatform.current.isDesktop()
