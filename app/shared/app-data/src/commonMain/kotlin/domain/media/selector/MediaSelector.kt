package com.wynime.app.domain.media.selector

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import com.wynime.app.data.models.preference.MediaPreference
import com.wynime.app.data.models.preference.MediaPreference.Companion.ANY_FILTER
import com.wynime.app.data.models.preference.MediaSelectorSettings
import com.wynime.app.domain.media.DroppedFileMedia
import com.wynime.app.domain.media.selector.filter.MediaSelectorFilterSortAlgorithm
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.isLocalCache
import com.wynime.datasources.api.source.MediaSourceKind
import kotlin.coroutines.CoroutineContext

interface MediaSelector {

    val filteredCandidates: Flow<List<MaybeExcludedMedia>>

    val filteredCandidatesMedia: Flow<List<Media>>

    val subjectCandidates: Flow<List<MaybeExcludedMedia>>

    val alliance: MediaPreferenceItem<String>

    val resolution: MediaPreferenceItem<String>

    val subtitleLanguageId: MediaPreferenceItem<String>

    val mediaSourceId: MediaPreferenceItem<String>

    val preferredCandidates: Flow<List<MaybeExcludedMedia>>

    val preferredCandidatesMedia: Flow<List<Media>>

    val selected: StateFlow<Media?>

    val events: MediaSelectorEvents

    fun autoSelectSnapshots(sources: Flow<List<MediaSourceSelectionSnapshot>>): Flow<MediaAutoSelectSnapshot>

    suspend fun selectAutomatically(candidate: Media, expectedSelection: Media?): Media?

    suspend fun select(candidate: Media): Boolean

    suspend fun selectTemporarily(candidate: Media): Boolean

    fun unselect()

    suspend fun trySelectDefault(): Media?

    suspend fun trySelectFromMediaSources(
        candidateSources: List<String>,
        overrideUserSelection: Boolean = false,
        blacklistMediaIds: Set<String> = emptySet(),
        allowNonPreferred: Boolean = false,
        candidateMediaFilter: ((Media) -> Boolean)? = null,
    ): Media?

    suspend fun awaitSelectFromMediaSources(
        candidateSources: List<String>,
        overrideUserSelection: Boolean = false,
        blacklistMediaIds: Set<String> = emptySet(),
        allowNonPreferred: Boolean = false,
        candidateMediaFilter: ((Media) -> Boolean)? = null,
    ): Media?

    suspend fun trySelectCached(): Media?

    suspend fun removePreferencesUntilFirstCandidate()
}

interface MediaPreferenceItem<T : Any> {

    val available: Flow<List<T>>

    val userSelected: Flow<OptionalPreference<T>>

    val defaultSelected: Flow<T?>

    val finalSelected: Flow<T?>

    suspend fun prefer(value: T)

    suspend fun removePreference()
}

