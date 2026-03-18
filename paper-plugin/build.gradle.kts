plugins {
    kotlin("jvm") version "2.3.10"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
}

group = rootProject.group
version = rootProject.version

base {
    archivesName.set("axion-plugin")
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
    mavenCentral()
}

dependencies {
    implementation(project(":protocol"))
    compileOnly("com.github.retrooper:packetevents-spigot:2.11.2")
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
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
