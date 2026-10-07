plugins {
    id("wynime.kmp-compose")
    alias(libs.plugins.kotlin.plugin.serialization)

}

kotlin {
    android {
        namespace = "com.wynime.app.ui.settings"
        packaging {
            resources {
                excludes.add("win32-x86-64/attach_hotspot_windows.dll")
                excludes.add("win32-x86/attach_hotspot_windows.dll")
                pickFirsts.add("META-INF/AL2.0")
                pickFirsts.add("META-INF/LGPL2.1")
                excludes.add("META-INF/DEPENDENCIES")
                excludes.add("META-INF/licenses/ASM")
            }
        }
    }
    sourceSets.commonMain.dependencies {
        api(projects.app.shared.uiFoundation)
        api(projects.app.shared.uiAdaptive)
        implementation(libs.compose.components.resources)
        implementation(projects.app.shared.reorderable)
        implementation(projects.app.shared.placeholder)
        implementation(libs.filekit.dialogs)
        implementation(libs.filekit.dialogs.compose)
        implementation(libs.atomicfu)
        implementation(libs.aboutlibraries.compose.m3)
        implementation(projects.utils.selectorWorkflow)
    }
    sourceSets.commonTest.dependencies {
        implementation(libs.kotlinx.coroutines.test)
        implementation(projects.utils.uiTesting)
    }
    sourceSets.androidMain.dependencies {
        implementation(libs.androidx.appcompat)
    }
    sourceSets.desktopMain.dependencies {
    }
    sourceSets.getByName("jvmTest").dependencies {
        implementation(libs.slf4j.simple)
        implementation(libs.ktor.client.mock)
        implementation(libs.ktor.server.core)
        implementation(libs.ktor.server.test.host)
    }
}

compose.resources {
    packageOfResClass = "com.wynime.app.ui.settings"
}
