package com.wynime.datasources.api.topic

import kotlinx.serialization.SerialName

import kotlinx.serialization.Serializable
import com.wynime.datasources.api.EpisodeSort
import com.wynime.datasources.api.topic.EpisodeRange.Combined
import com.wynime.datasources.api.topic.EpisodeRange.Companion.combined
import com.wynime.datasources.api.topic.EpisodeRange.Companion.unknownSeason
import com.wynime.datasources.api.topic.EpisodeRange.Range
import com.wynime.datasources.api.topic.EpisodeRange.Season
import com.wynime.datasources.api.topic.EpisodeRange.Single
import kotlin.jvm.JvmName

@Serializable
sealed class EpisodeRange {

    open val isKnown: Boolean get() = true

    abstract val knownSorts: Sequence<EpisodeSort>

    abstract fun isEmpty(): Boolean

    @SerialName("me.him188.ani.datasources.api.topic.EpisodeRange.Empty")
    @Serializable
    internal data object Empty : EpisodeRange() {
        override val knownSorts: Sequence<EpisodeSort>
            get() = emptySequence()

        override fun isEmpty(): Boolean = true

        override fun toString(): String = "EpisodeRange(empty)"
    }

    @SerialName("me.him188.ani.datasources.api.topic.EpisodeRange.Single")
    @Serializable
    internal class Single(
        val value: EpisodeSort,
    ) : EpisodeRange() {
        override val knownSorts: Sequence<EpisodeSort>
            get() = sequenceOf(value)

        override fun isEmpty(): Boolean = false

        override fun toString(): String = "$value..$value"

        override fun hashCode(): Int = value.hashCode()
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other is Range && other.start == other.end) {
                return value == other.start
            }
            if (other !is Single) return false
            other
            return value == other.value
        }
    }

    @SerialName("me.him188.ani.datasources.api.topic.EpisodeRange.Range")
    @Serializable
    internal class Range(
        val start: EpisodeSort,
        val end: EpisodeSort,
    ) : EpisodeRange() {
        override val knownSorts: Sequence<EpisodeSort>
            get() = sequence {
                if (!(start is EpisodeSort.Normal && end is EpisodeSort.Normal)) {
                    yield(start)
                    yield(end)
                    return@sequence
                }
                var curr = start.number
                if (start.isPartial) {
                    yield(start)
                    curr += 0.5f
                }
                while (curr < end.number) {
                    yield(EpisodeSort.Normal(curr))
                    curr += 1f
                }
                yield(EpisodeSort.Normal(end.number))
            }

        override fun isEmpty(): Boolean {
            if (start is EpisodeSort.Normal && end is EpisodeSort.Normal) {
                return end < start
            }
            return false
        }

        override fun toString(): String = "$start..$end"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other is Single && other.value == start && other.value == end) return true
            if (other !is Range) return false
            other
            return start == other.start && end == other.end
        }

        override fun hashCode(): Int {
            var result = start.hashCode()
            result = 31 * result + end.hashCode()
            return result
        }
    }

    @SerialName("me.him188.ani.datasources.api.topic.EpisodeRange.Combined")
    @Serializable
    class Combined internal constructor(
        val first: EpisodeRange,
        val second: EpisodeRange,
    ) : EpisodeRange() {
        override val knownSorts: Sequence<EpisodeSort>
            get() = sequence {
                yieldAll(first.knownSorts)
                yieldAll(second.knownSorts)
            }

        override fun isEmpty(): Boolean = first.isEmpty() && second.isEmpty()

        override fun toString(): String = buildString {
            if (first is Single) append(first.value) else append(first)
            append('+')
            if (second is Single) append(second.value) else append(second)
        }

        private fun flatten(): Sequence<EpisodeRange> {
            return sequence {
                if (first is Combined) {
                    yieldAll(first.flatten())
                } else {
                    yield(first)
                }
                if (second is Combined) {
                    yieldAll(second.flatten())
                } else {
                    yield(second)
                }
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EpisodeRange) return false
            return when (other) {
                is Combined -> {
                    flatten().sequenceEquals(other.flatten())
                }

                Empty -> false
                is Range -> knownSorts.sequenceEquals(other.knownSorts)
                is Season -> first == second && first == other
                is Single -> first == other && second == other
            }
        }

        override fun hashCode(): Int {
            var result = first.hashCode()
            result = 31 * result + second.hashCode()
            return result
        }
    }

    @SerialName("me.him188.ani.datasources.api.topic.EpisodeRange.Season")
    @Serializable
    data class Season(

        val rawNumber: Int,
    ) : EpisodeRange() {
        val numberOrZero: Int get() = if (rawNumber == -1) 0 else rawNumber
        val numberOrNull: Int? get() = if (rawNumber == -1) null else rawNumber

        override val knownSorts: Sequence<EpisodeSort> get() = emptySequence()
        override fun isEmpty(): Boolean = false
        override val isKnown: Boolean get() = false
        override fun toString(): String = if (rawNumber != -1) "S$rawNumber" else "S?"
    }

    companion object {
        fun empty(): EpisodeRange = Empty

        fun single(raw: EpisodeSort): EpisodeRange = Single(raw)

        fun single(raw: String): EpisodeRange = Single(EpisodeSort(raw))

        fun range(start: EpisodeSort, end: EpisodeSort): EpisodeRange = Range(start, end)

        fun range(start: String, end: String) = range(EpisodeSort(start), EpisodeSort(end))

        fun range(start: Int, end: Int) = range(EpisodeSort(start), EpisodeSort(end))

        fun combined(first: EpisodeRange, second: EpisodeRange): EpisodeRange {

            if (first is Empty && second is Empty) return Empty
            if (first is Empty) return second
            if (second is Empty) return first
            if (first == second) return first
            return Combined(first, second)
        }

        fun range(episodes: Iterable<EpisodeSort>): EpisodeRange =
            combined(episodes.map { single(it) })

        fun combined(list: Iterable<EpisodeRange>): EpisodeRange =
            list.reduceOrNull { acc, episodeRange -> combined(acc, episodeRange) }
                ?: Empty

        fun season(number: Int): Season = Season(number)

        @JvmName("seasonNullable")
        fun season(number: Int?): Season = Season(number ?: -1)

        fun unknownSeason(): Season = Season(-1)
    }
}

