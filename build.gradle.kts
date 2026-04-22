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
val is120Series = minecraftVersion.startsWith("1.20.")
val minecraftPatch = when {
    minecraftVersion.startsWith("1.21.") -> minecraftVersion.substringAfter("1.21.", "0").substringBefore('-').toIntOrNull() ?: 0
    else -> 0
}
val is1210To1214 = minecraftVersion.startsWith("1.21.") && minecraftPatch in 0..4
val is1215To1217 = minecraftVersion.startsWith("1.21.") && minecraftPatch in 5..7
val needsLegacyMouseInputStub = minecraftVersion.startsWith("1.21.") && minecraftPatch < 11
val needsLegacyWorldRenderStateStub = minecraftVersion.startsWith("1.21.") && minecraftPatch < 9
val supportsFabricDedicatedServer = minecraftVersion == "1.21.11"

// Define Minecraft version range for fabric.mod.json
val minecraftVersionRange = when {
    is120Series -> ">=1.20 <1.21"
    is1210To1214 -> ">=1.21 <=1.21.4"
    is1215To1217 -> ">=1.21.5 <=1.21.7"
    else -> ">=1.21.8"
}
val fabricServerEntrypoint = if (supportsFabricDedicatedServer) {
    "axion.server.fabric.AxionFabricServerMod"
} else {
    "axion.server.fabric.AxionFabricServerStubMod"
}

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


// Configure version-specific compat source sets
// Each Minecraft version has its own compat directory with VersionCompatImpl/Init
when {
    is120Series -> {
        sourceSets.named("client") {
            kotlin.srcDir("src/compat-1_20_6/kotlin")
        }
    }
    is1210To1214 -> {
        sourceSets.named("client") {
            kotlin.srcDir("src/compat-1_21_4/kotlin")
        }
    }
    is1215To1217 -> {
        sourceSets.named("client") {
            kotlin.srcDir("src/compat-1_21_7/kotlin")
        }
    }
    else -> {
        // 1.21.8+: Use 1.21.11 compat files
        sourceSets.named("client") {
            kotlin.srcDir("src/compat-1_21_11/kotlin")
        }
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
    inputs.property("fabric_server_entrypoint", fabricServerEntrypoint)
    inputs.property("minecraft_version_range", minecraftVersionRange)

    filesMatching("fabric.mod.json") {
        expand(
            "version" to modVersion,
            "fabric_server_entrypoint" to fabricServerEntrypoint,
            "minecraft_version_range" to minecraftVersionRange,
        )
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
    dependsOn("compileClientKotlin")
    dependsOn("processClientResources")
    if (supportsFabricDedicatedServer) {
        dependsOn(":fabric-server:compileKotlin")
    }
    archiveFileName.set("Axion-v${modVersion}-mc${minecraftVersion}-dev.jar")
    from(layout.projectDirectory.dir("protocol/build/classes/kotlin/main"))
    if (supportsFabricDedicatedServer) {
        from(layout.projectDirectory.dir("fabric-server/build/classes/kotlin/main"))
    }
    from(sourceSets["client"].output)
    exclude("net/minecraft/client/input/MouseInput.class")
    exclude("net/minecraft/client/render/state/WorldRenderState.class")
}

tasks.named<AbstractArchiveTask>("remapJar") {
    archiveFileName.set("Axion-v${modVersion}-mc${minecraftVersion}.jar")
}

tasks.named<AbstractArchiveTask>("sourcesJar") {
    archiveFileName.set("Axion-v${modVersion}-mc${minecraftVersion}-sources.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
