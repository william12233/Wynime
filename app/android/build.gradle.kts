import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
    id("wynime.android-application")
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.kotlinx.atomicfu)
    alias(libs.plugins.google.gms.google.services)
    idea
}

val archs = getPropertyOrNull("wynime.android.abis")
    ?.split(',')
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.takeIf { it.isNotEmpty() }
    ?.let { abis ->
        if (abis.size == 1 && abis.first() == "all") {
            listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        } else {
            abis
        }
    }
    ?: listOf("arm64-v8a")

private fun androidVersionCode(versionName: String): Int {
    val numericParts = versionName.substringBefore('-').split('.').map {
        it.toIntOrNull() ?: error("Invalid Android version name: $versionName")
    }
    require(numericParts.size == 3) { "Android version name must contain major.minor.patch: $versionName" }

    val qualifier = versionName.substringAfter('-', "").lowercase()
    val qualifierRank = when {
        qualifier.isEmpty() -> 3
        qualifier.startsWith("rc") -> 2
        qualifier.startsWith("beta") -> 1
        else -> 0
    }
    val qualifierNumber = qualifier
        .dropWhile { !it.isDigit() }
        .takeWhile(Char::isDigit)
        .toIntOrNull()
        ?: 0

    val code = numericParts[0] * 100_000_000 +
        numericParts[1] * 1_000_000 +
        numericParts[2] * 1_000 +
        qualifierRank * 100 +
        qualifierNumber
    require(code in 1..2_100_000_000) { "Android version code is out of range: $code" }
    return code
}

android {
    namespace = "com.wynime.app"
    compileSdk = getIntProperty("android.compile.sdk")
    defaultConfig {
        applicationId = "com.wynime.app"
        minSdk = getIntProperty("android.min.sdk")
        targetSdk = getIntProperty("android.compile.sdk")
        versionCode = androidVersionCode(project.version.toString())
        versionName = project.version.toString()
        ndk {

            abiFilters.clear()

            abiFilters += archs
        }
    }
    splits {
        abi {
            isEnable = true
            reset()

            include(*archs.toTypedArray())
            isUniversalApk = true
        }
    }
    signingConfigs {
        kotlin.runCatching { getProperty("signing_release_storeFileFromRoot") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let {
            create("release") {
                storeFile = rootProject.file(it)
                storePassword = getProperty("signing_release_storePassword")
                keyAlias = getProperty("signing_release_keyAlias")
                keyPassword = getProperty("signing_release_keyPassword")
            }
        }
        kotlin.runCatching { getProperty("signing_release_storeFile") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let {
            create("release") {
                storeFile = file(it)
                storePassword = getProperty("signing_release_storePassword")
                keyAlias = getProperty("signing_release_keyAlias")
                keyPassword = getProperty("signing_release_keyPassword")
            }
        }
    }
    packaging {
        jniLibs {

            useLegacyPackaging = true
        }
        resources {
            merges.add("META-INF/DEPENDENCIES")
            pickFirsts.add("META-INF/LICENSE.md")
            pickFirsts.add("META-INF/LICENSE-notice.md")

        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                *sharedAndroidProguardRules(),
            )
        }
        debug {
            applicationIdSuffix = getLocalProperty("wynime.android.debug.applicationIdSuffix") ?: ".debug2"
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    flavorDimensions += "distribution"
    productFlavors {
        create("default") {
            dimension = "distribution"
        }

    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets.getByName("main").assets.srcDir(rootProject.file("licenses"))
}

dependencies {

    implementation(projects.app.shared)
    implementation(projects.app.shared.application)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)

    implementation(libs.ktor.client.core)
    implementation(libs.mediamp.ffmpeg)
}

idea {
    module {
        excludeDirs.add(file(".cxx"))
    }
}

googleServices {
    missingGoogleServicesStrategy = (getLocalProperty("wynime.enable.firebase") ?: "false").toBooleanStrict()
        .let {
            if (it) MissingGoogleServicesStrategy.ERROR else MissingGoogleServicesStrategy.IGNORE
        }
}

tasks.configureEach {
    if (name.startsWith("processTv") && name.endsWith("GoogleServices")) {
        enabled = false
    }
}

configurations.configureEach {
    if (name.startsWith("tv") && name.endsWith("Classpath")) {
        exclude(group = "dev.gitlive", module = "firebase-analytics")
        exclude(group = "dev.gitlive", module = "firebase-analytics-android")
        exclude(group = "dev.gitlive", module = "firebase-app")
        exclude(group = "dev.gitlive", module = "firebase-app-android")
        exclude(group = "com.google.firebase")
        exclude(group = "com.google.android.gms")
    }
}
