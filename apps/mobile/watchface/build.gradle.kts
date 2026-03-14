plugins {
    alias(libs.plugins.android.application)
}

android {
    // Watch Face Push API requires: <client-app-package>.watchfacepush.<identifier>
    // Client = wear-device module (com.glycemicgpt.mobile[.debug])
    namespace = "com.glycemicgpt.mobile.watchfacepush.glycemicgpt"
    compileSdk = 35

    defaultConfig {
        // Release: client = com.glycemicgpt.mobile
        applicationId = "com.glycemicgpt.mobile.watchfacepush.glycemicgpt"
        minSdk = 33
        targetSdk = 35

        val appVersionName = "0.1.99" // x-release-please-version
        val parts = appVersionName.split(".")
        val major = parts.getOrElse(0) { "0" }.toInt()
        val minor = parts.getOrElse(1) { "0" }.toInt()
        val patch = parts.getOrElse(2) { "0" }.toInt()

        versionCode = major * 1_000_000 + minor * 10_000 + patch
        versionName = appVersionName
    }

    signingConfigs {
        val debugKsFile = System.getenv("DEBUG_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
        if (debugKsFile != null) {
            getByName("debug") {
                storeFile = file(debugKsFile)
                storePassword = requireNotNull(
                    System.getenv("DEBUG_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() },
                ) {
                    "DEBUG_KEYSTORE_PASSWORD must be set when DEBUG_KEYSTORE_FILE is provided"
                }
                keyAlias = requireNotNull(
                    System.getenv("DEBUG_KEY_ALIAS")?.takeIf { it.isNotBlank() },
                ) {
                    "DEBUG_KEY_ALIAS must be set when DEBUG_KEYSTORE_FILE is provided"
                }
                keyPassword = requireNotNull(
                    System.getenv("DEBUG_KEY_PASSWORD")?.takeIf { it.isNotBlank() },
                ) {
                    "DEBUG_KEY_PASSWORD must be set when DEBUG_KEYSTORE_FILE is provided"
                }
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // WFF is resource-only: exclude AGP build metadata from merged resources.
    // NOTE: Do NOT exclude META-INF signing files (MANIFEST.MF, CERT.SF, CERT.RSA)
    // as the DwfValidator requires a signed APK.
    packaging {
        resources {
            excludes += "/*.properties"
            excludes += "/*.version"
        }
    }
}

// No dependencies: WFF is a resource-only APK (hasCode=false)

// Set applicationId per variant to match the Watch Face Push API requirement:
// <wear-device-package>.watchfacepush.<name>
// Debug wear-device has package com.glycemicgpt.mobile.debug, so the debug
// watch face must use com.glycemicgpt.mobile.debug.watchfacepush.glycemicgpt.
androidComponents {
    onVariants { variant ->
        if (variant.buildType == "debug") {
            variant.applicationId.set("com.glycemicgpt.mobile.debug.watchfacepush.glycemicgpt")
        }
    }
}

// Post-build: strip classes.dex and re-sign with v1+v2 signing.
//
// Why this is needed:
//   1. AGP always generates classes.dex even with hasCode=false.
//      The DwfValidator rejects APKs containing classes.dex.
//   2. With minSdk >= 24, AGP uses only v2/v3 signing (no META-INF entries).
//      The DwfValidator requires v1 JAR signing (META-INF/MANIFEST.MF).
//   3. targetSdk 35 requires v2 signing minimum.
//   4. Stripping classes.dex invalidates any existing signature.
//
// Solution: strip classes.dex, zipalign, then re-sign with apksigner (v1+v2).
android.applicationVariants.all {
    val variant = this
    val stripTask = tasks.register("strip${variant.name.replaceFirstChar { it.uppercase() }}WffFiles") {
        description = "Strip classes.dex from WFF APK, zipalign, and re-sign with v1+v2"
        dependsOn(variant.assembleProvider)
        doLast {
            val apkDir = variant.outputs.first().outputFile.parentFile
            apkDir.listFiles()?.filter { it.extension == "apk" }?.forEach { apk ->
                // 1. Strip classes.dex and AGP build metadata
                exec {
                    commandLine("zip", "-d", apk.absolutePath, "classes.dex",
                        "META-INF/com/android/build/gradle/app-metadata.properties")
                    isIgnoreExitValue = true
                }

                // 2. Zipalign: ensure resources.arsc is 4-byte aligned.
                //    Required for Android R+ (API 30+). Must happen BEFORE signing
                //    because apksigner expects an aligned APK.
                val aligned = File(apk.parentFile, "aligned-${apk.name}")
                exec {
                    commandLine("zipalign", "-f", "4", apk.absolutePath, aligned.absolutePath)
                }
                if (!aligned.renameTo(apk)) {
                    // Fallback for cross-filesystem moves (e.g., Docker, NFS)
                    aligned.copyTo(apk, overwrite = true)
                    aligned.delete()
                }

                // 3. Re-sign with apksigner (v1+v2) using debug keystore.
                val envKs = System.getenv("DEBUG_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
                val ksFile = if (envKs != null) file(envKs) else
                    File(System.getProperty("user.home"), ".android/debug.keystore")
                val ksPass = System.getenv("DEBUG_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() } ?: "android"
                val keyAlias = System.getenv("DEBUG_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "androiddebugkey"
                val keyPass = System.getenv("DEBUG_KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: "android"

                if (!ksFile.exists()) {
                    logger.warn("Keystore not found at ${ksFile.absolutePath}; APK will be unsigned")
                } else {
                    exec {
                        commandLine(
                            "apksigner", "sign",
                            "--ks", ksFile.absolutePath,
                            "--ks-pass", "pass:$ksPass",
                            "--ks-key-alias", keyAlias,
                            "--key-pass", "pass:$keyPass",
                            "--v1-signing-enabled", "true",
                            "--v2-signing-enabled", "true",
                            apk.absolutePath,
                        )
                    }
                    logger.lifecycle("Stripped classes.dex, zipaligned, and re-signed ${apk.name} with v1+v2 signing")
                }
            }
        }
    }
    variant.assembleProvider.get().finalizedBy(stripTask)
}
