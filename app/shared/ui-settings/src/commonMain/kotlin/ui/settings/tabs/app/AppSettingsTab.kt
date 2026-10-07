package com.wynime.app.ui.settings.tabs.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wynime.app.data.models.preference.DesktopCloseBehavior
import com.wynime.app.data.models.preference.EpisodeListProgressTheme
import com.wynime.app.data.models.preference.FullscreenSwitchMode
import com.wynime.app.data.models.preference.NsfwMode
import com.wynime.app.data.models.preference.PlayerKernelConfig
import com.wynime.app.data.models.preference.ThemeSettings
import com.wynime.app.data.models.preference.UISettings
import com.wynime.app.data.models.preference.UpdateSettings
import com.wynime.app.data.models.preference.VideoScaffoldConfig
import com.wynime.app.data.models.preference.VideoEnhancementDefaultMode
import com.wynime.app.data.network.protocol.ReleaseClass
import com.wynime.app.navigation.MainScreenPage
import com.wynime.app.navigation.getIcon
import com.wynime.app.navigation.getText
import com.wynime.app.platform.currentWynimeBuildConfig
import com.wynime.app.ui.settings.tabs.WynimeHelperDestination
import com.wynime.app.ui.foundation.LocalPlatform
import com.wynime.app.ui.foundation.SteppedSlider
import com.wynime.app.ui.foundation.animation.WynimeAnimatedVisibility
import com.wynime.app.ui.foundation.animation.LocalWynimeMotionScheme
import com.wynime.app.ui.foundation.quantizeSliderValue
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.settings_app_close_behavior
import com.wynime.app.ui.lang.settings_app_close_behavior_exit
import com.wynime.app.ui.lang.settings_app_close_behavior_minimize_to_tray
import com.wynime.app.ui.lang.settings_app_episode_playback
import com.wynime.app.ui.lang.settings_app_initial_page
import com.wynime.app.ui.lang.settings_app_initial_page_description
import com.wynime.app.ui.lang.settings_app_episode_images
import com.wynime.app.ui.lang.settings_app_episode_images_description
import com.wynime.app.ui.lang.settings_app_light_up_mode
import com.wynime.app.ui.lang.settings_app_light_up_mode_description
import com.wynime.app.ui.lang.settings_app_list_animation
import com.wynime.app.ui.lang.settings_app_list_animation_description
import com.wynime.app.ui.lang.settings_app_my_collections
import com.wynime.app.ui.lang.settings_app_not_show_done_and_dropped_subjects
import com.wynime.app.ui.lang.settings_app_nsfw_blur
import com.wynime.app.ui.lang.settings_app_nsfw_content
import com.wynime.app.ui.lang.settings_app_nsfw_display
import com.wynime.app.ui.lang.settings_app_nsfw_hide
import com.wynime.app.ui.lang.settings_app_search
import com.wynime.app.ui.lang.settings_app_subject_title
import com.wynime.app.ui.lang.settings_app_use_original_title
import com.wynime.app.ui.lang.settings_app_use_original_title_description
import com.wynime.app.ui.lang.settings_player
import com.wynime.app.ui.lang.settings_player_audio_time_stretch
import com.wynime.app.ui.lang.settings_player_audio_time_stretch_description
import com.wynime.app.ui.lang.settings_player_auto_fullscreen_on_landscape
import com.wynime.app.ui.lang.settings_player_auto_mark_done
import com.wynime.app.ui.lang.settings_player_auto_play_next
import com.wynime.app.ui.lang.settings_player_auto_skip_op_ed
import com.wynime.app.ui.lang.settings_player_auto_skip_op_ed_description
import com.wynime.app.ui.lang.settings_player_auto_switch_media_on_error
import com.wynime.app.utils.formatSpeedValue
import com.wynime.app.ui.lang.settings_player_default_playback_speed
import com.wynime.app.ui.lang.settings_player_default_playback_speed_description
import com.wynime.app.ui.lang.settings_player_experimental_hls_segment_filter
import com.wynime.app.ui.lang.settings_player_experimental_hls_segment_filter_description
import com.wynime.app.ui.lang.settings_player_frame_preview
import com.wynime.app.ui.lang.settings_player_frame_preview_description
import com.wynime.app.ui.lang.settings_player_fullscreen_always_show
import com.wynime.app.ui.lang.settings_player_fullscreen_auto_hide
import com.wynime.app.ui.lang.settings_player_fullscreen_button
import com.wynime.app.ui.lang.settings_player_fullscreen_button_description
import com.wynime.app.ui.lang.settings_player_fullscreen_only_in_controller
import com.wynime.app.ui.lang.settings_player_hide_selector_on_select
import com.wynime.app.ui.lang.settings_player_long_press_fast_forward_speed
import com.wynime.app.ui.lang.settings_player_long_press_fast_forward_speed_description
import com.wynime.app.ui.lang.settings_player_op_ed_skip_duration
import com.wynime.app.ui.lang.settings_player_op_ed_skip_duration_seconds
import com.wynime.app.ui.lang.settings_player_playback_speed_range
import com.wynime.app.ui.lang.settings_player_playback_speed_range_description
import com.wynime.app.ui.lang.settings_player_remember_playback_speed
import com.wynime.app.ui.lang.settings_player_remember_playback_speed_description
import com.wynime.app.ui.lang.settings_player_video_enhancement_default
import com.wynime.app.ui.lang.settings_player_video_enhancement_default_description
import com.wynime.app.ui.lang.video_player_off
import com.wynime.app.ui.lang.video_player_performance
import com.wynime.app.ui.lang.video_player_quality
import com.wynime.app.ui.lang.settings_update_auto_check
import com.wynime.app.ui.lang.settings_update_auto_check_description
import com.wynime.app.ui.lang.settings_update_auto_download
import com.wynime.app.ui.lang.settings_update_auto_download_description
import com.wynime.app.ui.lang.settings_update_check
import com.wynime.app.ui.lang.settings_update_check_failed
import com.wynime.app.ui.lang.settings_update_checking
import com.wynime.app.ui.lang.settings_update_current_version
import com.wynime.app.ui.lang.settings_update_in_app_download
import com.wynime.app.ui.lang.settings_update_in_app_download_disabled
import com.wynime.app.ui.lang.settings_update_in_app_download_enabled
import com.wynime.app.ui.lang.settings_update_new_version
import com.wynime.app.ui.lang.settings_update_software
import com.wynime.app.ui.lang.settings_update_type
import com.wynime.app.ui.lang.settings_update_type_alpha
import com.wynime.app.ui.lang.settings_update_type_alpha_short
import com.wynime.app.ui.lang.settings_update_type_beta
import com.wynime.app.ui.lang.settings_update_type_beta_short
import com.wynime.app.ui.lang.settings_update_type_stable
import com.wynime.app.ui.lang.settings_update_type_stable_short
import com.wynime.app.ui.lang.settings_update_up_to_date
import com.wynime.app.ui.lang.settings_update_view_changelog
import com.wynime.app.ui.settings.SettingsTab
import com.wynime.app.ui.settings.framework.SettingsState
import com.wynime.app.ui.settings.framework.components.DropdownItem
import com.wynime.app.ui.settings.framework.components.RangeSliderItem
import com.wynime.app.ui.settings.framework.components.RowButtonItem
import com.wynime.app.ui.settings.framework.components.SettingsScope
import com.wynime.app.ui.settings.framework.components.SliderItem
import com.wynime.app.ui.settings.framework.components.SwitchItem
import com.wynime.app.ui.settings.framework.components.TextButtonItem
import com.wynime.app.ui.settings.framework.components.TextItem
import com.wynime.app.ui.settings.framework.createTestSettingsState
import com.wynime.app.ui.settings.framework.rememberTestSettingsState
import com.wynime.app.ui.settings.rendering.ReleaseClassIcon
import com.wynime.app.ui.settings.rendering.guessReleaseClass
import com.wynime.app.ui.settings.tabs.theme.ThemeGroup
import com.wynime.app.ui.update.AppUpdateState
import com.wynime.app.ui.update.AppUpdateViewModel
import com.wynime.app.ui.update.NewVersion
import com.wynime.app.ui.update.UpdateNotifier
import com.wynime.utils.platform.annotations.TestOnly
import com.wynime.utils.platform.isAndroid
import com.wynime.utils.platform.isDesktop
import com.wynime.utils.platform.isMobile
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.seconds

