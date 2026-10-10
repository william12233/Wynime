package com.wynime.app.ui.settings.tabs.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wynime.app.data.network.protocol.ReleaseClass
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.Res
import com.wynime.app.ui.foundation.a
import com.wynime.app.ui.foundation.icons.AwardStar
import com.wynime.app.ui.foundation.widgets.HeroIcon
import com.wynime.app.ui.foundation.widgets.HeroIconDefaults
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.acknowledgements
import com.wynime.app.ui.lang.settings_about_app_description
import com.wynime.app.ui.lang.settings_about_app_name
import com.wynime.app.ui.lang.settings_about_build_info
import com.wynime.app.ui.lang.settings_about_feedback
import com.wynime.app.ui.lang.settings_about_icon_description
import com.wynime.app.ui.lang.settings_about_source_code
import com.wynime.app.ui.lang.settings_about_version
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

@OptIn(TestOnly::class)
@Composable
fun AboutTab(
    state: AboutTabInfo,
    onTriggerDebugMode: () -> Unit,
    onClickBuildInfo: () -> Unit,
    onClickFeedback: () -> Unit,
    onClickSource: () -> Unit,
    onClickAcknowledgements: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            headlineContent = { Text(stringResource(Lang.acknowledgements)) },
            modifier = Modifier.clickable(onClick = onClickAcknowledgements),
            leadingContent = {
                Icon(Icons.Outlined.AwardStar, null)
            },
            colors = listItemColors,
        )

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
                TestAboutTabInfo, {}, {}, {}, {}, {},
            )
        }
    }
}
