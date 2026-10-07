package com.wynime.app.ui.settings.tabs.about

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors
import com.mikepenz.aboutlibraries.ui.compose.util.strippedLicenseContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wynime.app.ui.foundation.animation.WynimeAnimatedVisibility
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings_oss_licenses_homepage
import com.wynime.app.ui.lang.settings_oss_licenses_license
import org.jetbrains.compose.resources.stringResource

@Composable
fun OpenSourceLibrariesTab(
    loadLibrariesJsons: suspend () -> List<ByteArray>,
    modifier: Modifier = Modifier,
) {
    val libraries by produceState<Libs?>(null, loadLibrariesJsons) {
        value = withContext(Dispatchers.Default) {
            mergeOpenSourceLibraries(
                loadLibrariesJsons().map { json ->
                    Libs.Builder().withJson(json.decodeToString()).build()
                },
            )
        }
    }
    LibrariesContainer(
        libraries,
        modifier,

        colors = LibraryDefaults.libraryColors(libraryBackgroundColor = Color.Transparent),
        divider = { HorizontalDivider() },
        libraryRow = { _, library, expanded, toggle, _ ->
            OpenSourceLibraryRow(library, expanded, onToggleLicense = toggle)
        },
    )
}

fun mergeOpenSourceLibraries(parsed: List<Libs>): Libs {
    val licensesWithContentByHash = parsed.flatMap { it.licenses }
        .filter { !it.licenseContent.isNullOrBlank() }
        .associateBy { it.hash }
    val libraries = parsed.flatMap { it.libraries }
        .map { library ->
            library.copy(
                licenses = library.licenses
                    .map { licensesWithContentByHash[it.hash] ?: it }
                    .toSet(),
            )
        }
        .sortedBy { it.name.lowercase() }
    val licenses = parsed.flatMap { it.licenses }
        .groupBy { it.hash }
        .map { (hash, group) -> licensesWithContentByHash[hash] ?: group.first() }
        .toSet()
    return Libs(libraries, licenses)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpenSourceLibraryRow(
    library: Library,
    expanded: Boolean,
    onToggleLicense: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                library.name,
                Modifier.padding(end = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )

            val linkStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
            )
            Row(

                Modifier.widthIn(min = 100.dp).weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (library.licenses.isNotEmpty()) {
                    Text(
                        stringResource(Lang.settings_oss_licenses_license),
                        Modifier.clickable(onClick = onToggleLicense),
                        style = linkStyle,
                        maxLines = 1,
                    )
                }
                val homepage = library.website?.takeIf { it.isNotBlank() }
                    ?: library.scm?.url?.takeIf { it.isNotBlank() }
                if (homepage != null) {
                    val uriHandler = LocalUriHandler.current
                    Text(
                        stringResource(Lang.settings_oss_licenses_homepage),
                        Modifier.clickable { uriHandler.openUri(homepage) },
                        style = linkStyle,
                        maxLines = 1,
                    )
                }
            }
        }

        WynimeAnimatedVisibility(expanded) {
            Text(
                library.strippedLicenseContent.takeIf { it.isNotBlank() }
                    ?: library.licenses.mapNotNull { it.url }.joinToString("\n"),
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
                    .padding(12.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}
