/*
 * Gradle settings for the phone-controller Android side.
 *
 * Two modules:
 *   * :capability-core — a pure-JVM Kotlin module that compiles and unit-tests with
 *     NO Android SDK. It is the load-bearing decision model (`evaluate()`), shared
 *     with the portable Python core, and its CI lane stays light and SDK-free.
 *   * :app — the Android application module (AGP + Android SDK). Wired in in Slice 3
 *     so the real BluetoothHidDevice transport actually compiles; its `assembleDebug`
 *     runs in a CI job that provisions the Android SDK (see
 *     .github/workflows/android-ci.yml).
 *
 * DECIDE-AND-FLAG — :app is included ONLY when an Android SDK is present.
 * Applying the Android Gradle Plugin requires a resolvable SDK location; configuring
 * :app without one fails the WHOLE build ("SDK location not found") — including a bare
 * `gradle :capability-core:test`, because Gradle configures every included project
 * regardless of which task runs. Gating `include(":app")` on SDK presence keeps the
 * SDK-free `:capability-core:test` lane green (the design invariant Slice 2 leaned on)
 * while the assembleDebug job — which provisions the SDK, so ANDROID_HOME is set —
 * gets the app module. `android-actions/setup-android` exports ANDROID_HOME /
 * ANDROID_SDK_ROOT; a local dev with Android Studio has a `local.properties`. This is
 * a build-plumbing choice, fully reversible: drop the guard once every lane provisions
 * the SDK unconditionally.
 */
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "phone-controller-android"

include(":capability-core")

val androidSdkAvailable =
    System.getenv("ANDROID_HOME") != null ||
        System.getenv("ANDROID_SDK_ROOT") != null ||
        file("local.properties").exists()

if (androidSdkAvailable) {
    include(":app")
} else {
    logger.lifecycle(
        "settings: Android SDK not detected (no ANDROID_HOME/ANDROID_SDK_ROOT/" +
            "local.properties) — skipping :app; building SDK-free :capability-core only.",
    )
}
