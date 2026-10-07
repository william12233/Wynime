@file:Suppress("NOTHING_TO_INLINE", "KotlinRedundantDiagnosticSuppress")

package com.wynime.app.ui.exploration.search

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.wynime.app.data.models.subject.PersonPosition
import com.wynime.app.data.models.subject.RelatedPersonInfo
import com.wynime.app.data.network.LightRelatedPersonInfo
import kotlin.jvm.JvmInline

@Immutable
@JvmInline
value class RoleSet(
    private val delegate: List<Role>,
) {
    operator fun plus(other: RoleSet): RoleSet = RoleSet(delegate + other.delegate)
    operator fun minus(other: RoleSet): RoleSet = RoleSet(delegate - other.delegate.toSet())
    operator fun contains(role: Role): Boolean = role in delegate

    fun indexOf(role: Role): Int = delegate.indexOf(role)

    @Stable
    companion object {
        @Stable
        val Empty = RoleSet(emptyList())

        @Stable
        val Default =
            RoleSet(listOf(Role.AnimationWork, Role.Director, Role.Script, Role.Music))
    }
}

fun Sequence<LightRelatedPersonInfo>.filterByRoleSet(roleSet: RoleSet): Sequence<LightRelatedPersonInfo> {
    return filter f@{ person ->
        person.position in roleSet
    }
}

fun Sequence<LightRelatedPersonInfo>.sortedWithRoleSet(roleSet: RoleSet): Sequence<LightRelatedPersonInfo> {
    return sortedBy { person ->
        val index = roleSet.indexOf(person.position)
        if (index == -1) Int.MAX_VALUE else index
    }
}

typealias Role = PersonPosition
