package com.wynime.datasources.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import com.wynime.datasources.api.EpisodeSort.Normal
import com.wynime.datasources.api.EpisodeSort.Special
import com.wynime.datasources.api.EpisodeType.ED
import com.wynime.datasources.api.EpisodeType.MAD
import com.wynime.datasources.api.EpisodeType.MainStory
import com.wynime.datasources.api.EpisodeType.OAD
import com.wynime.datasources.api.EpisodeType.OP
import com.wynime.datasources.api.EpisodeType.OVA
import com.wynime.datasources.api.EpisodeType.PV
import com.wynime.datasources.api.EpisodeType.SP
import com.wynime.datasources.api.topic.EpisodeRange
import com.wynime.utils.serialization.BigNum

@Serializable
sealed class EpisodeSort : Comparable<EpisodeSort> {

    abstract val number: Float?

    internal abstract val raw: String

    abstract override fun toString(): String

    protected fun getNumberStr(number: Float?): String {
        if (number == null) {
            return ""
        }
        if (number.toInt().toFloat() == number) {
            if (number in 0.0..<10.0) {
                return "0${number.toInt()}"
            }
            return number.toInt().toString()
        }
        return number.toString()
    }

    @SerialName("me.him188.ani.datasources.api.EpisodeSort.Normal")
    @Serializable
    class Normal internal constructor(
        @ProtoNumber(1) override val number: Float,
    ) : EpisodeSort() {
        override val raw: String
            get() {
                if (number.toInt().toFloat() == number) return number.toInt().toString()
                return number.toString()
            }

        val isPartial: Boolean get() = number % 1f == 0.5f

        override fun toString(): String = getNumberStr(number)
    }

    @SerialName("me.him188.ani.datasources.api.EpisodeSort.Special")
    @Serializable
    class Special internal constructor(
        @ProtoNumber(1) @SerialName("episodeType") val type: EpisodeType,
        @ProtoNumber(2) override val number: Float?,
    ) : EpisodeSort() {
        override val raw: String get() = "${type.value}${getNumberStr(number)}"
        override fun toString(): String = raw
    }

    @SerialName("me.him188.ani.datasources.api.EpisodeSort.Unknown")
    @Serializable
    class Unknown internal constructor(
        @ProtoNumber(1) override val raw: String
    ) : EpisodeSort() {
        override val number: Float? get() = null
        override fun toString(): String = raw
    }

    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other === null) return false
        if (other !is EpisodeSort) return false

        val otherFloat = other.number
        val thisFloat = number
        return otherFloat == thisFloat && other.raw == raw
    }

    final override fun hashCode(): Int {
        if (number != null) return number.hashCode() + raw.hashCode()
        return raw.hashCode()
    }

    final override fun compareTo(other: EpisodeSort): Int {
        if (this is Normal) {
            if (other is Normal) return number.compareTo(other.number)
            if (other is Special) return -1
            return -1
        }
        if (this is Special) {
            if (other is Normal) return 1
            if (other is Special) {
                val typeCom = type.compareTo(other.type)
                if (typeCom != 0) return typeCom
                if (number == null) return -1
                if (other.number == null) return 0
                val numCom = number.compareTo(other.number)
                if (numCom != 0) return numCom
                return raw.compareTo(other.raw)
            }
            return -1
        }

        if (other is Normal) return 1
        if (other is Special) return 1

        return raw.compareTo(other.raw)
    }

    companion object {

    }
}

private fun getSpecialByRaw(raw: String): EpisodeSort {
    val type = EpisodeType.entries.firstOrNull { entry -> raw.startsWith(entry.value, ignoreCase = true) }
    if (type == null) return EpisodeSort.Unknown(raw)

    val num = raw.substringAfter(type.value).toFloatOrNull() ?: return Special(type, null)
    if (num < 0) return EpisodeSort.Unknown(raw)

    return if (num.toInt().toFloat() == num || num % 0.5f == 0f) {
        Special(type, num)
    } else {
        EpisodeSort.Unknown(raw)
    }
}

fun EpisodeSort(raw: String): EpisodeSort {
    val float = raw.toFloatOrNull() ?: return getSpecialByRaw(raw)
    if (float < 0) return EpisodeSort.Unknown(raw)
    return if (float.toInt().toFloat() == float || float % 0.5f == 0f) {
        Normal(float)
    } else {
        EpisodeSort.Unknown(raw)
    }
}

fun EpisodeSort(int: Int, type: EpisodeType? = MainStory): EpisodeSort {
    return EpisodeSort(BigNum(int), type)
}

fun EpisodeSort(int: BigNum, type: EpisodeType? = MainStory): EpisodeSort {
    if (int.isNegative()) return EpisodeSort.Unknown(int.toString())
    if (int.toFloat().toInt().toFloat() != int.toFloat()
        && int.toFloat() % 0.5f != 0f
    ) return EpisodeSort.Unknown(int.toString())
    return when (type) {
        MainStory -> Normal(int.toFloat())
        SP, OP, ED, PV, MAD, OVA, OAD -> Special(type, int.toFloat())
        null -> EpisodeSort.Unknown(int.toString())
    }
}