import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.kotlin.dsl.of
import java.util.Properties

abstract class LocalPropertiesValueSource :
    ValueSource<Map<String, String>, LocalPropertiesValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val localPropertiesFile: RegularFileProperty
    }

    override fun obtain(): Map<String, String> {
        val file = parameters.localPropertiesFile.get().asFile

        if (!file.isFile) return emptyMap()
        val properties = Properties()
        file.inputStream().buffered().use(properties::load)
        return properties.entries.associate { (key, value) -> key.toString() to value.toString() }
    }
}

val Project.localProperties: Provider<Map<String, String>>
    get() = providers.of(LocalPropertiesValueSource::class) {
        parameters.localPropertiesFile.set(layout.settingsDirectory.file("local.properties"))
    }

fun Project.wynimeProperty(name: String): Provider<String> =
    localProperties.map { it[name] ?: legacyPropertyName(name)?.let(it::get) }
        .orElse(providers.systemProperty(name))
        .orElse(providers.environmentVariable(name))
        .orElse(providers.gradleProperty(name))
        .let { property ->
            legacyPropertyName(name)?.let { legacy ->
                property.orElse(providers.systemProperty(legacy))
                    .orElse(providers.environmentVariable(legacy))
                    .orElse(providers.gradleProperty(legacy))
            } ?: property
        }

fun Project.getProperty(name: String) =
    getPropertyOrNull(name) ?: error("Property $name not found")

fun Project.getPropertyOrNull(name: String): String? =
    wynimeProperty(name).orNull

        ?: extensions.extraProperties.runCatching { get(name).toString() }.getOrNull()

private fun legacyPropertyName(name: String): String? =
    name.takeIf { it.startsWith("wynime.") }?.replaceFirst("wynime.", "ani.")

fun Project.getLocalProperty(key: String): String? =
    localProperties.get().let { it[key] ?: legacyPropertyName(key)?.let(it::get) }

fun Project.getIntProperty(name: String) = getProperty(name).toInt()

val Project.enableFirebase
    get() = getPropertyOrNull("wynime.enable.firebase")?.toBooleanStrict() ?: false
