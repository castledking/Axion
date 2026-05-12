import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.language.jvm.tasks.ProcessResources
import net.fabricmc.loom.api.LoomGradleExtensionAPI

plugins {
    id("fabric-loom") apply false
    id("net.fabricmc.fabric-loom") apply false
    kotlin("jvm")
}

version = property("mod_version") as String
group = property("maven_group") as String
val modVersion = version.toString()
val minecraftVersion = property("minecraft_version") as String
val minecraftPatch = when {
    minecraftVersion.startsWith("1.21.") -> minecraftVersion.substringAfter("1.21.", "0").substringBefore('-').toIntOrNull() ?: 0
    else -> 0
}

// Two release ranges, mirroring Axiom's multi-version-jar approach:
//   - rangeLegacy:  1.21.6 .. 1.21.8  (compat-1_21_7, no MouseInput / WorldRenderState classes,
//                                      both stubs required at compile time)
//   - rangeModern:  1.21.9 .. 1.21.11 (compat-1_21_11, MouseInput + WorldRenderState exist,
//                                      no stubs required)
//
// Cross-version mixin compatibility within each range is handled by `require = 0`
// dual-signature injections in MouseMixin and WorldRendererFallbackMixin.
val rangeMc1215 = minecraftVersion == "1.21.5"
val rangeLegacy = minecraftVersion.startsWith("1.21.") && minecraftPatch in 6..8
val rangeModern = minecraftVersion.startsWith("1.21.") && minecraftPatch >= 9
val rangeMc261x = minecraftVersion.startsWith("26.1")
val javaTargetVersion = if (rangeMc261x) 25 else 21

if (rangeMc261x) {
    apply(plugin = "net.fabricmc.fabric-loom")
} else {
    apply(plugin = "fabric-loom")
}

val needsLegacyMouseInputStub = rangeMc1215 || rangeLegacy
val needsLegacyWorldRenderStateStub = rangeMc1215 || rangeLegacy
val supportsFabricDedicatedServer = minecraftVersion == "1.21.11"

