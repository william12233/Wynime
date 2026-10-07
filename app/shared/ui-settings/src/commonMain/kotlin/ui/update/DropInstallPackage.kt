package com.wynime.app.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import com.wynime.app.domain.usecase.GlobalKoin
import com.wynime.app.platform.ContextMP
import com.wynime.app.platform.LocalContext
import com.wynime.app.platform.currentWynimeBuildConfig
import com.wynime.app.tools.update.UpdateInstallationRunner
import com.wynime.app.tools.update.UpdateInstallationState
import com.wynime.app.tools.update.UpdateInstaller
import com.wynime.app.ui.foundation.DragAndDropContent
import com.wynime.app.ui.foundation.WindowDropCardContent
import com.wynime.app.ui.foundation.WindowDropHandler
import com.wynime.app.ui.foundation.WindowDropPreview
import com.wynime.app.ui.foundation.widgets.LocalToaster
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings_debug_install_package_cancel
import com.wynime.app.ui.lang.settings_debug_install_package_confirm
import com.wynime.app.ui.lang.settings_debug_install_package_confirm_message
import com.wynime.app.ui.lang.settings_debug_install_package_confirm_new_version
import com.wynime.app.ui.lang.settings_debug_install_package_confirm_title
import com.wynime.app.ui.lang.settings_debug_install_package_drop_badge
import com.wynime.app.ui.lang.settings_debug_install_package_drop_meta
import com.wynime.app.ui.lang.settings_debug_install_package_drop_supported_hint
import com.wynime.app.ui.lang.settings_debug_install_package_drop_title
import com.wynime.app.ui.lang.settings_debug_install_package_drop_unknown_name
import com.wynime.app.ui.lang.settings_debug_install_package_unsupported
import com.wynime.datasources.api.topic.FileSize.Companion.bytes
import com.wynime.utils.io.SystemPath
import com.wynime.utils.io.inSystem
import com.wynime.utils.io.length
import com.wynime.utils.io.name
import org.jetbrains.compose.resources.stringResource

@Stable
class DropInstallPackageState(
    private val installer: UpdateInstaller,
) {
    private val installationRunner = UpdateInstallationRunner(installer)

    val installationState: StateFlow<UpdateInstallationState> get() = installationRunner.state

    val supportedExtensions: Set<String> get() = installer.installablePackageExtensions

    var pendingPackage: SystemPath? by mutableStateOf(null)
        private set

    var lastInstalledPackage: SystemPath? by mutableStateOf(null)
        private set

    val isInstalling: Boolean get() = installationState.value is UpdateInstallationState.Installing

    fun findInstallablePackage(files: List<Path>): SystemPath? =
        files.firstOrNull { installer.isInstallablePackage(it.inSystem) }?.inSystem

    fun offer(content: DragAndDropContent): DropInstallPackageOutcome {
        if (isInstalling) {
            return DropInstallPackageOutcome.IGNORED
        }
        if (content !is DragAndDropContent.FileList || content.files.isEmpty()) {
            return DropInstallPackageOutcome.IGNORED
        }
        val file = findInstallablePackage(content.files)
            ?: return DropInstallPackageOutcome.UNSUPPORTED
        pendingPackage = file
        return DropInstallPackageOutcome.PENDING_CONFIRMATION
    }

    fun dismissPending() {
        pendingPackage = null
    }

    suspend fun installPending(context: ContextMP) {
        val file = pendingPackage ?: return
        pendingPackage = null
        lastInstalledPackage = file
        installationRunner.install(file, packageUrls = emptyList(), context = context)
    }

    fun dismissFailure() {
        installationRunner.dismissFailure()
    }
}

enum class DropInstallPackageOutcome {

    IGNORED,

    UNSUPPORTED,

    PENDING_CONFIRMATION,
}

internal fun parsePackageVersion(fileName: String): String? = PACKAGE_VERSION_REGEX.find(fileName)?.value

private val PACKAGE_VERSION_REGEX =
    Regex("""\d+\.\d+\.\d+(?:-(?:alpha|beta|rc|dev)[0-9A-Za-z]*)?""", RegexOption.IGNORE_CASE)

object DropInstallPackageTestTags {
    const val CONFIRM_BUTTON = "drop_install_package_confirm"
    const val CANCEL_BUTTON = "drop_install_package_cancel"
}

@Composable
fun rememberDropInstallPackageState(): DropInstallPackageState {
    return remember { DropInstallPackageState(GlobalKoin.get<UpdateInstaller>()) }
}

