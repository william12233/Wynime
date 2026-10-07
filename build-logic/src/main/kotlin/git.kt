import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.kotlin.dsl.of
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.Serializable
import javax.inject.Inject

data class GitInfo(

    val branch: String,

    val commitSha: String,

    val commitTime: String,
) : Serializable {
    companion object {
        val UNKNOWN = GitInfo(branch = "", commitSha = "", commitTime = "")
    }
}

abstract class GitInfoValueSource : ValueSource<GitInfo, GitInfoValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val rootDir: DirectoryProperty
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): GitInfo {
        val commitSha = git("rev-parse", "HEAD") ?: return GitInfo.UNKNOWN
        val branch = git("rev-parse", "--abbrev-ref", "HEAD")
            ?.takeIf { it != "HEAD" }
            ?: System.getenv("GITHUB_HEAD_REF")?.takeIf { it.isNotBlank() }
            ?: System.getenv("GITHUB_REF_NAME")?.takeIf { it.isNotBlank() }
            ?: ""
        val commitTime = git("show", "-s", "--format=%cI", "HEAD") ?: ""

        return GitInfo(
            branch = branch,
            commitSha = commitSha,
            commitTime = commitTime,
        )
    }

    private fun git(vararg args: String): String? {
        val stdout = ByteArrayOutputStream()
        val result = runCatching {
            execOperations.exec {
                workingDir = parameters.rootDir.get().asFile
                commandLine("git", *args)
                standardOutput = stdout
                errorOutput = ByteArrayOutputStream()
                isIgnoreExitValue = true
            }
        }.getOrNull() ?: return null
        if (result.exitValue != 0) return null
        return stdout.toString(Charsets.UTF_8).trim()
    }
}

val Project.gitInfo: Provider<GitInfo>
    get() = providers.of(GitInfoValueSource::class) {
        parameters.rootDir.set(layout.settingsDirectory)
    }
