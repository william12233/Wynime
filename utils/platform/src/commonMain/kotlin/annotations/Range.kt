package com.wynime.utils.platform.annotations

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.TYPE)
expect annotation class Range(

    val from: Long,

    val to: Long
)
