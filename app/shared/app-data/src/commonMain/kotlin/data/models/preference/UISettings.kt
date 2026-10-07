package com.wynime.app.data.models.preference

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.text.intl.Locale
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import com.wynime.app.navigation.MainScreenPage

@Serializable
@Immutable
data class UISettings(

    val appLanguage: @Serializable(LocaleSerializer::class) Locale? = null,

    val mainSceneInitialPage: MainScreenPage = MainScreenPage.Exploration,

    @Suppress("DEPRECATION") @Deprecated(
        "For migration. Use themeSettings instead",
        level = DeprecationLevel.WARNING,
    ) val theme: LegacyThemeSettings? = null,
    val myCollections: MyCollectionsSettings = MyCollectionsSettings.Default,
    val searchSettings: SearchSettings = SearchSettings.Default,
    val episodeProgress: EpisodeProgressSettings = EpisodeProgressSettings.Default,
    val subjectAppearance: SubjectAppearanceSettings = SubjectAppearanceSettings.Default,
    val desktopCloseBehavior: DesktopCloseBehavior = DesktopCloseBehavior.EXIT,
    @Suppress("PropertyName") @Transient val _placeholder: Int = 0,
) {
    companion object {
        @Stable
        val Default = UISettings()
    }
}

@Serializable
enum class DesktopCloseBehavior {
    EXIT,
    MINIMIZE,
}

@Deprecated("For migration. Use themeSettings instead", level = DeprecationLevel.WARNING)
@Suppress("DEPRECATION")
@Serializable
@Immutable
data class LegacyThemeSettings(
    val darkMode: DarkMode = DarkMode.AUTO,

    val dynamicTheme: Boolean = false,
    @Suppress("PropertyName") @Transient val _placeholder: Int = 0,
) {
    companion object {
        @Stable
        val Default = LegacyThemeSettings()
    }
}

@Serializable
@Immutable
data class MyCollectionsSettings(
    val enableListAnimation1: Boolean = true,
) {
    companion object {
        @Stable
        val Default = MyCollectionsSettings()
    }
}

@Serializable
@Immutable
data class SubjectAppearanceSettings(

    val useOriginalTitle: Boolean = false,
) {
    companion object {
        @Stable
        val Default = SubjectAppearanceSettings()
    }
}

@Serializable
enum class NsfwMode {

    HIDE,

    BLUR,

    DISPLAY,
}

@Serializable
@Immutable
data class SearchSettings(
    val ignoreDoneAndDroppedSubjects: Boolean = false,
    val nsfwMode: NsfwMode = NsfwMode.BLUR,
) {
    companion object {
        @Stable
        val Default = SearchSettings()
    }
}

@Serializable
@Immutable
data class EpisodeProgressSettings(
    val theme: EpisodeListProgressTheme = EpisodeListProgressTheme.Default,

    val showEpisodeImages: Boolean = true,
) {
    companion object {
        @Stable
        val Default = EpisodeProgressSettings()
    }
}

@Immutable
@Serializable
enum class EpisodeListProgressTheme {

    LIGHT_UP,

    ACTION;

    companion object {
        @Stable
        val Default = ACTION
    }
}

internal object LocaleSerializer : KSerializer<Locale> {
    @Serializable
    data class Delegate(
        val languageTag: String,
    )

    override val descriptor: SerialDescriptor = Delegate.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: Locale
    ) {
        Delegate.serializer().serialize(encoder, Delegate(value.toLanguageTag()))
    }

    override fun deserialize(decoder: Decoder): Locale {
        val delegate = Delegate.serializer().deserialize(decoder)
        return Locale(delegate.languageTag)
    }
}
