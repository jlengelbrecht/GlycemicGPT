/**
 * Example runtime plugin for GlycemicGPT.
 *
 * This is a standalone Gradle project (NOT a submodule of the main app).
 * It demonstrates how to build a plugin JAR that can be sideloaded at runtime.
 *
 * Prerequisites:
 * - The pump-driver-api AAR or JAR must be available. Build it from the main
 *   project: `./gradlew :pump-driver-api:assembleRelease`
 * - Copy the AAR to this project's libs/ directory.
 *
 * Build steps:
 * 1. Compile: `./gradlew jar`
 * 2. Convert to DEX: `d8 --output dex-out/ build/libs/example-plugin.jar`
 * 3. Repackage DEX + manifest into the final plugin JAR:
 *    `cp build/libs/example-plugin.jar example-plugin.jar`
 *    `jar uf example-plugin.jar -C dex-out classes.dex`
 * 4. Install on device: copy example-plugin.jar to the app's plugins directory
 *    via adb or the app's "Add Plugin" button.
 *
 * See docs/plugin-architecture.md for full documentation.
 */
plugins {
    kotlin("jvm") version "2.0.21"
}

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    // Standalone: place the pump-driver-api AAR in libs/ and reference it:
    compileOnly(files("libs/pump-driver-api-release.aar"))

    // Alternatively, when building within the monorepo for testing:
    // compileOnly(project(":pump-driver-api"))

    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

tasks.jar {
    archiveBaseName.set("example-plugin")

    // Include the plugin manifest in the JAR
    from("src/main/resources")
}
