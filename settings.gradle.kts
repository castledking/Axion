pluginManagement {
    val loomVersion = providers.gradleProperty("loom_version").orElse("1.15.4").get()
    val kotlinVersion = providers.gradleProperty("kotlin_version").orElse("2.3.10").get()
    val paperweightVersion = providers.gradleProperty("paperweight_version").orElse("2.0.0-beta.19").get()

    plugins {
        id("fabric-loom") version loomVersion
        id("net.fabricmc.fabric-loom") version loomVersion
        id("io.papermc.paperweight.userdev") version paperweightVersion
        id("net.neoforged.moddev") version "2.0.49-beta"
        kotlin("jvm") version kotlinVersion
    }

    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.neoforged.net/releases/")
        mavenCentral()
    }
}

rootProject.name = "Axion"

include(":common")
include(":fabric")
include(":neoforge")
include(":protocol")
include(":paper-plugin")
include(":fabric-server")
