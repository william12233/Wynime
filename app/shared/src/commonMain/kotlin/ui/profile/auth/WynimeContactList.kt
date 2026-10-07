package com.wynime.app.ui.profile.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wynime.app.platform.LocalContext
import com.wynime.app.platform.navigation.rememberAsyncBrowserNavigator
import com.wynime.app.ui.foundation.icons.WynimeIcons
import com.wynime.app.ui.foundation.icons.GithubMark
import com.wynime.app.ui.foundation.icons.QqRoundedOutline
import com.wynime.app.ui.foundation.icons.Telegram
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings_about_qq_group
import com.wynime.app.ui.lang.settings_about_website
import com.wynime.app.ui.settings.tabs.WynimeHelperDestination
import org.jetbrains.compose.resources.stringResource

private val ContactIconSize = 24.dp

@Composable
fun WynimeContactList(
    modifier: Modifier = Modifier
) {
    val browserNavigator = rememberAsyncBrowserNavigator()
    val context = LocalContext.current
    val websiteText = stringResource(Lang.settings_about_website)
    val qqGroupText = stringResource(Lang.settings_about_qq_group)

    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp, alignment = Alignment.Start),
    ) {
        SuggestionChip(
            { browserNavigator.openBrowser(context, WynimeHelperDestination.GITHUB_HOME) },
            icon = {
                Icon(WynimeIcons.GithubMark, "Github", Modifier.size(ContactIconSize))
            },
            label = { Text("GitHub") },
        )

        SuggestionChip(
            { browserNavigator.openBrowser(context, WynimeHelperDestination.ANI_WEBSITE) },
            icon = {
                Icon(
                    Icons.Rounded.Public,
                    websiteText,
                    Modifier.size(ContactIconSize),
                )
            },
            label = { Text(websiteText) },
        )

        SuggestionChip(
            { browserNavigator.openJoinGroup(context) },
            icon = {
                Icon(
                    WynimeIcons.QqRoundedOutline,
                    qqGroupText,
                    Modifier.size(ContactIconSize),
                )
            },
            label = { Text(qqGroupText) },
        )

        SuggestionChip(
            { browserNavigator.openJoinTelegram(context) },
            icon = {
                Image(
                    WynimeIcons.Telegram, "Telegram",
                    Modifier.size(ContactIconSize),
                )
            },
            label = { Text("Telegram") },
        )
    }
}
