// Axion multiloader root build.
// Loader-specific build logic lives in fabric/build.gradle.kts and neoforge/build.gradle.kts.
// Shared code compiles in common/build.gradle against NeoForm (vanilla Minecraft).

plugins {
    id("fabric-loom") apply false
    id("net.fabricmc.fabric-loom") apply false
    id("net.neoforged.moddev") apply false
    kotlin("jvm") apply false
}
