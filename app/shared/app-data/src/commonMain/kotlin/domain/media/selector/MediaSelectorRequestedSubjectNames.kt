package com.wynime.app.domain.media.selector

import com.wynime.app.data.models.subject.SubjectInfo
import com.wynime.app.domain.sourceplugin.sourceTitleSeasonVariants
import com.wynime.datasources.api.source.MediaFetchRequest

/**
 * Adds names from an edited fetch request to the names used by media filtering.
 *
 * A source can find a title through an alternate spelling, so that spelling must also be part of
 * the selector context. Seasonal spellings are expanded only to equivalent seasonal forms; the
 * franchise-only fallback is intentionally not added here.
 */
internal fun SubjectInfo.withFetchRequestSubjectNames(request: MediaFetchRequest): SubjectInfo {
    val requestedNames = buildList {
        addAll(request.subjectNames)
        request.subjectNameCN?.let(::add)
    }.map(String::trim).filter(String::isNotBlank).distinct()
    if (requestedNames.isEmpty()) return this

    val namesToAdd = buildList {
        requestedNames.forEach { name ->
            add(name)
            addAll(sourceTitleSeasonVariants(name))
        }
    }
    val updatedAliases = (aliases + namesToAdd).distinct()
    return if (updatedAliases == aliases) this else copy(aliases = updatedAliases)
}
