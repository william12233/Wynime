package com.wynime.app.navigation

import kotlinx.serialization.SerialName

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import com.wynime.app.ui.lang.Lang
import com.wynime.app.ui.lang.main_screen_page_cache_management
import com.wynime.app.ui.lang.main_screen_page_collection
import com.wynime.app.ui.lang.main_screen_page_exploration
import org.jetbrains.compose.resources.stringResource

@Serializable
sealed class NavRoutes : NavKey {
    @SerialName("me.him188.ani.app.navigation.NavRoutes.EmailLoginStart")
    @Serializable
    data object EmailLoginStart : NavRoutes()

    @SerialName("me.him188.ani.app.navigation.NavRoutes.EmailLoginVerify")
    @Serializable
    data object EmailLoginVerify : NavRoutes()

    @SerialName("me.him188.ani.app.navigation.NavRoutes.Main")
    @Serializable
    data class Main(
        val initialPage: MainScreenPage,
        val requestSearchFocus: Boolean = false,
    ) : NavRoutes()

    @SerialName("me.him188.ani.app.navigation.NavRoutes.OAuthAuthorize")
    @Serializable
    data class OAuthAuthorize(val provider: String) : NavRoutes()

    @SerialName("me.him188.ani.app.navigation.NavRoutes.QrLoginScan")
    @Serializable
    data object QrLoginScan : NavRoutes()

    @SerialName("me.him188.ani.app.navigation.NavRoutes.QrLoginConfirm")
    @Serializable
    data class QrLoginConfirm(val requestId: String) : NavRoutes()

    @SerialName("me.him188.ani.app.navigation.NavRoutes.Settings")
    @Serializable
    data class Settings(

        val tab: SettingsTab? = null,
    ) : NavRoutes()

    @SerialName("me.him188.ani.app.navigation.NavRoutes.SubjectSearch")
    @Serializable
    data class SubjectSearch(
        val keyword: String? = null,
        val tags: List<String>? = null,
    ) : NavRoutes()

    @SerialName("me.him188.ani.app.navigation.NavRoutes.SubjectDetail")
    @Serializable
    data class SubjectDetail(
        val subjectId: Int,
        val placeholder: SubjectDetailPlaceholder? = null,
    ) : NavRoutes()

    @SerialName("me.him188.ani.app.navigation.NavRoutes.SubjectRelationGraph")
    @Serializable
    data class SubjectRelationGraph(
        val subjectId: Int,
    ) : NavRoutes()

    @SerialName("me.him188.ani.app.navigation.NavRoutes.PersonDetail")
    @Serializable
    data class PersonDetail(
        val personId: Int,
        val role: PersonDetailRole = PersonDetailRole.Staff,
    ) : NavRoutes()

    @SerialName("me.him188.ani.app.navigation.NavRoutes.CharacterDetail")
    @Serializable
    data class CharacterDetail(
        val characterId: Int,
    ) : NavRoutes()

    @SerialName("me.him188.ani.app.navigation.NavRoutes.SubjectCaches")
    @Serializable
    data class SubjectCaches(
        val subjectId: Int,
    ) : NavRoutes()

    @SerialName("me.him188.ani.app.navigation.NavRoutes.EpisodeDetail")
    @Serializable
    data class EpisodeDetail(
        val subjectId: Int,
        val episodeId: Int,
    ) : NavRoutes()

    @SerialName("me.him188.ani.app.navigation.NavRoutes.Caches")
    @Serializable
    data object Caches : NavRoutes()

    @SerialName("me.him188.ani.app.navigation.NavRoutes.CacheDetail")
    @Serializable
    data class CacheDetail(
        val cacheId: String,
    ) : NavRoutes()

    @SerialName("me.him188.ani.app.navigation.NavRoutes.Schedule")
    @Serializable
    data object Schedule : NavRoutes()

    @SerialName("me.him188.ani.app.navigation.NavRoutes.PlaybackHistory")
    @Serializable
    data object PlaybackHistory : NavRoutes()

    @SerialName("me.him188.ani.app.navigation.NavRoutes.PlaybackHistorySyncStatus")
    @Serializable
    data object PlaybackHistorySyncStatus : NavRoutes()

    @SerialName("me.him188.ani.app.navigation.NavRoutes.BangumiMerge")
    @Serializable
    data object BangumiMerge : NavRoutes()
}

@Serializable
enum class PersonDetailRole { VoiceActor, Staff }

@Serializable
data class SubjectDetailPlaceholder(
    val id: Int,
    val name: String = "",
    val nameCN: String = "",
    val coverUrl: String = "",
)

@Serializable
enum class MainScreenPage {
    Exploration,
    Collection,
    CacheManagement,
    ;

    companion object {
        @Stable
        val visibleEntries get() = entries
    }
}

@Immutable
@Serializable
enum class SettingsTab {
    PROFILE,

    APPEARANCE,
    THEME,
    UPDATE,

    PLAYER,
    MEDIA_SOURCE,
    MEDIA_SELECTOR,

    PROXY,

    STORAGE,

    SETTINGS_BACKUP,

    ABOUT,
    LOG,
    DEBUG,
    ;

    companion object {

        val Default = APPEARANCE
    }
}

@Stable
fun MainScreenPage.getIcon() = when (this) {
    MainScreenPage.Exploration -> Icons.Rounded.TravelExplore
    MainScreenPage.Collection -> Icons.Rounded.Star
    MainScreenPage.CacheManagement -> Icons.Rounded.DownloadDone
}

@Stable
@Composable
fun MainScreenPage.getText(): String = when (this) {
    MainScreenPage.Exploration -> stringResource(Lang.main_screen_page_exploration)
    MainScreenPage.Collection -> stringResource(Lang.main_screen_page_collection)
    MainScreenPage.CacheManagement -> stringResource(Lang.main_screen_page_cache_management)
}