sealed class CheckVersionResult {
    data class HasNewVersion(
        val newVersion: NewVersion,
    ) : CheckVersionResult()

    data object UpToDate : CheckVersionResult()
    data class Failed(
        val throwable: Throwable,
    ) : CheckVersionResult()
}

@Composable
fun AppSettingsTab(
    softwareUpdateGroupState: SoftwareUpdateGroupState,
    uiSettings: SettingsState<UISettings>,
    themeSettings: SettingsState<ThemeSettings>,
    videoScaffoldConfig: SettingsState<VideoScaffoldConfig>,
    playerKernelConfig: SettingsState<PlayerKernelConfig>,
    showDebug: Boolean,
    modifier: Modifier = Modifier
) {
    SettingsTab(modifier) {
        SoftwareUpdateGroup(softwareUpdateGroupState)
        AppearanceGroup(uiSettings)
        ThemeGroup(themeSettings)
        PlayerGroup(
            videoScaffoldConfig,
            playerKernelConfig,
            showDebug,
        )
        AppSettingsTabPlatform()
    }
}

@Composable
fun SettingsScope.AppearanceGroup(
    state: SettingsState<UISettings>,
) {
    val uiSettings by state

    LanguageSettingsPlatform(state)

    DropdownItem(
        selected = { uiSettings.mainSceneInitialPage },
        values = { MainScreenPage.visibleEntries },
        itemText = { Text(it.getText()) },
        onSelect = {
            state.update(uiSettings.copy(mainSceneInitialPage = it))
        },
        itemIcon = { Icon(it.getIcon(), null) },
        title = { Text(stringResource(Lang.settings_app_initial_page)) },
        description = { Text(stringResource(Lang.settings_app_initial_page_description)) },
    )
    if (LocalPlatform.current.isDesktop()) {
        DropdownItem(
            selected = { uiSettings.desktopCloseBehavior },
            values = { listOf(DesktopCloseBehavior.EXIT, DesktopCloseBehavior.MINIMIZE) },
            itemText = {
                Text(it.renderText())
            },
            exposedItemText = {
                Text(it.renderText())
            },
            onSelect = {
                state.update(uiSettings.copy(desktopCloseBehavior = it))
            },
            title = { Text(stringResource(Lang.settings_app_close_behavior)) },
        )
    }

    Group(title = { Text(stringResource(Lang.settings_app_search)) }, useThinHeader = true) {
        SwitchItem(
            checked = uiSettings.searchSettings.ignoreDoneAndDroppedSubjects,
            onCheckedChange = {
                state.update(
                    uiSettings.copy(
                        searchSettings = uiSettings.searchSettings.copy(
                            ignoreDoneAndDroppedSubjects = !uiSettings.searchSettings.ignoreDoneAndDroppedSubjects,
                        ),
                    ),
                )
            },
            title = { Text(stringResource(Lang.settings_app_not_show_done_and_dropped_subjects)) },
        )
        DropdownItem(
            selected = { uiSettings.searchSettings.nsfwMode },
            values = { NsfwMode.entries },
            itemText = {
                when (it) {
                    NsfwMode.HIDE -> Text(stringResource(Lang.settings_app_nsfw_hide))
                    NsfwMode.BLUR -> Text(stringResource(Lang.settings_app_nsfw_blur))
                    NsfwMode.DISPLAY -> Text(stringResource(Lang.settings_app_nsfw_display))
                }
            },
            onSelect = {
                state.update(
                    uiSettings.copy(
                        searchSettings = uiSettings.searchSettings.copy(nsfwMode = it),
                    ),
                )
            },
            title = { Text(stringResource(Lang.settings_app_nsfw_content)) },
        )
    }

    Group(title = { Text(stringResource(Lang.settings_app_my_collections)) }, useThinHeader = true) {
        SwitchItem(
            checked = uiSettings.myCollections.enableListAnimation1,
            onCheckedChange = {
                state.update(
                    uiSettings.copy(
                        myCollections = uiSettings.myCollections.copy(
                            enableListAnimation1 = !uiSettings.myCollections.enableListAnimation1,
                        ),
                    ),
                )
            },
            title = { Text(stringResource(Lang.settings_app_list_animation)) },
            description = { Text(stringResource(Lang.settings_app_list_animation_description)) },
        )
    }

    Group(title = { Text(stringResource(Lang.settings_app_episode_playback)) }, useThinHeader = true) {
        val episode by remember { derivedStateOf { uiSettings.episodeProgress } }
        SwitchItem(
            checked = episode.theme == EpisodeListProgressTheme.LIGHT_UP,
            onCheckedChange = {
                state.update(
                    uiSettings.copy(
                        episodeProgress = episode.copy(
                            theme = if (it) EpisodeListProgressTheme.LIGHT_UP else EpisodeListProgressTheme.ACTION,
                        ),
                    ),
                )
            },
            title = { Text(stringResource(Lang.settings_app_light_up_mode)) },
            description = { Text(stringResource(Lang.settings_app_light_up_mode_description)) },
        )
        SwitchItem(
            checked = episode.showEpisodeImages,
            onCheckedChange = {
                state.update(uiSettings.copy(episodeProgress = episode.copy(showEpisodeImages = it)))
            },
            title = { Text(stringResource(Lang.settings_app_episode_images)) },
            description = { Text(stringResource(Lang.settings_app_episode_images_description)) },
        )
    }

    Group(title = { Text(stringResource(Lang.settings_app_subject_title)) }, useThinHeader = true) {
        SwitchItem(
            checked = uiSettings.subjectAppearance.useOriginalTitle,
            onCheckedChange = {
                state.update(
                    uiSettings.copy(
                        subjectAppearance = uiSettings.subjectAppearance.copy(useOriginalTitle = it),
                    ),
                )
            },
            title = { Text(stringResource(Lang.settings_app_use_original_title)) },
            description = { Text(stringResource(Lang.settings_app_use_original_title_description)) },
        )
    }
}

