import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import java.io.File

fun Project.commentFreeGeneratedSources(
    name: String,
    sourceDirectory: File,
    producer: TaskProvider<out Task>,
): TaskProvider<JavaExec> {
    val version = extensions.getByType<VersionCatalogsExtension>().named("libs").findVersion("kotlin").get().requiredVersion
    val lexer = configurations.detachedConfiguration(
        dependencies.create("org.jetbrains.kotlin:kotlin-compiler-embeddable:$version"),
    )
    val repositoryRoot = rootProject.projectDir
    val cleanup = tasks.register<JavaExec>(name) {
        dependsOn(producer)
        classpath = lexer
        mainClass.set(repositoryRoot.resolve("scripts/strip-kotlin-comments.java").absolutePath)
        args(repositoryRoot.absolutePath, sourceDirectory.absolutePath)
    }
    producer.configure { finalizedBy(cleanup) }
    tasks.withType(KotlinCompilationTask::class.java).configureEach {
        mustRunAfter(producer, cleanup)
    }
    return cleanup
}
