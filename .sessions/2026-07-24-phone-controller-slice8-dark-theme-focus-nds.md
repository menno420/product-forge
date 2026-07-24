# Session — phone-controller Slice 8: dark theme, focus mode, app background, NDS pad (builder lane)

> **Status:** `in-progress`

📊 Model: fable-5 · high · feature build

Time: 2026-07-24 · lane: builder (phone-controller · slice 8) · owner-live directive
(same conversation as Slices 4–7)

💡 Session idea: owner sent two screen recordings of v0.7.0 plus asks — *"it could be
made to look a little better tho the core visuals are pretty good"*, *"I'd like to be
able to change the background"*, *"hide the main settings buttons etc, so you can go
into pure controller mode without any distractions"*, *"a layout specifically for a
Nintendo ds, so with a touchpad and buttons combined"*, and a standing *"if you notice
anything you can improve, go ahead."* The recordings show the app in the stock light
theme (grey chips on white, big title bar) and `SELECT` wrapping to two lines.

## previous-session review

v0.7.0 shipped (PR #37, tag live, release verified): per-button colors/shapes/opacity/
text-size, size steppers, duplicates, per-layout backgrounds, ABXY tints. No
descriptor change since v0.6.0, so bonds carry over. Owner has not yet re-tested on a
second device; these asks came from on-phone UI review.

## What this build does

**A · Dark controller theme (recordings ask "look better"):** manifest theme →
`Theme.DeviceDefault.NoActionBar` (dark widgets + dark dialogs, title bar gone);
app-wide dark background; status/nav bars tinted to match; action buttons restyled as
compact dark chips; pad buttons get flat rounded dark styling with pressed states
(lighten-on-dark / darken-on-color), inset so grids keep their gutters; A/B/X/Y tints
go solid console colors. Labels auto-size — `SELECT` never wraps again.

**B · Focus mode ("pure controller mode"):** one chip hides ALL chrome (status,
detail, action rows, layout row / landscape side panel) and auto-enters immersive;
only the pad remains plus a small translucent ⛶ chip (top-center) to exit. Guarded
against entering while the editor is open.

**C · App background:** Settings → App background… applies a color to the whole app
(all built-in pads); custom layouts' own Background… still overrides while active.
Palette extended (adds deep purple / charcoal).

**D · NDS pad (new built-in, appended so saved `b:<ordinal>` keys stay stable):**
DS-shaped combo — L/Select/Start/R rail, touch-screen area, speed slider + **Pen**
toggle, D-pad + X/A/B/Y diamond (Nintendo positions). Pen mode = stylus semantics:
finger down → LEFT press, drag → draw, lift → release (drag-accurate on DS emulators);
toggle off for hover/reposition. Portrait and landscape arrangements.

**E · Editor polish:** Color… becomes a real swatch grid (colored circles + Default),
not a text list. Bluetooth-off now says so honestly instead of leaving a misleading
fallback verdict on screen.

Wire format untouched — **no descriptor change, no re-pair needed** (v0.6.0+ bonds
keep working; saved layouts load unchanged). Version 0.8.0 (versionCode 6).

## Verification plan

hid-core/capability-core untouched (suites stay green); app compiled locally with the
Slice-4 kotlinc+android.jar+d8 pipeline before push; `python3 bootstrap.py check
--strict` green; CI (capability-core+hid-core tests, assemble-app, substrate-gate,
check) green on the PR; release cut by `phone-controller-v0.8.0` tag after merge.

## Result

_(fill on completion)_
