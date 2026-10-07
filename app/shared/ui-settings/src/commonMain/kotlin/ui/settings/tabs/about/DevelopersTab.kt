package com.wynime.app.ui.settings.tabs.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.ifNotNullThen
import com.wynime.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import com.wynime.app.ui.foundation.layout.paneVerticalPadding
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings_developers_main_contributors
import com.wynime.app.ui.lang.settings_developers_outstanding_contributors
import com.wynime.app.ui.lang.settings_developers_view_more_on_github
import com.wynime.app.ui.settings.him188
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun DevelopersTab(
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Column(modifier.fillMaxWidth()) {
        val listItemColors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        )

        WynimeHeroIconAndDescriptions()

        Spacer(Modifier.height(36.dp))

        @Composable
        fun LocalImage(
            res: DrawableResource,
            modifier: Modifier = Modifier
        ) {
            Image(
                painterResource(res),
                null,
                modifier.clip(CircleShape).size(48.dp),
            )
        }

        @Composable
        fun DeveloperItem(
            name: String,
            url: String?,
            avatar: @Composable () -> Unit,
            modifier: Modifier = Modifier,
            description: String? = null,
        ) {
            ListItem(
                headlineContent = { Text(name, style = MaterialTheme.typography.bodyLarge) },
                modifier = modifier.ifNotNullThen(url) {
                    clickable(onClick = { uriHandler.openUri(it) }, role = Role.Button)
                },
                leadingContent = { avatar() },
                supportingContent = description?.let { { Text(it) } },
                colors = listItemColors,
            )
        }

        @Composable
        fun Header(title: String) {
            ListItem(
                headlineContent = {
                    Text(
                        title,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                colors = listItemColors,
            )
        }

        developerCredits.forEachIndexed { index, credit ->
            when (index) {
                0 -> Header(stringResource(Lang.settings_developers_main_contributors))
                2 -> Header(stringResource(Lang.settings_developers_outstanding_contributors))
                developerCredits.lastIndex -> HorizontalDivider(Modifier.padding(vertical = 16.dp))
            }
            DeveloperItem(
                credit.name, credit.url,
                description = stringResource(credit.role),
                avatar = { LocalImage(credit.avatar) },
            )
        }

        ListItem(
            headlineContent = { Text(stringResource(Lang.settings_developers_view_more_on_github)) },
            modifier = Modifier.clickable {
                uriHandler.openUri("https://github.com/william12233/Wynime/graphs/contributors")
            },
            trailingContent = {
                Icon(Icons.Rounded.ArrowOutward, null)
            },
            colors = listItemColors,
        )

        Spacer(Modifier.height(currentWindowAdaptiveInfo1().windowSizeClass.paneVerticalPadding))
    }
}

@Composable
@Preview
private fun PreviewDevelopersTab() = ProvideCompositionLocalsForPreview {
    Surface {
        DevelopersTab()
    }
}
