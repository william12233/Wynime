package com.wynime.app.domain.media.selector.testFramework

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import com.wynime.app.data.models.episode.EpisodeInfo
import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.data.models.subject.SubjectSeriesInfo
import com.wynime.app.data.models.subject.SubjectSeriesInfoBuilder
import com.wynime.app.data.models.subject.toBuilder
import com.wynime.app.domain.media.createTestDefaultMedia
import com.wynime.app.domain.media.createTestMediaProperties
import com.wynime.app.domain.media.fetch.MediaFetchSession
import com.wynime.app.domain.media.fetch.create
import com.wynime.app.domain.media.selector.DefaultMediaSelector
import com.wynime.app.domain.media.selector.MediaSelectorContext
import com.wynime.app.domain.media.selector.MediaSelectorSourceTiers
import com.wynime.app.domain.media.selector.MediaSelectorSubtitlePreferences
import com.wynime.app.domain.media.selector.SubtitleKindPreference
import com.wynime.app.domain.mediasource.MediaSourceTier
import com.wynime.datasources.api.DefaultMedia
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.MediaExtraFiles
import com.wynime.datasources.api.SubtitleKind
import com.wynime.datasources.api.source.MediaFetchRequest
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.datasources.api.source.MediaSourceLocation
import com.wynime.datasources.api.topic.EpisodeRange
import com.wynime.datasources.api.topic.FileSize
import com.wynime.datasources.api.topic.FileSize.Companion.megaBytes
import com.wynime.datasources.api.topic.Resolution
import com.wynime.datasources.api.topic.ResourceLocation
import com.wynime.datasources.api.topic.SubtitleLanguage
import com.wynime.test.DynamicTestsBuilder
import com.wynime.utils.platform.collections.copyPut
import com.wynime.utils.platform.collections.toImmutable
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmName
import kotlin.random.Random
import com.wynime.datasources.api.Media

sealed class MediaSelectorTestSuite {
    val random = Random(42)

    val initApi = InitApi()
    val preferenceApi = PreferenceApi()

    inner class InitApi {
        lateinit var subjectName: String
        var aliases: MutableList<String> = mutableListOf()
        var seriesInfo: SubjectSeriesInfo = SubjectSeriesInfo.Fallback
        var episodeId: Int = 0
        var episodeSort: EpisodeSort = EpisodeSort(1)
        var episodeEp: EpisodeSort? = EpisodeSort(1)
        var episodeName: String = ""

        fun aliases(vararg aliases: String) {
            this.aliases.addAll(aliases)
        }

        var seasonSort: Int
            get() = seriesInfo.seasonSort
            set(value) {
                seriesInfo = seriesInfo.copy(seasonSort = value)
            }

        fun seriesInfo(
            seasonSort: Int = this.seasonSort, block: SubjectSeriesInfoBuilder.() -> Unit
        ) {
            seriesInfo = this.seriesInfo.toBuilder().also { it.seasonSort = seasonSort }.apply(block).build()
        }
    }

    inline fun initSubject(
        subjectName: String,
        block: InitApi.() -> Unit = {}
    ) {
        val init = initApi.apply(block)
        init.subjectName = subjectName

        preferenceApi.mediaSelectorContext.value = createMediaSelectorContextFromEmpty(
            true,
            subjectInfo = SubjectInfo.Empty.copy(
                nameCn = init.subjectName,
                aliases = init.aliases.toList(),
            ),
            episodeInfo = EpisodeInfo.Empty.copy(episodeId = init.episodeId, sort = init.episodeSort, ep = init.episodeEp, name = init.episodeName),
            subjectSeriesInfo = init.seriesInfo,
        )
        preferenceApi.mediaSelectorSettings.value = MediaSelectorSettings.Default
        preferenceApi.savedUserPreference.value = DEFAULT_PREFERENCE
        preferenceApi.savedDefaultPreference.value = DEFAULT_PREFERENCE
    }

