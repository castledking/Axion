plugins {
    id("multiloader-loader")
    id("net.neoforged.moddev")
    kotlin("jvm")
}

val minecraftVersion = findProperty("minecraft_version") as String

val neoforgeVersion = when {
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

    mods {
        create("axion") {
            sourceSet(sourceSets["main"])
        }
    }
}

neoForge.addModdingDependenciesTo(sourceSets.main.get())

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
    options.release.set((findProperty("java_version") as String).toInt())
}

kotlin {
    jvmToolchain((findProperty("java_version") as String).toInt())
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(findProperty("java_version") as String))
        freeCompilerArgs.add("-jvm-default=no-compatibility")
    }
}
