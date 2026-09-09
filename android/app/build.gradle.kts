import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Base64

plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

private val bangumiBrokerOriginKey = "WYNIME_BANGUMI_BROKER_ORIGIN"
private val unavailableBangumiBrokerHost = "wynime-bangumi-unavailable.invalid"

private fun decodeDartDefines(raw: String?): Map<String, String> {
    if (raw.isNullOrBlank()) return emptyMap()
    return raw.split(',').associate { encoded ->
        val decoded = try {
            String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            throw GradleException("Flutter dart-defines contains invalid base64.")
        }
        val separator = decoded.indexOf('=')
        if (separator <= 0) {
            throw GradleException("Flutter dart-defines contains an invalid entry.")
        }
        decoded.substring(0, separator) to decoded.substring(separator + 1)
    }
}

private fun isSafeBangumiHost(host: String): Boolean {
    if (host.length !in 1..253) return false
    return host.split('.').all { label ->
        label.length in 1..63 &&
            label.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?"))
    }
}

private fun configuredBangumiBroker(
    rawOrigin: String,
): Pair<Boolean, String> {
    val value = rawOrigin.trim()
    if (value.isEmpty()) return false to unavailableBangumiBrokerHost
    val origin = try {
        URI(value)
    } catch (_: IllegalArgumentException) {
        throw GradleException("$bangumiBrokerOriginKey must be a valid HTTPS origin.")
    }
    val host = origin.host?.lowercase()
    val valid = origin.scheme.equals("https", ignoreCase = true) &&
        !host.isNullOrBlank() &&
        isSafeBangumiHost(host) &&
        (origin.port == -1 || origin.port == 443) &&
        origin.userInfo == null &&
        origin.query == null &&
        origin.fragment == null &&
        (origin.path.isEmpty() || origin.path == "/")
    if (!valid) {
        throw GradleException("$bangumiBrokerOriginKey must be a valid HTTPS origin.")
    }
    return true to host!!
}

val dartBangumiOrigin = decodeDartDefines(
    providers.gradleProperty("dart-defines").orNull,
)[bangumiBrokerOriginKey]?.takeIf { it.isNotBlank() }
val directBangumiOrigin = providers.gradleProperty(bangumiBrokerOriginKey).orNull
    ?.takeIf { it.isNotBlank() }
val bangumiOriginValues = listOfNotNull(dartBangumiOrigin, directBangumiOrigin).distinct()
if (bangumiOriginValues.size > 1) {
    throw GradleException(
        "$bangumiBrokerOriginKey was supplied with conflicting values.",
    )
}
val (bangumiBrokerEnabled, bangumiBrokerHost) = configuredBangumiBroker(
    bangumiOriginValues.singleOrNull().orEmpty(),
)

val releaseStoreFile =
    providers.gradleProperty("wynimeReleaseStoreFile").orNull
        ?: System.getenv("WYNIME_RELEASE_STORE_FILE")
val releaseStorePassword =
    providers.gradleProperty("wynimeReleaseStorePassword").orNull
        ?: System.getenv("WYNIME_RELEASE_STORE_PASSWORD")
val releaseKeyAlias =
    providers.gradleProperty("wynimeReleaseKeyAlias").orNull
        ?: System.getenv("WYNIME_RELEASE_KEY_ALIAS")
val releaseKeyPassword =
    providers.gradleProperty("wynimeReleaseKeyPassword").orNull
        ?: System.getenv("WYNIME_RELEASE_KEY_PASSWORD")
val hasReleaseSigning =
    listOf(
        releaseStoreFile,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword,
    ).all { !it.isNullOrBlank() }

android {
    namespace = "io.github.william12233.wynime"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "io.github.william12233.wynime"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
        manifestPlaceholders["wynimeBangumiBrokerHost"] = bangumiBrokerHost
        buildConfigField(
            "boolean",
            "WYNIME_BANGUMI_BROKER_ENABLED",
            bangumiBrokerEnabled.toString(),
        )
        buildConfigField(
            "String",
            "WYNIME_BANGUMI_BROKER_HOST",
            "\"$bangumiBrokerHost\"",
        )
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("wynimeRelease") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("wynimeRelease")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    val media3Version = "1.10.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
}

flutter {
    source = "../.."
}
