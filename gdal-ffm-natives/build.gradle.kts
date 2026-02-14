import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar

plugins {
    `java-base`
    `maven-publish`
}

val nativeClassifiers = listOf(
    "linux-x86_64",
    "linux-aarch64",
    "osx-x86_64",
    "osx-aarch64",
    "windows-x86_64"
)

data class SwissProjRequirement(
    val label: String,
    val candidates: List<String>
)

val gdalSwissNativesEnabled = providers.gradleProperty("gdalSwissNativesEnabled")
    .map { raw ->
        when (raw.lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw GradleException(
                "Invalid value for gdalSwissNativesEnabled: '$raw' (expected true or false)"
            )
        }
    }
    .orElse(true)
    .get()

val swissProjRequirements = listOf(
    SwissProjRequirement("proj.db", listOf("proj.db")),
    SwissProjRequirement("CHENyx06a", listOf("CHENyx06a.gsb", "ch_swisstopo_CHENyx06a.tif")),
    SwissProjRequirement("CHENyx06_ETRS", listOf("CHENyx06_ETRS.gsb", "ch_swisstopo_CHENyx06_ETRS.tif")),
    SwissProjRequirement("egm96_15", listOf("egm96_15.gtx", "us_nga_egm96_15.tif"))
)

val swissProjAllowlistCandidates = swissProjRequirements
    .flatMap { it.candidates }
    .distinct()

fun String.toTaskSuffix(): String {
    return split('-', '_').joinToString("") { part ->
        part.replaceFirstChar { c -> c.uppercase() }
    }
}

val classifierJarTasks = nativeClassifiers.associateWith { classifier ->
    tasks.register<Jar>("nativesJar${classifier.toTaskSuffix()}") {
        group = LifecycleBasePlugin.BUILD_GROUP
        description = "Builds native bundle JAR for $classifier"
        archiveBaseName.set("gdal-ffm-natives")
        archiveClassifier.set("natives-$classifier")

        val classifierRoot = layout.projectDirectory.dir("src/main/resources/META-INF/gdal-native/$classifier")
        from(classifierRoot) {
            into("META-INF/gdal-native/$classifier")
        }
    }
}

val swissClassifierJarTasks: Map<String, TaskProvider<Jar>> = if (gdalSwissNativesEnabled) {
    nativeClassifiers.associateWith { classifier ->
        tasks.register<Jar>("nativesSwissJar${classifier.toTaskSuffix()}") {
            group = LifecycleBasePlugin.BUILD_GROUP
            description = "Builds Swiss native bundle JAR for $classifier"
            archiveBaseName.set("gdal-ffm-natives-swiss")
            archiveClassifier.set("natives-$classifier")

            val classifierRoot = layout.projectDirectory.dir("src/main/resources/META-INF/gdal-native/$classifier")
            val projRoot = classifierRoot.dir("share/proj")

            from(classifierRoot) {
                into("META-INF/gdal-native/$classifier")
                exclude("share/proj/**")
            }

            from(projRoot) {
                into("META-INF/gdal-native/$classifier/share/proj")
                include(*swissProjAllowlistCandidates.toTypedArray())
            }

            doFirst {
                val projDir = projRoot.asFile
                val stagedFiles = projDir.listFiles { file -> file.isFile }
                    ?.map { it.name }
                    ?.filterNot { it.startsWith(".") }
                    ?.toSet()
                    .orEmpty()

                if (stagedFiles.isEmpty()) {
                    return@doFirst
                }

                val missingGroups = swissProjRequirements.filter { requirement ->
                    requirement.candidates.none(stagedFiles::contains)
                }

                if (missingGroups.isNotEmpty()) {
                    val missingLabels = missingGroups.joinToString(", ") { requirement ->
                        "${requirement.label} [${requirement.candidates.joinToString(" | ")}]"
                    }

                    throw GradleException(
                        "Swiss PROJ subset for classifier '$classifier' is incomplete. " +
                            "Missing groups: $missingLabels. Available files in share/proj: " +
                            stagedFiles.sorted().joinToString(", ")
                    )
                }
            }
        }
    }
} else {
    emptyMap()
}

tasks.assemble {
    dependsOn(classifierJarTasks.values)
    if (gdalSwissNativesEnabled) {
        dependsOn(swissClassifierJarTasks.values)
    }
}

tasks.register("verifyNativeBundleLayout") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Checks that each classifier bundle has a manifest.json file"
    doLast {
        nativeClassifiers.forEach { classifier ->
            val manifest = layout.projectDirectory
                .file("src/main/resources/META-INF/gdal-native/$classifier/manifest.json")
                .asFile
            if (!manifest.isFile) {
                throw GradleException("Missing manifest.json for classifier: $classifier")
            }
        }
    }
}

tasks.check {
    dependsOn("verifyNativeBundleLayout")
}

publishing {
    publications {
        create<MavenPublication>("natives") {
            artifactId = "gdal-ffm-natives"

            classifierJarTasks.values.forEach { jarTaskProvider ->
                artifact(jarTaskProvider)
            }

            pom {
                name.set("gdal-ffm-natives")
                description.set("Bundled GDAL native libraries and runtime data per platform classifier")
            }
        }

        if (gdalSwissNativesEnabled) {
            create<MavenPublication>("nativesSwiss") {
                artifactId = "gdal-ffm-natives-swiss"

                swissClassifierJarTasks.values.forEach { jarTaskProvider ->
                    artifact(jarTaskProvider)
                }

                pom {
                    name.set("gdal-ffm-natives-swiss")
                    description.set("Bundled GDAL native libraries with Swiss-focused PROJ data subset")
                }
            }
        }
    }
    repositories {
        maven {
            name = "releaseTarget"
            val publishUrl = providers.gradleProperty("publishRepositoryUrl")
                .orElse(providers.environmentVariable("MAVEN_REPOSITORY_URL"))
                .orElse(layout.buildDirectory.dir("repo").map { it.asFile.absolutePath })
            url = uri(publishUrl.get())

            val publishUser = providers.gradleProperty("publishUsername")
                .orElse(providers.environmentVariable("MAVEN_USERNAME"))
                .orNull
            val publishPassword = providers.gradleProperty("publishPassword")
                .orElse(providers.environmentVariable("MAVEN_PASSWORD"))
                .orNull

            if (publishUser != null && publishPassword != null) {
                credentials {
                    username = publishUser
                    password = publishPassword
                }
            }
        }
    }
}
