package com.wynime.app.domain.media.selector.testFramework

import androidx.annotation.CheckResult
import com.wynime.app.domain.media.selector.MatchMetadata
import com.wynime.app.domain.media.selector.MaybeExcludedMedia
import com.wynime.app.domain.media.selector.MediaExclusionReason
import com.wynime.app.domain.media.selector.UnsafeOriginalMediaAccess
import com.wynime.app.domain.media.selector.testFramework.MaybeExcludedMediaAssertions.Assert
import com.wynime.app.domain.media.selector.testFramework.MaybeExcludedMediaAssertions.AssertNoMoreElements
import com.wynime.app.domain.media.selector.testFramework.MaybeExcludedMediaAssertions.Filter.FilterType
import com.wynime.datasources.api.Media
import com.wynime.datasources.api.source.MediaSourceKind
import com.wynime.datasources.api.topic.EpisodeRange
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmName
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

context(_: MediaSelectorTestSuite)
inline fun List<MaybeExcludedMedia>.assert(
    block: MaybeExcludedMediaAssertions.() -> Unit,
) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    MaybeExcludedMediaAssertions().apply(block).runOn(this)
}

context(_: MediaSelectorTestSuite)
@JvmName("assertListMedia")
inline fun List<Media>.assert(
    block: MaybeExcludedMediaAssertions.() -> Unit,
) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    MaybeExcludedMediaAssertions().apply(block).runOn(
        this.map {
            MaybeExcludedMedia.Included(
                it,
                metadata = MatchMetadata(
                    MatchMetadata.SubjectMatchKind.EXACT,
                    episodeMatchKind = MatchMetadata.EpisodeMatchKind.SORT,
                    similarity = 100,
                ),
            )
        },
    )
}

class MaybeExcludedMediaAssertions {
    private val rules = mutableListOf<Rule>()
    private val unusedTargets = mutableListOf<Target>()

    @CheckResult
    fun next() = Next(getCodeSource("filter")).also {
        unusedTargets.add(it)
    }

    @CheckResult
    fun single(): Filter {
        return Filter(
            getCodeSource("filter"),
            FilterType.SINGLE,
            failOnNoMatch = true,
            failIfMoreElement = true,
        ).also {
            unusedTargets.add(it)
        }
    }

    @CheckResult
    fun onSingle(
        sourceId: String? = null,
        mediaId: String? = null,
        episodeRange: EpisodeRange? = null,
    ): Filter {
        require(sourceId != null || mediaId != null || episodeRange != null)
        return Filter(
            getCodeSource("filter"),
            FilterType.SINGLE,
            failOnNoMatch = true,
            failIfMoreElement = false,
            sourceId,
            mediaId,
            episodeRange,
        ).also {
            unusedTargets.add(it)
        }
    }

    @CheckResult
    fun onOneOrMore(
        sourceId: String? = null,
        mediaId: String? = null,
        episodeRange: EpisodeRange? = null,
    ): Filter {
        require(sourceId != null || mediaId != null || episodeRange != null)
        return Filter(
            getCodeSource("filter"),
            FilterType.ALL,
            failOnNoMatch = true,
            failIfMoreElement = false,
            sourceId,
            mediaId,
            episodeRange,
        ).also {
            unusedTargets.add(it)
        }
    }

    @CheckResult
    fun onZeroOrMore(
        sourceId: String? = null,
        mediaId: String? = null,
        episodeRange: EpisodeRange? = null,
    ): Filter {
        require(sourceId != null || mediaId != null || episodeRange != null)
        return Filter(
            getCodeSource("filter"),
            FilterType.ALL,
            failOnNoMatch = false,
            failIfMoreElement = false,
            sourceId,
            mediaId,
            episodeRange,
        ).also {
            unusedTargets.add(it)
        }
    }

    @CheckResult
    fun onAll(): All = All(getCodeSource("filter")).also {
        unusedTargets.add(it)
    }

    fun assertNoMoreElements() {
        rules.add(AssertNoMoreElements(getCodeSource("assertion")))
    }

    fun Target.assert(
        mediaId: String? = null,
        included: Boolean? = null,
        sourceId: String? = null,
        source: Handle? = null,
        kind: MediaSourceKind? = null,
        exclusionReason: MediaExclusionReason? = null,
    ): Target {
        removeUnusedTarget(this)
        check(sourceId == null || source == null) {
            "sourceId and source cannot be set at the same time."
        }

        addCheck(
            this,
            CheckProperties(
                getCodeSource("assertion"),
                mediaId = mediaId,
                included = included,
                sourceId = sourceId ?: source?.instance?.mediaSourceId,
                kind = kind,
                exclusionReason = exclusionReason,
            ),
        )
        return this
    }

    private fun removeUnusedTarget(target: Target) {
        unusedTargets.remove(target)
    }

    fun runOn(list: List<MaybeExcludedMedia>) {
        unusedTargets.forEach { target ->
            target.codeSource.runWithSourceInfo {
                throw IllegalStateException("Detected unused target: $target. You must call .assert or other assertions on this target.")
            }
        }
        MaybeExcludedMediaAssertionsExecutor(list, rules).execute()
    }

    sealed class Target {
        abstract val codeSource: CodeSource
        abstract override fun toString(): String
    }

    data class Next(
        override val codeSource: CodeSource,
    ) : Target()

