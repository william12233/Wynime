package com.wynime.app.data.repository.user

import androidx.compose.runtime.Stable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import com.wynime.app.data.models.preference.AnalyticsSettings
import com.wynime.app.data.models.preference.DebugSettings
import com.wynime.app.data.models.preference.MediaCacheSettings
import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.app.data.models.preference.OneshotActionConfig
import com.wynime.app.data.models.preference.PlayerKernelConfig
import com.wynime.app.data.models.preference.ProfileSettings
import com.wynime.app.data.models.preference.ProxySettings
import com.wynime.app.data.models.preference.ThemeSettings
import com.wynime.app.data.models.preference.UISettings
import com.wynime.app.data.models.preference.UpdateSettings
import com.wynime.app.data.models.preference.VideoResolverSettings
import com.wynime.app.data.models.preference.VideoScaffoldConfig
import com.wynime.app.data.persistent.DataStoreJson
import com.wynime.utils.logging.debug
import com.wynime.utils.logging.error
import com.wynime.utils.logging.logger

interface SettingsRepository {
    val mediaSelectorSettings: Settings<MediaSelectorSettings>

    val defaultMediaPreference: Settings<MediaPreference>

    val profileSettings: Settings<ProfileSettings>
    val proxySettings: Settings<ProxySettings>
    val mediaCacheSettings: Settings<MediaCacheSettings>
    val uiSettings: Settings<UISettings>
    val themeSettings: Settings<ThemeSettings>
    val updateSettings: Settings<UpdateSettings>
    val videoScaffoldConfig: Settings<VideoScaffoldConfig>

    val playerKernelConfig: Settings<PlayerKernelConfig>

    val videoResolverSettings: Settings<VideoResolverSettings>

    val oneshotActionConfig: Settings<OneshotActionConfig>

    val analyticsSettings: Settings<AnalyticsSettings>
    val debugSettings: Settings<DebugSettings>
}

@Stable
interface Settings<T> {
    val flow: Flow<T>
    suspend fun set(value: T)
    suspend fun update(update: T.() -> T) = set(flow.first().update())
}

class PreferencesRepositoryImpl(
    private val preferences: DataStore<Preferences>,
) : SettingsRepository {
    private val format get() = DataStoreJson

    inner class BooleanPreference(
        val name: String,
        private val default: Boolean,
    ) : Settings<Boolean> {
        private val key = booleanPreferencesKey(name)
        override val flow: Flow<Boolean> = preferences.data.map { it[key] ?: default }
        override suspend fun update(update: (Boolean) -> Boolean) {
            preferences.edit {
                it[key] = update(it[key] ?: default)
            }
        }

        override suspend fun set(value: Boolean) {
            preferences.edit { it[key] = value }
        }
    }

    inner class SerializablePreference<T : Any>(
        val name: String,
        private val serializer: KSerializer<T>,
        private val default: () -> T,
    ) : Settings<T> {
        private val key = stringPreferencesKey(name)
        override val flow: Flow<T> = preferences.data
            .map { it[key] }
            .distinctUntilChanged()
            .map { string ->
                if (string == null) {
                    default()
                } else try {
                    format.decodeFromString(serializer, string)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to decode preference '$name'. Using default. Failed json: $string" }
                    default()
                }
            }

        override suspend fun update(update: (T) -> T) {
            logger.debug { "Updating preference '$key' with lambda" }
            preferences.edit { pref ->
                pref[key] = format.encodeToString(
                    serializer,
                    update(
                        pref[key]?.let { format.decodeFromString(serializer, it) }
                            ?: default(),
                    ),
                )
            }
        }

        override suspend fun set(value: T) {
            logger.debug { "Updating preference '$key' with: $value" }
            preferences.edit {
                it[key] = format.encodeToString(serializer, value)
            }
        }
    }

    override val mediaSelectorSettings: Settings<MediaSelectorSettings> = SerializablePreference(
        "mediaSelectorSettings",
        MediaSelectorSettings.serializer(),
        default = { MediaSelectorSettings.Default },
    )
    override val defaultMediaPreference: Settings<MediaPreference> =
        SerializablePreference(
            "defaultMediaPreference",
            MediaPreference.serializer(),
            default = { MediaPreference.PlatformDefault },
        )
    override val profileSettings: Settings<ProfileSettings> = SerializablePreference(
        "profileSettings",
        ProfileSettings.serializer(),
        default = { ProfileSettings.Default },
    )
    override val proxySettings: Settings<ProxySettings> = SerializablePreference(
        "proxyPreferences",
        ProxySettings.serializer(),
        default = { ProxySettings.Default },
    )
    override val mediaCacheSettings: Settings<MediaCacheSettings> = SerializablePreference(
        "cachePreferences",
        MediaCacheSettings.serializer(),
        default = { MediaCacheSettings.Default },
    )
    override val uiSettings: Settings<UISettings> = SerializablePreference(
        "uiSettings",
        UISettings.serializer(),
        default = { UISettings.Default },
    )
    override val themeSettings: Settings<ThemeSettings> = SerializablePreference(
        "themeSettings",
        ThemeSettings.serializer(),
        default = { ThemeSettings.Default },
    )
    override val updateSettings: Settings<UpdateSettings> = SerializablePreference(
        "updateSettings",
        UpdateSettings.serializer(),
        default = { UpdateSettings.Default },
    )
    override val videoScaffoldConfig: Settings<VideoScaffoldConfig> = SerializablePreference(
        "videoScaffoldConfig",
        VideoScaffoldConfig.serializer(),
        default = { VideoScaffoldConfig.Default },
    )
    override val playerKernelConfig: Settings<PlayerKernelConfig> = SerializablePreference(
        "playerKernelConfig",
        PlayerKernelConfig.serializer(),
        default = { PlayerKernelConfig.Default },
    )
    override val videoResolverSettings: Settings<VideoResolverSettings> = SerializablePreference(
        "videoResolverSettings",
        VideoResolverSettings.serializer(),
        default = { VideoResolverSettings.Default },
    )
    override val oneshotActionConfig: Settings<OneshotActionConfig> = SerializablePreference(
        "oneshotActionConfig",
        OneshotActionConfig.serializer(),
        default = { OneshotActionConfig.Default },
    )

    override val analyticsSettings: Settings<AnalyticsSettings> = SerializablePreference(
        "analyticsSettings",
        AnalyticsSettings.serializer(),
        default = { AnalyticsSettings.default() },
    )

    override val debugSettings: Settings<DebugSettings> = SerializablePreference(
        "debugSettings",
        DebugSettings.serializer(),
        default = { DebugSettings.Default },
    )

    private companion object {
        private val logger = logger<SettingsRepository>()
    }
}
