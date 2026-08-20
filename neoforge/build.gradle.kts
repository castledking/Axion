plugins {
    id("multiloader-loader")
    id("net.neoforged.moddev")
    kotlin("jvm")
}

val commonKotlinSrc = project(":common").layout.projectDirectory.dir("src/main/kotlin").asFile.absolutePath

sourceSets {
    create("client") {
        java.srcDir("src/client/kotlin")
        resources.srcDir("src/client/resources")
        kotlin.srcDir("src/compat-1_21_11/kotlin")
    }
}

sourceSets.main {
    kotlin.srcDir(commonKotlinSrc)
}

afterEvaluate {
    sourceSets.named("client") {
        kotlin.srcDir(commonKotlinSrc)
    }
}

neoForge {
    version = findProperty("neoforge_version") as String
}

dependencies {
    implementation(project(":protocol"))
    compileOnly("org.spongepowered:mixin:0.8.5")
}

kotlin {
    jvmToolchain((findProperty("java_version") as String).toInt())
}
