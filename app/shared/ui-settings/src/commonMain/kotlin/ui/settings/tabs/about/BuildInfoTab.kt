package com.wynime.app.ui.settings.tabs.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.wynime.app.platform.WynimeBuildConfig
import com.wynime.app.platform.currentWynimeBuildConfig
import com.wynime.app.ui.foundation.ProvideCompositionLocalsForPreview
import com.wynime.app.ui.foundation.setClipEntryText
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings_about_version
import com.wynime.app.ui.lang.settings_build_info_branch
import com.wynime.app.ui.lang.settings_build_info_build_type
import com.wynime.app.ui.lang.settings_build_info_commit
import com.wynime.app.ui.lang.settings_build_info_commit_time
import com.wynime.app.ui.lang.settings_build_info_copied
import com.wynime.app.ui.lang.settings_build_info_copy_all
import com.wynime.app.ui.lang.settings_build_info_distro_channel
import com.wynime.app.ui.lang.settings_build_info_open_commit
import com.wynime.app.ui.lang.settings_build_info_platform
import com.wynime.app.ui.lang.settings_build_info_unknown
import com.wynime.app.ui.settings.tabs.WynimeHelperDestination
import com.wynime.utils.platform.Platform
import com.wynime.utils.platform.annotations.TestOnly
import com.wynime.utils.platform.currentPlatform
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Immutable
data class BuildInfo(
    val versionName: String,

    val gitBranch: String,

    val gitCommitSha: String,

    val gitCommitTime: String,
    val distroChannel: String,
    val isDebug: Boolean,
    val platform: String,
) {
    val gitCommitShortSha: String get() = gitCommitSha.take(SHORT_SHA_LENGTH)

    val gitCommitUrl: String?
        get() = gitCommitSha.takeIf { it.isNotBlank() }?.let { "${WynimeHelperDestination.GITHUB_HOME}/commit/$it" }

    companion object {

        const val SHORT_SHA_LENGTH = 8

        fun current(
            buildConfig: WynimeBuildConfig = currentWynimeBuildConfig,
            platform: Platform = currentPlatform(),
        ): BuildInfo = BuildInfo(
            versionName = buildConfig.versionName,
            gitBranch = buildConfig.gitBranch,
            gitCommitSha = buildConfig.gitCommitSha,
            gitCommitTime = buildConfig.gitCommitTime,
            distroChannel = buildConfig.distroChannel,
            isDebug = buildConfig.isDebug,
            platform = platform.nameAndArch,
        )
    }
}

@Composable
fun BuildInfoTab(
    info: BuildInfo,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val toaster = LocalToaster.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    val unknown = stringResource(Lang.settings_build_info_unknown)
    val labelVersion = stringResource(Lang.settings_about_version)
    val labelBranch = stringResource(Lang.settings_build_info_branch)
    val labelCommit = stringResource(Lang.settings_build_info_commit)
    val labelCommitTime = stringResource(Lang.settings_build_info_commit_time)
    val labelBuildType = stringResource(Lang.settings_build_info_build_type)
    val labelDistroChannel = stringResource(Lang.settings_build_info_distro_channel)
    val labelPlatform = stringResource(Lang.settings_build_info_platform)

    val rows = listOf(
        labelVersion to info.versionName,
        labelBranch to info.gitBranch.ifBlank { unknown },
        labelCommit to info.gitCommitSha.ifBlank { unknown },
        labelCommitTime to info.gitCommitTime.ifBlank { unknown },
        labelBuildType to if (info.isDebug) "Debug" else "Release",
        labelDistroChannel to info.distroChannel,
        labelPlatform to info.platform,
    )

    fun copy(text: String) {
        scope.launch {
            clipboard.setClipEntryText(text)
            toaster.toast(getString(Lang.settings_build_info_copied))
        }
    }

    Column(modifier.fillMaxWidth()) {
        val listItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)

        for ((label, value) in rows) {
            val isCommit = label == labelCommit
            ListItem(
                headlineContent = { Text(label) },
                supportingContent = {
                    Text(
                        value,

                        fontFamily = if (isCommit || label == labelCommitTime) FontFamily.Monospace else null,
                    )
                },
                trailingContent = if (isCommit && info.gitCommitUrl != null) {
                    {
                        IconButton({ uriHandler.openUri(info.gitCommitUrl!!) }) {
                            Icon(
                                Icons.Rounded.OpenInNew,
                                contentDescription = stringResource(Lang.settings_build_info_open_commit),
                            )
                        }
                    }
                } else null,
                modifier = Modifier.clickable(onClick = { copy(value) }, role = Role.Button),
                colors = listItemColors,
            )
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = { copy(rows.joinToString("\n") { (label, value) -> "$label: $value" }) },
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(Lang.settings_build_info_copy_all))
        }
    }
}

@TestOnly
val TestBuildInfo
    get() = BuildInfo(
        versionName = "4.8.0-alpha02",
        gitBranch = "main",
        gitCommitSha = "28ec14ac9f6f1b2c3d4e5f60718293a4b5c6d7e8",
        gitCommitTime = "2026-09-20T12:00:00+08:00",
        distroChannel = "default",
        isDebug = true,
        platform = "macOS AArch64",
    )

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewBuildInfoTab() {
    ProvideCompositionLocalsForPreview {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            BuildInfoTab(TestBuildInfo)
        }
    }
}
