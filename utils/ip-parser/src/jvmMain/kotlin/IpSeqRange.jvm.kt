package com.wynime.utils.ipparser

import inet.ipaddr.AddressStringException
import inet.ipaddr.IPAddress
import inet.ipaddr.IPAddressString

internal actual fun IpSeqRange(ipSeqPattern: String): IpSeqRange {
    val range: IPAddress? = try {
        IPAddressString(ipSeqPattern).address
    } catch (ex: AddressStringException) {
        throw IllegalArgumentException(ex)
    }

    if (range == null) {
        throw IllegalArgumentException("Invalid IP range pattern: '$ipSeqPattern'")
    }

    return JvmIpSeqRange(range)
}

private class JvmIpSeqRange(
    private val range: IPAddress
) : IpSeqRange {
    override fun contains(address: String): Boolean {
        val ipAddress: IPAddress = try {
            IPAddressParser.parse(address)
                ?: return false
        } catch (_: Exception) {
            return false
        }

        return range.contains(ipAddress)
    }

    override fun toString(): String {
        return "JvmIpSeqRange(range=$range)"
    }
}