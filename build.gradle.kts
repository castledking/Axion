import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("fabric-loom") version "1.15.4"
    kotlin("jvm") version "2.3.10"
}

version = property("mod_version") as String
group = property("maven_group") as String
val modVersion = version.toString()
val minecraftVersion = property("minecraft_version") as String
val minecraftPatch = minecraftVersion.substringAfter("1.21.", "0").substringBefore('-').toIntOrNull() ?: 0
val needsLegacyMouseInputStub = minecraftVersion.startsWith("1.21.") && minecraftPatch < 11
val needsLegacyWorldRenderStateStub = minecraftVersion.startsWith("1.21.") && minecraftPatch < 9

base {
    archivesName.set(property("archives_base_name") as String)
}

repositories {
    maven("https://maven.fabricmc.net/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.terraformersmc.com/releases/")
    mavenCentral()
}

loom {
    splitEnvironmentSourceSets()

    mods {
        create("axion") {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["client"])
        }
    }
}

if (needsLegacyMouseInputStub) {
    sourceSets.named("client") {
        java.srcDir("src/client-legacy-stubs/java")
    }
}

if (needsLegacyWorldRenderStateStub) {
    sourceSets.named("client") {
        java.srcDir("src/client-1_21_8-stubs/java")
    }
}

dependencies {
    implementation(project(":protocol"))
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    modCompileOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")
    modLocalRuntime("com.terraformersmc:modmenu:${property("modmenu_version")}")
    testImplementation(kotlin("test"))
}

tasks.processResources {
    doFirst {
        delete(layout.buildDirectory.dir("resources/main"))
    }
    inputs.property("version", modVersion)

    filesMatching("fabric.mod.json") {
        expand("version" to modVersion)
    }
}

tasks.named<ProcessResources>("processClientResources") {
    doFirst {
        delete(layout.buildDirectory.dir("resources/client"))
    }
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

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    dependsOn(":protocol:compileKotlin")
    archiveFileName.set("Axion-v${modVersion}-mc${minecraftVersion}-dev.jar")
    from(layout.projectDirectory.dir("protocol/build/classes/kotlin/main"))
    exclude("net/minecraft/client/input/MouseInput.class")
    exclude("net/minecraft/client/render/state/WorldRenderState.class")
}

tasks.named<AbstractArchiveTask>("remapJar") {
    archiveFileName.set("Axion-v${modVersion}-mc${minecraftVersion}.jar")
}

tasks.named<AbstractArchiveTask>("sourcesJar") {
    archiveFileName.set("Axion-v${modVersion}-mc${minecraftVersion}-sources.jar")
}
