plugins {
    id("multiloader-loader")
    id("net.neoforged.moddev")
    kotlin("jvm")
}

version = (findProperty("mod_version") as String?) ?: "0.0.0"
group = (findProperty("maven_group") as String?) ?: "codes.castled.axion"

val minecraftVersion = findProperty("minecraft_version") as String

val neoforgeVersion = (findProperty("neoforge_version") as String?)
    ?.takeIf { it.isNotBlank() && it != "21.11.8" }
    ?: when {
    minecraftVersion.startsWith("26.") -> findProperty("neoforge_version") as String
    minecraftVersion == "1.21.10" -> "21.10.64"
    minecraftVersion.startsWith("1.21.11") -> "21.11.45"
    else -> findProperty("neoforge_version") as String
}

// Shared logic lives as a Mojmap-named copy under src/mojmap-common (the Yarn
// original stays in /common for Fabric). See feat/neoforge branch notes.
sourceSets.main {
    java.srcDir("src/client/java")
    kotlin.srcDir("src/client/kotlin")
    java.srcDir("src/compat-1_21_11/java")
    kotlin.srcDir("src/compat-1_21_11/kotlin")
    kotlin.srcDir("src/mojmap-common/kotlin")
    resources.srcDir("src/client/resources")
}

neoForge {
    version = neoforgeVersion

    accessTransformers.from("src/main/resources/META-INF/accesstransformer.cfg")

    mods {
        create("axion") {
            sourceSet(sourceSets["main"])
        }
    }
}

neoForge.addModdingDependenciesTo(sourceSets.main.get())


tasks.processResources {
    val ctx = mapOf(
        "version" to (version as String),
        "group" to ((group as String?) ?: "codes.castled.axion"),
        "minecraft_version" to minecraftVersion,
        "minecraft_version_range" to ((findProperty("minecraft_version_range") as String?) ?: "[1.21.9,1.21.11]"),
        "mod_name" to (findProperty("mod_name") as String),
        "mod_author" to (findProperty("mod_author") as String),
        "mod_id" to (findProperty("mod_id") as String),
        "license" to (findProperty("license") as String),
        "description" to ((findProperty("description") as String?) ?: ""),
        "neoforge_loader_version_range" to ((findProperty("neoforge_loader_version_range") as String?) ?: "[4,)"),
        "credits" to ((findProperty("credits") as String?) ?: ""),
        "java_version" to ((findProperty("java_version") as String?) ?: "21"),
    )
    inputs.properties(ctx)
    filesMatching(listOf("META-INF/neoforge.mods.toml", "pack.mcmeta", "*.mixins.json")) {
        expand(ctx)
    }
}

dependencies {
    implementation(project(":protocol"))
    compileOnly("org.spongepowered:mixin:0.8.5")
    compileOnly("io.github.llamalad7:mixinextras-common:0.3.5")
    annotationProcessor("io.github.llamalad7:mixinextras-common:0.3.5")
}

tasks.named("jar", Jar::class.java) {
    manifest {
        attributes(
            "Implementation-Version" to project.version,
            "Built-On-Minecraft" to minecraftVersion,
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(((findProperty("java_version") as String?) ?: "21").toInt())
}

kotlin {
    jvmToolchain(((findProperty("java_version") as String?) ?: "21").toInt())
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget((findProperty("java_version") as String?) ?: "21"))
        freeCompilerArgs.add("-jvm-default=no-compatibility")
    }
}
