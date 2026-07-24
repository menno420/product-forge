/*
 * app — the Android application module.
 *
 * Wired into the build in Slice 3 (see ../settings.gradle.kts `include(":app")`). This
 * module needs the Android Gradle Plugin + a full Android SDK, so its `assembleDebug`
 * runs in a dedicated CI job that provisions the SDK (android-actions/setup-android),
 * kept separate from the SDK-free `:capability-core:test` job.
 *
 * The Android layer consumes the SAME shared decision model the portable Python core
 * defines: it depends on :capability-core and calls `evaluate()` from the real
 * BluetoothHidDevice transport — the decision table is shared, never re-derived.
 */
plugins {
    id("com.android.application") version "8.5.2"
    kotlin("android") version "2.0.21"
}

// Release signing is env-driven so CI can sign from repo secrets while the keystore
// itself is never committed: PC_RELEASE_KEYSTORE (path), PC_RELEASE_KEYSTORE_PASSWORD,
// optional PC_RELEASE_KEY_ALIAS / PC_RELEASE_KEY_PASSWORD. With no env present,
// assembleRelease falls back to the debug signing config — the APK is then still
// installable (sideload-quality), just not stable-signature across machines.
val releaseStoreFile: String? = System.getenv("PC_RELEASE_KEYSTORE")
val releaseStorePassword: String? = System.getenv("PC_RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias: String = System.getenv("PC_RELEASE_KEY_ALIAS") ?: "phone-controller"
val releaseKeyPassword: String? = System.getenv("PC_RELEASE_KEY_PASSWORD") ?: releaseStorePassword
val hasReleaseKeystore = releaseStoreFile != null && releaseStorePassword != null

android {
    namespace = "com.productforge.phonecontroller"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.productforge.phonecontroller"
        minSdk = 28          // BluetoothHidDevice requires API 28 (Android 9)
        targetSdk = 34
        versionCode = 10
        versionName = "0.12.0"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }
        // Release stays unminified (no shrinker rules to maintain yet); its value over
        // debug is the stable, secrets-provided signature for in-place updates.
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // The Android layer consumes the SAME shared decision model the Python core
    // defines, and the pure-JVM HID report model (descriptor + report builders).
    implementation(project(":capability-core"))
    implementation(project(":hid-core"))
}