    inner class PreferenceApi {
        val savedUserPreference = MutableStateFlow(DEFAULT_PREFERENCE)
        val savedDefaultPreference = MutableStateFlow(DEFAULT_PREFERENCE)
        val mediaSelectorSettings = MutableStateFlow(MediaSelectorSettings.Default)
        val mediaSelectorContext = MutableStateFlow(
            createMediaSelectorContextFromEmpty(),
        )

        var sourceTiers
            get() = mediaSelectorContext.value.mediaSourceTiers
            set(value) {
                mediaSelectorContext.value = mediaSelectorContext.value.copy(
                    mediaSourceTiers = value,
                )
            }

        fun setSubtitlePreferences(
            preferences: MediaSelectorSubtitlePreferences = getCurrentSubtitlePreferences()
        ) {
            mediaSelectorContext.value = mediaSelectorContext.value.run {
                copy(subtitlePreferences = preferences)
            }
        }

        private fun getCurrentSubtitlePreferences() = (mediaSelectorContext.value.subtitlePreferences
            ?: MediaSelectorSubtitlePreferences.Companion.AllNormal)

        fun setSubtitlePreference(
            key: SubtitleKind,
            value: SubtitleKindPreference
        ) {
            setSubtitlePreferences(
                MediaSelectorSubtitlePreferences(
                    getCurrentSubtitlePreferences().values.copyPut(key, value).toImmutable(),
                ),
            )
        }

        fun preferKind(kind: MediaSourceKind?) {
            mediaSelectorSettings.value = mediaSelectorSettings.value.copy(
                preferKind = kind,
            )
        }
    }

    private var mediaIdCounter: Int = 0
    fun media(
        sourceId: String = SOURCE_PRIMARY_WEB,
        resolution: String = "1080P",
        alliance: String = "字幕组",
        size: FileSize = 1.megaBytes,
        publishedTime: Long = 0,
        subtitleLanguages: List<String> = listOf(
            SubtitleLanguage.ChineseSimplified,
            SubtitleLanguage.ChineseTraditional,
        ).map { it.id },
        location: MediaSourceLocation = MediaSourceLocation.Online,
        kind: MediaSourceKind = MediaSourceKind.WEB,
        episodeRange: EpisodeRange = EpisodeRange.single(EpisodeSort(1)),
        subtitleKind: SubtitleKind? = null,
        extraFiles: MediaExtraFiles = MediaExtraFiles.EMPTY,
        id: Int = mediaIdCounter++,
        originalTitle: String = "[字幕组] 孤独摇滚 $id",
        subjectName: String? = null,
        episodeName: String? = null,
        mediaId: String = "$sourceId.$id",
    ): DefaultMedia {
        return createTestDefaultMedia(
            mediaId = mediaId,
            mediaSourceId = sourceId,
            originalTitle = originalTitle,
            download = ResourceLocation.HttpStreamingFile("https://example.com/$id.m3u8"),
            originalUrl = "https://example.com/$id",
            publishedTime = publishedTime,
            episodeRange = episodeRange,
            properties = createTestMediaProperties(
                subjectName = subjectName,
                episodeName = episodeName,
                subtitleLanguageIds = subtitleLanguages,
                resolution = resolution,
                alliance = alliance,
                size = size,
                subtitleKind = subtitleKind,
            ),
            location = location,
            kind = kind,
            extraFiles = extraFiles,
        )
    }

