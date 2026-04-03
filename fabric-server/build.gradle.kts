import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("fabric-loom") version "1.15.4"
    kotlin("jvm") version "2.3.10"
}

group = rootProject.group
version = rootProject.version

val minecraftVersion = rootProject.property("minecraft_version") as String
val modVersion = project.version.toString()

base {
    archivesName.set("axion-fabric-server")
}

repositories {
    maven("https://maven.fabricmc.net/")
    mavenCentral()
}

dependencies {
    implementation(project(":protocol"))
    minecraft("com.mojang:minecraft:${rootProject.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${rootProject.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${rootProject.property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${rootProject.property("fabric_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${rootProject.property("fabric_kotlin_version")}")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-jvm-default=no-compatibility")
    }
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("version", modVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to modVersion)
    }
}

tasks.jar {
    dependsOn(":protocol:compileKotlin")
    archiveFileName.set("AxionFabricServer-v${modVersion}-mc${minecraftVersion}-dev.jar")
    from(project(":protocol").layout.buildDirectory.dir("classes/kotlin/main"))
}

tasks.named<AbstractArchiveTask>("remapJar") {
    archiveFileName.set("AxionFabricServer-v${project.version}-mc${minecraftVersion}.jar")
}