operator fun EpisodeRange.plus(other: EpisodeRange): EpisodeRange = EpisodeRange.combined(this, other)

operator fun EpisodeRange.contains(expected: EpisodeSort): Boolean = contains(expected, allowSeason = true)

fun EpisodeRange.contains(
    expected: EpisodeSort,
    allowSeason: Boolean = true,
    allowSpecial: Boolean = true,
): Boolean {
    if (allowSeason && this is Season) return true
    if (knownSorts.any { it == expected }) {

        return true
    }
    if (allowSpecial && expected is EpisodeSort.Special) {
        val expectedNumber = expected.number
        return knownSorts.any {
            val number = it.number
            number != null && number == expectedNumber
        }
    }
    return false
}

fun EpisodeRange.isSingleEpisode(): Boolean {
    return when (this) {
        is Single -> true
        is Range -> start == end
        is Combined -> first == second
        else -> false
    }
}

fun EpisodeRange.hasSeason(): Boolean = when (this) {
    is Season -> true
    is Combined -> first.hasSeason() || second.hasSeason()
    else -> false
}

fun EpisodeRange?.orEmpty(): EpisodeRange = this ?: EpisodeRange.empty()

@Serializable
data class Alliance(
    val id: String,
    val name: String,
)

private fun <T> Sequence<T>.sequenceEquals(other: Sequence<T>): Boolean {
    val iterator = iterator()
    val otherIterator = other.iterator()
    while (iterator.hasNext() && otherIterator.hasNext()) {
        if (iterator.next() != otherIterator.next()) return false
    }
    return iterator.hasNext() == otherIterator.hasNext()
}