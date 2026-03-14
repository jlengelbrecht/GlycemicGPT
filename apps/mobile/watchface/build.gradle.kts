plugins {
    alias(libs.plugins.android.application)
}

android {
    // Watch Face Push API requires: <client-app-package>.watchfacepush.<identifier>
    // Client app package = com.glycemicgpt.mobile (phone app's applicationId)
    namespace = "com.glycemicgpt.mobile.watchfacepush.glycemicgpt"
    compileSdk = 35

    defaultConfig {
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

    // WFF is resource-only: strip all build metadata that the validator rejects
    packaging {
        resources {
            excludes += "/META-INF/**"
            excludes += "/*.properties"
            excludes += "/*.version"
        }
    }
}

// No dependencies: WFF is a resource-only APK (hasCode=false)
