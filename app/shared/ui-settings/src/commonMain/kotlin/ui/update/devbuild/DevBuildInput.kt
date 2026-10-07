package com.wynime.app.ui.update.devbuild

import io.ktor.http.URLProtocol
import io.ktor.http.Url

sealed interface DevBuildInput {

    data class Commit(val sha: String) : DevBuildInput

    data class PullRequest(val number: Int) : DevBuildInput

    data class WorkflowRun(val runId: Long) : DevBuildInput

    data class Artifact(val artifactId: Long) : DevBuildInput

    data class PackageUrl(val url: String, val fileName: String) : DevBuildInput
}

fun parseDevBuildInput(text: String, repository: String): DevBuildInput? {
    val input = text.trim()
    if (input.isEmpty()) return null
    if (SHA_REGEX.matches(input)) return DevBuildInput.Commit(input.lowercase())
    PR_NUMBER_REGEX.matchEntire(input)?.let { return DevBuildInput.PullRequest(it.groupValues[1].toInt()) }

    val url = runCatching { Url(input) }.getOrNull() ?: return null
    if (url.protocol != URLProtocol.HTTP && url.protocol != URLProtocol.HTTPS) return null
    val segments = url.segments.filter { it.isNotEmpty() }
    return when (url.host.lowercase()) {

        "github.com", "www.github.com" -> parseGitHubPath(segments, repository) ?: parsePackageUrl(input, segments)
        "api.github.com" -> parseGitHubApiPath(segments, repository)
        else -> parsePackageUrl(input, segments)
    }
}

private fun parseGitHubPath(segments: List<String>, repository: String): DevBuildInput? {
    if (segments.size < 4 || !isRepository(segments[0], segments[1], repository)) return null
    val rest = segments.drop(2)
    return when (rest[0]) {
        "commit" -> rest.getOrNull(1)?.takeIf { SHA_REGEX.matches(it) }?.let { DevBuildInput.Commit(it.lowercase()) }
        "pull" -> {
            val number = rest.getOrNull(1)?.toIntOrNull() ?: return null
            val sha = rest.getOrNull(3)?.takeIf { rest.getOrNull(2) == "commits" && SHA_REGEX.matches(it) }
            if (sha != null) DevBuildInput.Commit(sha.lowercase()) else DevBuildInput.PullRequest(number)
        }

        "actions" -> {
            if (rest.getOrNull(1) != "runs") return null
            val runId = rest.getOrNull(2)?.toLongOrNull() ?: return null
            val artifactId = rest.getOrNull(4)?.takeIf { rest.getOrNull(3) == "artifacts" }?.toLongOrNull()
            if (artifactId != null) DevBuildInput.Artifact(artifactId) else DevBuildInput.WorkflowRun(runId)
        }

        else -> null
    }
}

private fun parseGitHubApiPath(segments: List<String>, repository: String): DevBuildInput? {
    if (segments.size < 5 || segments[0] != "repos" || !isRepository(segments[1], segments[2], repository)) return null
    val rest = segments.drop(3)
    return when (rest[0]) {
        "commits" -> rest.getOrNull(1)?.takeIf { SHA_REGEX.matches(it) }?.let { DevBuildInput.Commit(it.lowercase()) }
        "pulls" -> rest.getOrNull(1)?.toIntOrNull()?.let { DevBuildInput.PullRequest(it) }
        "actions" -> when (rest.getOrNull(1)) {
            "runs" -> rest.getOrNull(2)?.toLongOrNull()?.let { DevBuildInput.WorkflowRun(it) }
            "artifacts" -> rest.getOrNull(2)?.toLongOrNull()?.let { DevBuildInput.Artifact(it) }
            else -> null
        }

        else -> null
    }
}

private fun parsePackageUrl(url: String, segments: List<String>): DevBuildInput? {
    val fileName = segments.lastOrNull() ?: return null
    if (fileName.substringAfterLast('.', "").isEmpty()) return null
    return DevBuildInput.PackageUrl(url, fileName)
}

private fun isRepository(owner: String, name: String, repository: String): Boolean =
    "$owner/$name".equals(repository, ignoreCase = true)

private val SHA_REGEX = Regex("""[0-9a-fA-F]{7,40}""")
private val PR_NUMBER_REGEX = Regex("""#?(\d{1,6})""")
