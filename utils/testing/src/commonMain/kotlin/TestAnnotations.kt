package com.wynime.test

@OptIn(ExperimentalMultiplatform::class)
@OptionalExpectation
expect annotation class DisabledOnAndroid()

@OptionalExpectation
@OptIn(ExperimentalMultiplatform::class)
expect annotation class DisabledOnNative()

@OptIn(ExperimentalMultiplatform::class)
@OptionalExpectation
expect annotation class EnabledOnlyOnDesktop()
