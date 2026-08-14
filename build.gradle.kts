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

// Release ranges:
//   - rangeMc12101: 1.21 .. 1.21.1    (compat-1_21_0_1, no MouseInput / WorldRenderState classes,
//                                      both stubs required at compile time)
//   - rangeMc12123: 1.21.2 .. 1.21.3  (compat-1_21_4, shared pre-1.21.5 client path)
//   - rangeMc1214:  1.21.4            (compat-1_21_4, isolated exact-version port)
//   - rangeMc1215:  1.21.5            (compat-1_21_5, isolated exact-version port)
//   - rangeLegacy:  1.21.6 .. 1.21.8  (compat-1_21_7, no MouseInput / WorldRenderState classes,
//                                      both stubs required at compile time)
//   - rangeModern:  1.21.9 .. 1.21.11 (compat-1_21_11, MouseInput + WorldRenderState exist,
//                                      no stubs required)
//
// Cross-version mixin compatibility within each range is handled by `require = 0`
// dual-signature injections in MouseMixin and WorldRendererFallbackMixin.
val rangeMc12101 = minecraftVersion == "1.21" || minecraftVersion == "1.21.1"
val rangeMc12123 = minecraftVersion == "1.21.2" || minecraftVersion == "1.21.3"
val rangeMc1214 = minecraftVersion == "1.21.4"
val rangeMc1215 = minecraftVersion == "1.21.5"
val rangeLegacy = minecraftVersion.startsWith("1.21.") && minecraftPatch in 6..8
val rangeModern = minecraftVersion.startsWith("1.21.") && minecraftPatch >= 9
val rangeMc261x = minecraftVersion.startsWith("26.1")
val rangeMc262x = minecraftVersion.startsWith("26.2")
// Both 26.x ranges build for Java 25 in the official (Mojang) namespace with no
// remap step, so they share the toolchain shape. They do not share a rendering
// API — 26.2 deleted MultiBufferSource outright — so they keep separate compat
// trees and ship as separate jars.
val rangeMc26x = rangeMc261x || rangeMc262x
val exactMc1216 = minecraftVersion == "1.21.6"
val exactMc1217 = minecraftVersion == "1.21.7"
val exactMc1218 = minecraftVersion == "1.21.8"
val exactMc1219 = minecraftVersion == "1.21.9"
val exactMc12110 = minecraftVersion == "1.21.10"
val javaTargetVersion = if (rangeMc26x) 25 else 21
val mc26CompatDir = if (rangeMc262x) "src/compat-26_2" else "src/compat-26_1"

if (rangeMc26x) {
    apply(plugin = "net.fabricmc.fabric-loom")
} else {
    apply(plugin = "fabric-loom")
}

val needsLegacyMouseInputStub = rangeMc12101 || rangeMc12123 || rangeMc1214 || rangeMc1215 || rangeLegacy
val needsLegacyWorldRenderStateStub = rangeMc12101 || rangeMc12123 || rangeMc1214 || rangeMc1215 || rangeLegacy
val supportsFabricDedicatedServer = minecraftVersion == "1.21.11"

