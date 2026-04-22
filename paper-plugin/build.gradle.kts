plugins {
    kotlin("jvm") version "2.3.10"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
}

group = rootProject.group
version = rootProject.version
val minecraftVersion = rootProject.property("minecraft_version") as String
val isSupportedVersion = minecraftVersion in setOf("1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11")

base {
    archivesName.set("axion-plugin")
}

// Disable all tasks for unsupported versions
if (!isSupportedVersion) {
    tasks.withType<AbstractArchiveTask>().configureEach {
        enabled = false
    }
    tasks.withType<JavaCompile>().configureEach {
        enabled = false
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        enabled = false
    }
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
    mavenCentral()
}

dependencies {
    implementation(project(":protocol"))
    paperweight.paperDevBundle("${property("paper_version")}")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.processResources {
    inputs.property("version", project.version.toString())

    filesMatching("paper-plugin.yml") {
        expand("version" to project.version.toString())
    }
}

tasks.jar {
    archiveFileName.set("AxionPaper-v${project.version}-mc${minecraftVersion}.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
