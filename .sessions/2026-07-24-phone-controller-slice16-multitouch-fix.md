# Session — phone-controller Slice 16: fix simultaneous multi-touch (A + steer) (builder lane)

> **Status:** `complete`

📊 Model: fable-5 · high · feature build

Time: 2026-07-24 · lane: builder (phone-controller · slice 16) · owner-live directive
(same conversation as Slices 4–15)

💡 Session idea: owner playing Mario Kart (NDS) found a game-breaking bug — "multiple
inputs at the same time don't work, so I can't press A for gas and steer at the same
time." Critical fix.

## previous-session review

v0.15.0 shipped (PR #46): custom-layout widgets (sticks/D-pad/gyro/touchpad) +
8-way D-pad + templates. That slice made the D-pad a consuming DpadView on the
built-in pads — which, combined with the long-standing StickView on the Analog pad,
exposed the multi-touch flaw this slice fixes (the router-vs-consuming-child
conflict). Stable signing since v0.9.0; installs land in place.

## Root cause

The game pads set a single `SlidePadRouter` as the OnTouchListener on the pad ROOT,
with buttons as non-clickable children (so one router could hit-test all fingers for
slide-over glide). Android delivers touches to a ViewGroup's own onTouch ONLY while
no child has claimed a pointer (`mFirstTouchTarget == null`). Since Slice 6/15 the
pads also contain CONSUMING child widgets — StickView, TouchpadView, and (Slice 15)
DpadView. The moment such a widget grabs a finger (e.g. holding the D-pad to steer),
the root router stops receiving the OTHER fingers, so a simultaneous A press is
dropped. The `hold()` buttons on the Touchpad/Media pads never had this bug because
each consumes its own finger and Android split-touch delivers every finger to the
child under it independently.

## The fix

Make EVERY button consume its own touch (the proven `hold()` press-on-down /
release-on-up pattern) instead of relying on the shared root router; delete
`SlidePadRouter`. Now each button, stick, D-pad and touchpad is an independent
consuming view, so Android's motion-event splitting delivers every finger correctly →
any combination works at once (A + D-pad, A + B, stick + buttons, …). D-pad glide and
diagonals are preserved because DpadView handles its own multi-point touch internally
(the owner's original Slice-5 "slide the arrow controllers without lifting" request
lives in DpadView, not the button router). The only thing lost is gliding a single
finger BETWEEN face buttons — a minor nicety, correctly traded for working multi-touch.

## What this build does

- `ControllerPads.Builder`: `slide()`/`slideTinted()` now attach the per-button
  consuming press/release listener (shared `holdTouch` helper with `hold()`);
  `slidePadRoot()` drops the router, just enables `isMotionEventSplittingEnabled` and
  returns the tree. `slideActions` map removed.
- `CustomPadView`: play mode drives each button via its own consuming listener
  (turbo-wrapped action) instead of a `SlidePadRouter`; widgets already consume;
  splitting enabled. Edit mode unchanged (EditTouch drag).
- `SlidePadRouter.kt` deleted (no references remain).

Wire format untouched — no HID descriptor change, no re-pair. Version 0.16.0
(versionCode 14).

## Verification plan

hid-core unchanged (59/59 stay green); app compiles vs android.jar via the saved
pipeline; gate green; CI green on PR; merge → tag `phone-controller-v0.16.0` →
stable-signed release. Owner to field-verify A + steer on Mario Kart.

## Result

Shipped. SlidePadRouter deleted; slide()/slideTinted()/hold() now share one
holdTouch() per-button consuming listener; slidePadRoot() enables tree-wide
motion-event splitting and returns the tree. CustomPadView play mode drives each
button via its own HoldTouch listener (turbo-wrapped) and enables splitting; edit
mode unchanged. Every button/stick/dpad/touchpad is now an independent consuming
view, so Android delivers each finger to the control under it — 4+ simultaneous
inputs (no artificial cap; limited only by the digitizer, ~10 points). D-pad glide
+ diagonals preserved inside DpadView. Only face-button-to-face-button glide is
gone (minor, correctly traded for working multi-touch).

hid-core 59/59 (unchanged); app vs android.jar 90 classes. v0.16.0 (versionCode 14),
stable-signed, no HID descriptor change → installs in place, no re-pair. Owner to
field-verify A + steer on Mario Kart. PR/tag/release: control/status.md heartbeat.
