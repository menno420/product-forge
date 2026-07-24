# Session — phone-controller Slice 14: gyro upgrade — selectable target, visualizer, recenter (builder lane)

> **Status:** `complete`

📊 Model: fable-5 · high · feature build

Time: 2026-07-24 · lane: builder (phone-controller · slice 14) · owner-live directive
(same conversation as Slices 4–13)

💡 Session idea: owner found gyro (Analog pad toggle) but "it's currently not very
obvious what the gyro controls actually manage" — wants to (1) LINK gyro to a chosen
input, (2) SEE how the input is displayed, and (3) set the neutral "0 input" level
for how the phone is held. The v0.6.0 gyro is a silent, fixed roll→Z/pitch→RZ →
right-stick pipe. This slice makes it configurable, visible, and recenterable.

## previous-session review

v0.13.0 shipped (PR #44): gesture recorder + GESTURE binding — the overlay feature
is complete. Stable signing since v0.9.0. hid-core 59/59 green.

## What this build does

**A · Selectable gyro target (the "link to an input" ask):** GyroDriver refactored
to emit a normalized tilt vector `(nx, ny)` in −1..1 (was hard-wired to right-stick
Z/RZ). MainActivity routes it to a chosen target:
  * Right stick (default — unchanged behaviour: nx→Z, ny→RZ)
  * Left stick (nx→X, ny→Y)
  * Mouse pointer (rate mapping: tilt = pointer velocity, remainder-carried like the
    touchpad — great for aiming on a PC/TV with no controller support)

**B · Live visualizer (the "see the input" ask):** new `GyroVisualizerView` (Canvas
dot-in-a-box + crosshair + magnitude readout). Lives in the Gyro settings dialog and
runs the sensor in PREVIEW mode while open, so you see the dot track your tilt
immediately — before connecting anything. Labels the current target + numeric output.

**C · Recenter / neutral (the "0-input for how you hold it" ask):** GyroDriver.recenter()
drops the baseline so the next sample becomes the new neutral. Exposed as a Recenter
button in the Gyro dialog AND a LONG-PRESS on the Analog pad's Gyro button (haptic
confirm) so you can reset neutral mid-game without leaving it.

**D · Sensitivity + invert:** Gyro dialog gets a sensitivity slider (maps
fullTiltDegrees 40°→10°, higher = reach full deflection with less tilt) and invert-X
/ invert-Y switches (all persisted). Needed for the mouse/left-stick targets to feel
right.

Entry: one new Settings → **Gyro…** dialog owns all of it (target picker,
sensitivity, invert X/Y, Recenter, live visualizer). The Analog-pad Gyro button stays
the arm/disarm control (now also long-press = recenter). Anti-bloat: no new pads, no
new chrome rows.

Wire format untouched — no HID descriptor change, no re-pair. Version 0.14.0
(versionCode 12).

## Verification plan

hid-core suites unchanged (59/59 stay green — this is app-only); app compiles vs
android.jar via the saved pipeline; gate green; CI green on PR; merge → tag
`phone-controller-v0.14.0` → stable-signed release.

## Result

Shipped, all four blocks (A-D) + recenter. GyroDriver refactored to emit normalized
(nx,ny) with invertX/Y + fullTiltDegrees; MainActivity routes to a selectable target
(right/left stick absolute, mouse rate-mapped with remainder carry). New
GyroVisualizerView (Canvas dot-in-box + crosshair + direction line). Settings →
Gyro… dialog: target picker, sensitivity slider (40°→10°), invert X/Y, Recenter, and
the visualizer running the sensor in PREVIEW while open (see it before connecting).
Analog-pad Gyro button: click = arm/disarm (now target-aware), long-press = recenter.

Guard recipes: armed vs preview split via syncGyroSensor() (sensor runs if either);
stopGyro() fully disarms on pad-switch/disconnect/editor/destroy; mouse remainders
reset on arm + target change so no pointer jump; driver stays target-agnostic (host
routes) so the wire format is untouched. Default target = right stick → v0.6.0
behaviour preserved for anyone who never opens the new dialog.

hid-core untouched (59/59 stay green); app vs android.jar 83 classes. v0.14.0
(versionCode 12), stable-signed, no HID descriptor change → installs in place, no
re-pair. PR/tag/release: control/status.md heartbeat.
