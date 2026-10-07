package com.wynime.app.domain.mediasource

import com.wynime.app.domain.mediasource.MediaListFilters.removeSpecials
import com.wynime.test.TestFactory
import com.wynime.test.runDynamicTests
import kotlin.test.Test
import kotlin.test.assertEquals

class StringMatcherTest {

    @Test
    fun `test empty strings`() {
        val result = StringMatcher.calculateMatchRate("", "")
        assertEquals(100, result, "Empty strings should have 100% match")
    }

    @Test
    fun `test identical strings`() {
        val result = StringMatcher.calculateMatchRate("kotlin", "kotlin")
        assertEquals(100, result, "Identical strings should have 100% match")
    }

    @Test
    fun `test single character difference`() {
        val result = StringMatcher.calculateMatchRate("kotlin", "kotli")

        assertEquals(83, result, "Strings with one character difference should have around 83% match")
    }

    @Test
    fun `test partial match`() {
        val result = StringMatcher.calculateMatchRate("hello", "hallo")

        assertEquals(80, result, "Expected around 80% similarity for 'hello' and 'hallo'")
    }

    @Test
    fun `test completely different strings`() {
        val result = StringMatcher.calculateMatchRate("abcd", "wxyz")

        assertEquals(0, result, "Completely different strings should have 0% match")
    }

    @Test
    fun `test one string empty`() {
        val result = StringMatcher.calculateMatchRate("kotlin", "")

        assertEquals(0, result, "Non-empty vs. empty string should have 0% match")
    }

    @TestFactory
    fun `test matches`() = runDynamicTests {
        infix fun String.matches(another: String): Pair<String, String> = this to another
        infix fun Pair<String, String>.at(expected: Int) {
            add("$first matches $second at $expected") {
                val result = StringMatcher.calculateMatchRate(first, second)
                assertEquals(expected, result)
            }
        }

        "哥特萝莉侦探事件薄" matches "哥特萝莉侦探事件簿" at 88

        "哥特萝莉侦探事件簿" matches "哥特萝莉侦探事件簿第二季" at 75

        "败犬女主太多了" matches "败犬女主太多啦" at 85

        "别当欧尼酱了" matches "别当哥哥了" at 50

        "地狱少女第一季" matches "地。 ―关于地球的运动―" at 8
        "地狱少女第一季" matches "地。" at 14
        val chiRemoved = removeSpecials(
            "地。 ―关于地球的运动―",
            removeWhitespace = true,
            replaceNumbers = true,
        )
        "地狱少女第一季" matches chiRemoved at 9
        "地狱少女" matches "地。" at 25
    }

    @Test
    fun `test GOSICK2`() {
        val result = StringMatcher.calculateMatchRate("哥特萝莉侦探事件簿", "哥特萝莉侦探事件薄")

        assertEquals(88, result)
    }
}