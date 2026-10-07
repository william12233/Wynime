package com.wynime.utils.ipparser

import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IpSeqRangeTest {

    @Test
    fun `invalid range`() {
        assertThrows<IllegalArgumentException> {
            IpSeqRange("invalid")
        }
    }

    @Test
    fun `ipv4 range`() {
        assertEquals(true, IpSeqRange("192.168.1.0/24").contains("192.168.1.252"))
    }

    @Test
    fun `ipv4 single address`() {

        val ipRange = IpSeqRange("10.0.0.1")
        assertTrue(ipRange.contains("10.0.0.1"), "Single IP range should contain itself")
        assertFalse(ipRange.contains("10.0.0.2"), "Single IP range should not contain another address")
    }

    @Test
    fun `ipv4 cidr boundaries`() {

        val ipRange = IpSeqRange("10.0.0.0/30")

        assertTrue(ipRange.contains("10.0.0.0"))
        assertTrue(ipRange.contains("10.0.0.1"))
        assertTrue(ipRange.contains("10.0.0.2"))
        assertTrue(ipRange.contains("10.0.0.3"))

        assertFalse(ipRange.contains("10.0.0.4"))
        assertFalse(ipRange.contains("9.255.255.255"))
        assertFalse(ipRange.contains("10.0.1.0"))
    }

    @Test
    fun `ipv4 large cidr`() {

        val ipRange = IpSeqRange("10.0.0.0/8")
        assertTrue(ipRange.contains("10.0.0.1"))
        assertTrue(ipRange.contains("10.255.255.255"))

        assertFalse(ipRange.contains("11.0.0.0"))
        assertFalse(ipRange.contains("9.255.255.255"))
    }

    @Test
    fun `ipv4 broadcast address`() {

        val ipRange = IpSeqRange("192.168.0.0/24")
        assertTrue(ipRange.contains("192.168.0.255"), "Should contain broadcast address in that CIDR")
        assertFalse(ipRange.contains("192.168.1.255"))
    }

    @Test
    fun `ipv4 edge cases`() {

        val ipRange1 = IpSeqRange("0.0.0.0")
        assertTrue(ipRange1.contains("0.0.0.0"))
        assertFalse(ipRange1.contains("0.0.0.1"))

        val ipRange2 = IpSeqRange("255.255.255.255")
        assertTrue(ipRange2.contains("255.255.255.255"))
        assertFalse(ipRange2.contains("255.255.255.254"))
    }

    @Test
    fun `ipv4 invalid addresses`() {

        val ipRange = IpSeqRange("192.168.1.0/24")

        assertFalse(ipRange.contains("999.999.999.999"))
        assertFalse(ipRange.contains("256.256.256.256"))
        assertFalse(ipRange.contains("not_an_ip"))
    }

    @Test
    fun `ipv6 single address`() {

        val ipRange = IpSeqRange("::1")
        assertTrue(ipRange.contains("::1"), "Should contain the loopback address itself")
        assertFalse(ipRange.contains("::2"), "Should not contain another address")
    }

    @Test
    fun `ipv6 short notation`() {

        val ipRange = IpSeqRange("fe80::/64")

        assertTrue(ipRange.contains("fe80::"), "Network address itself")
        assertTrue(ipRange.contains("fe80::1"), "Slight increment")
        assertTrue(ipRange.contains("fe80::abcd:1234"), "Some random short within fe80::/64")

        assertFalse(ipRange.contains("fe80:1::"), "fe80:0001:: is not in fe80::/64")
        assertFalse(ipRange.contains("fe81::"), "Different prefix")
    }

    @Test
    fun `ipv6 long notation`() {

        val ipRange = IpSeqRange("2001:db8::/120")

        assertTrue(ipRange.contains("2001:db8::0"))
        assertTrue(ipRange.contains("2001:db8::1"))
        assertTrue(ipRange.contains("2001:db8::ff"))
        assertFalse(ipRange.contains("2001:db8::100"))
    }

    @Test
    fun `ipv6 full range`() {

        val ipRange = IpSeqRange("::/0")

        assertTrue(ipRange.contains("::1"))
        assertTrue(ipRange.contains("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff"))
        assertTrue(ipRange.contains("abcd::1234"))
    }

    @Test
    fun `ipv6 edge addresses`() {

        val ipRange1 = IpSeqRange("::")
        assertTrue(ipRange1.contains("::"), "Unspecified address should contain itself")
        assertFalse(ipRange1.contains("::1"), "Unspecified address does not cover ::1")

        val ipRange2 = IpSeqRange("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff")
        assertTrue(ipRange2.contains("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff"))
        assertFalse(ipRange2.contains("ffff:ffff:ffff:ffff:ffff:ffff:ffff:fffe"))
    }

    @Test
    fun `ipv6 invalid addresses`() {
        val ipRange = IpSeqRange("2001:db8::/32")

        assertFalse(ipRange.contains("not_an_ipv6"))
        assertFalse(ipRange.contains("2001:::db8"))
        assertFalse(ipRange.contains("2001:db8:xyz::1"))
        assertFalse(ipRange.contains("2001:db8::ffff:gggg"))
    }

    @Test
    fun `mixing ipv4 with ipv6 is invalid pattern`() {

        assertThrows<IllegalArgumentException> {
            IpSeqRange("192.168.0.1-2001:db8::")
        }
    }

    @Test
    fun `mixing ipv4 mapped ipv6 - check parser ability`() {

        val ipRange = IpSeqRange("::ffff:192.168.0.0/112")
        assertFalse(ipRange.contains("::ffff:192.168.0.1"))
        assertFalse(ipRange.contains("::ffff:192.168.1.1"))
    }
}
