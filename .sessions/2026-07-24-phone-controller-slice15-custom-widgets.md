# Session — phone-controller Slice 15: custom-layout widgets + starter templates (builder lane)

> **Status:** `in-progress`

📊 Model: fable-5 · high · feature build

Time: 2026-07-24 · lane: builder (phone-controller · slice 15) · owner-live directive
(same conversation as Slices 4–14)

💡 Session idea: owner field-tested v0.14.0 (NDS game on a tablet) and found two real
gaps — "the preset layouts aren't customisable" and "for a custom layout it's not
possible to add the sticks or gyro inputs." Root cause: the built-in pads use rich
widgets (StickView, gyro toggle, touchpad) but a CustomLayout holds ONLY buttons, and
the presets aren't CustomLayouts at all. This slice makes custom layouts hold those
widgets, and adds preset-like starter templates.

## previous-session review

v0.14.0 shipped (PR #45): gyro target/visualizer/recenter. Owner confirms the app
looks high-end; connected-play field test now happening (this feedback is from it).

## What this build does

**A · Widget model (CustomLayout):** new `PadWidgetType {LEFT_STICK, RIGHT_STICK,
TOUCHPAD, GYRO}` + `PadWidgetSpec` (percent x/y/w/h). `CustomLayout.widgets`
(optional list; JSON "widgets" written only when non-empty → v≤0.14 layouts load
unchanged). `duplicate()`/`fromShareString()` carry widgets. A `PadPositioned`
interface (xPct/yPct/wPct/hPct + clampToPad) is implemented by BOTH button and widget
specs so the editor's drag logic is shared.

**B · CustomPadView renders widgets:** PLAY mode wires live views to the host —
LEFT/RIGHT stick → StickView → onLeftStick/onRightStick (deadzone from Settings),
TOUCHPAD → TouchpadView → mouse, GYRO → a toggle button (click = arm/disarm via the
shared GyroToggle, long-press = recenter). EDIT mode renders labelled placeholder
boxes (non-interactive, draggable). onLayout positions widgets by percent alongside
buttons. Sticks/touchpad consume their own touches, so they coexist with the button
SlidePadRouter.

**C · Editor gains widgets:** toolbar "+ Widget" → type picker → places it → config
dialog (change type / size S–XL / delete). Drag + short-press reuse the generalized
EditTouch (buttons AND widgets via PadPositioned).

**D · Starter templates:** the New-layout flow first picks a template — Blank, GBA
(the old default), Full gamepad, or Analog (two stick widgets + gyro widget + face
buttons) — so a custom layout can begin looking like a preset and then be edited.
This is the practical answer to "customise a preset" without cloning the hardcoded
preset builders (a literal preset→CustomLayout clone can follow later if wanted).

Wire format untouched — no HID descriptor change, no re-pair. Widgets that emit HID
(sticks) reuse existing transport calls; GYRO reuses the Slice-14 routing. Version
0.15.0 (versionCode 13).

## Verification plan

hid-core suites unchanged (59/59 stay green — app-only); app compiles vs android.jar
via the saved pipeline; gate green; CI green on PR; merge → tag
`phone-controller-v0.15.0` → stable-signed release.

## Result

_(fill on completion)_
