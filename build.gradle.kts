import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.language.jvm.tasks.ProcessResources
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import java.net.HttpURLConnection
import java.net.URL

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
val exactMc1216 = minecraftVersion == "1.21.6"
val exactMc1217 = minecraftVersion == "1.21.7"
val exactMc1218 = minecraftVersion == "1.21.8"
val exactMc1219 = minecraftVersion == "1.21.9"
val exactMc12110 = minecraftVersion == "1.21.10"
val javaTargetVersion = if (rangeMc261x) 25 else 21

if (rangeMc261x) {
    apply(plugin = "net.fabricmc.fabric-loom")
} else {
    apply(plugin = "fabric-loom")
}

val needsLegacyMouseInputStub = rangeMc1215 || rangeLegacy
val needsLegacyWorldRenderStateStub = rangeMc1215 || rangeLegacy
val supportsFabricDedicatedServer = minecraftVersion == "1.21.11"

// Define Minecraft version range for fabric.mod.json.
// build-axion.sh can override this for exact-version test artifacts while the
// release range artifacts keep their advertised multi-version metadata.
val minecraftVersionRange = (findProperty("axion_minecraft_version_range") as String?)?.trim()?.takeIf { it.isNotEmpty() } ?: when {
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

    // Custom run configurations
    runs {
        named("client") {
            configName = "Axion Client"
            runDir = (findProperty("axion_run_dir") as String?) ?: "run"
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
    } else if (exactMc1216) {
        kotlin.srcDir("src/compat-1_21_6/kotlin")
    } else if (exactMc1217) {
        kotlin.srcDir("src/compat-1_21_7/kotlin")
    } else if (exactMc1218) {
        kotlin.srcDir("src/compat-1_21_8/kotlin")
    } else if (exactMc1219) {
        kotlin.srcDir("src/compat-1_21_9/kotlin")
    } else if (exactMc12110) {
        kotlin.srcDir("src/compat-1_21_10/kotlin")
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
        // Force lifecycle-events to a version that includes EntityLoadData interface
        // (needed for Loom's interface injection in 26.1.2+)
        constraints {
            implementation("net.fabricmc.fabric-api:fabric-lifecycle-events-v1:4.1.0+6d50a0854c") {
                because("EntityLoadData was removed in 4.0.6 builds but is required for 26.1.2 interface injection")
            }
        }
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

    if (rangeMc1215) {
        exclude("assets/axion/shaders/core/preview_shell.*")
    }

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
}

tasks.named<ProcessResources>("processClientResources") {
    doFirst {
        delete(layout.buildDirectory.dir("resources/client"))
        // Copy version-specific mixin config
        val mixinConfigSource = when {
            rangeMc1215 -> "axion.client.mixins-1.21.5.json"
            else -> null
        }
        if (mixinConfigSource != null) {
            val sourceFile = file("src/client/resources/$mixinConfigSource")
            if (sourceFile.exists()) {
                copy {
                    from(sourceFile)
                    into(layout.buildDirectory.dir("resources/client"))
                    rename { "axion.client.mixins.json" }
                }
            }
        }
    }
    if (rangeMc261x) {
        filesMatching("axion.client.mixins.json") {
            filter { line ->
                if (line.contains("\"WorldRendererFallbackMixin\"")) null else line
            }
        }
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
val rangeFileTag = (findProperty("axion_artifact_tag") as String?)?.trim()?.takeIf { it.isNotEmpty() } ?: when {
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

// Create run tasks for each supported version
tasks.register("runClient1215") {
    group = "axion"
    description = "Run Minecraft 1.21.5 client with Axion"
    doLast {
        logger.lifecycle("Use Loom's generated runClient task with -Pminecraft_version=1.21.5.")
    }
}

tasks.register("runClient1217") {
    group = "axion"
    description = "Run Minecraft 1.21.7 client with Axion (legacy range)"
    doLast {
        logger.lifecycle("Use Loom's generated runClient task with -Pminecraft_version=1.21.7.")
    }
}

tasks.register("runClient12111") {
    group = "axion"
    description = "Run Minecraft 1.21.11 client with Axion (modern range)"
    doLast {
        logger.lifecycle("Use Loom's generated runClient task with -Pminecraft_version=1.21.11.")
    }
}

tasks.register("runClient261") {
    group = "axion"
    description = "Run Minecraft 26.1.x client with Axion"
    doLast {
        logger.lifecycle("Use Loom's generated runClient task with -Pminecraft_version=26.1.")
    }
}

// Unified run task that accepts a target version.
// Use -PtargetVersion=1.21.5 (NOT -Pversion, which clashes with Gradle's project.version).
tasks.register("runClientVersion") {
    group = "axion"
    description = "Run client for specified version (use -PtargetVersion=1.21.5,1.21.7,1.21.11,26.1)"
    val target = project.findProperty("targetVersion") as String? ?: "26.1"

    dependsOn(
        when (target) {
            "1.21.5" -> "runClient1215"
            "1.21.7" -> "runClient1217"
            "1.21.11" -> "runClient12111"
            "26.1" -> "runClient261"
            else -> throw IllegalArgumentException("Unsupported version: $target. Use 1.21.5, 1.21.7, 1.21.11, or 26.1")
        },
    )
    doLast {
        // The selected run task is wired through dependsOn above.
    }
}

// Paper server integration task
tasks.register("setupPaperServer") {
    group = "axion"
    description = "Setup Paper server for current Minecraft version"
    val version = project.findProperty("version") as String? ?: minecraftVersion
    val paperVersion = when (version) {
        "1.21.5" -> "1.21.5-R0.1-SNAPSHOT"
        "1.21.7" -> "1.21.7-R0.1-SNAPSHOT"
        "1.21.11" -> "1.21.11-R0.1-SNAPSHOT"
        "26.1" -> property("paper_version") as String
        else -> property("paper_version") as String
    }
    val rangeTag = when {
        version == "1.21.5" -> "mc1.21.5"
        version == "1.21.7" -> "mc1.21.6-1.21.8"
        version == "1.21.11" -> "mc1.21.9-1.21.11"
        version.startsWith("26.1") -> "mc26.1.x"
        else -> "mc${version}"
    }
    val runDir = file("run/paper/$version")
    val paperJar = runDir.resolve("paper-server.jar")
    val pluginsDir = runDir.resolve("plugins")

    doLast {
        println("Setting up Paper server for Minecraft $version (Paper $paperVersion)")

        // Create directories
        runDir.mkdirs()
        pluginsDir.mkdirs()

        // Download Paper server if not present
        if (!paperJar.exists()) {
            println("Downloading Paper server $paperVersion...")
            val paperUrl = when (version) {
                "1.21.5" -> "https://fill-data.papermc.io/v1/objects/2ae6ae22adf417699746e0f89fc2ef6cb6ee050a5f6608cee58f0535d60b509e/paper-1.21.5-114.jar"
                "1.21.7" -> "https://fill-data.papermc.io/v1/objects/83838188699cb2837e55b890fb1a1d39ad0710285ed633fbf9fc14e9f47ce078/paper-1.21.7-32.jar"
                "1.21.11" -> "https://fill-data.papermc.io/v1/objects/e708e8c132dc143ffd73528cccb9532e2eb17628b1a0eee74469bf466c7003f8/paper-1.21.11-116.jar"
                "26.1" -> "https://fill-data.papermc.io/v1/objects/b51d49a5f62446b7cfc01e6c29e48e0ce6abd35a783134aace1047b839b178ef/paper-26.1.2-63.jar"
                else -> throw IllegalArgumentException("Unknown version: $version")
            }

            paperJar.parentFile.mkdirs()
            paperJar.downloadTo(paperJar, paperUrl)
        } else {
            println("Paper server already downloaded: $paperJar")
        }

        // Copy AxionPaper plugin if built
        val pluginJar = file("paper-plugin/build/libs/${rangeTag}/AxionPaper-v${modVersion}-${rangeTag}.jar")
        if (pluginJar.exists()) {
            println("Copying AxionPaper plugin...")
            copy {
                from(pluginJar)
                into(pluginsDir)
            }
        } else {
            println("Warning: AxionPaper jar not found at $pluginJar")
            println("Run ./build-axion.sh to build the plugin first.")
        }

        // Create server.properties if not present
        val serverProps = runDir.resolve("server.properties")
        if (!serverProps.exists()) {
            val port = when (version) {
                "1.21.5" -> 25567
                "1.21.7" -> 25568
                "1.21.11" -> 25569
                "26.1" -> 25570
                else -> 25565
            }
            println("Creating server.properties (port $port)...")
            serverProps.writeText("""
                server-port=$port
                enable-rcon=false
                enable-command-block=true
                spawn-protection=0
                gamemode=creative
                difficulty=peaceful
                level-seed=axiontest
                max-players=10
                online-mode=false
            """.trimIndent())
        }

        // Create eula.txt if not present
        val eulaFile = runDir.resolve("eula.txt")
        if (!eulaFile.exists()) {
            println("Creating eula.txt (accepting EULA)...")
            eulaFile.writeText("eula=true")
        }

        println()
        println("Paper server setup complete!")
        println("Server directory: $runDir")
        println("To start the server, run: java -jar $paperJar nogui")
    }
}

// Helper function to download file
fun java.io.File.downloadTo(target: java.io.File, url: String) {
    target.outputStream().use { output ->
        URL(url).openStream().use { input ->
            input.copyTo(output)
        }
    }
}