@Stable
class SoftwareUpdateGroupState(
    val updateSettings: SettingsState<UpdateSettings>,
    val currentVersion: String = currentWynimeBuildConfig.versionName,
    val releaseClass: ReleaseClass = guessReleaseClass(currentVersion),
)

@Composable
fun SettingsScope.SoftwareUpdateGroup(
    state: SoftwareUpdateGroupState,
    modifier: Modifier = Modifier,
) {
    val autoUpdate: AppUpdateViewModel = viewModel { AppUpdateViewModel() }
    Group(title = { Text(stringResource(Lang.settings_update_software)) }, modifier = modifier) {
        TextItem(
            description = { Text(stringResource(Lang.settings_update_current_version)) },
            icon = { ReleaseClassIcon(state.releaseClass) },
            title = { Text(state.currentVersion) },
        )
        HorizontalDividerItem()
        val uriHandler = LocalUriHandler.current
        RowButtonItem(
            onClick = {
                uriHandler.openUri(
                    WynimeHelperDestination.RELEASE_PREFIX + currentWynimeBuildConfig.versionName,
                )
            },
            icon = { Icon(Icons.Rounded.ArrowOutward, null) },
        ) { Text(stringResource(Lang.settings_update_view_changelog)) }
        HorizontalDividerItem()
        val updateSettings by state.updateSettings
        SwitchItem(
            updateSettings.autoCheckUpdate,
            onCheckedChange = {
                state.updateSettings.update(updateSettings.copy(autoCheckUpdate = !updateSettings.autoCheckUpdate))
            },
            title = { Text(stringResource(Lang.settings_update_auto_check)) },
            description = { Text(stringResource(Lang.settings_update_auto_check_description)) },
        )
        HorizontalDividerItem()
        DropdownItem(
            selected = { updateSettings.releaseClass },
            values = { ReleaseClass.enabledEntries },
            itemText = {
                when (it) {
                    ReleaseClass.ALPHA -> Text(stringResource(Lang.settings_update_type_alpha))
                    ReleaseClass.BETA -> Text(stringResource(Lang.settings_update_type_beta))
                    ReleaseClass.RC,
                    ReleaseClass.STABLE -> Text(stringResource(Lang.settings_update_type_stable))
                }
            },
            exposedItemText = {
                when (it) {
                    ReleaseClass.ALPHA -> Text(stringResource(Lang.settings_update_type_alpha_short))
                    ReleaseClass.BETA -> Text(stringResource(Lang.settings_update_type_beta_short))
                    ReleaseClass.RC,
                    ReleaseClass.STABLE -> Text(stringResource(Lang.settings_update_type_stable_short))
                }
            },
            onSelect = {
                state.updateSettings.update(updateSettings.copy(releaseClass = it))
            },
            itemIcon = {
                ReleaseClassIcon(it)
            },
            title = { Text(stringResource(Lang.settings_update_type)) },
        )

            HorizontalDividerItem()
            SwitchItem(
                updateSettings.inAppDownload,
                { state.updateSettings.update(updateSettings.copy(inAppDownload = it)) },
                title = { Text(stringResource(Lang.settings_update_in_app_download)) },
                description = {
                    if (updateSettings.inAppDownload) {
                        Text(stringResource(Lang.settings_update_in_app_download_enabled))
                    } else {
                        Text(stringResource(Lang.settings_update_in_app_download_disabled))
                    }
                },
                enabled = updateSettings.autoCheckUpdate,
            )
            WynimeAnimatedVisibility(updateSettings.inAppDownload) {
                Column {
                    HorizontalDividerItem()
                    SwitchItem(
                        updateSettings.autoDownloadUpdate,
                        { state.updateSettings.update(updateSettings.copy(autoDownloadUpdate = it)) },
                        title = { Text(stringResource(Lang.settings_update_auto_download)) },
                        description = { Text(stringResource(Lang.settings_update_auto_download_description)) },
                        enabled = updateSettings.autoCheckUpdate,
                    )
                }
            }

        HorizontalDividerItem()

        val updatePresentation by autoUpdate.presentationFlow.collectAsStateWithLifecycle()
        TextButtonItem(
            onClick = {
                if (updatePresentation.isCheckingUpdate) {
                    return@TextButtonItem
                }
                autoUpdate.startCheckLatestVersion(uriHandler)
            },
            title = {
                when {
                    updatePresentation.isCheckingUpdate -> {
                        Text(stringResource(Lang.settings_update_checking))
                    }

                    updatePresentation.checkUpdateError != null -> {
                        Text(stringResource(Lang.settings_update_check_failed))
                    }

                    updatePresentation.state is AppUpdateState.HasNewVersion -> {
                        val newVersion = updatePresentation.newVersion
                        if (newVersion != null) {
                            Text(stringResource(Lang.settings_update_new_version, newVersion.name))
                        } else {
                            Text(stringResource(Lang.settings_update_up_to_date))
                        }
                    }

                    else -> {
                        Text(stringResource(Lang.settings_update_check))
                    }
                }
            },
        )
        Box(Modifier.fillMaxWidth()) {
            UpdateNotifier(autoUpdate)
        }
    }
}

