/*
 * Fixed media-remote HID report descriptor + SDP metadata for the Slice-2 skeleton.
 *
 * Slice 2 ships ONE hard-coded HID device: a Consumer-Control "media remote"
 * (play/pause, next, prev, vol +/-, mute). Fully customisable layouts (keyboard,
 * mouse, gamepad, user-defined key maps) are Slice 3+ — this descriptor is
 * deliberately the smallest useful thing that proves the registerApp()/sendReport()
 * transport end to end.
 *
 * NOTE: this file is Android-app skeleton source. It is NOT compiled by the CI lane
 * (which builds only the pure-JVM :capability-core module); it compiles in Slice 3
 * once the Android/AGP build is wired in. Kept dependency-free (plain ByteArray +
 * constants) so it is trivially reviewable now.
 */
package com.productforge.phonecontroller.transport

/**
 * The USB-HID report descriptor for a single-report Consumer Control device.
 *
 * Report layout (Report ID 1, one input report, 1 byte = 8 button bits):
 *   bit0 Play/Pause · bit1 Scan Next · bit2 Scan Prev · bit3 Mute
 *   bit4 Volume Up · bit5 Volume Down · bit6 Stop · bit7 (reserved / constant pad)
 *
 * The bytes below are a standard Consumer-Control descriptor; each usage maps to the
 * matching bit in [MediaButton]. Receivers that accept a standard BT-HID consumer
 * device (per the Slice-1 receiver matrix: PCs, LG webOS, most 2022+ smart TVs) adopt
 * it with no companion app.
 */
object MediaRemoteHidDescriptor {

    /** HID Report ID used for every media report (single-report device). */
    const val REPORT_ID: Int = 1

    /** Length of the input report payload, in bytes (Report ID byte excluded). */
    const val REPORT_LENGTH_BYTES: Int = 1

    /**
     * Consumer-Control HID report descriptor bytes.
     *
     * Usage Page (Consumer) 0x0C, Usage (Consumer Control) 0x01, one Report ID,
     * seven named consumer usages as 1-bit inputs + a 1-bit constant pad.
     */
    val DESCRIPTOR: ByteArray = byteArrayOf(
        0x05.toByte(), 0x0C.toByte(), //   Usage Page (Consumer)
        0x09.toByte(), 0x01.toByte(), //   Usage (Consumer Control)
        0xA1.toByte(), 0x01.toByte(), //   Collection (Application)
        0x85.toByte(), REPORT_ID.toByte(), //     Report ID (1)
        0x15.toByte(), 0x00.toByte(), //     Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(), //     Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(), //     Report Size (1)
        0x95.toByte(), 0x07.toByte(), //     Report Count (7)
        0x09.toByte(), 0xCD.toByte(), //     Usage (Play/Pause)
        0x09.toByte(), 0xB5.toByte(), //     Usage (Scan Next Track)
        0x09.toByte(), 0xB6.toByte(), //     Usage (Scan Previous Track)
        0x09.toByte(), 0xE2.toByte(), //     Usage (Mute)
        0x09.toByte(), 0xE9.toByte(), //     Usage (Volume Increment)
        0x09.toByte(), 0xEA.toByte(), //     Usage (Volume Decrement)
        0x09.toByte(), 0xB7.toByte(), //     Usage (Stop)
        0x81.toByte(), 0x02.toByte(), //     Input (Data,Var,Abs)
        0x95.toByte(), 0x01.toByte(), //     Report Count (1)
        0x81.toByte(), 0x01.toByte(), //     Input (Const) — 1-bit pad to a full byte
        0xC0.toByte(),                //   End Collection
    )
}

/**
 * The seven media buttons, each carrying the input-report bit it toggles. A "press"
 * report sets the button's bit; the matching "release" report is all-zero.
 */
enum class MediaButton(val bit: Int) {
    PLAY_PAUSE(0),
    NEXT(1),
    PREVIOUS(2),
    MUTE(3),
    VOLUME_UP(4),
    VOLUME_DOWN(5),
    STOP(6);

    /** The single-byte input-report payload for this button held down. */
    fun pressReport(): ByteArray = byteArrayOf((1 shl bit).toByte())

    companion object {
        /** The all-zero payload: nothing pressed (a release). */
        val RELEASE_REPORT: ByteArray = byteArrayOf(0x00)
    }
}