    companion object {
        val DEFAULT_PREFERENCE = MediaPreference.Empty.copy(
            fallbackResolutions = listOf(
                Resolution.R2160P,
                Resolution.R1440P,
                Resolution.R1080P,
                Resolution.R720P,
            ).map { it.id },
            fallbackSubtitleLanguageIds = listOf(
                SubtitleLanguage.ChineseSimplified,
                SubtitleLanguage.ChineseTraditional,
            ).map { it.id },
        )

        const val SOURCE_PRIMARY_WEB = "web-primary"
        const val SOURCE_SECONDARY_WEB = "web-secondary"

        @Suppress("SameParameterValue", "INVISIBLE_REFERENCE")
        @kotlin.internal.LowPriorityInOverloadResolution
        fun createMediaSelectorContextFromEmpty(
            subjectCompleted: Boolean = false,
            mediaSourcePrecedence: List<String> = emptyList(),
            subtitleKindFilters: MediaSelectorSubtitlePreferences = MediaSelectorSubtitlePreferences.Companion.AllNormal,
            subjectSequelNames: Set<String> = emptySet(),
            subjectInfo: SubjectInfo = SubjectInfo.Empty,
            episodeInfo: EpisodeInfo = EpisodeInfo.Empty,
            mediaSelectorSourceTiers: MediaSelectorSourceTiers = MediaSelectorSourceTiers.Companion.Empty,
        ) = createMediaSelectorContextFromEmpty(
            subjectCompleted = subjectCompleted,
            mediaSourcePrecedence = mediaSourcePrecedence,
            subtitleKindFilters = subtitleKindFilters,
            subjectSeriesInfo = SubjectSeriesInfo.Fallback.copy(sequelSubjectNames = subjectSequelNames),
            subjectInfo = subjectInfo,
            episodeInfo = episodeInfo,
            mediaSelectorSourceTiers = mediaSelectorSourceTiers,
        )

        @Suppress("SameParameterValue")
        fun createMediaSelectorContextFromEmpty(
            subjectCompleted: Boolean = false,
            mediaSourcePrecedence: List<String> = emptyList(),
            subtitleKindFilters: MediaSelectorSubtitlePreferences = MediaSelectorSubtitlePreferences.Companion.AllNormal,
            subjectSeriesInfo: SubjectSeriesInfo = SubjectSeriesInfo.Fallback,
            subjectInfo: SubjectInfo = SubjectInfo.Empty,
            episodeInfo: EpisodeInfo = EpisodeInfo.Empty,
            mediaSelectorSourceTiers: MediaSelectorSourceTiers = MediaSelectorSourceTiers.Companion.Empty,
        ) =
            MediaSelectorContext(
                subjectFinished = subjectCompleted,
                mediaSourcePrecedence = mediaSourcePrecedence,
                subtitlePreferences = subtitleKindFilters,
                subjectSeriesInfo = subjectSeriesInfo,
                subjectInfo = subjectInfo,
                episodeInfo = episodeInfo,
                mediaSourceTiers = mediaSelectorSourceTiers,
            )
    }
}

class SimpleMediaSelectorTestSuite(
    val testScope: TestScope,

    private val cachingEnabled: Boolean = false,
) : MediaSelectorTestSuite() {
    val mediaApi = MediaApi()

    inner class MediaApi {
        val mediaList: MutableStateFlow<MutableList<Media>> = MutableStateFlow(mutableListOf())

        fun addMedia(vararg media: Media) {
            mediaList.value.addAll(media)
        }

        fun addMedia(media: DefaultMedia): DefaultMedia {
            mediaList.value.add(media)
            return media
        }

        fun shuffle() {
            mediaList.value.shuffle(random)
        }

        fun addSimpleWebMedia(
            subjectName: String,
            episodeSort: EpisodeSort? = null,
            episodeRange: EpisodeRange? = null,
        ) {
            val finalRange = if (episodeSort == null && episodeRange == null) {
                EpisodeRange.single(EpisodeSort(1))
            } else if (episodeSort != null) {
                require(episodeRange == null) {
                    "episodeSort and episodeRange cannot be both set."
                }
                EpisodeRange.single(episodeSort)
            } else {
                episodeRange!!
            }

            addMedia(
                media(

                    alliance = "简中",
                    episodeRange = finalRange, kind = MediaSourceKind.WEB,
                    subjectName = subjectName,
                    originalTitle = "$subjectName $finalRange",
                    subtitleLanguages = listOf(SubtitleLanguage.ChineseSimplified.id),
                ),
            )
        }
    }

    val selector = DefaultMediaSelector(
        mediaSelectorContextNotCached = preferenceApi.mediaSelectorContext,
        mediaListNotCached = mediaApi.mediaList,
        savedUserPreference = preferenceApi.savedUserPreference,
        savedDefaultPreference = preferenceApi.savedDefaultPreference,
        enableCaching = cachingEnabled,
        mediaSelectorSettings = preferenceApi.mediaSelectorSettings,
        flowCoroutineContext = if (cachingEnabled) {
            testScope.coroutineContext[ContinuationInterceptor]!!
        } else {
            Dispatchers.Default
        },
        cachingScope = testScope.backgroundScope,
    )
}

