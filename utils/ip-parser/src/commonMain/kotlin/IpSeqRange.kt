package com.wynime.utils.ipparser

interface IpSeqRange {
    fun contains(address: String): Boolean

    companion object {

        fun parse(ipSeqPattern: String): IpSeqRange = IpSeqRange(ipSeqPattern)
    }
}

internal expect fun IpSeqRange(ipSeqPattern: String): IpSeqRange
