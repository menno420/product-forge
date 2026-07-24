# Session — phone-controller Slice 12: local-play overlay foundation (builder lane)

> **Status:** `in-progress`

📊 Model: fable-5 · high · feature build

Time: 2026-07-24 · lane: builder (phone-controller · slice 12) · owner-live directive
(same conversation as Slices 4–11; owner: after the OP-Auto-Clicker framing —
"start in the most valuable/structured order")

💡 Session idea: the overlay/local-mapper, stage 1 from the Slice-11 research doc,
built in dependency order so the risky Android service surface sits on a pure,
CI-proven core. Owner's OP-Auto-Clicker insight is exactly the architecture:
floating overlay + AccessibilityService dispatching taps/swipes; our trigger is a
button press (theirs is a timer). Anti-bloat rule from Slice 11 holds: ONE new
entry ("Play on this phone…"), everything else reuses the layout model.

## previous-session review

v0.11.0 shipped (PR #42): voice commands + the overlay research doc this slice
executes. Stable signing in place since v0.9.0 — installs land in place.

## What this build does

**A · Gesture geometry core (hid-core, pure JVM, CI-TESTED — the durable base):**
`TouchGesture` (list of timed percent-point strokes; `tap`/`hold`/`swipe`
builders), `GesturePoint`/`GestureStroke`, `GestureGeometry` (percent→pixel with
edge clamping, duration clamp, stroke densify). Single-point tap is just the
simplest gesture, so the Slice-13 recorder (multi-point swipes) layers on with no
rework. New `GestureGeometryTest`.

**B · TapAccessibilityService (app):** tracks display metrics; `dispatchTouch(
gesture)` builds a `GestureDescription` from the geometry pixel points and
dispatches it into whatever app is in front. Config XML (canRetrieveWindowContent
false — we only inject, never read the screen: privacy-clean). Held via a weak
static so the overlay can reach it.

**C · OverlayPlayService (app):** foreground service (type specialUse; persistent
"tap to stop" notification). Renders a chosen CustomLayout's buttons as INDIVIDUAL
`TYPE_APPLICATION_OVERLAY` windows (one per button) so gaps between them pass
touches through to the game — the correct passthrough architecture (Octopus/Mantis
model), not one full-screen window. Each button press → tap (or hold) at its own
saved position via the accessibility service. A small draggable ⛔ handle window
stops the overlay.

**D · Entry + permissions (MainActivity):** "Play on this phone…" dialog — pick a
layout, guided grants for Draw-over-apps (Settings.canDrawOverlays) and the
Accessibility service, then Start. Honest hint text on what each permission does
and that competitive-online games are unsupported.

Explicit Slice-13 defer: swipe RECORDER, `PadActionType.GESTURE` (saved gestures
bound to buttons), multi-finger, per-game profiles, optional Shizuku backend.

Wire format untouched — no HID descriptor change, no re-pair. New permissions:
SYSTEM_ALERT_WINDOW, FOREGROUND_SERVICE(+_SPECIAL_USE), BIND_ACCESSIBILITY_SERVICE
(on the service). Version 0.12.0 (versionCode 10).

## Verification plan

hid-core suite grows (GestureGeometryTest) and must stay green; app compiles vs
android.jar via the saved pipeline (res/xml added → aapt2 picks it up); gate green;
CI green on PR; merge → tag `phone-controller-v0.12.0` → stable-signed release.

## Result

_(fill on completion)_
