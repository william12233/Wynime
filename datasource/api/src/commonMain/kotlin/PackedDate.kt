@file:Suppress("NOTHING_TO_INLINE", "KotlinRedundantDiagnosticSuppress", "unused")

package com.wynime.datasources.api

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import com.wynime.datasources.api.PackedDate.Companion.Invalid
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmField
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@JvmInline
@Serializable
value class PackedDate(

    @JvmField val packed: Int
) : Comparable<PackedDate> {
    inline val isValid: Boolean get() = packed != Int.MAX_VALUE
    inline val isInvalid: Boolean get() = packed == Int.MAX_VALUE

    inline val year: Int get() = if (isValid) DatePacker.unpack1(packed) else 0

    inline val month: Int get() = if (isValid) DatePacker.unpack2(packed) else 0

    inline val day: Int get() = if (isValid) DatePacker.unpack3(packed) else 0

    companion object {

        @JvmStatic
        val Invalid = PackedDate(Int.MAX_VALUE)

        fun parseFromDate(date: String): PackedDate {
            val split = date.split("-")
            if (split.size < 3) return Invalid
            return PackedDate(
                split[0].toIntOrNull() ?: return Invalid,
                split[1].toIntOrNull() ?: return Invalid,
                split[2].toIntOrNull() ?: return Invalid,
            )
        }

        private val UTC8 = TimeZone.of("UTC+8")

        fun now(): PackedDate {
            val timeZone = UTC8
            val calendar = Clock.System.now().toLocalDateTime(timeZone)

            val year = calendar.year
            val month = calendar.month.number
            val day = calendar.day
            return PackedDate(year, month, day)
        }
    }

    override fun compareTo(other: PackedDate): Int = packed.compareTo(other.packed)

    override fun toString(): String {
        return if (isInvalid) "Invalid" else "$year-$month-$day"
    }
}

val UTC9 = TimeZone.of("UTC+9")

fun PackedDate.toStringExcludingSameYear(): String = when {
    isInvalid -> toString()
    year == PackedDate.now().year -> "$month-$day"
    else -> toString()
}

fun PackedDate.toLocalDateOrNull(): LocalDate? {
    if (isInvalid) return null
    return try {
        LocalDate(year, month, day)
    } catch (e: IllegalArgumentException) {
        null
    }
}

fun PackedDate?.isNullOrInvalid(): Boolean = this == null || this.isInvalid

inline fun PackedDate.ifInvalid(block: () -> PackedDate): PackedDate {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
    return if (isInvalid) block() else this
}

inline val PackedDate.seasonMonth: Int
    get() = when (month) {
        12, in 1..2 -> 1
        in 3..5 -> 4
        in 6..8 -> 7
        in 9..11 -> 10
        else -> 0
    }

operator fun PackedDate.minus(other: PackedDate): Duration {
    if (this.isInvalid || other.isInvalid) return Duration.INFINITE

    val thisDate = LocalDate(this.year, this.month, this.day)
    val otherDate = LocalDate(other.year, other.month, other.day)

    val thisInstant = thisDate.atStartOfDayIn(TimeZone.UTC)
    val otherInstant = otherDate.atStartOfDayIn(TimeZone.UTC)

    return (thisInstant.toEpochMilliseconds() - otherInstant.toEpochMilliseconds()).milliseconds
}

inline fun PackedDate(
    year: Int,
    month: Int,
    day: Int,
): PackedDate = if (year in 0..9999 && month in 1..12 && day in 1..31) {
    PackedDate(DatePacker.pack(year, month, day))
} else {
    Invalid
}

@Suppress("NOTHING_TO_INLINE")
@PublishedApi
internal object DatePacker {
    inline fun pack(
        val1: Int,
        val2: Int,
        val3: Int,
    ): Int {
        return val1.shl(16) or val2.shl(8) or val3
    }

    inline fun unpack1(value: Int): Int = value.shr(16).and(0xFFFF)
    inline fun unpack2(value: Int): Int = value.shr(8).and(0xFF)
    inline fun unpack3(value: Int): Int = value.and(0xFF)
}
