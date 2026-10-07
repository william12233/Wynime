package com.wynime.test

actual typealias TestFactory = org.junit.jupiter.api.TestFactory

actual typealias DynamicTest = org.junit.jupiter.api.DynamicTest

class DynamicTestContainer(
    val collection: Iterable<DynamicTest>
) : Iterable<DynamicTest> by collection

actual typealias DynamicTestsResult = DynamicTestContainer

actual fun dynamicTest(displayName: String, action: () -> Unit): DynamicTest {
    return DynamicTest.dynamicTest(displayName, action)
}

actual fun runDynamicTests(dynamicTests: List<DynamicTest>): DynamicTestsResult =
    DynamicTestContainer(dynamicTests)