@Composable
fun SettingsScope.PlayerGroup(
    videoScaffoldConfig: SettingsState<VideoScaffoldConfig>,
    playerKernelConfig: SettingsState<PlayerKernelConfig>,
    showDebug: Boolean
) {
    Group(title = { Text(stringResource(Lang.settings_player)) }) {
        val config by videoScaffoldConfig
        DropdownItem(
            selected = { config.fullscreenSwitchMode },
            values = { FullscreenSwitchMode.entries },
            itemText = {
                Text(
                    when (it) {
                        FullscreenSwitchMode.ALWAYS_SHOW_FLOATING -> stringResource(Lang.settings_player_fullscreen_always_show)
                        FullscreenSwitchMode.AUTO_HIDE_FLOATING -> stringResource(Lang.settings_player_fullscreen_auto_hide)
                        FullscreenSwitchMode.ONLY_IN_CONTROLLER -> stringResource(Lang.settings_player_fullscreen_only_in_controller)
                    },
                )
            },
            onSelect = {
                videoScaffoldConfig.update(config.copy(fullscreenSwitchMode = it))
            },
            title = { Text(stringResource(Lang.settings_player_fullscreen_button)) },
            description = { Text(stringResource(Lang.settings_player_fullscreen_button_description)) },
        )
        HorizontalDividerItem()
        DropdownItem(
            selected = { config.videoEnhancementDefaultMode },
            values = {
                listOf(
                    VideoEnhancementDefaultMode.PERFORMANCE,
                    VideoEnhancementDefaultMode.QUALITY,
                    VideoEnhancementDefaultMode.OFF,
                )
            },
            itemText = {
                Text(
                    when (it) {
                        VideoEnhancementDefaultMode.OFF -> stringResource(Lang.video_player_off)
                        VideoEnhancementDefaultMode.PERFORMANCE -> stringResource(Lang.video_player_performance)
                        VideoEnhancementDefaultMode.QUALITY -> stringResource(Lang.video_player_quality)
                    },
                )
            },
            onSelect = {
                videoScaffoldConfig.update(config.copy(videoEnhancementDefaultMode = it))
            },
            title = { Text(stringResource(Lang.settings_player_video_enhancement_default)) },
            description = {
                Text(stringResource(Lang.settings_player_video_enhancement_default_description))
            },
        )
        HorizontalDividerItem()
        SwitchItem(
            checked = config.autoMarkDone,
            onCheckedChange = {
                videoScaffoldConfig.update(config.copy(autoMarkDone = it))
            },
            title = { Text(stringResource(Lang.settings_player_auto_mark_done)) },
        )
        HorizontalDividerItem()
        SwitchItem(
            checked = config.hideSelectorOnSelect,
            onCheckedChange = {
                videoScaffoldConfig.update(config.copy(hideSelectorOnSelect = it))
            },
            title = { Text(stringResource(Lang.settings_player_hide_selector_on_select)) },
        )
        if (LocalPlatform.current.isMobile()) {
            HorizontalDividerItem()
            SwitchItem(
                checked = config.autoFullscreenOnLandscapeMode,
                onCheckedChange = {
                    videoScaffoldConfig.update(config.copy(autoFullscreenOnLandscapeMode = it))
                },
                title = { Text(stringResource(Lang.settings_player_auto_fullscreen_on_landscape)) },
            )
        }
        HorizontalDividerItem()
        SwitchItem(
            checked = config.autoPlayNext,
            onCheckedChange = {
                videoScaffoldConfig.update(config.copy(autoPlayNext = it))
            },
            title = { Text(stringResource(Lang.settings_player_auto_play_next)) },
        )
        HorizontalDividerItem()
        SwitchItem(
            checked = config.autoSkipOpEd,
            onCheckedChange = {
                videoScaffoldConfig.update(config.copy(autoSkipOpEd = it))
            },
            title = { Text(stringResource(Lang.settings_player_auto_skip_op_ed)) },
            description = { Text(stringResource(Lang.settings_player_auto_skip_op_ed_description)) },
        )
        HorizontalDividerItem()
        DropdownItem(
            selected = { config.opEdSkipDuration },
            values = { listOf(80.seconds, 85.seconds, 90.seconds) },
            itemText = {
                Text(stringResource(Lang.settings_player_op_ed_skip_duration_seconds, it.inWholeSeconds))
            },
            onSelect = {
                videoScaffoldConfig.update(config.copy(opEdSkipDuration = it))
            },
            title = { Text(stringResource(Lang.settings_player_op_ed_skip_duration)) },
        )
        HorizontalDividerItem()
        SwitchItem(
            checked = config.autoSwitchMediaOnPlayerError,
            onCheckedChange = {
                videoScaffoldConfig.update(config.copy(autoSwitchMediaOnPlayerError = it))
            },
            title = { Text(stringResource(Lang.settings_player_auto_switch_media_on_error)) },
        )
        if (LocalPlatform.current.isAndroid()) {
            HorizontalDividerItem()
            SwitchItem(
                checked = config.enableHighQualityAudioTimeStretch,
                onCheckedChange = {
                    videoScaffoldConfig.update(config.copy(enableHighQualityAudioTimeStretch = it))
                },
                title = { Text(stringResource(Lang.settings_player_audio_time_stretch)) },
                description = { Text(stringResource(Lang.settings_player_audio_time_stretch_description)) },
            )
        }
        HorizontalDividerItem()

            SwitchItem(
                checked = config.enableExperimentalHlsSegmentFiltering,
                onCheckedChange = {
                    videoScaffoldConfig.update(config.copy(enableExperimentalHlsSegmentFiltering = it))
                },
                title = { Text(stringResource(Lang.settings_player_experimental_hls_segment_filter)) },
                description = { Text(stringResource(Lang.settings_player_experimental_hls_segment_filter_description)) },
            )
            HorizontalDividerItem()

        HorizontalDividerItem()
        SwitchItem(
            checked = config.enableFramePreview,
            onCheckedChange = {
                videoScaffoldConfig.update(config.copy(enableFramePreview = it))
            },
            title = { Text(stringResource(Lang.settings_player_frame_preview)) },
            description = { Text(stringResource(Lang.settings_player_frame_preview_description)) },
        )
        HorizontalDividerItem()
        PlaybackSpeedItems(config, videoScaffoldConfig)
        PlayerGroupPlatform(videoScaffoldConfig, playerKernelConfig)
    }
}

