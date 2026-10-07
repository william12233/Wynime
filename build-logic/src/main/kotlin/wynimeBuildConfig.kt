import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.DefaultTask
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import javax.inject.Inject

abstract class BuildConfigPlatform @Inject constructor(private val platformName: String) : Named {
    override fun getName(): String = platformName

    abstract val fieldLines: ListProperty<String>

    private fun addField(name: String, isOverride: Boolean, valueLiteral: String) {
        val prefix = if (isOverride) "override " else ""
        fieldLines.add("${prefix}val $name = $valueLiteral")
    }

    fun stringField(name: String, value: String?, isOverride: Boolean = true) {
        addField(name, isOverride, if (value == null) "null" else "\"${escapeKotlinString(value)}\"")
    }

    private fun escapeKotlinString(value: String): String = buildString(value.length) {
        for (c in value) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '$' -> append("\\$")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
    }

    fun booleanField(name: String, value: Boolean, isOverride: Boolean = true) {
        addField(name, isOverride, value.toString())
    }

    fun integerField(name: String, value: Int, isOverride: Boolean = true) {
        addField(name, isOverride, value.toString())
    }

    fun expressionField(name: String, expression: String, isOverride: Boolean = true) {
        addField(name, isOverride, expression)
    }
}

abstract class BuildConfigExtension @Inject constructor(objects: ObjectFactory) {
    abstract val packageName: Property<String>
    abstract val className: Property<String>
    abstract val outputDir: DirectoryProperty

    val platforms: NamedDomainObjectContainer<BuildConfigPlatform> =
        objects.domainObjectContainer(BuildConfigPlatform::class.java)

    fun platform(name: String, configure: BuildConfigPlatform.() -> Unit) {
        platforms.maybeCreate(name).configure()
    }
}

@CacheableTask
abstract class GenerateBuildConfigTask : DefaultTask() {
    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val className: Property<String>

    @get:Input
    abstract val platformName: Property<String>

    @get:Input
    abstract val fieldLines: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generateBuildConfig() {
        val outputDir = outputDirectory.get().asFile
        outputDir.mkdirs()

        val classNameValue = className.get()
        val platformNameValue = platformName.get()
        val fieldsCode = fieldLines.get().joinToString("\n    ")

        val content = """
package ${packageName.get()}

object ${classNameValue}${platformNameValue.replaceFirstChar { it.uppercase() }} : $classNameValue {
    $fieldsCode
}
""".trim() + "\n"

        if (classNameValue == "WynimeBuildConfig") outputDir.resolve("AniBuildConfig.kt").delete()
        outputDir.resolve("$classNameValue.kt").writeText(content)
    }
}

class WynimeBuildConfigPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = with(project) {
        val extension = extensions.create<BuildConfigExtension>("buildConfig")
        extension.packageName.convention("buildconfig")
        extension.className.convention("BuildConfig")
        extension.outputDir.convention(layout.buildDirectory.dir("generated/buildconfig"))

        extension.platforms.all {
            val platform = this
            val platformDir = extension.outputDir.map { it.dir(platform.name) }

            val generateTask = tasks.register<GenerateBuildConfigTask>(
                "generate${extension.className.get()}${platform.name.replaceFirstChar { it.uppercase() }}",
            ) {
                group = "build"
                description = "Generates ${extension.className.get()} for ${platform.name}"

                packageName.set(extension.packageName)
                className.set(extension.className)
                platformName.set(platform.name)
                fieldLines.set(platform.fieldLines)
                outputDirectory.set(platformDir)
            }

            plugins.withType(KotlinBasePlugin::class.java) {
                extensions.findByType(KotlinMultiplatformExtension::class.java)
                    ?.sourceSets
                    ?.matching { it.name == "${platform.name}Main" }
                    ?.configureEach {
                        kotlin.srcDir(generateTask.flatMap { it.outputDirectory })
                    }
            }
        }
    }
}
