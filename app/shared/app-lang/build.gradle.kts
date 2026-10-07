plugins {
    id("wynime.kmp-compose")
    alias(libs.plugins.kotlin.plugin.serialization)

    idea
}

kotlin {
    android {
        namespace = "com.wynime.app.ui.lang"
    }
    sourceSets.commonMain.dependencies {
        implementation(libs.atomicfu)
        api(libs.compose.components.resources)
    }
    sourceSets.commonTest.dependencies {
    }
    sourceSets.androidMain.dependencies {
    }
    sourceSets.desktopMain.dependencies {
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.wynime.app.ui.lang"
    customDirectory(
        "commonMain",
        project.provider {
            project.layout.projectDirectory.dir("src/androidMain/res")
        },
    )
}

val populateStringsLocales by tasks.registering(Copy::class) {
    group = "wynime"
    description =
        "Populate string resources for locales speaking Simplified or Traditional Chinese."

    val chtLocales = listOf(
        "values-zh-rMO",
    )
    val chsLocales = listOf(
        "values-zh",
        "values-zh-rSG",
    )
    val stringsDirectory = layout.projectDirectory.dir("src/androidMain/res")
    into(stringsDirectory)

    for (locale in chtLocales) {
        from(stringsDirectory.dir("values-zh-rHK")) {
            include("*.xml")
            into(locale)
        }
    }

    for (locale in chsLocales) {
        from(stringsDirectory.dir("values-zh-rCN")) {
            include("*.xml")
            into(locale)
        }
    }
}

tasks.matching {

    it.name.startsWith("convertXmlValueResources")
            || it.name.startsWith("copyNonXmlValueResources")

            || (it.name.startsWith("generate") && it.name.endsWith("Resources"))
            || it.name.startsWith("extractDeepLinks")
            || (it.name.startsWith("map") && it.name.endsWith("SourceSetPaths"))
}.configureEach {
    dependsOn(populateStringsLocales)
}

idea {
    module {
        excludeDirs.add(file("src/androidMain/res/values-zh"))
        excludeDirs.add(file("src/androidMain/res/values-zh-rMO"))
        excludeDirs.add(file("src/androidMain/res/values-zh-rSG"))
    }
}
