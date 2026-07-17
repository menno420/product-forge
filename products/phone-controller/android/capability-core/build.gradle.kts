/*
 * capability-core — the Kotlin port of the Slice-1 verdict engine, as a PURE-JVM
 * library (no Android Gradle Plugin, no Android SDK). Being SDK-free is the whole
 * point: the load-bearing decision model (`evaluate()`) compiles and unit-tests in a
 * plain JVM, so CI can prove it green without downloading an Android toolchain.
 *
 * The on-device transport (../app/.../BluetoothHidDeviceTransport.kt) depends on this
 * same decision model, so the Android layer and the portable Python core share one
 * verdict table.
 */
plugins {
    kotlin("jvm") version "2.0.21"
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
