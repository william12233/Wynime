package com.wynime.app.ui.settings.tabs.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.DelicateCoroutinesApi
import com.wynime.app.data.network.protocol.ReleaseClass
import com.wynime.app.navigation.QQ_GROUP_ID
import com.wynime.app.platform.LocalContext
import com.wynime.app.platform.navigation.rememberAsyncBrowserNavigator
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.Res
import com.wynime.app.ui.foundation.a
import com.wynime.app.ui.foundation.animation.WynimeAnimatedVisibility
import com.wynime.app.ui.foundation.icons.WynimeIcons
import com.wynime.app.ui.foundation.icons.AwardStar
import com.wynime.app.ui.foundation.icons.DeployedCodeAccount
import com.wynime.app.ui.foundation.icons.News
import com.wynime.app.ui.foundation.icons.QqRoundedOutline
import com.wynime.app.ui.foundation.icons.Telegram
import com.wynime.app.ui.foundation.widgets.HeroIcon
import com.wynime.app.ui.foundation.widgets.HeroIconDefaults
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.acknowledgements
import com.wynime.app.ui.lang.developer_list
import com.wynime.app.ui.lang.settings_about_app_description
import com.wynime.app.ui.lang.settings_about_app_name
import com.wynime.app.ui.lang.settings_about_build_info
import com.wynime.app.ui.lang.settings_about_chat_groups
import com.wynime.app.ui.lang.settings_about_feedback
import com.wynime.app.ui.lang.settings_about_icon_description
import com.wynime.app.ui.lang.settings_about_qq_group
import com.wynime.app.ui.lang.settings_about_release_notes
import com.wynime.app.ui.lang.settings_about_source_code
import com.wynime.app.ui.lang.settings_about_version
import com.wynime.app.ui.lang.settings_about_website
import com.wynime.app.ui.lang.settings_help_telegram
import com.wynime.app.ui.settings.rendering.ReleaseClassIcon
import com.wynime.app.ui.settings.rendering.guessReleaseClass
import com.wynime.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Immutable
data class AboutTabInfo(
    val version: String,
    val releaseClass: ReleaseClass = guessReleaseClass(version),
    val buildInfo: BuildInfo = BuildInfo.current(),
)

