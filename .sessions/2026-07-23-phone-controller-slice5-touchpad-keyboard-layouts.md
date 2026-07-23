# Session — phone-controller Slice 5: touchpad-mouse + full keyboard + layout presets (builder lane)

> **Status:** `complete`

📊 Model: fable-5 · high · feature build

Time: 2026-07-23 · lane: builder (phone-controller · slice 5) · owner-live directive
(same conversation as Slice 4)

💡 Session idea: the owner playtested v0.4.0 against a laptop within the hour —
pairing, keyboard pad and emulator-keys flow all confirmed working over the air — and
asked for three growth features in the same breath: *"the ability to move the mouse on
my pc or move a cursor on another device … by moving my finger across my screen, as
well as a full scale keyboard … and some differently controller layouts."* That is
Slice 5: a Report-4 relative mouse + touchpad surface, a full QWERTY pad, and a
layout picker with controller presets.

## previous-session review

Slice 4 (PR #33, squash `ccb1e98`, tag `phone-controller-v0.4.0`) shipped the usable
controller and the release pipeline; the Release is live with the APK + sha256
(android-release run 30044359167). Owner field results, same day: laptop pairing ✓,
keyboard input ✓, GBA emulator driven via keys ✓; one open diagnosis — the gamepad
collection didn't auto-register in their (still unnamed) emulator; keyboard from the
same combo descriptor works, so the split points at emulator-side
DirectInput/mapping, with the hardwaretester.com/joy.cpl check handed to the owner.
Not blocked on it: this slice's features are orthogonal, and a
dpad-as-axes/gamepad-only-mode toggle stays a candidate follow-up if the tester
result comes back negative.

## What this build does

**A · `:hid-core` — Report 4: relative mouse.** Combo descriptor grows a standard
mouse collection (3 buttons + 5-bit pad, X/Y/wheel as signed relative bytes; payload
`[buttons, dx, dy, wheel]`). `MouseButton` (LEFT/RIGHT/MIDDLE on bits 0/1/2),
`MouseState` (held-buttons byte + clamped-delta report builder). `KeyUsage` grows the
full key map (`letterUsage`/`digitUsage` helpers + punctuation/nav usages) for the
QWERTY pad. Tests updated (4 report IDs, 5 collections) + new mouse/key-map suites.

**B · Transport mouse APIs.** `mouseButton` (hold-capable — hold Left + drag =
drag-select), `mouseMove(dx,dy)`, `mouseScroll(notches)`, `mouseClick` (tap pair) —
same `@Synchronized`/`liveHost` pattern as the rest; all-release on disconnect/stop
now zeroes the mouse buttons too.

**C · Touchpad pad.** New `TouchpadView`: one-finger drag moves the pointer
(dp-normalized with fractional-remainder carry, so speed feels the same across screen
densities; sensitivity slider 0.5–3.0×, persisted), quick tap = left click,
two-finger tap = right click, two-finger drag = natural scrolling (content follows
fingers), plus hold-capable Left/Middle/Right buttons under the surface. On an
Android target this is a literal on-screen cursor — Android shows a system pointer
whenever a BT mouse connects.

**D · Full keyboard pad.** Five-row QWERTY (digits, letters, punctuation, arrows,
Esc/Tab/Backspace/Delete/Enter/Space) with hold-capable Shift/Ctrl/Alt — two-thumb
chords like a physical keyboard; key auto-repeat comes free from the host OS's
typematic handling of held HID keys.

**E · Layout picker.** The three fixed tabs become a persisted spinner of six
layouts: **Full gamepad** (Slice-4 pad) · **GBA pad** (D-pad, B/A, L/R,
Select/Start — matched to the owner's GBA-emulator use) · **Touchpad** · **Keyboard**
· **Emu keys** (Slice-4 keys pad) · **Media**. The customizable layout *editor*
remains the roadmap differentiator; presets are its cheap forerunner.

**F · Slide-over game pads (mid-slice live ask).** The owner's playtest surfaced the
classic controller-feel gap: sliding a held finger from one button to the next did
nothing (each button captured its own touch stream from finger-down). New
`SlidePadRouter`: the game pads (Full gamepad · GBA · Emu keys) route ALL touches at
the pad root, hit-test every active finger per event, and press/release on the diff —
D-pad glide works like a physical pad, multi-finger chords keep working. The QWERTY
pad deliberately keeps per-key holds (glide across a typing surface would spam
letters); media keeps taps.

**G · Landscape + rotation-safe connection (mid-slice live ask).** The owner holds
the phone in landscape; the Slice-4 portrait stack squeezed the pad there. Landscape
now gets a compact scrollable side panel (status + actions + layout picker) with the
pad at full height — and the activity opts out of rotation restarts
(`configChanges` + manual rebuild) because a recreate would tear down the HID
registration and drop the live host connection mid-game.

**H · Version + docs.** v0.5.0 (versionCode 3); product + android READMEs updated
(features, gesture table, four-report table, layout list); heartbeat + this card.

## Owner field results folded in (v0.4.0, same conversation)

Laptop host: pairing ✓ · keyboard input ✓ · GBA emulator via keys ✓ · **gamepad
reports confirmed live on hardwaretester.com** ✓ — so the earlier "gamepad not seen
by the emulator" is emulator-side input binding (DirectInput/manual mapping), not the
HID device. README now says so; exact mapping steps go to the owner once they name
the emulator.

## Verify — results (run locally this session, before landing)

Same manual toolchain as Slice 4 (kotlinc 2.0.21 embeddable + JUnit console + SDK
build-tools 34; CI re-proves on the canonical Gradle lanes):

- **Pure-JVM lanes — 36/36 tests green** (13 capability lockstep + 23 hid-core:
  descriptor invariants now pin FOUR report IDs / five collections, mouse state,
  key-map helpers).
- **App module compiles against android.jar** (platform-34, jvm-target 17) — all six
  layouts, SlidePadRouter, TouchpadView, landscape scaffolding, transport mouse APIs.
- `python3 bootstrap.py check --strict` — green at flip.

## Decide-and-flag

- **Relative mouse (not absolute/digitizer)** — relative is what PC/Android hosts
  accept from BT mice universally; absolute-touch is a different device class with
  spottier host support. Reversible: a digitizer collection could ride a later
  report ID.
- **Natural scrolling** (content follows fingers) — phone-native expectation;
  flagged here since traditional-wheel users may want an invert toggle later.
- **Modifiers are hold-capable, not sticky** — matches every other control's
  press/release semantics and needs no state machinery; sticky-keys noted as a
  possible accessibility follow-up.
- **Spinner over tab row** — six layouts no longer fit a button row on narrow
  screens; selection persists via activity prefs.

## Guard recipes

- Mouse feel is host-influenced (pointer acceleration differs per OS) — the
  sensitivity slider is the tuning surface; if a host feels wrong, tune there before
  touching `TouchpadView` gain math (`GAIN_DP` constant, one place).
- The descriptor-invariant tests pin report IDs 1–4 and the collection count; any
  descriptor edit that fails them is a receiver-facing wire-format change and needs
  a re-pair on targets to take effect (delete + re-pair the BT device when testing
  descriptor changes — hosts cache HID descriptors per bond).

## Verify

```bash
cd products/phone-controller/android
gradle :capability-core:test :hid-core:test   # SDK-free lanes (CI: capability-core job)
gradle :app:assembleDebug                     # CI: assemble-app job (+ APK artifact)
python3 bootstrap.py check --strict           # substrate gate, locally
```
