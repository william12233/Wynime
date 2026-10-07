package org.intellij.lang.annotations

@OptIn(ExperimentalMultiplatform::class)
@Retention(AnnotationRetention.SOURCE)
@OptionalExpectation
expect annotation class Language(
    val value: String,
    val prefix: String = "",
    val suffix: String = ""
)
