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
