package com.wynime.app.ui.oauth

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.ifThen
import com.wynime.app.ui.foundation.layout.WynimeWindowInsets
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.isWidthAtLeastMedium
import com.wynime.app.ui.foundation.widgets.BackNavigationIconButton
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AuthorizationScreenLayout(
    onNavigateSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(ScrollState) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier,
        topBar = {
            TopAppBar(
                title = title,
                navigationIcon = { BackNavigationIconButton(onNavigateBack) },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onNavigateSettings) {
                        Icon(Icons.Outlined.Settings, stringResource(Lang.settings))
                    }
                },
                windowInsets = WynimeWindowInsets.forTopAppBar(),
            )
        },
        contentWindowInsets = WynimeWindowInsets.forPageContent(),
    ) { contentPadding ->
        BoxWithConstraints {
            val availableHeight = maxHeight - contentPadding.calculateTopPadding() - contentPadding.calculateBottomPadding() - 48.dp
            val scrollState = rememberScrollState()
            Column(
                Modifier.fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .ifThen(currentWindowAdaptiveInfo1().windowSizeClass.isWidthAtLeastMedium) { widthIn(max = 480.dp) }
                    .padding(contentPadding)
                    .padding(horizontal = 24.dp)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(scrollState),
            ) {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = availableHeight),
                    verticalArrangement = Arrangement.Center,
                ) {
                    content(scrollState)
                }
            }
        }
    }
}
