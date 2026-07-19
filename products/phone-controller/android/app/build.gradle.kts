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

android {
    namespace = "com.productforge.phonecontroller"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.productforge.phonecontroller"
        minSdk = 28          // BluetoothHidDevice requires API 28 (Android 9)
        targetSdk = 34
        versionCode = 1
        versionName = "0.3.0-slice3"
    }

    buildTypes {
        // Debug is the only variant Slice 3 needs (assembleDebug in CI). Release
        // signing/minification is a later, owner-facing slice.
        getByName("debug") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // The Android layer consumes the SAME shared decision model the Python core defines.
    implementation(project(":capability-core"))
}