    data class Filter(
        override val codeSource: CodeSource,
        val type: FilterType,
        val failOnNoMatch: Boolean,
        val failIfMoreElement: Boolean,
        val sourceId: String? = null,
        val mediaId: String? = null,
        val episodeRange: EpisodeRange? = null,
    ) : Target() {
        enum class FilterType {
            ALL,
            SINGLE
        }
    }

    data class All(
        override val codeSource: CodeSource,
    ) : Target()

    sealed class Rule()

    data class Assert(
        val target: Target,
        val checks: MutableList<Check> = mutableListOf(),
    ) : Rule()

    data class AssertNoMoreElements(
        val source: CodeSource,
    ) : Rule()

    sealed class Check(val source: CodeSource)

    class CheckProperties(
        source: CodeSource,
        @Suppress("unused")
        private val _usePositionalArguments: Nothing? = null,
        val mediaId: String? = null,
        val included: Boolean? = null,
        val sourceId: String? = null,
        val kind: MediaSourceKind? = null,
        val exclusionReason: MediaExclusionReason? = null,
    ) : Check(source)

    private fun addCheck(target: Target, check: Check) {
        for (rule in rules.asReversed()) {
            when (rule) {
                is Assert -> {
                    if (rule.target == target) {
                        rule.checks.add(check)
                        return
                    }
                    continue
                }

                is AssertNoMoreElements -> continue
            }
        }

        rules.add(Assert(target, mutableListOf(check)))
    }
}

class CodeSource(
    val exception: Exception,
)

inline fun <R> CodeSource.runWithSourceInfo(
    block: () -> R,
): R {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return try {
        block()
    } catch (e: Throwable) {
        e.addSuppressed(this.exception)
        throw e
    }
}

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@kotlin.internal.InlineOnly
inline fun getCodeSource(name: String): CodeSource = CodeSource(Exception("Source code for $name"))

private class MaybeExcludedMediaAssertionsExecutor(
    private val list: List<MaybeExcludedMedia>,
    private val rules: List<MaybeExcludedMediaAssertions.Rule>,
) {
    private var index = -1

    fun execute() {
        for (rule in rules) {
            when (rule) {
                is Assert -> {
                    val medias = rule.target.resolveOrFail()
                    for (media in medias) {
                        for (check in rule.checks) {
                            check.runOnOrFail(media)
                        }
                    }
                }

                is AssertNoMoreElements -> {
                    rule.source.runWithSourceInfo {
                        assertEquals(
                            index, list.lastIndex,
                            message = "Expected no more elements, but found ${list.lastIndex - index} more elements. Current index = $index, list size = ${list.size}",
                        )
                    }
                }
            }
        }
    }

    @OptIn(UnsafeOriginalMediaAccess::class)
    private fun MaybeExcludedMediaAssertions.Target.resolveOrFail(): List<MaybeExcludedMedia> {
        this.codeSource.runWithSourceInfo {
            when (this) {
                is MaybeExcludedMediaAssertions.All -> {
                    return (if (index == -1) {
                        list
                    } else {
                        list.drop(index)
                    }).also {
                        index = list.lastIndex
                    }
                }

                is MaybeExcludedMediaAssertions.Next -> {
                    return listOf(atIndexOrFail(++index))
                }

                is MaybeExcludedMediaAssertions.Filter -> {
                    val matched = list.filter { media ->
                        media.original.matches(this)
                    }

                    if (this.failOnNoMatch) {
                        assertNotEquals(
                            0, matched.size,
                            "Found empty match for filter: $this",
                        )
                    }

                    if (this.failIfMoreElement) {
                        assertEquals(
                            matched.size, list.size,
                            "Found excess elements in the less for filter: $this",
                        )
                    }

                    when (this.type) {
                        FilterType.ALL -> {}
                        FilterType.SINGLE -> {
                            if (matched.size > 1) {
                                assertEquals(
                                    1, matched.size,
                                    "Expected one exact match but found ${matched.size}, for filter: $this",
                                )
                            }
                        }
                    }

                    return matched
                }
            }
        }
    }

    private fun Media.matches(filter: MaybeExcludedMediaAssertions.Filter): Boolean {
        filter.mediaId?.let {
            if (this.mediaId != it) {
                return false
            }
        }

        filter.sourceId?.let {
            if (mediaSourceId != it) {
                return false
            }
        }

        filter.episodeRange?.let {
            if (episodeRange != it) {
                return false
            }
        }

        return true
    }

    private fun atIndexOrFail(index: Int): MaybeExcludedMedia {
        return list.getOrNull(index)
            ?: throw AssertionError("Expected one more media at index $index, but found none. List size = ${list.size}")
    }

    @OptIn(UnsafeOriginalMediaAccess::class)
    private fun MaybeExcludedMediaAssertions.Check.runOnOrFail(media: MaybeExcludedMedia) {
        this.source.runWithSourceInfo {
            when (this) {
                is MaybeExcludedMediaAssertions.CheckProperties -> {
                    this.included?.let {
                        assertEquals(it, media is MaybeExcludedMedia.Included)
                    }

                    this.mediaId?.let {
                        assertEquals(it, media.original.mediaId)
                    }

                    this.sourceId?.let {
                        assertEquals(it, media.original.mediaSourceId)
                    }

                    this.kind?.let {
                        assertEquals(it, media.original.kind)
                    }

                    this.exclusionReason?.let {
                        assertEquals(it, media.exclusionReason)
                    }
                }
            }
        }
    }
}
