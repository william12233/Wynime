@file:JvmName("DynamicTestKt_common")

package com.wynime.test

import kotlin.jvm.JvmName
import kotlin.test.Test

@Target(AnnotationTarget.CLASS)
annotation class TestContainer

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
expect annotation class TestFactory()

expect open class DynamicTest

expect class DynamicTestsResult

expect fun dynamicTest(displayName: String, action: () -> Unit): DynamicTest

expect fun runDynamicTests(dynamicTests: List<DynamicTest>): DynamicTestsResult

fun runDynamicTests(vararg dynamicTests: Iterable<DynamicTest>): DynamicTestsResult =
    runDynamicTests(dynamicTests = dynamicTests.flatMap { it })

fun runDynamicTests(vararg dynamicTests: Sequence<DynamicTest>): DynamicTestsResult =
    runDynamicTests(dynamicTests = dynamicTests.flatMap { it })

fun runDynamicTests(vararg dynamicTests: DynamicTest): DynamicTestsResult = runDynamicTests(dynamicTests.toList())

fun runDynamicTests(dynamicTests: Sequence<DynamicTest>): DynamicTestsResult = runDynamicTests(dynamicTests.toList())

class DynamicTestsBuilder {
    private val list = mutableListOf<DynamicTest>()
    fun add(displayName: String, action: () -> Unit) {
        list.add(dynamicTest(displayName, action))
    }

    internal fun build() = list
}

fun runDynamicTests(action: DynamicTestsBuilder.() -> Unit): DynamicTestsResult =
    runDynamicTests(dynamicTests = DynamicTestsBuilder().apply(action).build())