@OptIn(DelicateCoroutinesApi::class, TestOnly::class)
@Composable
fun AboutTab(
    state: AboutTabInfo,
    onTriggerDebugMode: () -> Unit,
    onClickBuildInfo: () -> Unit,
    onClickReleaseNotes: () -> Unit,
    onClickWebsite: () -> Unit,
    onClickFeedback: () -> Unit,
    onClickSource: () -> Unit,
    onClickDevelopers: () -> Unit,
    onClickAcknowledgements: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(modifier.fillMaxWidth()) {

        WynimeHeroIconAndDescriptions()

        Spacer(Modifier.height(36.dp))

        val listItemColors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        )

        ListItem(
            headlineContent = { Text(stringResource(Lang.settings_about_version)) },
            modifier = Modifier.clickable(onClick = onTriggerDebugMode, role = Role.Button),
            leadingContent = { ReleaseClassIcon(state.releaseClass) },
            supportingContent = { Text(state.version) },
            colors = listItemColors,
        )
        ListItem(
            headlineContent = { Text(stringResource(Lang.settings_about_build_info)) },
            modifier = Modifier.clickable(onClick = onClickBuildInfo, role = Role.Button),
            leadingContent = {
                Icon(Icons.Outlined.Info, contentDescription = null)
            },
            supportingContent = {

                val branch = state.buildInfo.gitBranch
                val sha = state.buildInfo.gitCommitShortSha
                val summary = listOf(branch, sha).filter { it.isNotBlank() }.joinToString(" @ ")
                if (summary.isNotEmpty()) Text(summary)
            },
            colors = listItemColors,
        )
        ListItem(
            headlineContent = { Text(stringResource(Lang.settings_about_release_notes)) },
            modifier = Modifier.clickable(onClick = onClickReleaseNotes, role = Role.Button),
            leadingContent = {
                Icon(Icons.Outlined.News, contentDescription = null)
            },
            colors = listItemColors,
        )
        ListItem(
            headlineContent = { Text(stringResource(Lang.settings_about_website)) },
            modifier = Modifier.clickable(onClick = onClickWebsite),
            leadingContent = {
                Icon(Icons.Outlined.Home, contentDescription = null)
            },
            colors = listItemColors,
        )
        ListItem(
            headlineContent = { Text(stringResource(Lang.settings_about_feedback)) },
            modifier = Modifier.clickable(onClick = onClickFeedback),
            leadingContent = {
                Icon(Icons.Outlined.Feedback, contentDescription = null)
            },
            colors = listItemColors,
        )
        ListItem(
            headlineContent = { Text(stringResource(Lang.settings_about_source_code)) },
            modifier = Modifier.clickable(onClick = onClickSource),
            leadingContent = {
                Icon(Icons.Outlined.Code, contentDescription = null)
            },
            colors = listItemColors,
        )
        ListItem(
            headlineContent = { Text(stringResource(Lang.developer_list)) },
            modifier = Modifier.clickable(onClick = onClickDevelopers),
            leadingContent = {
                Icon(Icons.Outlined.DeployedCodeAccount, contentDescription = null)
            },
            colors = listItemColors,
        )
        ListItem(
            headlineContent = { Text(stringResource(Lang.acknowledgements)) },
            modifier = Modifier.clickable(onClick = onClickAcknowledgements),
            leadingContent = {
                Icon(Icons.Outlined.AwardStar, null)
            },
            colors = listItemColors,
        )

        var showChatGroups by rememberSaveable { mutableStateOf(false) }
        ListItem(
            headlineContent = { Text(stringResource(Lang.settings_about_chat_groups)) },
            modifier = Modifier.clickable { showChatGroups = !showChatGroups },
            leadingContent = {
                Icon(Icons.Outlined.Forum, null)
            },
            colors = listItemColors,
        )
        WynimeAnimatedVisibility(
            showChatGroups,
            label = "ChatGroups",
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(start = (24 + 16).dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val browserNavigator = rememberAsyncBrowserNavigator()
                SuggestionChip(
                    { browserNavigator.openJoinGroup(context) },
                    icon = {
                        Icon(
                            WynimeIcons.QqRoundedOutline, stringResource(Lang.settings_about_qq_group),
                            Modifier.size(20.dp),
                        )
                    },
                    label = { Text(QQ_GROUP_ID) },
                )

                SuggestionChip(
                    { browserNavigator.openJoinTelegram(context) },
                    icon = {
                        Image(
                            WynimeIcons.Telegram, stringResource(Lang.settings_help_telegram),
                            Modifier.size(20.dp),
                        )
                    },
                    label = { Text(stringResource(Lang.settings_help_telegram)) },
                )
            }
        }
    }
}

@Composable
fun WynimeHeroIconAndDescriptions(modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 36.dp)) {
        HeroIcon(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
            ),
        ) {
            Icon(
                painterResource(Res.drawable.a),
                contentDescription = stringResource(Lang.settings_about_icon_description),
                Modifier
                    .clip(CircleShape)
                    .size(HeroIconDefaults.iconSize),
                tint = Color.Unspecified,
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(Lang.settings_about_app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(Lang.settings_about_app_description),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@TestOnly
val TestAboutTabInfo
    get() = AboutTabInfo(
        version = "4.8.0-alpha02",
        releaseClass = ReleaseClass.ALPHA,
        buildInfo = TestBuildInfo,
    )

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewAboutTab() {
    ProvideCompositionLocalsForPreview {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            AboutTab(
                TestAboutTabInfo, {}, {}, {}, {}, {}, {}, {}, {},
            )
        }
    }
}
