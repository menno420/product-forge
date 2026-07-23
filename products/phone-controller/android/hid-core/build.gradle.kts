/*
 * hid-core — the HID report model (combo descriptor + input-report builders), as a
 * PURE-JVM library, exactly like :capability-core. Being SDK-free is the point: the
 * descriptor bytes and the report bit-math are the load-bearing pieces of Slice 4
 * (a wrong bit means a dead button on the receiving device), so they compile and
 * unit-test in a plain JVM and CI proves them green without an Android toolchain.
 *
 * The Android transport (../app/.../BluetoothHidDeviceTransport.kt) consumes this
 * module for its SDP descriptor and every input report it sends.
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