class InstallPackageDropHandler(
    private val state: DropInstallPackageState,
    private val onUnsupported: () -> Unit,
) : WindowDropHandler {
    override fun onDragStarted(content: DragAndDropContent?): WindowDropPreview? {
        if (state.isInstalling) return null
        val file = when (content) {
            null -> null
            is DragAndDropContent.FileList -> state.findInstallablePackage(content.files) ?: return null
            is DragAndDropContent.PlainText, DragAndDropContent.Unsupported -> return null
        }
        return WindowDropPreview { InstallPackageDropCard(file) }
    }

    override fun onDrop(content: DragAndDropContent): Boolean {
        return when (state.offer(content)) {
            DropInstallPackageOutcome.PENDING_CONFIRMATION -> true
            DropInstallPackageOutcome.UNSUPPORTED -> {
                onUnsupported()
                false
            }

            DropInstallPackageOutcome.IGNORED -> false
        }
    }

    @Composable
    override fun supportedHint(): String = stringResource(
        Lang.settings_debug_install_package_drop_supported_hint,
        state.supportedExtensions.joinToString("、"),
    )
}

@Composable
fun rememberInstallPackageDropHandler(state: DropInstallPackageState): InstallPackageDropHandler {
    val toaster = LocalToaster.current
    val unsupportedMessage = stringResource(
        Lang.settings_debug_install_package_unsupported,
        state.supportedExtensions.joinToString(", "),
    )
    val onUnsupported by rememberUpdatedState { toaster.toast(unsupportedMessage) }
    return remember(state) { InstallPackageDropHandler(state) { onUnsupported() } }
}

@Composable
private fun InstallPackageDropCard(file: SystemPath?) {
    WindowDropCardContent(
        icon = Icons.Rounded.Inventory2,
        title = stringResource(Lang.settings_debug_install_package_drop_title),
        subtitle = file?.name ?: stringResource(Lang.settings_debug_install_package_drop_unknown_name),
        description = stringResource(Lang.settings_debug_install_package_drop_meta, currentWynimeBuildConfig.versionName),
        badge = stringResource(Lang.settings_debug_install_package_drop_badge),
    )
}

@Composable
fun InstallPackageDropDialogs(state: DropInstallPackageState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    state.pendingPackage?.let { file ->
        ConfirmInstallPackageDialog(
            file = file,
            currentVersion = currentWynimeBuildConfig.versionName,
            onConfirm = {
                scope.launch { state.installPending(context) }
            },
            onDismissRequest = state::dismissPending,
        )
    }

    val installationState by state.installationState.collectAsStateWithLifecycle()
    (installationState as? UpdateInstallationState.Failed)?.let { failed ->
        FailedToInstallDialog(
            message = failed.result.message ?: failed.result.reason.toString(),
            onDismissRequest = state::dismissFailure,
            file = state.lastInstalledPackage,
        )
    }
}

@Composable
private fun ConfirmInstallPackageDialog(
    file: SystemPath,
    currentVersion: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val fileName = file.name
    val newVersion = remember(fileName) { parsePackageVersion(fileName) }
    val details = remember(file) {
        listOfNotNull(
            file.path.parent?.toString(),
            runCatching { file.length() }.getOrNull()?.takeIf { it > 0 }?.bytes?.toString(),
        ).joinToString(" · ")
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag(DropInstallPackageTestTags.CONFIRM_BUTTON),
            ) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Lang.settings_debug_install_package_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.testTag(DropInstallPackageTestTags.CANCEL_BUTTON),
            ) {
                Text(stringResource(Lang.settings_debug_install_package_cancel))
            }
        },
        title = { Text(stringResource(Lang.settings_debug_install_package_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainer) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(48.dp).background(colors.primaryContainer, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Inventory2,
                                contentDescription = null,
                                Modifier.size(26.dp),
                                tint = colors.onPrimaryContainer,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                fileName,
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.MiddleEllipsis,
                            )
                            if (details.isNotEmpty()) {
                                Text(
                                    details,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.MiddleEllipsis,
                                )
                            }
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VersionChip(currentVersion, colors.secondaryContainer, colors.onSecondaryContainer)
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        Modifier.size(20.dp),
                        tint = colors.outline,
                    )
                    VersionChip(
                        newVersion ?: stringResource(Lang.settings_debug_install_package_confirm_new_version),
                        colors.primaryContainer,
                        colors.onPrimaryContainer,
                    )
                }
                Text(stringResource(Lang.settings_debug_install_package_confirm_message))
            }
        },
    )
}

@Composable
private fun VersionChip(text: String, containerColor: Color, contentColor: Color) {
    Surface(shape = CircleShape, color = containerColor, contentColor = contentColor) {
        Text(
            text,
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
    }
}