class FetchMediaSelectorTestSuite(
    private val testDispatcher: CoroutineContext,

    private val cachingEnabled: Boolean = false,

    private val cachingScope: CoroutineScope? = null,
) : MediaSelectorTestSuite() {
    private lateinit var fetchSession: TestMediaFetchSession<*>

    context(scope: TestScope)
    fun <R> configureFetchSession(
        startInBackground: Boolean = true,
        block: TestMediaFetchSessionBuilder.() -> R,
    ): TestMediaFetchSession<R> {
        return buildTestMediaFetchSession(
            dispatcher = testDispatcher,
        ) {
            request {
                val mediaSelectorContext = preferenceApi.mediaSelectorContext.value
                takeFrom(
                    MediaFetchRequest.create(
                        mediaSelectorContext.subjectInfo!!,
                        mediaSelectorContext.episodeInfo!!,
                    ),
                )
            }

            block()
        }.also {
            fetchSession = it
            if (startInBackground) {
                it.session.startInBackground()
            }
        }
    }

    val selector by lazy {
        DefaultMediaSelector(
            mediaSelectorContextNotCached = preferenceApi.mediaSelectorContext,
            mediaListNotCached = fetchSession.session.cumulativeResults,
            savedUserPreference = preferenceApi.savedUserPreference,
            savedDefaultPreference = preferenceApi.savedDefaultPreference,
            enableCaching = cachingEnabled,
            mediaSelectorSettings = preferenceApi.mediaSelectorSettings,
            flowCoroutineContext = testDispatcher,
            cachingScope = cachingScope,
        )
    }

    context(scope: TestScope)
    fun MediaFetchSession.startInBackground() {
        scope.backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            cumulativeResults.collect()
        }
    }

}

fun MediaSelectorTestSuite.setSourceTier(sourceId: String, tier: Int?) = setSourceTier(sourceId, tier?.toUInt())

@JvmName("setSourceTierUInt")
fun MediaSelectorTestSuite.setSourceTier(sourceId: String, tier: UInt?) =
    setSourceTier(sourceId, tier?.let { MediaSourceTier(it) })

fun MediaSelectorTestSuite.setSourceTier(sourceId: String, tier: MediaSourceTier?) {
    val oldTiers = preferenceApi.mediaSelectorContext.value.mediaSourceTiers
    val newMap = oldTiers?.tiers.orEmpty().toMutableMap()
    if (tier != null) {
        newMap[sourceId] = tier
    } else {
        newMap.remove(sourceId)
    }
    preferenceApi.mediaSelectorContext.value = preferenceApi.mediaSelectorContext.value.copy(
        mediaSourceTiers = MediaSelectorSourceTiers(newMap, oldTiers?.channelTiers.orEmpty()) {

            MediaSourceTier.Fallback
        },
    )
}

fun MediaSelectorTestSuite.setChannelTiers(sourceId: String, vararg pairs: Pair<String, UInt>) {
    val oldTiers = preferenceApi.mediaSelectorContext.value.mediaSourceTiers
    val newChannelTiers = oldTiers?.channelTiers.orEmpty().toMutableMap()
    newChannelTiers[sourceId] = pairs.associate { (channel, tier) -> channel to MediaSourceTier(tier) }
    preferenceApi.mediaSelectorContext.value = preferenceApi.mediaSelectorContext.value.copy(
        mediaSourceTiers = MediaSelectorSourceTiers(oldTiers?.tiers.orEmpty(), newChannelTiers) {
            MediaSourceTier.Fallback
        },
    )
}