class DefaultMediaSelector(
    mediaSelectorContextNotCached: Flow<MediaSelectorContext>,
    mediaListNotCached: Flow<List<Media>>,

    savedUserPreference: Flow<MediaPreference>,

    private val savedDefaultPreference: Flow<MediaPreference>,
    mediaSelectorSettings: Flow<MediaSelectorSettings>,

    private val flowCoroutineContext: CoroutineContext = Dispatchers.Default,

    private val enableCaching: Boolean = true,
    private val algorithm: MediaSelectorFilterSortAlgorithm = MediaSelectorFilterSortAlgorithm(),

    private val cachingScope: CoroutineScope? = null,
) : MediaSelector {
    private fun <T> Flow<T>.cached(): Flow<T> {
        if (!enableCaching) return this

        return this.shareIn(
            cachingScope ?: CoroutineScope(flowCoroutineContext),
            SharingStarted.WhileSubscribed(5_000),
            replay = 1,
        )
    }

    private val mediaSelectorSettings = mediaSelectorSettings.cached()
    private val mediaSelectorContext = mediaSelectorContextNotCached.cached()

    private val mediaList = mediaListNotCached.cached()

    @OptIn(UnsafeOriginalMediaAccess::class)
    override val filteredCandidates: Flow<List<MaybeExcludedMedia>> = combine(
        mediaList,
        savedDefaultPreference,

        this.mediaSelectorSettings,
        this.mediaSelectorContext,
    ) { list, pref, settings, context ->
        algorithm.filterMediaList(list, pref, settings, context)
            .let { algorithm.sortMediaList(it, settings, context) }
    }.cached()

    override val subjectCandidates: Flow<List<MaybeExcludedMedia>> = combine(
        mediaList,
        savedDefaultPreference,
        this.mediaSelectorSettings,
        this.mediaSelectorContext,
    ) { list, pref, settings, context ->
        algorithm.filterMediaListForSubject(list, pref, settings, context)
            .let { algorithm.sortMediaList(it, settings, context) }
    }.cached()

    override val filteredCandidatesMedia: Flow<List<Media>> = filteredCandidates.map { list ->
        list.mapNotNull { it.result }
    }.flowOn(flowCoroutineContext)

    private val savedUserPreferenceNotCached = savedUserPreference
    private val savedUserPreference: Flow<MediaPreference> = savedUserPreference.cached()

    override val alliance = mediaPreferenceItem(
        "alliance",
        getFromMediaList = { list ->
            list.mapTo(HashSet(list.size)) { it.properties.alliance }
                .sortedBy { it }
        },
        getFromPreference = { it.alliance },
    )
    override val resolution = mediaPreferenceItem(
        "resolution",
        getFromMediaList = { list ->
            list.mapTo(HashSet(list.size)) { it.properties.resolution }
                .sortedBy { it }
        },
        getFromPreference = { it.resolution },
    )
    override val subtitleLanguageId = mediaPreferenceItem(
        "subtitleLanguage",
        getFromMediaList = { list ->
            list.flatMapTo(HashSet(list.size)) { it.properties.subtitleLanguageIds }
                .sortedByDescending {
                    when (it.uppercase()) {
                        "8K", "4320P" -> 6
                        "4K", "2160P" -> 5
                        "2K", "1440P" -> 4
                        "1080P" -> 3
                        "720P" -> 2
                        "480P" -> 1
                        "360P" -> 0
                        else -> -1
                    }
                }
        },
        getFromPreference = { it.subtitleLanguageId },
    )
    override val mediaSourceId = mediaPreferenceItem(
        "mediaSource",
        getFromMediaList = { list ->
            list.mapTo(HashSet(list.size)) { it.properties.resolution }
                .sortedBy { it }
        },
        getFromPreference = { it.mediaSourceId },
    )

    private val newPreferences = combine(
        savedDefaultPreference,
        alliance.finalSelected,
        resolution.finalSelected,
        subtitleLanguageId.finalSelected,
        mediaSourceId.finalSelected,
    ) { default, alliance, resolution, subtitleLanguage, mediaSourceId ->
        default.copy(
            alliance = alliance,
            resolution = resolution,
            subtitleLanguageId = subtitleLanguage,
            mediaSourceId = mediaSourceId,
        )
    }.flowOn(flowCoroutineContext)

    private val preferredCandidatesNotCached =
        combine(this.filteredCandidates, newPreferences) { mediaList, mergedPreferences ->
            algorithm.filterByPreference(mediaList, mergedPreferences)
        }

    override val preferredCandidates: Flow<List<MaybeExcludedMedia>> = preferredCandidatesNotCached.cached()
    override val preferredCandidatesMedia: Flow<List<Media>> =
        preferredCandidates.map { list -> list.mapNotNull { it.result } }

    override val selected: MutableStateFlow<Media?> = MutableStateFlow(null)
    override val events = MutableMediaSelectorEvents()

    override fun autoSelectSnapshots(sources: Flow<List<MediaSourceSelectionSnapshot>>): Flow<MediaAutoSelectSnapshot> =
        combine(sources, savedDefaultPreference, newPreferences, mediaSelectorSettings, mediaSelectorContext) {
                sourceSnapshots, default, preference, settings, context ->
            val media = sourceSnapshots.flatMap { it.results }.distinctBy { it.mediaId }
            val candidates = algorithm.sortMediaList(
                algorithm.filterMediaList(media, default, settings, context), settings, context,
            ).filterIsInstance<MaybeExcludedMedia.Included>()
            MediaAutoSelectSnapshot(
                sourceSnapshots, candidates,
                algorithm.filterByPreference(candidates, preference).filterIsInstance<MaybeExcludedMedia.Included>(),
                preference, settings, context,
            )
        }

    override suspend fun selectAutomatically(candidate: Media, expectedSelection: Media?): Media? {
        if (selected.value != expectedSelection || candidate == expectedSelection) return null
        val event = SelectEvent(candidate, subtitleLanguageId = null, previousMedia = expectedSelection)
        events.onBeforeSelect.emit(event)
        if (!selected.compareAndSet(expectedSelection, candidate)) return null
        events.onSelect.emit(event)
        return candidate
    }

    override suspend fun select(candidate: Media): Boolean {
        return selectImpl(candidate, updatePreference = true)
    }

    override suspend fun selectTemporarily(candidate: Media): Boolean {
        return selectImpl(candidate, updatePreference = false)
    }

    private suspend fun selectImpl(
        candidate: Media,
        updatePreference: Boolean,
        force: Boolean = false
    ): Boolean {
        val previous = selected.value

        if (!force && previous == candidate) return false

        events.onBeforeSelect.emit(
            SelectEvent(
                media = candidate,
                subtitleLanguageId = null,
                previousMedia = previous,
            ),
        )

        selected.value = candidate

        if (updatePreference) {
            alliance.preferWithoutBroadcast(candidate.properties.alliance)
            resolution.preferWithoutBroadcast(candidate.properties.resolution)
            mediaSourceId.preferWithoutBroadcast(candidate.mediaSourceId)
            candidate.properties.subtitleLanguageIds.singleOrNull()?.let {
                subtitleLanguageId.preferWithoutBroadcast(it)
            }

            broadcastChangePreference()
            if (candidate.kind == MediaSourceKind.WEB) {
                broadcastWebSourcePreference(candidate.mediaSourceId)
            }
        }

        events.onSelect.emit(
            SelectEvent(
                media = candidate,
                subtitleLanguageId = null,
                previousMedia = previous,
            ),
        )

        return true
    }

    override fun unselect() {
        selected.value = null
    }

    private suspend fun selectDefault(candidate: Media): Media? = selectAutomatically(candidate, expectedSelection = null)

    private suspend fun broadcastChangePreference(overrideLanguageId: String? = null) {
        if (events.onChangePreference.subscriptionCount.value == 0) return
        val savedUserPreference = savedUserPreferenceNotCached.first()
        val preference = newPreferences.first()
        events.onChangePreference.emit(
            savedUserPreference.copy(
                alliance = preference.alliance,
                resolution = preference.resolution,
                subtitleLanguageId = overrideLanguageId ?: preference.subtitleLanguageId,
                mediaSourceId = preference.mediaSourceId,
            ),
        )
    }

    private suspend fun broadcastWebSourcePreference(mediaSourceId: String) {
        events.onPreferWebSource.emit(
            PreferWebSourceEvent(
                mediaSelectorContext.first().subjectInfo?.subjectId ?: return,
                mediaSourceId,
            ),
        )
    }

    private suspend fun findUsingPreferenceFromCandidates(
        candidates: List<MaybeExcludedMedia.Included>,
        mergedPreference: MediaPreference,
    ): Media? = MediaSelectionDecider.findByPreference(
        candidates,
        mergedPreference,
        alliance.available.first(),
        mediaSelectorContext.first { it.allFieldsLoaded() },
        mediaSelectorSettings.first(),
    )

    override suspend fun trySelectDefault(): Media? {
        if (selected.value != null) return null
        val candidates = preferredCandidates.first()
        if (candidates.none { it is MaybeExcludedMedia.Included }) return null
        val mergedPreference = newPreferences.first()
        return findUsingPreferenceFromCandidates(
            candidates.filterIsInstance<MaybeExcludedMedia.Included>(),
            mergedPreference,
        )?.let {
            selectDefault(it)
        }
    }

    override suspend fun trySelectFromMediaSources(
        candidateSources: List<String>,
        overrideUserSelection: Boolean,
        blacklistMediaIds: Set<String>,
        allowNonPreferred: Boolean,
        candidateMediaFilter: ((Media) -> Boolean)?
    ): Media? {
        if (candidateSources.isEmpty()) return null

        fun bake(candidates: List<MaybeExcludedMedia.Included>): List<MaybeExcludedMedia.Included> {
            return candidates.filter {
                it.result.mediaSourceId in candidateSources && it.result.mediaId !in blacklistMediaIds
                        && (candidateMediaFilter == null || candidateMediaFilter(it.result))
            }
                .sortedBy { candidateSources.indexOf(it.result.mediaSourceId) }
        }

        val selected = run {
            val mergedPreference = newPreferences.first()

            findUsingPreferenceFromCandidates(
                bake(preferredCandidates.first().filterIsInstance<MaybeExcludedMedia.Included>()),
                mergedPreference.copy(alliance = ANY_FILTER),
            )?.let { return@run it }

            if (allowNonPreferred) {

                findUsingPreferenceFromCandidates(
                    bake(filteredCandidates.first().filterIsInstance<MaybeExcludedMedia.Included>()),
                    mergedPreference.copy(
                        alliance = ANY_FILTER,
                        resolution = ANY_FILTER,
                        subtitleLanguageId = ANY_FILTER,
                        mediaSourceId = ANY_FILTER,
                    ),
                )?.let { return@run it }
            }
            null
        }

        return selected?.let {
            if (overrideUserSelection) {
                if (selectImpl(it, updatePreference = false)) {
                    it
                } else {
                    null
                }
            } else {
                selectDefault(it)
            }
        }
    }

    override suspend fun awaitSelectFromMediaSources(
        candidateSources: List<String>,
        overrideUserSelection: Boolean,
        blacklistMediaIds: Set<String>,
        allowNonPreferred: Boolean,
        candidateMediaFilter: ((Media) -> Boolean)?
    ): Media? {
        if (candidateSources.isEmpty()) return null

        fun bake(candidates: List<MaybeExcludedMedia.Included>): List<MaybeExcludedMedia.Included> {
            return candidates.filter {
                it.result.mediaSourceId in candidateSources && it.result.mediaId !in blacklistMediaIds
                        && (candidateMediaFilter == null || candidateMediaFilter(it.result))
            }
                .sortedBy { candidateSources.indexOf(it.result.mediaSourceId) }
        }

        val selected = combine(preferredCandidates, filteredCandidates) { preferred, candidates ->
            val preferredSelected = findUsingPreferenceFromCandidates(
                bake(preferred.filterIsInstance<MaybeExcludedMedia.Included>()),
                newPreferences.first().copy(alliance = ANY_FILTER),
            )
            if (preferredSelected != null) return@combine preferredSelected
            if (!allowNonPreferred) return@combine null

            val filteredSelected = findUsingPreferenceFromCandidates(
                bake(candidates.filterIsInstance<MaybeExcludedMedia.Included>()),
                newPreferences.first().copy(
                    alliance = ANY_FILTER,
                    resolution = ANY_FILTER,
                    subtitleLanguageId = ANY_FILTER,
                    mediaSourceId = ANY_FILTER,
                ),
            )
            return@combine filteredSelected
        }
            .filterNotNull()
            .first()

        return if (overrideUserSelection) {
            if (selectImpl(selected, updatePreference = false)) {
                selected
            } else {
                null
            }
        } else {
            selectDefault(selected)
        }
    }

    override suspend fun trySelectCached(): Media? {
        if (selected.value != null) return null

        if (!mediaSelectorContext.first().hasEpisode) return null

        fun List<MaybeExcludedMedia>.firstCachedOrNull(): Media? =
            firstNotNullOfOrNull { candidate -> candidate.result?.takeIf { it.isLocalCache() } }

        val cached = preferredCandidates.first().firstCachedOrNull()
            ?: filteredCandidates.first().firstCachedOrNull() ?: return null
        return selectDefault(cached)
    }

    override suspend fun removePreferencesUntilFirstCandidate() {
        if (preferredCandidatesMedia.first().isNotEmpty()) return
        alliance.removePreference()
        if (preferredCandidatesNotCached.first().isNotEmpty()) return
        resolution.removePreference()
        if (preferredCandidatesNotCached.first().isNotEmpty()) return
        subtitleLanguageId.removePreference()
        if (preferredCandidatesNotCached.first().isNotEmpty()) return
        mediaSourceId.removePreference()
    }

    interface MediaPreferenceItemImpl<T : Any> : MediaPreferenceItem<T> {
        fun preferWithoutBroadcast(value: T)
    }

    private inline fun <reified T : Any> mediaPreferenceItem(
        debugName: String,
        crossinline getFromMediaList: (list: List<Media>) -> List<T>,
        crossinline getFromPreference: (MediaPreference) -> T?,
    ) = object : MediaPreferenceItemImpl<T> {
        override val available: Flow<List<T>> = filteredCandidatesMedia.map { list ->
            getFromMediaList(list)
        }.flowOn(flowCoroutineContext).cached()

        private val overridePreference: MutableStateFlow<OptionalPreference<T>> =
            MutableStateFlow(OptionalPreference.noPreference())

        override val userSelected: Flow<OptionalPreference<T>> =
            combine(savedUserPreference, overridePreference) { preference, override ->
                override.flatMapNoPreference {
                    OptionalPreference.preferIfNotNull(getFromPreference(preference))
                }
            }.flowOn(flowCoroutineContext)

        override val defaultSelected: Flow<T?> = savedDefaultPreference.map { getFromPreference(it) }
            .flowOn(flowCoroutineContext).cached()

        override val finalSelected: Flow<T?> = combine(userSelected, defaultSelected) { user, default ->
            user.orElse { default }
        }.flowOn(flowCoroutineContext)

        override suspend fun removePreference() {
            withContext(flowCoroutineContext) {
                overridePreference.value = OptionalPreference.preferNoValue()
                broadcastChangePreference(null)
            }
        }

        override fun preferWithoutBroadcast(value: T) {
            overridePreference.value = OptionalPreference.prefer(value)
        }

        override suspend fun prefer(value: T) {
            withContext(flowCoroutineContext) {
                preferWithoutBroadcast(value)
                broadcastChangePreference(null)
            }
        }

        override fun toString(): String = "MediaPreferenceItem($debugName)"
    }
}
