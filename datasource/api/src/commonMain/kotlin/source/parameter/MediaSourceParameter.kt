package com.wynime.datasources.api.source.parameter

sealed interface MediaSourceParameter<T> {
    val name: String
    val description: String?
    val default: () -> T
    val visibleWhen: MediaSourceParameterVisibilityCondition?
        get() = null

    fun parseFromString(value: String): T
}

data class MediaSourceParameterVisibilityCondition(
    val parameterName: String,
    val acceptedValues: Set<String>,
) {
    init {
        require(parameterName.isNotEmpty()) { "parameterName must not be empty" }
        require(acceptedValues.isNotEmpty()) { "acceptedValues must not be empty" }
    }
}

fun MediaSourceParameter<*>.hasValue(vararg acceptedValues: String): MediaSourceParameterVisibilityCondition =
    MediaSourceParameterVisibilityCondition(name, acceptedValues.toSet())

private val TrueValidator: (String) -> Boolean = { true }
private val NoopSanitizer: (String) -> String = { it }

class StringParameter(
    override val name: String,
    override val description: String? = null,
    override val default: () -> String,
    val placeholder: String? = null,
    val isRequired: Boolean = false,

    validate: (String) -> Boolean = TrueValidator,

    val sanitize: (String) -> String = NoopSanitizer,
    override val visibleWhen: MediaSourceParameterVisibilityCondition? = null,
) : MediaSourceParameter<String> {
    val validate: (String) -> Boolean = {
        if (isRequired && it.isBlank()) {
            false
        } else {
            validate(it)
        }
    }

    init {
        require(name.isNotEmpty()) { "name must not be empty" }
    }

    override fun parseFromString(value: String): String {
        return sanitize(value)
    }
}

data class BooleanParameter(
    override val name: String,
    override val description: String? = null,
    override val default: () -> Boolean,
    override val visibleWhen: MediaSourceParameterVisibilityCondition? = null,
) : MediaSourceParameter<Boolean> {
    init {
        require(name.isNotEmpty()) { "name must not be empty" }
    }

    override fun parseFromString(value: String): Boolean {
        return value.toBoolean()
    }
}

data class SimpleEnumParameter(
    override val name: String,
    val oneOf: List<String>,
    override val description: String? = null,
    override val default: () -> String,
    override val visibleWhen: MediaSourceParameterVisibilityCondition? = null,
) : MediaSourceParameter<String> {
    init {
        require(name.isNotEmpty()) { "name must not be empty" }
        require(oneOf.isNotEmpty()) { "oneOf must not be empty" }
    }

    override fun parseFromString(value: String): String {
        return value
    }
}
