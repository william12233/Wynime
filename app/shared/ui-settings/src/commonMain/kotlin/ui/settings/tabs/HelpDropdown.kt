package com.wynime.app.ui.settings.tabs

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wynime.app.platform.LocalContext
import com.wynime.app.platform.WynimeBrand
import com.wynime.app.platform.navigation.rememberAsyncBrowserNavigator
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings_help_feedback
import com.wynime.app.ui.lang.settings_help_github
import com.wynime.app.ui.lang.settings_help_qq
import com.wynime.app.ui.lang.settings_help_telegram
import com.wynime.app.ui.lang.settings_help_website
import org.jetbrains.compose.resources.stringResource

object WynimeHelperDestination {
    const val GITHUB_HOME = WynimeBrand.githubHome
    const val GITHUB_CONTRIBUTORS = WynimeBrand.githubContributors
    const val ANI_WEBSITE = WynimeBrand.githubHome
    const val ISSUE_TRACKER = WynimeBrand.githubIssues
    const val RELEASE_PREFIX = WynimeBrand.githubReleaseTagPrefix

    const val GITHUB_REPO = WynimeBrand.githubHome
    const val BANGUMI = "https://bangumi.tv"
}

@Composable
fun HelpDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val browserNavigator = rememberAsyncBrowserNavigator()
    val context = LocalContext.current

    DropdownMenu(expanded, onDismissRequest, modifier) {
        DropdownMenuItem(
            text = { Text(stringResource(Lang.settings_help_qq)) },
            onClick = { browserNavigator.openJoinGroup(context) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(Lang.settings_help_telegram)) },
            onClick = { browserNavigator.openJoinTelegram(context) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(Lang.settings_help_github)) },
            onClick = { browserNavigator.openBrowser(context, WynimeHelperDestination.GITHUB_HOME) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(Lang.settings_help_feedback)) },
            onClick = { browserNavigator.openBrowser(context, WynimeHelperDestination.ISSUE_TRACKER) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(Lang.settings_help_website)) },
            onClick = { browserNavigator.openBrowser(context, WynimeHelperDestination.ANI_WEBSITE) },
        )
    }
}
