/*
 * Gradle settings for the phone-controller Android side.
 *
 * Only :capability-core (a pure-JVM Kotlin module) is included in the build — it is
 * the piece that compiles and unit-tests with NO Android SDK, so the CI lane stays
 * light and reliable (see .github/workflows/android-ci.yml, added in the companion
 * workflow PR). The Android app module lives under `app/` as reviewable SKELETON
 * source (it needs the Android SDK + AGP and is wired into the build in Slice 3); it
 * is intentionally NOT `include`d here yet so `gradle :capability-core:test` never
 * requires an Android toolchain.
 */
rootProject.name = "phone-controller-android"

include(":capability-core")
