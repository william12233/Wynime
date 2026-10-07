package com.wynime.app.ui.update.devbuild

import androidx.compose.runtime.Immutable
import kotlin.time.Instant

@Immutable
data class DevBuildCommit(
    val sha: String,

    val title: String,
    val author: String,
    val committedAt: Instant?,
    val htmlUrl: String,

    val build: DevBuildRun?,

    val artifact: DevBuildArtifact?,
) {
    val shortSha: String get() = sha.take(SHORT_SHA_LENGTH)

    companion object {

        const val SHORT_SHA_LENGTH = 8
    }
}

@Immutable
data class DevBuildRun(
    val id: Long,
    val status: DevBuildStatus,
    val htmlUrl: String,
)

enum class DevBuildStatus {
    QUEUED,
    IN_PROGRESS,
    SUCCESS,
    FAILURE,
    CANCELLED,
    OTHER,
    ;

    companion object {
        fun fromWorkflowRun(status: String?, conclusion: String?): DevBuildStatus = when (status) {
            "queued", "waiting", "pending", "requested" -> QUEUED
            "in_progress" -> IN_PROGRESS
            "completed" -> when (conclusion) {
                "success" -> SUCCESS
                "failure", "timed_out", "startup_failure", "action_required" -> FAILURE
                "cancelled", "skipped" -> CANCELLED
                else -> OTHER
            }

            else -> OTHER
        }
    }
}

@Immutable
data class DevBuildArtifact(
    val id: Long,
    val name: String,
    val sizeInBytes: Long,
    val archiveDownloadUrl: String,
)

@Immutable
data class DevBuildPullRequest(
    val number: Int,
    val title: String,
    val htmlUrl: String,
    val headRef: String,
    val isFromFork: Boolean,
)

fun buildDevBuildCommits(
    commits: List<GitHubCommit>,
    runs: List<GitHubWorkflowRun>,
    artifacts: List<GitHubArtifact>,
    spec: DevBuildPackageSpec,
): List<DevBuildCommit> {
    val runBySha = runs.groupBy { it.headSha }.mapValues { (_, list) -> list.maxBy { it.id } }
    val artifactsBySha = artifacts
        .filter { !it.expired && it.workflowRun != null && it.name in spec.candidateArtifactNames }
        .groupBy { it.workflowRun!!.headSha }

    return commits.map { commit ->
        val run = runBySha[commit.sha]
        val artifact = artifactsBySha[commit.sha]
            ?.sortedWith(
                compareBy<GitHubArtifact> { spec.candidateArtifactNames.indexOf(it.name) }.thenByDescending { it.id },
            )
            ?.firstOrNull()
        DevBuildCommit(
            sha = commit.sha,
            title = commit.commit.message.lineSequence().firstOrNull()?.trim().orEmpty(),
            author = commit.author?.login?.takeIf { it.isNotBlank() }
                ?: commit.commit.author?.name.orEmpty(),
            committedAt = (commit.commit.committer?.date ?: commit.commit.author?.date)
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { Instant.parse(it) }.getOrNull() },
            htmlUrl = commit.htmlUrl,
            build = run?.let {
                DevBuildRun(
                    id = it.id,
                    status = DevBuildStatus.fromWorkflowRun(it.status, it.conclusion),
                    htmlUrl = it.htmlUrl,
                )
            },
            artifact = artifact?.let {
                DevBuildArtifact(
                    id = it.id,
                    name = it.name,
                    sizeInBytes = it.sizeInBytes,
                    archiveDownloadUrl = it.archiveDownloadUrl,
                )
            },
        )
    }
}

fun parseMainBranchShortSha(versionName: String, branch: String = GitHubDevBuildApi.DEFAULT_BRANCH): String? =
    Regex("""-${Regex.escape(branch)}-([0-9a-fA-F]{7,40})$""").find(versionName)?.groupValues?.get(1)?.lowercase()
