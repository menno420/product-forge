# Session — phone-controller Slice 6: layout editor, analog sticks, turbo/haptics, ergonomics (builder lane)

> **Status:** `in-progress`

📊 Model: fable-5 · high · feature build

Time: 2026-07-23 · lane: builder (phone-controller · slice 6) · owner-live directive
(same conversation as Slices 4–5 + the market-research pass)

💡 Session idea: owner reply to the research gap plan: *"yes, implement all additions
you mentioned."* This slice ships the P1/P2 set (and the cheap P3 pieces): custom
layout editor with per-button turbo + haptics, on-screen analog sticks (descriptor
rev), gyro→right-stick, dim/immersive session modes, per-host layout memory +
auto-reconnect, the stale-pairing warning promised after the owner's touchpad
report, keyboard F-row, and a measured input-path latency stat.

## previous-session review

v0.5.0 shipped (PR #34, tag live) + market research merged (PR #35). Owner field
results: everything works on the laptop except the touchpad — diagnosed as the HID
descriptor cache (host paired against the v0.4.0 three-report descriptor; Report 4
dropped), fix = re-pair both sides. That incident is why this slice adds the
per-bond descriptor-hash warning. Research verdict: the serverless-gamepad rivals
are weak on exactly analog sticks + customization; Monect's server-based moat is
gyro + layout editor + macros.

## Scope (decide-and-flag deviations from the research wording)

- **Right stick rides Z/RZ**, not RX/RY — Android documents AXIS_Z/AXIS_RZ as the
  right analog stick, so this maps cleanly on Android receivers with no custom
  keylayout. *(Deviation flag:)* **analog triggers deferred** — retro emulators
  don't consume them, digital L2/R2 bits already exist, and skipping them keeps the
  descriptor on one usage page. One descriptor rev = one re-pair, softened by the
  new stale-pairing warning shipping in the same APK.
- **Macros + long-press key alternates deferred** (P3 tail): a record/playback
  engine is its own slice; noted in the README ladder.
- Turbo is a **custom-layout, per-button flag** (10 Hz) — built-in presets stay
  predictable.

## What this build does

**A · hid-core:** gamepad report grows to 7 bytes — [buttons ×2, hat, X, Y, Z, RZ]
(right stick centered 0); `GamepadState.setLeftStick/setRightStick`; tests updated
(report length, right-stick clamp, neutral).

**B · Analog pad + StickView:** new layout — two circular thumbsticks (drawn
Canvas views: base ring + thumb, configurable deadzone from settings, auto-center
on release) + slide-over A/B/X/Y, L1/R1/L2/R2, Select/Start. **Gyro→R-stick**
toggle on the pad (game-rotation-vector deltas → Z/RZ, baseline captured at
enable; unregisters on disable/pad-switch/disconnect).

**C · Custom layout editor:** layouts are named lists of percent-positioned
buttons (`layout/CustomLayout.kt`, org.json codec — no new deps;
`LayoutStore.kt` in prefs). `CustomPadView` renders them; play mode reuses the
slide-router semantics (+ per-button turbo via `TurboEngine`, 10 Hz); edit mode =
drag to move (1% snap), long-press a button → dialog (label, action picker across
gamepad/dpad/keys/modifiers/media/mouse, size S/M/L/XL, turbo, delete), toolbar
add/rename/save. Custom layouts join the layout spinner; "Layouts…" manager in
Settings (new/edit/delete).

**D · Ergonomics:** `Dim` (window-brightness floor toggle) and `Immersive`
(hide bars) toggles; haptic tick on press (global toggle, VIRTUAL_KEY feedback —
no permission); per-host layout memory (auto-switch on connect) + silent
auto-reconnect to the last host after registration; **stale-pairing warning** —
per-bond descriptor hash remembered, mismatch on connect surfaces "paired before
the last update → re-pair" in the status detail.

**E · Keyboard reach + measurement:** F1–F12 row on the QWERTY pad; transport
keeps a rolling average of sendReport() wall-time, surfaced in Settings as the
input-path latency stat (measured, not asserted — README ladder #10 seed).

**F · Version + docs:** v0.6.0 (versionCode 4); READMEs updated (7-byte report
table, Analog pad, editor how-to, **re-pair note**, new toggles).

## Guard recipes

- Descriptor changed again → hosts paired on ≤0.5.0 need one unpair/re-pair; the
  in-app warning now says so on connect. When testing descriptor changes, always
  delete the bond on BOTH sides.
- StickView deadzone is applied UI-side (StickView), NOT in GamepadState — keep it
  that way so the state stays a pure wire-format builder.
- TurboEngine must release on pad switch/disconnect: `cancelAll()` is called from
  showPad() and the disconnect callback — if a stuck rapid-fire ever appears,
  check those two call sites first.

## Verify

```bash
cd products/phone-controller/android
gradle :capability-core:test :hid-core:test
gradle :app:assembleDebug
python3 bootstrap.py check --strict
```
