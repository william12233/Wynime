/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.settings

import androidx.compose.runtime.Stable
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.util.strippedLicenseContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.sourceplugin.SourcePluginRegistry
import me.him188.ani.app.ui.settings.SettingsViewModel
import me.him188.ani.app.ui.settings.tabs.about.mergeOpenSourceLibraries

@Stable
class TvSettingsViewModel(
    private val settings: SettingsRepository,
    private val sourcePlugins: SourcePluginRegistry,
    private val loadLibraries: suspend () -> List<ByteArray>,
) : SettingsViewModel() {
    private val eventsChannel = Channel<TvSettingsEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()
    private val reload = MutableStateFlow(0)
    private val libraries = MutableStateFlow<List<TvSettingsLibrary>>(emptyList())
    private val librariesLoading = MutableStateFlow(false)
    private val librariesFailed = MutableStateFlow(false)

    private val base = combine(
        settings.uiSettings.flow, settings.themeSettings.flow, settings.videoScaffoldConfig.flow,
        settings.playerKernelConfig.flow,
    ) { appearance, theme, video, kernel ->
        TvSettingsUiState(appearance = appearance, theme = theme, video = video, kernel = kernel)
    }
    private val preferences = combine(
        base, settings.defaultMediaPreference.flow, settings.mediaSelectorSettings.flow, settings.videoResolverSettings.flow,
    ) { state, preference, selector, resolver ->
        state.copy(preference = preference, selector = selector, resolver = resolver)
    }
    private val sources = sourcePlugins.states.map { plugins ->
        plugins.map { plugin ->
            val manifest = plugin.installed.manifest
            TvSettingsSource(
                id = plugin.installed.id,
                name = plugin.metadata?.displayName ?: manifest.displayName,
                description = plugin.metadata?.description ?: manifest.description,
                url = plugin.metadata?.website ?: manifest.website,
                enabled = plugin.installed.enabled && plugin.errorMessage == null,
                factory = "source-plugin",
                subscription = null,
            )
        }
    }
    private val settingsFlow = combine(
        preferences, sources,
    ) { state, sources ->
        state.copy(
            loaded = true, sources = sources,
        )
    }
    val uiState = combine(
        reload.flatMapLatest { settingsFlow.catch { emit(TvSettingsUiState(loadFailed = true)) } },
        libraries, librariesLoading, librariesFailed,
    ) { state, libraries, loading, failed ->
        state.copy(libraries = libraries, librariesLoading = loading, librariesFailed = failed)
    }.stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), TvSettingsUiState())

    fun onIntent(intent: TvSettingsIntent) {
        if (intent == TvSettingsIntent.Retry) {
            reload.update { it + 1 }
            return
        }
        backgroundScope.launch {
            try {
                when (intent) {
                    is TvSettingsIntent.Appearance -> settings.uiSettings.update(intent.update)
                    is TvSettingsIntent.Theme -> settings.themeSettings.update(intent.update)
                    is TvSettingsIntent.Video -> settings.videoScaffoldConfig.update(intent.update)
                    is TvSettingsIntent.Kernel -> settings.playerKernelConfig.update(intent.update)
                    is TvSettingsIntent.Preference -> settings.defaultMediaPreference.update(intent.update)
                    is TvSettingsIntent.Selector -> settings.mediaSelectorSettings.update(intent.update)
                    is TvSettingsIntent.Resolver -> settings.videoResolverSettings.update(intent.update)
                    is TvSettingsIntent.SourceEnabled -> sourcePlugins.setEnabled(intent.id, intent.enabled)
                    TvSettingsIntent.LoadLibraries -> readLibraries()
                    TvSettingsIntent.Retry -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                eventsChannel.send(TvSettingsEvent.SaveFailed)
            }
        }
    }

    private suspend fun readLibraries() {
        if (librariesLoading.value || libraries.value.isNotEmpty()) return
        librariesLoading.value = true
        librariesFailed.value = false
        try {
            val parsed = mergeOpenSourceLibraries(loadLibraries().map { Libs.Builder().withJson(it.decodeToString()).build() })
            libraries.value = parsed.libraries.map { library ->
                TvSettingsLibrary(
                    library.uniqueId, library.name, library.artifactVersion.orEmpty(),
                    library.website?.takeIf(String::isNotBlank) ?: library.scm?.url?.takeIf(String::isNotBlank),
                    library.licenses.joinToString { it.name },
                    library.strippedLicenseContent.ifBlank { library.licenses.mapNotNull { it.url }.joinToString("\n") },
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            librariesFailed.value = true
        } finally {
            librariesLoading.value = false
        }
    }
}

