/*
 * app — the Android application module (SKELETON, Slice 2).
 *
 * ⚠️ NOT WIRED INTO THE BUILD OR CI YET. This module needs the Android Gradle Plugin
 * + a full Android SDK, so it is deliberately absent from ../settings.gradle.kts and
 * is NOT compiled by the CI lane (which builds only the SDK-free :capability-core).
 * Slice 3 wires this in: uncomment the `include(":app")` in settings.gradle.kts, add
 * an `assembleDebug` CI job with an Android-SDK setup step, and grow the media-remote
 * skeleton into the customisable controller UI.
 *
 * The block below is the intended AGP configuration, kept as documentation-in-code so
 * the wiring in Slice 3 is a small, obvious diff rather than a from-scratch guess.
 */

/*
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
        versionName = "0.2.0-slice2"
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
*/
