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

tasks.assemble {
    dependsOn(classifierJarTasks.values)
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
