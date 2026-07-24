# Session — phone-controller Slice 13: gesture recorder + GESTURE binding (builder lane)

> **Status:** `complete`

📊 Model: fable-5 · high · feature build

Time: 2026-07-24 · lane: builder (phone-controller · slice 13) · owner-live directive
(same conversation as Slices 4–12; owner: "Yes go ahead" on the OP-style recorder)

💡 Session idea: stage 2 of the owner's OP-Auto-Clicker architecture — record a
real finger path once, save it named, bind it to an overlay button; pressing the
button replays the swipe on the game underneath. Layers on the Slice-12 gesture
model exactly as designed (a recorded swipe is just a multi-point stroke).

## previous-session review

v0.12.0 shipped (PR #43): pure gesture core (53/53), inject-only accessibility
service, per-button overlay windows with tap/hold-at-position, "Play on this
phone…" entry. Owner has not yet field-tested overlay mode (needs a game).

## What this build does

**A · TouchGestureCodec (hid-core, pure JVM, CI-TESTED):** versioned compact text
form for a TouchGesture — `g1|start:dur:x,y;x,y…|…` — encode/decode round-trip
exact to 4 decimals, fail-soft decode (null on malformed), point-count cap. Doubles
as the future share format for gestures.

**B · SavedGesture + GestureStore (app):** named gestures in prefs JSON
(fail-soft, LayoutStore pattern): id, name, codec string, duration summary.

**C · Recorder (app):** `GestureRecorderView` — full-screen capture surface
(dialog on the phone screen, no overlay permission needed to author): one finger
draws a path; MOVE samples become percent points with real timing (points thinned
to ≥8 ms / ≥0.5% spacing, capped); the path renders live; lifting ends the take.
Buttons: Save (name prompt), Retry, Cancel. A tap (no movement) records as a
tap-at-point; long-press-in-place records a timed hold.

**D · `PadActionType.GESTURE`:** code = saved-gesture id. Editor: action picker
gains GESTURE → list of saved gestures + "Record new…". In HID (remote) mode a
GESTURE button is INERT by design (resolveRaw returns no-op — gestures only mean
something on this phone's screen; documented, not hidden).

**E · Overlay dispatch:** OverlayPlayService — a GESTURE button replays its saved
gesture verbatim (positions are absolute screen percents from the recording);
other buttons keep Slice-12 tap/hold-at-position. Missing/deleted gesture = no-op.

**F · Manager:** Settings → "Recorded gestures…" — list (name + duration), tap →
Rename / Delete. One entry, same dialog grammar as everything else.

Deferred (recorded): per-game auto profiles, multi-finger recording, gesture
share/import (codec is ready for it), Shizuku backend.

Wire format untouched — no descriptor change, no re-pair. Version 0.13.0
(versionCode 11).

## Verification plan

hid-core suite grows (TouchGestureCodecTest round-trip/fail-soft) and stays green;
app compiles vs android.jar; gate green; CI green; merge → tag
`phone-controller-v0.13.0` → stable-signed release verified.

## Result

Shipped, all six blocks (A-F). Durable codec is CI-PROVEN: hid-core 59/59 (+6
TouchGestureCodecTest — round-trip exact to the 4-decimal grid, multi-point/timing
survival, fail-soft null on malformed/unknown-version/oversized, decode clamping).
App compiles vs android.jar (79 classes).

Guard recipes: GESTURE inert in HID/remote resolveRaw (and excluded from macro/
voice step pickers) — meaningful only in overlay mode; overlay TapDispatch resolves
the bound gesture ONCE at bind time (missing/deleted → tap-at-position fallback);
recorder thins samples (≥0.5% space, ≤200 pts/stroke) so long drags stay under the
codec cap, and floors tap durations to 50 ms so dispatched taps register; recorder
runs as a full-screen Dialog on THIS phone (no overlay permission just to author);
manager + pickers all label→handler / index-safe.

Model note: a recorded swipe is exactly a multi-point GestureStroke — the Slice-12
model needed zero changes, as designed. Codec doubles as the future gesture-share
format (same envelope philosophy as layouts).

v0.13.0 (versionCode 11), stable-signed, no HID descriptor change → installs in
place, no re-pair. Deferred (recorded): per-game auto-profiles, multi-finger
recording, gesture share/import UI, Shizuku. PR/tag/release: status heartbeat.
