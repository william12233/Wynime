package com.wynime.utils.platform.annotations

@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This constructor is for serialization only",
)
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.PROPERTY)
annotation class SerializationOnly