@JvmName("setSourceTiersPairStringInt")
fun MediaSelectorTestSuite.setSourceTiers(vararg pairs: Pair<String, Int?>) =
    setSourceTiers(*pairs.map { it.first to it.second?.toUInt() }.toTypedArray())

@JvmName("setSourceTiersPairStringUInt")
fun MediaSelectorTestSuite.setSourceTiers(vararg pairs: Pair<String, UInt?>) {
    val oldTiers = preferenceApi.mediaSelectorContext.value.mediaSourceTiers
    val newMap = oldTiers?.tiers.orEmpty().toMutableMap()
    for ((sourceId, tier) in pairs) {
        if (tier == null) {
            newMap.remove(sourceId)
        } else {
            newMap[sourceId] = MediaSourceTier(tier)
        }
    }
    preferenceApi.mediaSelectorContext.value = preferenceApi.mediaSelectorContext.value.copy(
        mediaSourceTiers = MediaSelectorSourceTiers(newMap, oldTiers?.channelTiers.orEmpty()) {
            MediaSourceTier.Fallback
        },
    )
}

fun MediaSelectorTestSuite.getMediaSourceTier(sourceId: String) = preferenceApi.sourceTiers?.tiers?.get(sourceId)

context(suite: MediaSelectorTestSuite)
var Handle.tier: Int?
    get() = suite.getMediaSourceTier(instance.mediaSourceId)?.value?.toInt()
    set(value) {
        suite.setSourceTier(instance.mediaSourceId, value?.toUInt())
    }

context(suite: MediaSelectorTestSuite)
fun Handle.channelTiers(vararg pairs: Pair<String, Int>) {
    suite.setChannelTiers(
        instance.mediaSourceId,
        *pairs.map { (channel, tier) -> channel to tier.toUInt() }.toTypedArray(),
    )
}

fun runSimpleMediaSelectorTestSuite(
    cachingEnabled: Boolean = false,
    buildTest: SimpleMediaSelectorTestSuite.() -> Unit = {},
    thenCheck: suspend SimpleMediaSelectorTestSuite.() -> Unit
): TestResult = runTest {
    val suite = SimpleMediaSelectorTestSuite(this, cachingEnabled)
    suite.apply(buildTest)
    suite.thenCheck()
}

fun runFetchMediaSelectorTestSuite(
    cachingEnabled: Boolean = false,
    buildTest: context(TestScope) FetchMediaSelectorTestSuite.() -> Unit = {},
    thenCheck: suspend context(TestScope) FetchMediaSelectorTestSuite.() -> Unit
): TestResult = runTest {
    FetchMediaSelectorTestSuite(
        this.coroutineContext[ContinuationInterceptor]!!,
        cachingEnabled = cachingEnabled,
        cachingScope = backgroundScope,
    ).apply { buildTest() }.thenCheck()
}

inline fun DynamicTestsBuilder.addSimpleMediaSelectorTest(
    name: String? = null,
    crossinline buildTest: SimpleMediaSelectorTestSuite.() -> Unit = {},
    crossinline thenCheck: suspend SimpleMediaSelectorTestSuite.() -> Unit,
) {
    val scheduler = TestCoroutineScheduler()
    val dispatcher = StandardTestDispatcher(scheduler)
    val scope = TestScope(dispatcher)
    val suite = SimpleMediaSelectorTestSuite(scope).apply(buildTest)

    add(name ?: suite.initApi.subjectName) {
        runBlocking {
            scope.runTest {
                thenCheck(suite)
            }
        }
    }
}

suspend inline fun SimpleMediaSelectorTestSuite.assertMedias(block: MaybeExcludedMediaAssertions.() -> Unit) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    selector.filteredCandidates.first().assert(block)
}