@Composable
private fun SettingsScope.PlaybackSpeedItems(
    config: VideoScaffoldConfig,
    videoScaffoldConfig: SettingsState<VideoScaffoldConfig>,
) {
    val persistedRange = config.minPlaybackSpeed..config.maxPlaybackSpeed

    var rangeDragOverride by remember { mutableStateOf<ClosedFloatingPointRange<Float>?>(null) }
    var rangeDragging by remember { mutableStateOf(false) }
    val effectiveRange = rangeDragOverride ?: persistedRange
    LaunchedEffect(persistedRange, rangeDragOverride, rangeDragging) {
        if (!rangeDragging && rangeDragOverride == persistedRange) {
            rangeDragOverride = null
        }
    }

    RangeSliderItem(
        value = effectiveRange,
        onValueChange = {
            rangeDragging = true
            rangeDragOverride = VideoScaffoldConfig.normalizePlaybackSpeedRange(it, effectiveRange)
        },
        onValueChangeFinished = {
            val finalRange = rangeDragOverride ?: return@RangeSliderItem

            val committedConfig = config.withPlaybackSpeedRange(finalRange)
            rangeDragging = false
            rangeDragOverride = committedConfig.minPlaybackSpeed..committedConfig.maxPlaybackSpeed
            videoScaffoldConfig.update(committedConfig)
        },
        valueRange = VideoScaffoldConfig.MIN_SUPPORTED_PLAYBACK_SPEED..VideoScaffoldConfig.MAX_SUPPORTED_PLAYBACK_SPEED,
        steps = 14,
        valueIndicator = { Text(it.formatSpeedValue()) },
        valueLabel = {
            Text("${effectiveRange.start.formatSpeedValue()}x–${effectiveRange.endInclusive.formatSpeedValue()}x")
        },
        title = { Text(stringResource(Lang.settings_player_playback_speed_range)) },
        description = { Text(stringResource(Lang.settings_player_playback_speed_range_description)) },
    )

    val animatedRangeStart by animateFloatAsState(effectiveRange.start, label = "playbackSpeedRangeStart")
    val animatedRangeEnd by animateFloatAsState(effectiveRange.endInclusive, label = "playbackSpeedRangeEnd")
    val displayRange =
        if (animatedRangeStart < animatedRangeEnd) animatedRangeStart..animatedRangeEnd else effectiveRange

    HorizontalDividerItem()

    SwitchItem(
        checked = config.rememberPlaybackSpeed,
        onCheckedChange = {
            videoScaffoldConfig.update(config.copy(rememberPlaybackSpeed = it))
        },
        title = { Text(stringResource(Lang.settings_player_remember_playback_speed)) },
        description = { Text(stringResource(Lang.settings_player_remember_playback_speed_description)) },
    )

    val motionScheme = LocalWynimeMotionScheme.current.animatedVisibility
    WynimeAnimatedVisibility(
        visible = !config.rememberPlaybackSpeed,
        enter = motionScheme.columnEnter,
        exit = motionScheme.columnExit,
    ) {
        Column {
            HorizontalDividerItem()
            SpeedSliderItem(
                value = config.playbackSpeed,
                displayRange = displayRange,
                commitRange = effectiveRange,
                onCommit = { videoScaffoldConfig.update(config.copy(playbackSpeed = it)) },
                title = { Text(stringResource(Lang.settings_player_default_playback_speed)) },
                description = { Text(stringResource(Lang.settings_player_default_playback_speed_description)) },
            )
        }
    }

    HorizontalDividerItem()

    SpeedSliderItem(
        value = config.fastForwardSpeed,
        displayRange = displayRange,
        commitRange = effectiveRange,
        onCommit = { videoScaffoldConfig.update(config.copy(fastForwardSpeed = it)) },
        title = { Text(stringResource(Lang.settings_player_long_press_fast_forward_speed)) },
        description = { Text(stringResource(Lang.settings_player_long_press_fast_forward_speed_description)) },
    )
}

