fun configureFlattenSourceSets() {
    val flatten = extra.runCatching { get("flatten.sourceset") }.getOrNull()?.toString()?.toBoolean() ?: true
    if (!flatten) return
    sourceSets {
        findByName("main")?.apply {
            resources.srcDirs(listOf(projectDir.resolve("resources")))
            java.srcDirs(listOf(projectDir.resolve("src")))
        }
        findByName("test")?.apply {
            resources.srcDirs(listOf(projectDir.resolve("testResources")))
            java.srcDirs(listOf(projectDir.resolve("test")))
        }
    }
}

fun Project.configureFlattenMppSourceSets() {
    kotlinSourceSets?.invoke {
        fun setForTarget(
            targetName: String,
        ) {
            findByName("${targetName}Main")?.apply {
                resources.srcDirs(listOf(projectDir.resolve("${targetName}Resources")))
                kotlin.srcDirs(listOf(projectDir.resolve("${targetName}Main"), projectDir.resolve(targetName)))
            }
            findByName("${targetName}Test")?.apply {
                resources.srcDirs(listOf(projectDir.resolve("${targetName}TestResources")))
                kotlin.srcDirs(listOf(projectDir.resolve("${targetName}Test")))
            }
        }

        setForTarget("common")

        allKotlinTargets().configureEach {
            val targetName = name
            setForTarget(targetName)
        }
    }
}

configureFlattenSourceSets()
configureFlattenMppSourceSets()