// Define Minecraft version range for fabric.mod.json.
// build-axion.sh can override this for exact-version test artifacts while the
// release range artifacts keep their advertised multi-version metadata.
val minecraftVersionRange = (findProperty("axion_minecraft_version_range") as String?)?.trim()?.takeIf { it.isNotEmpty() } ?: when {
    rangeMc12101 -> ">=1.21 <=1.21.1"
    rangeMc12123 -> ">=1.21.2 <=1.21.3"
    rangeMc1215 -> "1.21.5"
    rangeMc1214 -> "1.21.4"
    rangeLegacy -> ">=1.21.6 <=1.21.8"
    rangeModern -> ">=1.21.9 <=1.21.11"
    rangeMc261x -> ">=26.1 <=26.1.2"
    // Open at the patch level so 26.2.1+ is picked up without a rebuild, but
    // closed before 26.3: 26.2 deleted MultiBufferSource and the whole
    // immediate-mode render surface relative to 26.1, so the next minor is
    // likely to break this jar the same way rather than run it.
    rangeMc262x -> ">=26.2 <26.3"
    else -> ">=1.21.5"
}
val loaderVersionRange = when {
    else -> ">=${project.property("loader_version")}"
}
val fabricApiVersionRange = when {
    else -> "*"
}
val fabricKotlinVersionRange = when {
    else -> ">=${project.property("fabric_kotlin_version")}"
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
    mavenLocal()
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

// 1.21-1.21.1 stubs for APIs that don't exist in these versions
if (rangeMc12101) {
    sourceSets.named("client") {
        java.srcDir("src/client-1_21_0_1-stubs/java")
    }
}

// Two compat source sets corresponding to the two release ranges
sourceSets.named("client") {
    if (rangeMc12101) {
        kotlin.srcDir("src/compat-1_21_0_1/kotlin")
        // PreviewBlockTessellator references BlockModelPart which does not exist in 1.21-1.21.1
        kotlin.exclude("axion/client/render/PreviewBlockTessellator.kt")

    } else if (rangeMc12123 || rangeMc1214) {
        kotlin.srcDir("src/compat-1_21_4/kotlin")
    } else if (rangeMc1215) {
        kotlin.srcDir("src/compat-1_21_5/kotlin")
    } else if (exactMc1216 || exactMc1217 || exactMc1218) {
        // 1.21.6, 1.21.7, 1.21.8 are byte-identical at the source level —
        // single shared compat folder, separate builds per MC version.
        kotlin.srcDir("src/compat-1_21_6_8/kotlin")
    } else if (exactMc1219 || exactMc12110) {
        // 1.21.9 and 1.21.10 are byte-identical at the source level.
        kotlin.srcDir("src/compat-1_21_9_10/kotlin")
    } else if (rangeMc26x) {
        // 26.x Fabric builds in the official namespace and uses the
        // compatibility aliases in src/compat-26_1 / src/compat-26_2.
        kotlin.srcDir("$mc26CompatDir/kotlin")
        // InGameHud does not exist in 26.x (HUD is HudElement-based)
        kotlin.exclude("axion/mixin/client/InGameHudMixin*")
        // onEntityCollision removed in 26.x — replaced by stepOn on Block
        kotlin.exclude("axion/mixin/client/AbstractPressurePlateBlockMixin*")
        kotlin.exclude("axion/mixin/client/CobwebBlockMixin*")
        kotlin.exclude("axion/mixin/client/TripwireBlockMixin*")

    } else {
        // 1.21.9+: registry-manager-based serialization, has MouseInput / WorldRenderState
        kotlin.srcDir("src/compat-1_21_11/kotlin")
    }
}

if (rangeMc26x) {
    sourceSets.named("main") {
        kotlin.srcDir("$mc26CompatDir/kotlin")
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
    if (!rangeMc26x) {
        if (yarnMappings.isNotEmpty()) {
            add("mappings", "net.fabricmc:yarn:${yarnMappings}:v2")
        } else {
            add("mappings", extensions.getByType<LoomGradleExtensionAPI>().officialMojangMappings())
        }
    }
    if (rangeMc26x) {
        implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
        implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")
        implementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
        compileOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")
        runtimeOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")
        if (rangeMc261x) {
            // Force lifecycle-events to a version that includes EntityLoadData interface
            // (needed for Loom's interface injection in 26.1.2+)
            constraints {
                implementation("net.fabricmc.fabric-api:fabric-lifecycle-events-v1:4.1.0+6d50a0854c") {
                    because("EntityLoadData was removed in 4.0.6 builds but is required for 26.1.2 interface injection")
                }
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
    inputs.property("loader_version_range", loaderVersionRange)
    inputs.property("java_target_version", javaTargetVersion)
    inputs.property("fabric_api_version_range", fabricApiVersionRange)
    inputs.property("fabric_kotlin_version_range", fabricKotlinVersionRange)

    if (rangeMc12101 || rangeMc12123 || rangeMc1214 || rangeMc1215) {
        exclude("assets/axion/shaders/core/preview_shell.*")
    }

    filesMatching("fabric.mod.json") {
        expand(
            "version" to modVersion,
            "fabric_server_entrypoint" to fabricServerEntrypoint,
            "minecraft_version_range" to minecraftVersionRange,
            "loader_version_range" to loaderVersionRange,
            "java_target_version" to javaTargetVersion,
            "fabric_api_version_range" to fabricApiVersionRange,
            "fabric_kotlin_version_range" to fabricKotlinVersionRange,
        )
    }
}

tasks.named<ProcessResources>("processClientResources") {
    inputs.property("minecraft_version", minecraftVersion)

    doFirst {
        delete(layout.buildDirectory.dir("resources/client"))
        // Copy version-specific mixin config
        val mixinConfigSource = when {
            rangeMc12101 || rangeMc12123 || rangeMc1214 || rangeMc1215 -> "axion.client.mixins-1.21.5.json"
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
    if (rangeMc26x) {
        doLast {
            val mixinConfig = layout.buildDirectory.file(
                "resources/client/axion.client.mixins.json",
            ).get().asFile
            val parsed = groovy.json.JsonSlurper().parse(mixinConfig) as Map<*, *>
            val normalized = linkedMapOf<String, Any?>()
            parsed.forEach { (key, value) -> normalized[key.toString()] = value }
            val excludedMixins = setOf(
                "InGameHudMixin",
                "WorldRendererFallbackMixin",
                "AbstractPressurePlateBlockMixin",
                "CobwebBlockMixin",
                "TripwireBlockMixin",
            )
            val clientMixins = normalized["client"] as? List<*>
                ?: throw GradleException("Mixin config has no client array: $mixinConfig")
            normalized["client"] = clientMixins.filterNot { it in excludedMixins }
            mixinConfig.writeText(
                groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(normalized)) + "\n",
            )
        }
    } else {
        filesMatching("axion.client.mixins.json") {
            filter { line ->
                when {
                    line.contains("\"GameRendererPostOutlineMixin\"") -> null
                    line.contains("\"GuiMixin\"") -> null
                    else -> line
                }
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

val verifyIntegratedNoClipWiring by tasks.registering {
    group = "verification"
    description = "Verifies that integrated-server no-clip authority is packaged for every supported version."
    dependsOn("compileClientKotlin", "processClientResources")

    doLast {
        val clientClasses = layout.buildDirectory.dir("classes/kotlin/client").get().asFile
        val mixinConfig = layout.buildDirectory.file("resources/client/axion.client.mixins.json").get().asFile
        val parsedMixinConfig = try {
            groovy.json.JsonSlurper().parse(mixinConfig) as Map<*, *>
        } catch (exception: Exception) {
            throw GradleException(
                "Processed mixin config is invalid for Minecraft $minecraftVersion: $mixinConfig",
                exception,
            )
        }
        val clientMixins = (parsedMixinConfig["client"] as? List<*>)
            ?.filterIsInstance<String>()
            ?: throw GradleException("Processed mixin config has no client array: $mixinConfig")
        if (rangeMc26x) {
            check("ServerEntityMixin" in clientMixins && "SodiumMoveSourceLevelSliceMixin" in clientMixins) {
                "Processed 26.x mixin config dropped a required client mixin: $mixinConfig"
            }
            check(
                clientMixins.none {
                    it in setOf(
                        "InGameHudMixin",
                        "WorldRendererFallbackMixin",
                        "AbstractPressurePlateBlockMixin",
                        "CobwebBlockMixin",
                        "TripwireBlockMixin",
                    )
                },
            ) {
                "Processed 26.x mixin config retained a legacy-only client mixin: $mixinConfig"
            }
        }
        val requiredClasses = listOf(
            "axion/client/compat/NoClipService.class",
            "axion/mixin/client/ServerEntityMixin.class",
        )

        requiredClasses.forEach { relativePath ->
            check(clientClasses.resolve(relativePath).isFile) {
                "Missing integrated-server no-clip class for Minecraft $minecraftVersion: $relativePath"
            }
        }
        check(mixinConfig.readText().contains("\"ServerEntityMixin\"")) {
            "Integrated-server no-clip mixin is not enabled for Minecraft $minecraftVersion"
        }

        if (rangeMc262x) {
            // 26.2 renamed the HUD class Gui -> Hud and handed the name Gui to
            // the new screen manager. @Mixin(Gui::class) therefore still
            // compiles and still resolves on 26.2 — it just binds the wrong
            // class, never applies, and leaves the vanilla hotbar drawn under
            // Axion's. Nothing else in the build can catch that.
            val hotbarMixin = file(
                "src/compat-26_2/kotlin/axion/mixin/client/GuiMixin.kt",
            ).readText()
            check("@Mixin(Hud::class)" in hotbarMixin) {
                "Minecraft $minecraftVersion hotbar mixin must target Hud; " +
                    "Gui is the screen manager there and the mixin would silently never apply"
            }
        }
    }
}

val gpuPreviewCompatDir = when {
    rangeMc12101 -> "src/compat-1_21_0_1"
    rangeMc12123 || rangeMc1214 -> "src/compat-1_21_4"
    rangeMc1215 -> "src/compat-1_21_5"
    rangeLegacy -> "src/compat-1_21_6_8"
    exactMc1219 || exactMc12110 -> "src/compat-1_21_9_10"
    rangeMc26x -> mc26CompatDir
    else -> "src/compat-1_21_11"
}

val allPreviewCompatDirs = listOf(
    "src/compat-1_21_0_1",
    "src/compat-1_21_4",
    "src/compat-1_21_5",
    "src/compat-1_21_6_8",
    "src/compat-1_21_9_10",
    "src/compat-1_21_11",
    "src/compat-26_1",
    "src/compat-26_2",
)

val verifyXraySelectionRenderingCoverage by tasks.registering {
    group = "verification"
    description = "Verifies selection outlines and symmetry anchors render through blocks in every compatibility branch."

    doLast {
        allPreviewCompatDirs.forEach { compatDir ->
            val pulsingCuboidRenderer = file(
                "$compatDir/kotlin/axion/client/render/PulsingCuboidRenderer.kt",
            ).readText()
            // The vanilla lines layer bakes a depth test into its pipeline, so
            // wrapping the draw in DepthRenderCompat cannot make the outline
            // show through terrain. Both entry points must emit their edges as
            // beams on the same no-depth fill layer the pulse uses.
            val selectionBoxBody = pulsingCuboidRenderer
                .substringAfter("fun renderSelectionBox(")
                .substringBefore("fun renderOutlineBox(")
            listOf(
                "renderXrayOutline(",
                "layer = fillLayer",
            ).forEach { requiredSource ->
                check(requiredSource in selectionBoxBody) {
                    "Selection outline can be hidden by terrain in $compatDir: missing $requiredSource"
                }
            }
            check("DepthRenderCompat.renderThroughBlocks(consumers, lineLayer)" !in selectionBoxBody) {
                "Selection outline in $compatDir still draws on the depth-tested lines layer"
            }
            val outlineBoxBody = pulsingCuboidRenderer
                .substringAfter("fun renderOutlineBox(")
                .substringBefore("fun renderXrayOutline(")
            listOf(
                "RenderLayerCompat.xrayQuads()",
                "renderXrayOutline(",
            ).forEach { requiredSource ->
                check(requiredSource in outlineBoxBody) {
                    "Outline-only selection can be hidden by terrain in $compatDir: missing $requiredSource"
                }
            }
            check("DepthRenderCompat.renderThroughBlocks(consumers, lineLayer)" !in outlineBoxBody) {
                "Outline-only selection in $compatDir still draws on the depth-tested lines layer"
            }

            val symmetryRenderer = file(
                "$compatDir/kotlin/axion/client/render/SymmetryGizmoRenderer.kt",
            ).readText()
            listOf(
                "SymmetryGizmoStylePolicy.color(",
                "rotationalEnabled = config.rotationalEnabled",
                "mirrorEnabled = config.mirrorEnabled",
                "DepthRenderCompat.renderThroughBlocks(consumers, fillLayer, lineLayer)",
            ).forEach { requiredSource ->
                check(requiredSource in symmetryRenderer) {
                    "Symmetry anchor styling/depth coverage is incomplete in $compatDir: missing $requiredSource"
                }
            }
        }
    }
}

val verifyPreviewVisualCoverage by tasks.registering {
    group = "verification"
    description = "Guards preview topology, transparency, pulse, and two-sided rendering in every compatibility branch."

    doLast {
        val visualPolicy = file(
            "src/client/kotlin/axion/client/render/PreviewVisualPolicy.kt",
        ).readText()
        check(
            "const val XRAY_BLOCK_PREVIEWS: Boolean = true" in visualPolicy ||
                "const val XRAY_BLOCK_PREVIEWS: Boolean = false" in visualPolicy,
        ) {
            "XRAY_BLOCK_PREVIEWS must be explicitly true or false"
        }

        listOf(
            "src/client/resources/axion.client.mixins.json",
            "src/client/resources/axion.client.mixins-1.21.5.json",
        ).forEach { mixinConfigPath ->
            check("\"PreviewCloudRendererMixin\"" !in file(mixinConfigPath).readText()) {
                "Cloud rendering must not be cancelled globally by $mixinConfigPath"
            }
        }

        allPreviewCompatDirs.forEach { compatDir ->
            val cloudMixin = file(
                "$compatDir/kotlin/axion/mixin/client/PreviewCloudRendererMixin.kt",
            )
            check(!cloudMixin.exists()) {
                "Cloud rendering is still cancelled globally by $cloudMixin"
            }

            val versionCompat = file(
                "$compatDir/kotlin/axion/client/compat/VersionCompatImpl.kt",
            ).readText()
            when (compatDir) {
                "src/compat-1_21_9_10", "src/compat-1_21_11" ->
                    check(".withDepthWrite(true)" in versionCompat) {
                        "Preview depth does not mask later cloud pixels in $compatDir"
                    }
                "src/compat-26_1", "src/compat-26_2" ->
                    check(
                        Regex("DepthStencilState\\(CompareOp\\.[A-Z_]+, true,").containsMatchIn(versionCompat),
                    ) {
                        "Preview depth does not mask later cloud pixels in $compatDir"
                    }
            }

            val pipelineFile = file(
                "$compatDir/kotlin/axion/client/render/BlockPreviewPipeline.kt",
            )
            val pipelineSource = pipelineFile.readText()
            val destinationGhostCall = pipelineSource
                .substringAfter("fun renderDestination(", missingDelimiterValue = "")
                .substringAfter("GhostBlockPreviewRenderer.render(", missingDelimiterValue = "")
                .substringBefore("return true", missingDelimiterValue = "")
            check("clipboard = scene.shellClipboard" in destinationGhostCall) {
                "Destination preview discards full occupancy before face culling in $pipelineFile"
            }
            check("fallbackClipboard = scene.fallbackGhostClipboard" in destinationGhostCall) {
                "Destination preview does not keep its surface-only emergency fallback in $pipelineFile"
            }

            val ghostFile = file(
                "$compatDir/kotlin/axion/client/render/GhostBlockPreviewRenderer.kt",
            )
            val ghostSource = ghostFile.readText()
            val normalizedRemoteIdentity =
                compatDir == "src/compat-26_1" || compatDir == "src/compat-26_2"
            val ghostOccupancySource = if (normalizedRemoteIdentity) {
                "fallbackCells = renderFallbackClipboard.nonAirCells()"
            } else {
                "fallbackCells = fallbackClipboard.nonAirCells()"
            }
            val chunkedClipboardSource = if (normalizedRemoteIdentity) {
                "renderClipboard,\n                    renderFallbackClipboard,\n                    origins"
            } else {
                "clipboard,\n                    fallbackClipboard,\n                    origins"
            }
            listOf(
                "fallbackClipboard: ClipboardBuffer",
                ghostOccupancySource,
                chunkedClipboardSource,
            ).forEach { requiredSource ->
                check(requiredSource in ghostSource) {
                    "Ghost preview loses its separate occupancy/surface inputs in $ghostFile: missing $requiredSource"
                }
            }

            val chunkedSessionFile = file(
                "$compatDir/kotlin/axion/client/render/gpu/ChunkedPreviewSession.kt",
            )
            val chunkedSessionSource = chunkedSessionFile.readText()
            listOf(
                "surfaceClipboard: ClipboardBuffer",
                "val surfaceCells = surfaceClipboard.nonAirCells()",
                "val stateHalo = PreviewStateHalo.retain(occupiedCells, surfaceCells)",
                "surfaceCells.forEach { cell ->",
                "stateHalo.forEach { cell ->",
            ).forEach { requiredSource ->
                check(requiredSource in chunkedSessionSource) {
                    "Chunked preview topology is incomplete in $chunkedSessionFile: missing $requiredSource"
                }
            }
            check(
                "private val occupancy" !in chunkedSessionSource &&
                    "ClipboardSelectionRenderer.surfaceClipboard" !in chunkedSessionSource
            ) {
                "Chunked preview in $chunkedSessionFile duplicates full occupancy or recomputes its supplied surface"
            }

            if (compatDir == "src/compat-26_1" || compatDir == "src/compat-26_2") {
                val fallbackCacheFile = file(
                    "$compatDir/kotlin/axion/client/render/ChunkedPreviewRegion.kt",
                )
                val fallbackCacheSource = fallbackCacheFile.readText()
                listOf(
                    "val surfaceClipboard: ClipboardBuffer",
                    "surfaceClipboard = surfaceClipboard",
                    "val stateHalo = PreviewStateHalo.retain(clipboard.nonAirCells(), surfaceCells)",
                    "stateHalo.forEach { cell ->",
                ).forEach { requiredSource ->
                    check(requiredSource in fallbackCacheSource) {
                        "26.x CPU fallback retains full interior state maps in $fallbackCacheFile: missing $requiredSource"
                    }
                }
                check("clipboard.nonAirCells().forEach" !in fallbackCacheSource) {
                    "26.x CPU fallback still expands every interior cell in $fallbackCacheFile"
                }
                check("surfaceClipboard = renderFallbackClipboard" in ghostSource) {
                    "26.x Ghost renderer drops the surface input before its CPU fallback in $ghostFile"
                }

                val occlusionCompatFile = file(
                    "$compatDir/kotlin/axion/client/render/gpu/PreviewOcclusionCompat.kt",
                )
                val occlusionCompatSource = occlusionCompatFile.readText()
                listOf(
                    "getOcclusionShape()",
                    "Shapes.joinIsNotEmpty(",
                    "BooleanOp.ONLY_FIRST",
                ).forEach { requiredSource ->
                    check(requiredSource in occlusionCompatSource) {
                        "26.x preview surface culling treats partial occluders as full cubes in " +
                            "$occlusionCompatFile: missing $requiredSource"
                    }
                }
                val fallbackTessellator = file(
                    "$compatDir/kotlin/axion/client/render/PreviewBlockTessellator.kt",
                ).readText()
                check("PreviewOcclusionPolicy.shouldReplaceNeighborWithAir(" in fallbackTessellator) {
                    "26.x CPU preview fallback exposes identical translucent neighbors in $compatDir"
                }
                check("PreviewOcclusionPolicy.shouldReplaceNeighborWithAir(" in chunkedSessionSource) {
                    "26.x chunked preview exposes identical translucent neighbors in $compatDir"
                }
            } else {
                val fallbackCacheFile = file(
                    "$compatDir/kotlin/axion/client/render/AxionPreviewMeshCache.kt",
                )
                val fallbackCacheSource = fallbackCacheFile.readText()
                listOf(
                    "val surfaceClipboard: ClipboardBuffer",
                    "maxBlocks / surfaceCells.size.coerceAtLeast(1)",
                    "val stateHalo = PreviewStateHalo.retain(occupiedCells, cellsToRender)",
                    "stateHalo.forEach { cell ->",
                ).forEach { requiredSource ->
                    check(requiredSource in fallbackCacheSource) {
                        "Legacy CPU fallback retains full interior state maps in $fallbackCacheFile: missing $requiredSource"
                    }
                }
                check(
                    "filterSurfaceCells" !in fallbackCacheSource &&
                        "occupiedCells.forEach { cell ->" !in fallbackCacheSource
                ) {
                    "Legacy CPU fallback still recomputes or expands full occupancy in $fallbackCacheFile"
                }

                val previewShellFile = file(
                    "$compatDir/kotlin/axion/client/render/PreviewShellBlockRenderer.kt",
                )
                val previewShellSource = previewShellFile.readText()
                check(
                    "surfaceClipboard: ClipboardBuffer" in previewShellSource &&
                        "surfaceClipboard = surfaceClipboard" in previewShellSource
                ) {
                    "Legacy shell fallback drops its supplied surface in $previewShellFile"
                }
                val blockTessellator = file(
                    "$compatDir/kotlin/axion/client/render/AxionBlockTessellator.kt",
                ).readText()
                check(
                    blockTessellator.split("PreviewOcclusionPolicy.shouldReplaceNeighborWithAir(").size - 1 >= 2
                ) {
                    "Legacy preview views expose identical translucent neighbors in $compatDir"
                }
            }

            val versionCompatFile = file(
                "$compatDir/kotlin/axion/client/compat/VersionCompatImpl.kt",
            )
            val versionCompatSource = versionCompatFile.readText()
            check(
                "surfaceClipboard: ClipboardBuffer" in versionCompatSource &&
                    "session.setFromClipboard(clipboard, surfaceClipboard, origins" in versionCompatSource
            ) {
                "Version bridge drops the preview surface/state-halo input in $versionCompatFile"
            }

            val placementFile = file(
                "$compatDir/kotlin/axion/client/render/PlacementPreviewRenderer.kt",
            )
            val placementSource = placementFile.readText()
            check(
                "detailedMovePreview && destinationSelectionClipboard.nonAirCells().size <=" in placementSource
            ) {
                "Placement preview budgets only its surface rather than full occupancy in $placementFile"
            }
            val destinationAlphaPolicy = "PreviewVisualPolicy.CULLED_DESTINATION_ALPHA"
            val moveDestinationAlphaPolicy = "PreviewVisualPolicy.CULLED_DESTINATION_ALPHA"
            val sparseDestinationAlphaPolicy = "PreviewVisualPolicy.CULLED_SPARSE_DESTINATION_ALPHA"
            val moveSourceAlphaPolicy = "PreviewVisualPolicy.CULLED_MOVE_SOURCE_ALPHA"
            listOf(
                destinationAlphaPolicy,
                moveDestinationAlphaPolicy,
                sparseDestinationAlphaPolicy,
                moveSourceAlphaPolicy,
            ).forEach { requiredSource ->
                check(requiredSource in placementSource) {
                    "Placement preview visual policy differs in $placementFile: missing $requiredSource"
                }
            }
            val repeatFile = file(
                "$compatDir/kotlin/axion/client/render/RepeatPreviewRenderer.kt",
            )
            val repeatSource = repeatFile.readText()
            listOf(
                "MAX_DESTINATION_OCCUPANCY_CELLS",
                "private fun isDestinationGhostWithinBudget(",
                "occupiedCellCount.toLong() * instanceCount.coerceAtLeast(0L)",
                "renderGhost = renderGhost",
            ).forEach { requiredSource ->
                check(requiredSource in repeatSource) {
                    "Repeat preview occupancy budget is incomplete in $repeatFile: missing $requiredSource"
                }
            }
            val clippedSmearBody = repeatSource
                .substringAfter("private fun renderClippedSmear(", missingDelimiterValue = "")
                .substringBefore("private fun renderStandardRepeat(", missingDelimiterValue = "")
            val clippedSmearBudgetIndex = clippedSmearBody.indexOf(
                "if (!isDestinationGhostWithinBudget(occupiedCellCount, preview.repeatCount.toLong()))",
            )
            val clippedSmearLayoutIndex = clippedSmearBody.indexOf("val layout = clippedSmearLayout(")
            check(clippedSmearBudgetIndex >= 0 && clippedSmearLayoutIndex > clippedSmearBudgetIndex) {
                "Clipped SMEAR builds its offset/candidate layout before applying the occupancy budget in $repeatFile"
            }
            check("PulsingCuboidRenderer.renderOutlineBox(" in clippedSmearBody) {
                "Over-budget clipped SMEAR drops its lightweight outline fallback in $repeatFile"
            }
            listOf(
                "val ghostClipboard = ClipboardSelectionRenderer.surfaceClipboard(selectionClipboard)",
                "clipboard = selectionClipboard",
                "fallbackClipboard = ghostClipboard",
            ).forEach { requiredSource ->
                check(requiredSource in clippedSmearBody) {
                    "Clipped SMEAR loses its full-occupancy/surface split in $repeatFile: missing $requiredSource"
                }
            }

            val repeatSegmentBody = repeatSource
                .substringAfter("private fun renderRepeatSegment(", missingDelimiterValue = "")
                .substringBefore("private fun isDestinationGhostWithinBudget(", missingDelimiterValue = "")
            val standardBudgetIndex = repeatSegmentBody.indexOf(
                "val renderGhost = isDestinationGhostWithinBudget(",
            )
            val surfaceBuildIndex = repeatSegmentBody.indexOf(
                "ClipboardSelectionRenderer.surfaceClipboard(selectionClipboard)",
            )
            val smearOffsetBuildIndex = repeatSegmentBody.indexOf(
                "RegionRepeatPlacementService.smearOffsets(step, repeatCount)",
            )
            val ghostOriginBuildIndex = repeatSegmentBody.indexOf("val baseGhostOrigins =")
            check(
                standardBudgetIndex >= 0 &&
                    surfaceBuildIndex > standardBudgetIndex &&
                    smearOffsetBuildIndex > standardBudgetIndex &&
                    ghostOriginBuildIndex > standardBudgetIndex
            ) {
                "Standard repeat materializes surfaces, offsets, or origins before applying the occupancy budget in $repeatFile"
            }
            check(
                "!renderGhost -> emptyList()" in repeatSegmentBody &&
                    "val ghostOrigins = if (!renderGhost)" in repeatSegmentBody
            ) {
                "Over-budget standard repeat still constructs destination origin lists in $repeatFile"
            }
            listOf(
                destinationAlphaPolicy,
                sparseDestinationAlphaPolicy,
            ).forEach { requiredSource ->
                check(requiredSource in repeatSource) {
                    "Repeat preview visual policy differs in $repeatFile: missing $requiredSource"
                }
            }

            val pulsingFile = file(
                "$compatDir/kotlin/axion/client/render/PulsingCuboidRenderer.kt",
            )
            val pulsingSource = pulsingFile.readText()
            val selectionBoxBody = pulsingSource
                .substringAfter("fun renderSelectionBox(")
                .substringBefore("fun renderOutlineBox(")
            check("PreviewVisualPolicy.pulseAlpha(" in selectionBoxBody) {
                "Placement pulse can disappear at its color crossover in $pulsingFile"
            }
            val emitQuadBody = pulsingSource
                .substringAfter("private fun emitQuad(", missingDelimiterValue = "")
                .substringBefore("private fun emitTriangles(", missingDelimiterValue = "")
            val emitTrianglesBody = pulsingSource
                .substringAfter("private fun emitTriangles(", missingDelimiterValue = "")
                .substringBefore("private fun emitTriangle(", missingDelimiterValue = "")
            check("-normalX" !in emitQuadBody && "-normalX" !in emitTrianglesBody) {
                "Filled boxes unconditionally emit reverse windings and compound opacity in $pulsingFile"
            }
            val reverseWindingBody = pulsingSource
                .substringAfter("if (RenderLayerCompat.requiresReverseWinding(layer)) {", missingDelimiterValue = "")
                .substringBefore("\n        }\n    }\n\n    private fun emitFace", missingDelimiterValue = "")
            check(reverseWindingBody.split("emitFace(").size - 1 == 6) {
                "Cull-enabled filled boxes are not two-sided in $pulsingFile"
            }

            val selectionStateFile = file(
                "$compatDir/kotlin/axion/client/render/SelectionStateRenderer.kt",
            )
            val idleSelectionBody = selectionStateFile.readText()
                .substringAfter("SelectionState.Idle -> {", missingDelimiterValue = "")
                .substringBefore("is SelectionState.FirstCornerSet", missingDelimiterValue = "")
            listOf(
                "pendingMagicSelection.region.minCorner()",
                "pendingMagicSelection.clipboardBuffer",
                "sparse = true",
            ).forEach { requiredSource ->
                check(requiredSource in idleSelectionBody) {
                    "First Magic Select result is not routed to the renderer in $selectionStateFile: missing $requiredSource"
                }
            }
        }

        val haloSource = file(
            "src/client/kotlin/axion/client/render/gpu/PreviewStateHalo.kt",
        ).readText()
        listOf(
            "BlockPos.asLong(x - 1, y, z)",
            "BlockPos.asLong(x + 1, y, z)",
            "BlockPos.asLong(x, y - 1, z)",
            "BlockPos.asLong(x, y + 1, z)",
            "BlockPos.asLong(x, y, z - 1)",
            "BlockPos.asLong(x, y, z + 1)",
        ).forEach { requiredNeighbor ->
            check(requiredNeighbor in haloSource) {
                "Preview state halo no longer retains every direct surface neighbor: missing $requiredNeighbor"
            }
        }

        val topologySource = file(
            "src/client/kotlin/axion/client/render/PreviewSurfaceTopology.kt",
        ).readText()
        check(
            "retainBoundaryCells(" in topologySource &&
                "retainBoundaryOffsets(" in topologySource
        ) {
            "Translucent source previews have no occupancy-only boundary extractor"
        }

        listOf(
            "src/compat-1_21_9_10",
            "src/compat-1_21_11",
            "src/compat-26_1",
            "src/compat-26_2",
        ).forEach { compatDir ->
            val versionCompat = file(
                "$compatDir/kotlin/axion/client/compat/VersionCompatImpl.kt",
            ).readText()
            val previewPipeline = versionCompat
                .substringAfter("fun getPreviewShellPipeline(")
                .substringBefore("fun playSoundClient(")
            check(
                ".withCull(PreviewVisualPolicy.CULL_GHOST_BACK_FACES)" in previewPipeline &&
                    ".withCull(true)" !in previewPipeline
            ) {
                "Ghost shell is view-angle dependent in $compatDir because back faces are culled"
            }
        }

        listOf("src/compat-26_1", "src/compat-26_2").forEach { compatDir ->
            val versionCompatFile = file(
                "$compatDir/kotlin/axion/client/compat/VersionCompatImpl.kt",
            )
            val versionCompat = versionCompatFile.readText()
            val bufferedLayer = versionCompat
                .substringAfter("fun getBufferedPreviewShellLayer(", missingDelimiterValue = "")
                .substringBefore("fun playSoundClient(", missingDelimiterValue = "")
            listOf(
                "if (ShaderPackCompat.isShaderPackActive()) return baseLayer",
                "getPreviewShellPipeline(",
                ".withTexture(\"Sampler0\", TextureAtlas.LOCATION_BLOCKS, blockAtlasSampler)",
                ".sortOnUpload()",
                "RenderLayerCompat.createPipelineLayer(",
            ).forEach { requiredSource ->
                check(requiredSource in bufferedLayer) {
                    "Buffered preview fallback loses its pipeline/atlas contract in $versionCompatFile: " +
                        "missing $requiredSource"
                }
            }

            listOf(
                "$compatDir/kotlin/axion/client/render/PreviewBlockTessellator.kt",
                "$compatDir/kotlin/axion/client/render/gpu/ChunkedPreviewSession.kt",
            ).forEach { fallbackPath ->
                val fallbackFile = file(fallbackPath)
                val fallbackSource = fallbackFile.readText()
                check("VersionCompatImpl.getBufferedPreviewShellLayer(" in fallbackSource) {
                    "CPU textured fallback bypasses the no-cull preview layer in $fallbackFile"
                }
                check(
                    "getBuffer(RenderLayerCompat.blockTranslucentCull())" !in fallbackSource
                ) {
                    "CPU textured fallback still draws through a depth-writing culled layer in $fallbackFile"
                }
            }

            // A destination ghost's alpha is solved for an exact number of shell
            // crossings, so a translucent block's texel alpha must not compound
            // into it. That used to be enforced by rewriting every cell to
            // opaque grey concrete on Paper, which cost the player all texture
            // information; the shell shader now drops texel alpha instead.
            val ghostRenderer = file(
                "$compatDir/kotlin/axion/client/render/GhostBlockPreviewRenderer.kt",
            ).readText()
            check("PreviewVisualPolicy.ignoresTextureAlpha(" in ghostRenderer) {
                "Destination preview in $compatDir can multiply its opacity by stained-glass texture alpha"
            }
            check("PreviewBlockIdentityPolicy.normalize(" !in ghostRenderer) {
                "Destination preview in $compatDir is back to hiding real block identities behind neutral concrete"
            }
        }

        val renderLayerCompat = file(
            "src/client/kotlin/axion/client/render/RenderLayerCompat.kt",
        ).readText()
        val pipelineLayerFactory = renderLayerCompat
            .substringAfter("fun createPipelineLayer(", missingDelimiterValue = "")
            .substringBefore("private val renderLayerFactoryClasses", missingDelimiterValue = "")
        listOf(
            "RenderLayer::class.java.declaredMethods.firstOrNull",
            "method.parameterCount == 2",
            "factory.isAccessible = true",
            "factory.invoke(null, name, renderSetup) as RenderLayer",
        ).forEach { requiredSource ->
            check(requiredSource in pipelineLayerFactory) {
                "Buffered preview relies on Loom-only RenderLayer factory visibility: missing $requiredSource"
            }
        }

        val previewFragmentShader = file(
            "src/main/resources/assets/axion/shaders/core/preview_shell.fsh",
        ).readText()
        check(
            "if (texColor.a < 0.1)" in previewFragmentShader &&
                "if (color.a < 0.1)" !in previewFragmentShader
        ) {
            "Preview shader alpha-cutout must not discard low-alpha CPU fallback vertices"
        }
        check(
            "#ifdef IGNORE_TEXTURE_ALPHA" in previewFragmentShader &&
                "color.a = vertexColor.a * ColorModulator.a;" in previewFragmentShader
        ) {
            "Preview shader cannot separate a ghost's policy alpha from the sampled texel alpha"
        }

        val preview261 = file(
            "src/compat-26_1/kotlin/axion/client/compat/VersionCompatImpl.kt",
        ).readText()
        val depthPolicy261 = preview261
            .substringAfter("private val previewDepthState =", missingDelimiterValue = "")
            .substringBefore("private val bufferedPreviewShellLayers", missingDelimiterValue = "")
        listOf(
            "if (PreviewVisualPolicy.XRAY_BLOCK_PREVIEWS)",
            "DepthStencilState(CompareOp.ALWAYS_PASS, true, 0.0f, 0.0f)",
            "DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true, -1.0f, -1.0f)",
        ).forEach { requiredSource ->
            check(requiredSource in depthPolicy261) {
                "26.1 preview depth policy is incomplete: missing $requiredSource"
            }
        }
        check(".withDepthStencilState(previewDepthState)" in preview261) {
            "26.1 preview pipeline bypasses the shared x-ray/scene-depth policy"
        }
        val drawer261 = file(
            "src/compat-26_1/kotlin/axion/client/render/gpu/AxionPreviewBlockDrawer.kt",
        ).readText()
        check("_polygonOffset(" !in drawer261 && "_enablePolygonOffset(" !in drawer261) {
            "26.1 preview visibility must not depend on mutable OpenGL state"
        }

        val preview262 = file(
            "src/compat-26_2/kotlin/axion/client/compat/VersionCompatImpl.kt",
        ).readText()
        val depthPolicy262 = preview262
            .substringAfter("private val previewDepthState =", missingDelimiterValue = "")
            .substringBefore("private val previewShellPipelines", missingDelimiterValue = "")
        listOf(
            "if (PreviewVisualPolicy.XRAY_BLOCK_PREVIEWS)",
            "DepthStencilState(CompareOp.ALWAYS_PASS, true, 0.0f, 0.0f)",
            "DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true, 1.0f, 1.0f)",
        ).forEach { requiredSource ->
            check(requiredSource in depthPolicy262) {
                "26.2 reversed-depth preview policy is incomplete: missing $requiredSource"
            }
        }
        check(".withDepthStencilState(previewDepthState)" in preview262) {
            "26.2 preview pipeline bypasses the shared x-ray/scene-depth policy"
        }

        listOf("src/compat-1_21_9_10", "src/compat-1_21_11").forEach { compatDir ->
            val versionCompat = file(
                "$compatDir/kotlin/axion/client/compat/VersionCompatImpl.kt",
            ).readText()
            val depthPolicy = versionCompat
                .substringAfter("private val previewDepthTest =", missingDelimiterValue = "")
                .substringBefore("fun getPreviewShellPipeline", missingDelimiterValue = "")
            listOf(
                "if (PreviewVisualPolicy.XRAY_BLOCK_PREVIEWS)",
                "DepthTestFunction.NO_DEPTH_TEST",
                "DepthTestFunction.LEQUAL_DEPTH_TEST",
            ).forEach { requiredSource ->
                check(requiredSource in depthPolicy) {
                    "Modern preview depth policy is incomplete in $compatDir: missing $requiredSource"
                }
            }
            check(".withDepthTestFunction(previewDepthTest)" in versionCompat) {
                "Modern preview pipeline bypasses the shared depth policy in $compatDir"
            }
        }

        mapOf(
            "src/compat-1_21_0_1" to "val layer = RenderLayerCompat.blockTranslucentCull()",
            "src/compat-1_21_4" to "val layer = RenderLayerCompat.blockTranslucentCull()",
            "src/compat-1_21_5" to "pass.setPipeline(RenderPipelines.RENDERTYPE_TRANSLUCENT_MOVING_BLOCK)",
            "src/compat-1_21_6_8" to "return RenderPipelines.RENDERTYPE_TRANSLUCENT_MOVING_BLOCK",
        ).forEach { (compatDir, requiredDepthRoute) ->
            val depthRouteSource = if (compatDir == "src/compat-1_21_6_8") {
                file("$compatDir/kotlin/axion/client/compat/VersionCompatImpl.kt").readText()
            } else {
                file("$compatDir/kotlin/axion/client/render/gpu/AxionPreviewBlockDrawer.kt").readText()
            }
            check(requiredDepthRoute in depthRouteSource) {
                "Legacy preview no longer uses its depth-tested vanilla route in $compatDir"
            }
        }

        // 26.x draws previews immediately from the world render context,
        // using the live context pose matrix and the framebuffer depth.
        listOf("src/compat-26_1", "src/compat-26_2").forEach { compatDir ->
            val sessionFile = file(
                "$compatDir/kotlin/axion/client/render/gpu/ChunkedPreviewSession.kt",
            )
            val session = sessionFile.readText()
            listOf(
                "camera.rotation().conjugate(Quaternionf())",
                "RenderSystem.getProjectionMatrixBuffer()",
                "client.framebuffer.depthTextureView",
            ).forEach { requiredSource ->
                check(requiredSource in session) {
                    "26.x preview matrix/depth capture is missing in " +
                        "$sessionFile: $requiredSource"
                }
            }

            val lifecycleFile = file(
                "$compatDir/kotlin/axion/client/render/gpu/ChunkedPreviewLifecycle.kt",
            )
            val lifecycle = lifecycleFile.readText()
            listOf(
                "val projection: GpuBufferSlice",
                "fun enqueuePostWorldDraw(",
                "fun captureSceneDepthBeforeHand()",
                "preservedSceneDepth.capture(MinecraftClient.getInstance().framebuffer)",
                "queuedSceneDepth: GpuTextureView?",
                "fun flushPostWorldDraws()",
            ).forEach { requiredSource ->
                check(requiredSource in lifecycle) {
                    "26.x post-world preview queue is incomplete in $lifecycleFile: missing $requiredSource"
                }
            }

            val drawerFile = file(
                "$compatDir/kotlin/axion/client/render/gpu/AxionPreviewBlockDrawer.kt",
            )
            val drawer = drawerFile.readText()
            listOf(
                "val baseMv = Matrix4f(baseModelView)",
                "pass.setUniform(\"Projection\", projection)",
                "sceneDepth: GpuTextureView",
            ).forEach { requiredSource ->
                check(requiredSource in drawer) {
                    "26.x post-outline preview loses a captured world uniform in " +
                        "$drawerFile: missing $requiredSource"
                }
            }
            check("mainTarget.depthTextureView" !in drawer) {
                "26.x late preview reattaches main depth after vanilla has cleared it in $drawerFile"
            }

            val preservedDepthFile = file(
                "$compatDir/kotlin/axion/client/render/gpu/PreservedSceneDepth.kt",
            )
            val preservedDepth = preservedDepthFile.readText()
            listOf(
                "GpuTexture.USAGE_COPY_DST",
                "GpuTexture.USAGE_RENDER_ATTACHMENT",
                "sourceTexture.getWidth(0)",
                "sourceTexture.getHeight(0)",
                "copyTextureToTexture(",
                "textureView?.close()",
                "texture?.close()",
            ).forEach { requiredSource ->
                check(requiredSource in preservedDepth) {
                    "26.x preserved scene-depth resource is incomplete in " +
                        "$preservedDepthFile: missing $requiredSource"
                }
            }

            val mixinFile = file(
                "$compatDir/kotlin/axion/mixin/client/GameRendererPostOutlineMixin.kt",
            )
            check(mixinFile.isFile) {
                "26.x has no post-entity-outline preview hook: $mixinFile"
            }
            val mixin = mixinFile.readText()
            listOf(
                "renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
                "CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V",
                "shift = At.Shift.BEFORE",
                "ChunkedPreviewLifecycle.captureSceneDepthBeforeHand()",
                "LevelRenderer;doEntityOutline()V",
                "shift = At.Shift.AFTER",
                "ChunkedPreviewLifecycle.flushPostWorldDraws()",
            ).forEach { requiredSource ->
                check(requiredSource in mixin) {
                    "26.x post-entity-outline preview hook is incomplete in " +
                        "$mixinFile: missing $requiredSource"
                }
            }
        }
        check("\"GameRendererPostOutlineMixin\"" in file("src/client/resources/axion.client.mixins.json").readText()) {
            "26.x post-entity-outline preview mixin is not enabled"
        }
        check(
            "line.contains(\"\\\"GameRendererPostOutlineMixin\\\"\") -> null" in
                file("build.gradle.kts").readText()
        ) {
            "The 26.x-only post-outline mixin is not filtered out of legacy jars"
        }

        val normalizedReloadInvalidators = mutableSetOf<String>()
        listOf("src/compat-26_1", "src/compat-26_2").forEach { compatDir ->
            val reloadInvalidatorFile = file(
                "$compatDir/kotlin/axion/client/compat/PreviewResourceReloadInvalidation.kt",
            )
            check(reloadInvalidatorFile.isFile) {
                "26.x preview meshes are not invalidated after resource/model reloads in $compatDir"
            }
            val reloadInvalidator = reloadInvalidatorFile.readText()
            listOf(
                "ResourceLoader.get(PackType.CLIENT_RESOURCES)",
                "ResourceManagerReloadListener {",
                "ChunkedPreviewLifecycle.closeAll()",
                "AxionPreviewBlockDrawer.resetFailureState()",
                "resourceLoader.addListenerOrdering(ResourceReloaderKeys.AFTER_VANILLA, listenerId)",
                "registered.compareAndSet(false, true)",
            ).forEach { requiredSource ->
                check(requiredSource in reloadInvalidator) {
                    "26.x preview resource-reload invalidation is incomplete in " +
                        "$reloadInvalidatorFile: missing $requiredSource"
                }
            }
            val closeIndex = reloadInvalidator.indexOf("ChunkedPreviewLifecycle.closeAll()")
            val resetIndex = reloadInvalidator.indexOf("AxionPreviewBlockDrawer.resetFailureState()")
            check(closeIndex >= 0 && resetIndex > closeIndex) {
                "26.x resource reload must close stale meshes before re-enabling the GPU drawer in $reloadInvalidatorFile"
            }
            normalizedReloadInvalidators += reloadInvalidator
                .replace(Regex("\\s+"), " ")
                .trim()

            val versionInitFile = file(
                "$compatDir/kotlin/axion/client/compat/VersionCompatInit.kt",
            )
            check("PreviewResourceReloadInvalidation.register()" in versionInitFile.readText()) {
                "26.x resource-reload invalidation is never registered in $versionInitFile"
            }
        }
        check(normalizedReloadInvalidators.size == 1) {
            "26.1 and 26.2 resource-reload invalidation contracts have diverged"
        }
    }
}

val verifyMagicSelectFirstRenderCoverage by tasks.registering {
    group = "verification"
    description = "Guards the 26.x small Magic Select outline path from delayed/off-screen rendering."

    doLast {
        val outlineCompat = file(
            "src/client/kotlin/axion/client/render/VertexRenderingCompat.kt",
        ).readText()
        val coordinateTransform = outlineCompat
            .substringAfter("fun outlineCoordinate(")
            .substringBefore("fun drawFilledBox(")
        check("coordinate + offset" in coordinateTransform && "coordinate - offset" !in coordinateTransform) {
            "Manual small-selection outlines apply the camera offset with the wrong sign"
        }

        listOf("src/compat-26_1", "src/compat-26_2").forEach { compatDir ->
            val selectionRenderer = file(
                "$compatDir/kotlin/axion/client/render/ClipboardSelectionRenderer.kt",
            ).readText()
            check("MAX_SINGLE_SHAPE_UNION_CELLS: Int = 0" in selectionRenderer) {
                "Small Magic Select outlines in $compatDir still use the version-sensitive merged-shape route"
            }
            check("computeBoundaryEdges(clipboard)" in selectionRenderer) {
                "Magic Select in $compatDir lacks its immediate explicit-boundary outline route"
            }
            val boundaryCall = selectionRenderer
                .substringAfter("is ComponentOutline.BoundaryEdges -> {", missingDelimiterValue = "")
                .substringBefore("\n                            }", missingDelimiterValue = "")
            listOf(
                "cameraX = cameraPos.x",
                "cameraY = cameraPos.y",
                "cameraZ = cameraPos.z",
            ).forEach { requiredCameraArg ->
                check(requiredCameraArg in boundaryCall) {
                    "Magic Select boundary call has the wrong camera convention in $compatDir: missing $requiredCameraArg"
                }
            }
            val boundaryRenderer = selectionRenderer
                .substringAfter("private fun renderBoundaryEdges(")
                .substringBefore("private fun emitLineVertex(")
            listOf(
                "originX + edge.x1 - cameraX",
                "originY + edge.y1 - cameraY",
                "originZ + edge.z1 - cameraZ",
                "originX + edge.x2 - cameraX",
                "originY + edge.y2 - cameraY",
                "originZ + edge.z2 - cameraZ",
            ).forEach { requiredTransform ->
                check(requiredTransform in boundaryRenderer) {
                    "Magic Select boundary coordinates are not camera-relative in $compatDir: missing $requiredTransform"
                }
            }
        }
    }
}

val verifyFabricServerRangeCompatibility by tasks.registering {
    group = "verification"
    description = "Guards the 1.21.9-1.21.11 Fabric server bundle from 1.21.11-only permission APIs."

    doLast {
        val networkingSource = file(
            "fabric-server/src/main/kotlin/axion/server/fabric/AxionFabricServerNetworking.kt",
        ).readText()
        check("GameModeCommand.PERMISSION_CHECK" !in networkingSource && "player.permissions" !in networkingSource) {
            "Fabric game-mode permissions must remain binary-compatible with Minecraft 1.21.9-1.21.11"
        }
        listOf(
            "PlayerConfigEntry(player.gameProfile)",
            "server.playerManager.isOperator(",
        ).forEach { requiredSource ->
            check(requiredSource in networkingSource) {
                "Fabric game-mode permission wiring is missing $requiredSource"
            }
        }
    }
}

val verifyMoveSourceReplacementCoverage by tasks.registering {
    group = "verification"
    description = "Verifies that Move alone renders a post-scroll glass replacement at the source region."

    doLast {
        val expectedTagFiles = mutableSetOf<File>()
        val normalizedMoveSourceRenderers = mutableSetOf<String>()
        val moveSourceStateFile = file(
            "src/client/kotlin/axion/client/render/MoveSourceRenderState.kt",
        )
        check(moveSourceStateFile.isFile) {
            "Move source replacement has no shared selected-block suppression state"
        }
        val moveSourceState = moveSourceStateFile.readText()
        listOf(
            "PlacementPreviewPolicy.activePreview(state)",
            "it.mode == PlacementToolMode.MOVE",
            "preview.sourceClipboardBuffer.nonAirCells()",
            "cell.absolutePos(sourceOrigin)",
            "fun shouldSuppress(worldIdentity: Any, pos: BlockPos): Boolean",
            "current.world === worldIdentity",
            "MoveSourceRenderInvalidator.invalidate(",
            "fun clearIfWorldChanged(",
        ).forEach { requiredSource ->
            check(requiredSource in moveSourceState) {
                "Move source suppression state is incomplete: missing $requiredSource"
            }
        }
        check("sourceRegion.minCorner()" in moveSourceState) {
            "Move source suppression offsets are not anchored to the selected source region"
        }
        check(
            "sourceRegion.minCorner().." !in moveSourceState &&
                "sourceRegion.maxCorner()" !in moveSourceState
        ) {
            "Move source suppression uses the bounding box instead of selected non-air cells"
        }
        listOf(
            "private data class Snapshot(",
            "val world: ClientWorld",
            "val positions: LongOpenHashSet",
            "@Volatile",
            "private var snapshot: Snapshot? = null",
            "previous.world === world",
            "if (current.world === world) return false",
            "val dirtySections = HashSet<SectionCoordinate>(sections)",
            "dirtySections += previous.sections",
            "MoveSourceRenderInvalidator.invalidate(world, dirtySections)",
        ).forEach { requiredSource ->
            check(requiredSource in moveSourceState) {
                "Move source suppression publication/lifecycle is incomplete: missing $requiredSource"
            }
        }
        check(
            "snapshot?.positions?.add" !in moveSourceState &&
                "snapshot?.positions?.remove" !in moveSourceState &&
                "snapshot?.positions?.clear" !in moveSourceState
        ) {
            "Published MOVE source positions are mutated after worker-thread publication"
        }

        val sodiumMixinFile = file(
            "src/client/kotlin/axion/mixin/client/SodiumMoveSourceLevelSliceMixin.kt",
        )
        check(sodiumMixinFile.isFile) {
            "Sodium bypasses the vanilla MOVE source suppression mixin"
        }
        val sodiumMixin = sodiumMixinFile.readText()
        listOf(
            "@Pseudo",
            "net.caffeinemc.mods.sodium.client.world.LevelSlice",
            "value = \"getBlockState\"",
            "args = [Int::class, Int::class, Int::class]",
            "at = [At(\"HEAD\")]",
            "cancellable = true",
            "require = 0",
            "MoveSourceRenderState.suppressedState(level, x, y, z)",
        ).forEach { requiredSource ->
            check(requiredSource in sodiumMixin) {
                "Sodium MOVE source suppression is incomplete: missing $requiredSource"
            }
        }
        val nextSnapshotBody = moveSourceState
            .substringAfter("val nextSnapshot = Snapshot(", missingDelimiterValue = "")
        val publishIndex = nextSnapshotBody.indexOf("snapshot = nextSnapshot")
        val dirtyUnionIndex = nextSnapshotBody.indexOf("val dirtySections = HashSet<SectionCoordinate>(sections)")
        val invalidateIndex = nextSnapshotBody.indexOf(
            "MoveSourceRenderInvalidator.invalidate(world, dirtySections)",
        )
        check(
            publishIndex >= 0 &&
                dirtyUnionIndex > publishIndex &&
                invalidateIndex > dirtyUnionIndex
        ) {
            "MOVE source snapshot must be atomically published before old/new sections are rebuilt"
        }
        val clearBranch = moveSourceState
            .substringAfter("if (world == null || preview == null) {", missingDelimiterValue = "")
            .substringBefore("val sourceOrigin =", missingDelimiterValue = "")
        check(
            "snapshot = null" in clearBranch &&
                "world === previous.world" in clearBranch &&
                "MoveSourceRenderInvalidator.invalidate(world, previous.sections)" in clearBranch
        ) {
            "Clearing a MOVE preview does not reveal the old source sections in the same world"
        }

        // The preview shell shader carries no lightmap, so ambient occlusion is
        // the only thing that can darken a preview — and it is computed against
        // the full-occupancy neighbour halo, which for the move source is the
        // replaced volume itself. With AO on, the glass renders as a black slab.
        val identityPolicy = file(
            "src/client/kotlin/axion/client/render/PreviewBlockIdentityPolicy.kt",
        ).readText()
        check("fun usesAmbientOcclusion(previewId: String)" in identityPolicy) {
            "Move source glass has no ambient-occlusion policy to keep sky light reading through it"
        }
        check("MOVE_SOURCE_SESSION_TAG" in identityPolicy.substringAfter("fun usesAmbientOcclusion(")) {
            "Ambient-occlusion policy no longer singles out the move-source replacement"
        }

        allPreviewCompatDirs.forEach { compatDir ->
            val placementRenderer = file(
                "$compatDir/kotlin/axion/client/render/PlacementPreviewRenderer.kt",
            )
            val placementSource = placementRenderer.readText()
            expectedTagFiles += placementRenderer.canonicalFile

            // 26.2 collapsed the sixteen per-colour block constants into
            // ColorCollection accessors, so the same block is spelled
            // differently there.
            val lightGrayGlass = if (compatDir == "src/compat-26_2") {
                "Blocks.STAINED_GLASS.lightGray().defaultState"
            } else {
                "Blocks.LIGHT_GRAY_STAINED_GLASS.defaultState"
            }
            listOf(
                "PlacementToolController.currentPreview() ?: return",
                "renderMoveSourceReplacement(context, preview)",
                "PlacementPreviewPolicy.shouldRenderMoveSourceReplacement(preview)",
                "preview.sourceRegion.minCorner()",
                "preview.sourceClipboardBuffer",
                "val sourceSurfaceClipboard = moveSourceSurfaceClipboard(sourceOccupancyClipboard)",
                "fallbackClipboard = sourceSurfaceClipboard",
                "MOVE_SOURCE_GHOST_SCALE: Float = 1.0f",
                lightGrayGlass,
                "sessionTag = \"move-source-replacement\"",
            ).forEach { requiredSource ->
                check(requiredSource in placementSource) {
                    "Move source replacement coverage is incomplete in $placementRenderer: missing $requiredSource"
                }
            }
            val moveSourceRenderer = placementSource
                .substringAfter("private fun renderMoveSourceReplacement(")
                .substringBefore("private fun moveSourceClipboard(")
            check("PlacementToolMode.CLONE" !in moveSourceRenderer) {
                "Clone must not enter the Move source replacement renderer in $placementRenderer"
            }
            normalizedMoveSourceRenderers += moveSourceRenderer
                .replace("forceChunked = true,", "")
                // Fold 26.2's ColorCollection spelling back to the shared one so
                // the renderers still have to agree on everything that matters.
                .replace("Blocks.STAINED_GLASS.lightGray()", "Blocks.LIGHT_GRAY_STAINED_GLASS")
                .replace(Regex("\\s+"), " ")
                .trim()
            val moveSourceClipboard = placementSource
                .substringAfter("private fun moveSourceClipboard(")
            check(
                "val selectedCells = source.nonAirCells()" in moveSourceClipboard &&
                    "surfaceClipboard(" !in moveSourceClipboard &&
                    "PreviewSurfaceTopology.retainBoundaryCells(source.nonAirCells())" in moveSourceClipboard
            ) {
                "Move replacement must split full glass occupancy from occupancy-boundary geometry in $placementRenderer"
            }

            val selectionRenderer = file(
                "$compatDir/kotlin/axion/client/render/ClipboardSelectionRenderer.kt",
            )
            check("GLASS_OVERLAY_" !in selectionRenderer.readText()) {
                "Generic selection glass is amplified in $selectionRenderer; replacement glass must be Move-only"
            }

            val selectionStateRenderer = file(
                "$compatDir/kotlin/axion/client/render/SelectionStateRenderer.kt",
            ).readText()
            val pendingMagicSelectionBranch = selectionStateRenderer
                .substringAfter("SelectionState.Idle ->")
                .substringBefore("is SelectionState.FirstCornerSet")
            check(
                "baseAlpha = 0" in pendingMagicSelectionBranch &&
                    "pulseFillColor = null" in pendingMagicSelectionBranch
            ) {
                "Pre-scroll Magic Select must be outline-only in $compatDir"
            }

            val ghostRenderer = file(
                "$compatDir/kotlin/axion/client/render/GhostBlockPreviewRenderer.kt",
            ).readText()
            val chunkedPreviewCall = ghostRenderer
                .substringAfter("VersionCompatImpl.renderChunkedPreview(")
                .substringBefore("if (handled)")
            check("scale" in chunkedPreviewCall) {
                "Chunked preview routing drops overlay scale in $compatDir"
            }

            val suppressionMixin = file(
                "$compatDir/kotlin/axion/mixin/client/MoveSourceChunkRendererRegionMixin.kt",
            )
            check(suppressionMixin.isFile) {
                "Move source blocks remain in the world chunk mesh for $compatDir"
            }
            val suppressionSource = suppressionMixin.readText()
            listOf(
                "@Inject(",
                "method = [\"getBlockState\"]",
                "method = [\"getFluidState\"]",
                "method = [\"getBlockEntity\"]",
                "at = [At(\"HEAD\")]",
                "cancellable = true",
                "@Shadow",
                "@Final",
                "MoveSourceRenderState.shouldSuppress(",
                "Blocks.AIR.defaultState",
                "Blocks.AIR.defaultState.fluidState",
                "cir.returnValue = null",
            ).forEach { requiredSource ->
                check(requiredSource in suppressionSource) {
                    "Move source chunk suppression is incomplete in $suppressionMixin: missing $requiredSource"
                }
            }
            val regionWorldField = if (compatDir == "src/compat-26_1" || compatDir == "src/compat-26_2") {
                "private lateinit var level: ClientWorld" to
                    "MoveSourceRenderState.shouldSuppress(level, pos)"
            } else {
                "private lateinit var world: World" to
                    "MoveSourceRenderState.shouldSuppress(world, pos)"
            }
            check(
                regionWorldField.first in suppressionSource &&
                    suppressionSource.split(regionWorldField.second).size - 1 == 3
            ) {
                "Every MOVE source channel must be scoped to the chunk region's world in $suppressionMixin"
            }

            val invalidator = file(
                "$compatDir/kotlin/axion/client/render/MoveSourceRenderInvalidator.kt",
            )
            check(invalidator.isFile) {
                "Move source mask changes do not rebuild affected render sections in $compatDir"
            }
            val invalidatorSource = invalidator.readText()
            val invalidationCall = when (compatDir) {
                "src/compat-1_21_0_1" ->
                    "world.scheduleBlockRenders(section.x, section.y, section.z)"
                "src/compat-26_1", "src/compat-26_2" ->
                    "world.setSectionDirtyWithNeighbors(section.x, section.y, section.z)"
                else ->
                    "world.scheduleChunkRenders("
            }
            check(invalidationCall in invalidatorSource) {
                "Move source mask changes use the wrong section invalidation API in $invalidator"
            }
            if (
                compatDir != "src/compat-1_21_0_1" &&
                compatDir != "src/compat-26_1" &&
                compatDir != "src/compat-26_2"
            ) {
                listOf(
                    "section.x - 1",
                    "section.y - 1",
                    "section.z - 1",
                    "section.x + 1",
                    "section.y + 1",
                    "section.z + 1",
                ).forEach { requiredNeighbor ->
                    check(requiredNeighbor in invalidatorSource) {
                        "MOVE source invalidation misses neighbor render sections in " +
                            "$invalidator: missing $requiredNeighbor"
                    }
                }
            }

            if (compatDir == "src/compat-26_1") {
                val previewDrawer = file(
                    "$compatDir/kotlin/axion/client/render/gpu/AxionPreviewBlockDrawer.kt",
                ).readText()
                val previewPipeline = file(
                    "$compatDir/kotlin/axion/client/compat/VersionCompatImpl.kt",
                ).readText()
                check(
                    "DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true, -1.0f, -1.0f)" in previewPipeline &&
                        ".withDepthStencilState(previewDepthState)" in previewPipeline
                ) {
                    "26.1 Move source replacement bypasses the scene-depth policy"
                }
                check("_polygonOffset(" !in previewDrawer && "com.mojang.blaze3d.opengl" !in previewDrawer) {
                    "26.1 Move source replacement still relies on mutable OpenGL depth state"
                }
            } else if (compatDir == "src/compat-26_2") {
                // Source masking removes the original terrain cells; the
                // replacement glass still reads preserved scene depth so real
                // foreground terrain hides it.
                val previewDrawer = file(
                    "$compatDir/kotlin/axion/client/render/gpu/AxionPreviewBlockDrawer.kt",
                ).readText()
                check("com.mojang.blaze3d.opengl" !in previewDrawer && "_polygonOffset(" !in previewDrawer) {
                    "26.2 Move source replacement still controls depth through raw OpenGL"
                }
                val previewPipeline = file(
                    "$compatDir/kotlin/axion/client/compat/VersionCompatImpl.kt",
                ).readText()
                check(
                    "DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true, 1.0f, 1.0f)" in previewPipeline &&
                        ".withDepthStencilState(previewDepthState)" in previewPipeline
                ) {
                    "26.2 Move source replacement bypasses the reversed scene-depth policy"
                }
            } else {
                val chunkedSession = file(
                    "$compatDir/kotlin/axion/client/render/gpu/ChunkedPreviewSession.kt",
                ).readText()
                check(
                    "scale = meshScale" in chunkedSession &&
                        "scale.toBits()" in chunkedSession
                ) {
                    "Chunked preview meshes do not preserve scale in $compatDir"
                }
            }
        }

        check(normalizedMoveSourceRenderers.size == 1) {
            "Move source replacement renderers differ across compatibility branches"
        }

        val actualTagFiles = fileTree("src") {
            include("**/*.kt")
        }.files.filterTo(mutableSetOf()) { sourceFile ->
            "sessionTag = \"move-source-replacement\"" in sourceFile.readText()
        }.mapTo(mutableSetOf()) { it.canonicalFile }

        check(actualTagFiles == expectedTagFiles) {
            "Move source replacement session must occur only in every compatibility PlacementPreviewRenderer; " +
                "expected=$expectedTagFiles actual=$actualTagFiles"
        }

        val placementController = file(
            "src/client/kotlin/axion/client/tool/PlacementToolController.kt",
        ).readText()
        val magicSelectionScrollBranch = placementController
            .substringAfter("val magicSelection = AxionClientState.clipboardState")
            .substringBefore("is CloneToolState.RegionDefined")
        listOf(
            "mode = mode",
            "sourceRegion = magicSelection.region",
            "clipboardBuffer = magicSelection.clipboardBuffer",
        ).forEach { requiredSource ->
            check(requiredSource in magicSelectionScrollBranch) {
                "Magic Select no longer forwards $requiredSource into the post-scroll placement preview"
            }
        }

        val clientState = file(
            "src/client/kotlin/axion/client/AxionClientState.kt",
        ).readText()
        val placementStateUpdate = clientState
            .substringAfter("fun updatePlacementToolState(")
            .substringBefore("fun updateEraseToolState(")
        check("MoveSourceRenderState.synchronize(state)" in placementStateUpdate) {
            "MOVE source suppression does not follow every placement preview lifecycle transition"
        }

        val placementControllerTick = placementController
            .substringAfter("fun onEndTick(")
            .substringBefore("fun currentPreview(")
        check("MoveSourceRenderState.clearIfWorldChanged(client.world)" in placementControllerTick) {
            "MOVE source suppression can leak across client-world changes"
        }

        val clientBootstrap = file(
            "src/client/kotlin/axion/client/AxionClientBootstrap.kt",
        ).readText()
        check(
            "CLIENT_STOPPING.register { MoveSourceRenderState.clear() }" in clientBootstrap &&
                "MoveSourceRenderState.clear()" in clientBootstrap
                    .substringAfter("VersionCompatImpl.onPlayDisconnect {")
                    .substringBefore("logger.info(")
        ) {
            "MOVE source suppression is not cleared on client stop and disconnect"
        }

        listOf(
            "src/client/resources/axion.client.mixins.json",
            "src/client/resources/axion.client.mixins-1.21.5.json",
        ).forEach { mixinConfigPath ->
            val mixinConfig = file(mixinConfigPath).readText()
            check("\"MoveSourceChunkRendererRegionMixin\"" in mixinConfig) {
                "MOVE source chunk suppression mixin is not enabled in $mixinConfigPath"
            }
            check("\"SodiumMoveSourceLevelSliceMixin\"" in mixinConfig) {
                "Sodium MOVE source suppression mixin is not enabled in $mixinConfigPath"
            }
        }

        val disconnectHandler = file(
            "src/client/kotlin/axion/client/AxionClientBootstrap.kt",
        ).readText()
            .substringAfter("VersionCompatImpl.onPlayDisconnect")
            .substringBefore("Saved hotbar flush handlers registered")
        check(
            "PlacementToolController.reset()" in disconnectHandler &&
                "MoveSourceRenderState.clear()" in disconnectHandler &&
                "ClientThreadCleanupScheduler.schedule(" in disconnectHandler
        ) {
            "Disconnect teardown must run on the client thread and clear both the MOVE preview state and its render mask"
        }
        check(
            disconnectHandler.indexOf("MoveSourceRenderState.clear()") <
                disconnectHandler.indexOf("PlacementToolController.reset()")
        ) {
            "Disconnect teardown must clear the old-world MOVE mask before reset can schedule chunk rebuilds"
        }

        listOf(
            "src/compat-1_21_0_1",
            "src/compat-1_21_4",
            "src/compat-1_21_5",
            "src/compat-1_21_6_8",
            "src/compat-1_21_9_10",
            "src/compat-1_21_11",
            "src/compat-26_1",
            "src/compat-26_2",
        ).forEach { compatDir ->
            val connection = file(
                "$compatDir/kotlin/axion/client/network/AxionServerConnection.kt",
            ).readText()
            val disconnect = connection
                .substringAfter("VersionCompatImpl.onPlayDisconnect")
                .substringBefore("fun state()")
            val scheduledCleanup = disconnect
                .substringAfter("ClientThreadCleanupScheduler.schedule(", missingDelimiterValue = "")
            check(
                scheduledCleanup.isNotEmpty() &&
                    "enqueueOnClientThread = { task -> VersionCompatImpl.runOnRenderThread(client, task) }" in scheduledCleanup &&
                    "AxionPreviewBufferCache.invalidate()" in scheduledCleanup
            ) {
                "Preview caches can be destroyed off the render context in $compatDir"
            }
        }
    }
}

val verifyGpuPreviewCoverage by tasks.registering {
    group = "verification"
    description = "Verifies that every supported Minecraft range packages and enables persistent GPU previews."
    dependsOn("compileClientKotlin")

    doLast {
        val clientClasses = layout.buildDirectory.dir("classes/kotlin/client").get().asFile
        val requiredClasses = listOf(
            "axion/client/render/AxionPreviewBuffer.class",
            "axion/client/render/gpu/AxionPreviewBlockDrawer.class",
            "axion/client/render/gpu/ChunkedPreviewSession.class",
        )
        requiredClasses.forEach { relativePath ->
            check(clientClasses.resolve(relativePath).isFile) {
                "Missing GPU preview class for Minecraft $minecraftVersion: $relativePath"
            }
        }

        val versionCompat = file("$gpuPreviewCompatDir/kotlin/axion/client/compat/VersionCompatImpl.kt").readText()
        val disabledRoute = Regex(
            """supportsChunkedPreview\s*\(\s*\)\s*:\s*Boolean\s*=\s*false""",
        )
        check(!disabledRoute.containsMatchIn(versionCompat)) {
            "GPU preview routing is explicitly disabled for Minecraft $minecraftVersion"
        }
        val placementRenderer = file(
            "$gpuPreviewCompatDir/kotlin/axion/client/render/PlacementPreviewRenderer.kt",
        ).readText()
        check("sessionTag = \"move-source\"" !in placementRenderer) {
            "Move preview for Minecraft $minecraftVersion still draws a second textured source mesh"
        }

        val blockTessellator = file(
            "$gpuPreviewCompatDir/kotlin/axion/client/render/AxionBlockTessellator.kt",
        ).readText()
        check("state.block == Blocks.GRASS_BLOCK" !in blockTessellator) {
            "GPU terrain preview for Minecraft $minecraftVersion disables side culling for grass blocks"
        }

        val pulsingCuboidRenderer = file(
            "$gpuPreviewCompatDir/kotlin/axion/client/render/PulsingCuboidRenderer.kt",
        ).readText()
        val selectionBoxBody = pulsingCuboidRenderer
            .substringAfter("fun renderSelectionBox(")
            .substringBefore("fun renderOutlineBox(")
        check("RenderLayerCompat.xrayQuads()" in selectionBoxBody) {
            "Placement pulse for Minecraft $minecraftVersion can write depth over the GPU preview"
        }

        if (rangeMc12101 || rangeMc12123 || rangeMc1214 || rangeMc1215) {
            val drawer = file(
                "$gpuPreviewCompatDir/kotlin/axion/client/render/gpu/AxionPreviewBlockDrawer.kt",
            ).readText()
            check("Matrix4f(RenderSystem.getModelViewMatrix())" in drawer) {
                "Legacy GPU preview for Minecraft $minecraftVersion is not using the active camera model-view matrix"
            }
        }

        if (rangeMc26x) {
            // AtlasManager keeps two maps: atlasByTexture (keyed by the texture
            // path) and atlasById. getAtlasOrThrow reads the id map, so passing
            // TextureAtlas.LOCATION_BLOCKS throws, the preview never binds its
            // sampler, and the GL backend silently draws against whatever the
            // slot already held — correct-looking under vanilla, black under any
            // mod that stops leaving the atlas there. RenderSetup.withTexture
            // does want the texture path, so only the lookup must use the id.
            val atlasLookup = versionCompat
                .substringAfter("fun getBlockAtlasTextureView(", missingDelimiterValue = "")
                .substringBefore("\n    /**", missingDelimiterValue = "")
            check("getAtlasOrThrow(AtlasIds.BLOCKS)" in atlasLookup) {
                "Minecraft $minecraftVersion preview pass resolves the block atlas by texture path, not atlas id"
            }
            check(".withTexture(\"Sampler0\", TextureAtlas.LOCATION_BLOCKS" in versionCompat) {
                "Minecraft $minecraftVersion buffered preview layer must name the atlas texture path, not the atlas id"
            }

            val drawer = file(
                "$gpuPreviewCompatDir/kotlin/axion/client/render/gpu/AxionPreviewBlockDrawer.kt",
            ).readText()
            check("getPreviewShellPipeline" in drawer) {
                "Minecraft $minecraftVersion GPU previews are not using Axion's visible preview pipeline"
            }
            check("RenderLayerCompat.translucentMovingBlock()" !in drawer) {
                "Minecraft $minecraftVersion GPU previews regressed to the invisible moving-block pipeline"
            }
            check("check(drawList.size == 1)" in drawer) {
                "Minecraft $minecraftVersion can submit independently sorted translucent section meshes"
            }

            val chunkedSession = file(
                "$gpuPreviewCompatDir/kotlin/axion/client/render/gpu/ChunkedPreviewSession.kt",
            ).readText()
            listOf(
                "PreviewTranslucencySortPolicy.effectiveCamera(",
                "PreviewTranslucencySortPolicy.globalMeshPlan(",
                "buildGlobalBuffer(meshPlan.anchor",
                "builtBuffer.sortQuads(",
                "VertexSorting.byDistance(sortX, sortY, sortZ)",
                "resortBuffers(effectiveCamera)",
                "chunkBuffers.put(anchorKey, replacement)",
                "chunkBuffers.size == meshPlan.batchCount",
                "builtBuffer.close()",
                "builtSection.allocator.close()",
            ).forEach { requiredSource ->
                check(requiredSource in chunkedSession) {
                    "Minecraft $minecraftVersion preview mesh loses global translucency sorting or allocator ownership: " +
                        "missing $requiredSource"
                }
            }

            val previewBuffer = file(
                "$gpuPreviewCompatDir/kotlin/axion/client/render/AxionPreviewBuffer.kt",
            ).readText()
            listOf(
                "MeshData.SortState?",
                "buildSortedIndexBuffer(",
                "PreviewTranslucencySortPolicy.shouldResort(",
                "sortedIndices.close()",
                "allocator.close()",
            ).forEach { requiredSource ->
                check(requiredSource in previewBuffer) {
                    "Minecraft $minecraftVersion preview index buffers cannot be safely re-sorted: missing $requiredSource"
                }
            }
            // 26.1 bundled the transform/projection uniforms into the private
            // MATRICES_PROJECTION_SNIPPET; 26.2 declares them as a shared
            // BindGroupLayout instead.
            val previewUniforms = if (rangeMc262x) {
                "BindGroupLayouts.MATRICES_PROJECTION"
            } else {
                "MATRICES_PROJECTION_SNIPPET"
            }
            check(previewUniforms in versionCompat) {
                "Minecraft $minecraftVersion preview pipeline is missing transform/projection uniforms"
            }
            // Textured previews read and write their surface depth. This keeps
            // later cloud pixels out of the preview without cancelling clouds
            // elsewhere. 26.2 uses reversed Z, so the accepted comparison and
            // bias direction flip.
            val expectedDepthState = if (rangeMc262x) {
                "DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true, 1.0f, 1.0f)"
            } else {
                "DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true, -1.0f, -1.0f)"
            }
            check(
                "if (PreviewVisualPolicy.XRAY_BLOCK_PREVIEWS)" in versionCompat &&
                    expectedDepthState in versionCompat &&
                    ".withDepthStencilState(previewDepthState)" in versionCompat
            ) {
                "Minecraft $minecraftVersion preview pipeline bypasses scene-depth policy"
            }

            val renderLayerCompat = file(
                "src/client/kotlin/axion/client/render/RenderLayerCompat.kt",
            ).readText()
            check("createRenderSetup" in renderLayerCompat && "findXrayPipelineSnippet" in renderLayerCompat) {
                "Minecraft $minecraftVersion x-ray selection layer lacks the RenderSetup/uniform compatibility path"
            }
        }

        val bufferBytecode = clientClasses
            .resolve("axion/client/render/AxionPreviewBuffer.class")
            .readBytes()
            .toString(Charsets.ISO_8859_1)
        check(
            "net/minecraft/client/gl/VertexBuffer" in bufferBytecode ||
                "com/mojang/blaze3d/buffers/GpuBuffer" in bufferBytecode ||
                "net/minecraft/client/gl/GpuBuffer" in bufferBytecode
        ) {
            "GPU preview buffer for Minecraft $minecraftVersion is not GPU-resident"
        }
    }
}

val verifyAuthoritativeHistoryReplay by tasks.registering {
    group = "verification"
    description = "Verifies that undo/redo survives world drift and large confirmed edits"

    doLast {
        listOf(
            "paper-plugin/src/main/kotlin/axion/server/paper/AxionHistoryActionService.kt",
            "fabric-server/src/main/kotlin/axion/server/fabric/AxionFabricHistoryActionService.kt",
        ).forEach { servicePath ->
            val source = file(servicePath).readText()
            check("World no longer matches the undo target" !in source) {
                "Undo still rejects a whole transaction after a recorded cell changes in $servicePath"
            }
            check("World no longer matches the redo target" !in source) {
                "Redo still rejects a whole transaction after a recorded cell changes in $servicePath"
            }
            check(
                !servicePath.startsWith("paper-plugin/") ||
                    ("validateUndo(" in source && "validateRedo(" in source)
            ) {
                "Paper authoritative replay bypasses permission or region checks in $servicePath"
            }
            val undoBody = source.substringAfter("fun undo(").substringBefore("fun redo(")
            check(undoBody.indexOf("history.commitUndo(") > undoBody.indexOf("transaction.changes.asReversed()")) {
                "Undo consumes its history entry before the recorded world state is restored in $servicePath"
            }
            val redoBody = source.substringAfter("fun redo(")
            check(redoBody.indexOf("history.commitRedo(") > redoBody.indexOf("transaction.changes")) {
                "Redo consumes its history entry before the recorded world state is restored in $servicePath"
            }
        }

        val transport = file(
            "protocol/src/main/kotlin/axion/protocol/AxionTransportCodec.kt",
        ).readText()
        check("MAX_SERIALIZED_BYTES: Int = 256 * 1024 * 1024" in transport) {
            "Confirm transport limit is not 256 MiB"
        }
        check("MAX_CHUNKS: Int =" in transport && "MAX_SERIALIZED_BYTES" in transport.substringAfter("MAX_CHUNKS: Int =")) {
            "Chunk-count limit does not scale with the serialized confirm limit"
        }
        check(
            "max-bytes: 268435456" in file("paper-plugin/src/main/resources/config.yml").readText() &&
                "256 * 1024 * 1024" in file(
                    "paper-plugin/src/main/kotlin/axion/server/paper/AxionPolicyService.kt",
                ).readText()
        ) {
            "Paper's default history budget was not raised to 256 MiB"
        }

        listOf(
            "src/client/kotlin/axion/client/history/HistoryManager.kt",
            "fabric-server/src/main/kotlin/axion/server/fabric/AxionFabricServerHistory.kt",
        ).forEach { historyPath ->
            val source = file(historyPath).readText()
            check("256 * 1024 * 1024" in source) {
                "History budget was not raised alongside confirmation transport in $historyPath"
            }
        }
        listOf(
            "src/client/kotlin/axion/client/history/HistoryManager.kt",
            "paper-plugin/src/main/kotlin/axion/server/paper/AxionServerHistory.kt",
            "fabric-server/src/main/kotlin/axion/server/fabric/AxionFabricServerHistory.kt",
        ).forEach { historyPath ->
            val source = file(historyPath).readText()
            check("protectedTransactionId" in source || "protectedEntryId" in source) {
                "Newest oversized edit can still evict itself from undo history in $historyPath"
            }
        }
    }
}

tasks.named("check") {
    dependsOn(verifyIntegratedNoClipWiring)
    dependsOn(verifyGpuPreviewCoverage)
    dependsOn(verifyFabricServerRangeCompatibility)
    dependsOn(verifyMoveSourceReplacementCoverage)
    dependsOn(verifyXraySelectionRenderingCoverage)
    dependsOn(verifyPreviewVisualCoverage)
    dependsOn(verifyMagicSelectFirstRenderCoverage)
    dependsOn(verifyAuthoritativeHistoryReplay)
}

// Range-style filename, e.g. "mc1.21.9-1.21.11"
val rangeFileTag = (findProperty("axion_artifact_tag") as String?)?.trim()?.takeIf { it.isNotEmpty() } ?: when {
    rangeMc12101 -> "mc1.21-1.21.1"
    rangeMc12123 -> "mc1.21.2-1.21.3"
    rangeMc1214 -> "mc1.21.4"
    rangeMc1215 -> "mc1.21.5"
    rangeLegacy -> "mc1.21.6-1.21.8"
    rangeModern -> "mc1.21.9-1.21.11"
    rangeMc261x -> "mc26.1.x"
    rangeMc262x -> "mc26.2.x"
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
        if (rangeMc26x) {
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

if (!rangeMc26x) {
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

tasks.register("runClient262") {
    group = "axion"
    description = "Run Minecraft 26.2.x client with Axion"
    doLast {
        logger.lifecycle("Use Loom's generated runClient task with -Pminecraft_version=26.2.")
    }
}

// Unified run task that accepts a target version.
// Use -PtargetVersion=1.21.5 (NOT -Pversion, which clashes with Gradle's project.version).
tasks.register("runClientVersion") {
    group = "axion"
    description = "Run client for specified version (use -PtargetVersion=1.21.5,1.21.7,1.21.11,26.1,26.2)"
    val target = project.findProperty("targetVersion") as String? ?: "26.2"

    dependsOn(
        when (target) {
            "1.21.5" -> "runClient1215"
            "1.21.7" -> "runClient1217"
            "1.21.11" -> "runClient12111"
            "26.1" -> "runClient261"
            "26.2" -> "runClient262"
            else -> throw IllegalArgumentException("Unsupported version: $target. Use 1.21.5, 1.21.7, 1.21.11, 26.1, or 26.2")
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
        "1.21.1" -> "1.21.1-R0.1-SNAPSHOT"
        "1.21.5" -> "1.21.5-R0.1-SNAPSHOT"
        "1.21.7" -> "1.21.7-R0.1-SNAPSHOT"
        "1.21.11" -> "1.21.11-R0.1-SNAPSHOT"
        "26.1" -> property("paper_version") as String
        "26.2" -> property("paper_version") as String
        else -> property("paper_version") as String
    }
    val rangeTag = when {
        version == "1.21.1" -> "mc1.21-1.21.1"
        version == "1.21.5" -> "mc1.21.5"
        version == "1.21.7" -> "mc1.21.6-1.21.8"
        version == "1.21.11" -> "mc1.21.9-1.21.11"
        version.startsWith("26.1") -> "mc26.1.x"
        version.startsWith("26.2") -> "mc26.2.x"
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
                "1.21.1" -> "https://fill-data.papermc.io/v1/objects/39bd8c00b9e18de91dcabd3cc3dcfa5328685a53b7187a2f63280c22e2d287b9/paper-1.21.1-133.jar"
                "1.21.5" -> "https://fill-data.papermc.io/v1/objects/2ae6ae22adf417699746e0f89fc2ef6cb6ee050a5f6608cee58f0535d60b509e/paper-1.21.5-114.jar"
                "1.21.7" -> "https://fill-data.papermc.io/v1/objects/83838188699cb2837e55b890fb1a1d39ad0710285ed633fbf9fc14e9f47ce078/paper-1.21.7-32.jar"
                "1.21.11" -> "https://fill-data.papermc.io/v1/objects/e708e8c132dc143ffd73528cccb9532e2eb17628b1a0eee74469bf466c7003f8/paper-1.21.11-116.jar"
                "26.1" -> "https://fill-data.papermc.io/v1/objects/b51d49a5f62446b7cfc01e6c29e48e0ce6abd35a783134aace1047b839b178ef/paper-26.1.2-63.jar"
                "26.2" -> "https://fill.papermc.io/v3/projects/paper/versions/26.2/builds/65/downloads/server:default"
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
                "1.21.1" -> 25566
                "1.21.5" -> 25567
                "1.21.7" -> 25568
                "1.21.11" -> 25569
                "26.1" -> 25570
                "26.2" -> 25571
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