@Composable
private fun SettingsScope.SpeedSliderItem(
    value: Float,
    displayRange: ClosedFloatingPointRange<Float>,
    commitRange: ClosedFloatingPointRange<Float>,
    onCommit: (Float) -> Unit,
    title: @Composable RowScope.() -> Unit,
    description: @Composable (() -> Unit)? = null,
) {
    var dragOverride by remember { mutableStateOf<Float?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val displayValue = (dragOverride ?: value).coerceIn(displayRange)
    LaunchedEffect(value, dragOverride, dragging) {
        if (!dragging && dragOverride == value) {
            dragOverride = null
        }
    }
    SliderItem(
        title = title,
        description = description,
        valueLabel = {
            Text("${quantizeSliderValue(displayValue, displayRange).formatSpeedValue()}x")
        },
    ) {
        SteppedSlider(
            value = displayValue,
            onValueChange = {
                dragging = true
                dragOverride = quantizeSliderValue(it, displayRange)
            },
            onValueChangeFinished = { displayedValue ->
                val finalValue = dragOverride ?: displayedValue
                val committedValue = quantizeSliderValue(finalValue, commitRange)
                dragging = false
                dragOverride = committedValue
                onCommit(committedValue)
            },
            valueRange = displayRange,
            valueIndicator = { Text(it.formatSpeedValue(), maxLines = 1, softWrap = false) },
        )
    }
}

@Composable
internal expect fun SettingsScope.LanguageSettingsPlatform(
    state: SettingsState<UISettings>,
)

@Composable
internal expect fun SettingsScope.AppSettingsTabPlatform()

@Composable
private fun DesktopCloseBehavior.renderText(): String {
    return when (this) {
        DesktopCloseBehavior.EXIT -> stringResource(Lang.settings_app_close_behavior_exit)
        DesktopCloseBehavior.MINIMIZE -> stringResource(Lang.settings_app_close_behavior_minimize_to_tray)
    }
}

@Composable
internal expect fun SettingsScope.PlayerGroupPlatform(
    videoScaffoldConfig: SettingsState<VideoScaffoldConfig>,
    playerKernelConfig: SettingsState<PlayerKernelConfig>,
)

@OptIn(TestOnly::class)
@Preview
@Composable
private fun PreviewAppSettingsTab() {
    AppSettingsTab(
        softwareUpdateGroupState = rememberTestSoftwareUpdateGroupState(),
        uiSettings = rememberTestSettingsState(UISettings.Default),
        themeSettings = rememberTestSettingsState(ThemeSettings.Default),
        videoScaffoldConfig = rememberTestSettingsState(VideoScaffoldConfig.Default),
        playerKernelConfig = rememberTestSettingsState(PlayerKernelConfig.Default),
        showDebug = true,
    )
}

@TestOnly
@Composable
internal fun rememberTestSoftwareUpdateGroupState(): SoftwareUpdateGroupState {
    val scope = rememberCoroutineScope()
    return remember {
        SoftwareUpdateGroupState(
            updateSettings = createTestSettingsState(UpdateSettings(autoCheckUpdate = true), scope),
        )
    }
}
