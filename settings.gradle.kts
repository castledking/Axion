pluginManagement {
    val loomVersion = providers.gradleProperty("loom_version").orElse("1.15.4").get()
    val kotlinVersion = providers.gradleProperty("kotlin_version").orElse("2.3.10").get()
    val paperweightVersion = providers.gradleProperty("paperweight_version").orElse("2.0.0-beta.19").get()

    plugins {
        id("fabric-loom") version loomVersion
        id("net.fabricmc.fabric-loom") version loomVersion
        id("io.papermc.paperweight.userdev") version paperweightVersion
        kotlin("jvm") version kotlinVersion
    }

    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://repo.papermc.io/repository/maven-public/")
        mavenCentral()
    }
}

rootProject.name = "Axion"

include(":protocol")
include(":paper-plugin")
include(":fabric-server")