// Define Minecraft version range for fabric.mod.json
val minecraftVersionRange = when {
    rangeMc1215 -> "1.21.5"
    rangeLegacy -> ">=1.21.6 <=1.21.8"
    rangeModern -> ">=1.21.9 <=1.21.11"
    rangeMc261x -> ">=26.1 <=26.1.2"
    else -> ">=1.21.5"
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

extensions.configure<LoomGradleExtensionAPI>("loom") {
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


// Two compat source sets corresponding to the two release ranges
sourceSets.named("client") {
    if (rangeMc1215) {
        kotlin.srcDir("src/compat-1_21_5/kotlin")
        kotlin.exclude("axion/client/render/gpu/**")
        kotlin.exclude("axion/client/render/AxionPreviewBuffer.kt")
    } else if (rangeLegacy) {
        // 1.21.5 .. 1.21.8: Codec-based NBT serialization, no MouseInput / WorldRenderState
        kotlin.srcDir("src/compat-1_21_7/kotlin")
    } else if (rangeMc261x) {
        // 26.1.x Fabric builds in the official namespace and uses the
        // compatibility aliases in src/compat-26_1.
        kotlin.srcDir("src/compat-26_1/kotlin")
    } else {
        // 1.21.9+: registry-manager-based serialization, has MouseInput / WorldRenderState
        kotlin.srcDir("src/compat-1_21_11/kotlin")
    }
}

if (rangeMc261x) {
    sourceSets.named("main") {
        kotlin.srcDir("src/compat-26_1/kotlin")
        kotlin.exclude("axion/client/**")
        kotlin.exclude("axion/mixin/**")
        kotlin.exclude("net/fabricmc/**")
        kotlin.exclude("net/minecraft/client/**")
        kotlin.exclude("net/minecraft/command/**")
        kotlin.exclude("net/minecraft/entity/**")
        kotlin.exclude("net/minecraft/network/**")
        kotlin.exclude("net/minecraft/registry/**")
        kotlin.exclude("net/minecraft/screen/**")
        kotlin.exclude("net/minecraft/server/**")
        kotlin.exclude("net/minecraft/sound/**")
        kotlin.exclude("net/minecraft/text/**")
        kotlin.exclude("net/minecraft/util/function/**")
        kotlin.exclude("net/minecraft/util/hit/**")
        kotlin.exclude("net/minecraft/util/math/random/**")
        kotlin.exclude("net/minecraft/util/shape/**")
        kotlin.exclude("net/minecraft/world/MoreAliases.kt")
        kotlin.exclude("net/minecraft/world/biome/**")
        kotlin.exclude("net/minecraft/world/chunk/**")
    }
}

dependencies {
    implementation(project(":protocol"))
    add("minecraft", "com.mojang:minecraft:${property("minecraft_version")}")
    val yarnMappings = (findProperty("yarn_mappings") as String?)?.trim().orEmpty()
    if (!rangeMc261x) {
        if (yarnMappings.isNotEmpty()) {
            add("mappings", "net.fabricmc:yarn:${yarnMappings}:v2")
        } else {
            add("mappings", extensions.getByType<LoomGradleExtensionAPI>().officialMojangMappings())
        }
    }
    if (rangeMc261x) {
        implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
        implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")
        implementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
        compileOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")
        runtimeOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")
    } else {
        add("modImplementation", "net.fabricmc:fabric-loader:${property("loader_version")}")
        add("modImplementation", "net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")
        add("modImplementation", "net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
        add("modCompileOnly", "com.terraformersmc:modmenu:${property("modmenu_version")}")
        add("modLocalRuntime", "com.terraformersmc:modmenu:${property("modmenu_version")}")
    }
    testImplementation(kotlin("test"))
}

tasks.processResources {
    doFirst {
        delete(layout.buildDirectory.dir("resources/main"))
    }
    inputs.property("version", modVersion)
    inputs.property("fabric_server_entrypoint", fabricServerEntrypoint)
    inputs.property("minecraft_version_range", minecraftVersionRange)
    inputs.property("loader_version", project.property("loader_version") as String)
    inputs.property("java_target_version", javaTargetVersion)
    inputs.property("fabric_kotlin_version", project.property("fabric_kotlin_version") as String)

    filesMatching("fabric.mod.json") {
        expand(
            "version" to modVersion,
            "fabric_server_entrypoint" to fabricServerEntrypoint,
            "minecraft_version_range" to minecraftVersionRange,
            "loader_version" to project.property("loader_version") as String,
            "java_target_version" to javaTargetVersion,
            "fabric_kotlin_version" to project.property("fabric_kotlin_version") as String,
        )
    }
    if (rangeMc1215) {
        exclude("assets/axion/shaders/core/preview_shell.*")
    }
}

tasks.named<ProcessResources>("processClientResources") {
    doFirst {
        delete(layout.buildDirectory.dir("resources/client"))
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

java {
    sourceCompatibility = JavaVersion.toVersion(javaTargetVersion)
    targetCompatibility = JavaVersion.toVersion(javaTargetVersion)
    withSourcesJar()
}

tasks.test {
    useJUnitPlatform()
}

// Range-style filename matching Axiom's release pattern, e.g. "mc1.21.9-1.21.11"
val rangeFileTag = when {
    rangeMc1215 -> "mc1.21.5"
    rangeLegacy -> "mc1.21.6-1.21.8"
    rangeModern -> "mc1.21.9-1.21.11"
    rangeMc261x -> "mc26.1.x"
    else -> "mc${minecraftVersion}"
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(":protocol:compileKotlin")
    dependsOn("compileClientKotlin")
    dependsOn("processClientResources")
    if (supportsFabricDedicatedServer) {
        dependsOn(":fabric-server:compileKotlin")
    }
    archiveFileName.set(
        if (rangeMc261x) {
            "Axion-v${modVersion}-${rangeFileTag}.jar"
        } else {
            "Axion-v${modVersion}-${rangeFileTag}-dev.jar"
        },
    )
    from(layout.projectDirectory.dir("protocol/build/classes/kotlin/main"))
    if (supportsFabricDedicatedServer) {
        from(layout.projectDirectory.dir("fabric-server/build/classes/kotlin/main"))
    }
    from(sourceSets["client"].output)
    exclude("net/minecraft/client/input/MouseInput.class")
    exclude("net/minecraft/client/render/state/WorldRenderState.class")
}

if (!rangeMc261x) {
    tasks.named<AbstractArchiveTask>("remapJar") {
        archiveFileName.set("Axion-v${modVersion}-${rangeFileTag}.jar")
    }
}

tasks.named<AbstractArchiveTask>("sourcesJar") {
    archiveFileName.set("Axion-v${modVersion}-${rangeFileTag}-sources.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
