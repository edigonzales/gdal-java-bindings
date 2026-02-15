plugins {
    `java-library`
    `maven-publish`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(23))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(23)
}

sourceSets {
    named("main") {
        java.srcDir("src/generated/java")
    }

    create("integrationTest") {
        java.srcDir("src/integrationTest/java")
        compileClasspath += sourceSets["main"].output + configurations["testRuntimeClasspath"]
        runtimeClasspath += output + compileClasspath
    }
}

configurations {
    named("integrationTestImplementation") {
        extendsFrom(configurations["testImplementation"])
    }
    named("integrationTestRuntimeOnly") {
        extendsFrom(configurations["testRuntimeOnly"])
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    "integrationTestImplementation"(platform("org.junit:junit-bom:5.11.4"))
    "integrationTestImplementation"("org.junit.jupiter:junit-jupiter")
    "integrationTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests that require bundled GDAL libraries."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    shouldRunAfter(tasks.test)
    onlyIf {
        System.getenv("GDAL_FFM_RUN_INTEGRATION") == "true"
    }
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.register<JavaExec>("smokeTest") {
    description = "Runs a GDAL translate smoke test using repository test data."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn("integrationTestClasses")

    val inputFile = layout.projectDirectory.file("src/integrationTest/resources/smoke/reclass.tif").asFile
    val outputFile = layout.buildDirectory.file("smoke-test-output/reclass-smoke.tif").get().asFile
    val nativesResources = project(":gdal-ffm-natives")
        .layout.projectDirectory
        .dir("src/main/resources")

    classpath = sourceSets["integrationTest"].runtimeClasspath + files(nativesResources)
    mainClass.set("ch.so.agi.gdal.ffm.GdalSmoke")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    inputs.file(inputFile)
    outputs.file(outputFile)
    outputs.upToDateWhen { false }

    doFirst {
        if (!inputFile.isFile) {
            throw GradleException(
                "Smoke test input is missing: ${inputFile.absolutePath}. " +
                    "Expected gdal-ffm-core/src/integrationTest/resources/smoke/reclass.tif."
            )
        }
        outputFile.parentFile.mkdirs()
        setArgs(listOf(outputFile.absolutePath, inputFile.absolutePath))
    }
}

tasks.register<JavaExec>("smokeTestPackagedNative") {
    description = "Runs a GDAL translate smoke test against a packaged native classifier JAR."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn("integrationTestClasses")

    val inputFile = layout.projectDirectory.file("src/integrationTest/resources/smoke/reclass.tif").asFile
    val smokeNativeJar = providers.gradleProperty("gdalFfmSmokeNativeJar")
    val smokeLabel = providers.gradleProperty("gdalFfmSmokeLabel")
        .map { label -> label.trim().ifEmpty { "packaged" } }
        .orElse("packaged")
    val outputFile = smokeLabel.flatMap { label ->
        layout.buildDirectory.file("smoke-test-output/${label}-reclass-smoke.tif")
    }
    val tmpDir = smokeLabel.flatMap { label ->
        layout.buildDirectory.dir("tmp/smoke/$label")
    }

    classpath = sourceSets["integrationTest"].runtimeClasspath
    mainClass.set("ch.so.agi.gdal.ffm.GdalSmoke")
    inputs.file(inputFile)
    inputs.property("gdalFfmSmokeNativeJar", smokeNativeJar.orNull ?: "")
    inputs.property("gdalFfmSmokeLabel", smokeLabel)
    outputs.file(outputFile)
    outputs.upToDateWhen { false }

    doFirst {
        val nativeJarPath = smokeNativeJar.orNull?.trim()
        if (nativeJarPath.isNullOrEmpty()) {
            throw GradleException(
                "Missing required property -PgdalFfmSmokeNativeJar=<path-to-native-jar> for smokeTestPackagedNative."
            )
        }
        val nativeJar = file(nativeJarPath)
        if (!nativeJar.isFile) {
            throw GradleException("Packaged smoke native JAR does not exist: ${nativeJar.absolutePath}")
        }
        if (!inputFile.isFile) {
            throw GradleException(
                "Smoke test input is missing: ${inputFile.absolutePath}. " +
                    "Expected gdal-ffm-core/src/integrationTest/resources/smoke/reclass.tif."
            )
        }

        val smokeOutputFile = outputFile.get().asFile
        val smokeTmpDir = tmpDir.get().asFile
        smokeOutputFile.parentFile.mkdirs()
        smokeTmpDir.mkdirs()

        classpath = sourceSets["integrationTest"].runtimeClasspath + files(nativeJar)
        jvmArgs(
            "--enable-native-access=ALL-UNNAMED",
            "-Djava.io.tmpdir=${smokeTmpDir.absolutePath}"
        )
        setArgs(listOf(smokeOutputFile.absolutePath, inputFile.absolutePath))
    }
}

tasks.check {
    dependsOn(integrationTest)
}

tasks.register<Exec>("generateFfmBindings") {
    group = "code generation"
    description = "Regenerates low-level FFM bindings using tools/jextract/regenerate.sh"
    workingDir = rootDir
    commandLine("bash", "tools/jextract/regenerate.sh")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "gdal-ffm-core"
            from(components["java"])
            pom {
                name.set("gdal-ffm-core")
                description.set("Java FFM core bindings and high-level API for GDAL utilities")
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
