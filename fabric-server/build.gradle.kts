import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import net.fabricmc.loom.api.LoomGradleExtensionAPI

plugins {
    id("fabric-loom") apply false
    id("net.fabricmc.fabric-loom") apply false
    kotlin("jvm")
}

group = rootProject.group
version = rootProject.version

val minecraftVersion = rootProject.property("minecraft_version") as String
val modVersion = project.version.toString()
val isSupportedVersion = minecraftVersion == "1.21.11"
val javaTargetVersion = if (minecraftVersion.startsWith("26.1")) 25 else 21

if (minecraftVersion.startsWith("26.1")) {
    apply(plugin = "net.fabricmc.fabric-loom")
} else {
    apply(plugin = "fabric-loom")
}

base {
    archivesName.set("axion-fabric-server")
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
    maven("https://maven.fabricmc.net/")
    mavenCentral()
}

dependencies {
    implementation(project(":protocol"))
    add("minecraft", "com.mojang:minecraft:${rootProject.property("minecraft_version")}")
    val yarnMappings = (rootProject.findProperty("yarn_mappings") as String?)?.trim().orEmpty()
    if (!minecraftVersion.startsWith("26.1")) {
        if (yarnMappings.isNotEmpty()) {
            add("mappings", "net.fabricmc:yarn:${yarnMappings}:v2")
        } else {
            add("mappings", extensions.getByType<LoomGradleExtensionAPI>().officialMojangMappings())
        }
    }
    if (minecraftVersion.startsWith("26.1")) {
        implementation("net.fabricmc:fabric-loader:${rootProject.property("loader_version")}")
        implementation("net.fabricmc.fabric-api:fabric-api:${rootProject.property("fabric_version")}")
        implementation("net.fabricmc:fabric-language-kotlin:${rootProject.property("fabric_kotlin_version")}")
    } else {
        add("modImplementation", "net.fabricmc:fabric-loader:${rootProject.property("loader_version")}")
        add("modImplementation", "net.fabricmc.fabric-api:fabric-api:${rootProject.property("fabric_version")}")
        add("modImplementation", "net.fabricmc:fabric-language-kotlin:${rootProject.property("fabric_kotlin_version")}")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaTargetVersion)
}

kotlin {
    jvmToolchain(javaTargetVersion)
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(javaTargetVersion.toString()))
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

if (!minecraftVersion.startsWith("26.1")) {
    tasks.named<AbstractArchiveTask>("remapJar") {
        archiveFileName.set("AxionFabricServer-v${project.version}-mc${minecraftVersion}.jar")
    }
}
