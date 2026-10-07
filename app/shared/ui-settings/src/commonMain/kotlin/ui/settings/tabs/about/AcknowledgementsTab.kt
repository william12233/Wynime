package com.wynime.app.ui.settings.tabs.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.Res
import com.wynime.app.ui.foundation.bangumi
import com.wynime.app.ui.foundation.tmdb
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings_acknowledgements_bangumi
import com.wynime.app.ui.lang.settings_acknowledgements_bangumi_description
import com.wynime.app.ui.lang.settings_acknowledgements_oss_licenses
import com.wynime.app.ui.lang.settings_acknowledgements_oss_licenses_description
import com.wynime.app.ui.lang.settings_acknowledgements_tmdb
import com.wynime.app.ui.lang.settings_acknowledgements_tmdb_description
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AcknowledgementsTab(
    onClickOpenSourceLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        val uriHandler = LocalUriHandler.current
        val listItemColors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        )

        ListItem(
            headlineContent = { Text(stringResource(Lang.settings_acknowledgements_bangumi)) },
            Modifier.clickable {
                uriHandler.openUri("https://bangumi.tv")
            },
            supportingContent = { Text(stringResource(Lang.settings_acknowledgements_bangumi_description)) },
            leadingContent = {
                Image(
                    painterResource(Res.drawable.bangumi),
                    contentDescription = null,
                    Modifier.clip(CircleShape).size(24.dp),
                )
            },
            colors = listItemColors,
        )

        ListItem(
            headlineContent = { Text(stringResource(Lang.settings_acknowledgements_tmdb)) },
            Modifier.clickable {
                uriHandler.openUri("https://www.themoviedb.org")
            },
            supportingContent = { Text(stringResource(Lang.settings_acknowledgements_tmdb_description)) },
            leadingContent = {

                Image(
                    painterResource(Res.drawable.tmdb),
                    contentDescription = null,
                    Modifier.size(24.dp),
                )
            },
            colors = listItemColors,
        )

        ListItem(
            headlineContent = { Text(stringResource(Lang.settings_acknowledgements_oss_licenses)) },
            Modifier.clickable(onClick = onClickOpenSourceLicenses),
            supportingContent = { Text(stringResource(Lang.settings_acknowledgements_oss_licenses_description)) },
            colors = listItemColors,
        )
    }
}

@Composable
@Preview
private fun PreviewAcknowledgementsTab() = ProvideCompositionLocalsForPreview {
    Surface {
        AcknowledgementsTab(onClickOpenSourceLicenses = {})
    }
}
