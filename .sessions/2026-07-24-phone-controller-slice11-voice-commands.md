# Session — phone-controller Slice 11: voice commands + overlay/mapper research (builder lane)

> **Status:** `complete`

📊 Model: fable-5 · high · feature build

Time: 2026-07-24 · lane: builder (phone-controller · slice 11) · owner-live directive
(same conversation as Slices 4–10; owner: "Start with voice commands and do some
research about what the possibilities are for the overlay idea … while still
keeping the app simple to use, it shouldn't become bloated")

💡 Session idea: two deliverables. (1) Voice commands — user-defined phrases fire
any existing action (the accessibility/hands-free media-remote framing from the
ideas rundown; honest ~1 s latency documented, not hidden). (2) A committed
research doc on the overlay/local-mapper space (Octopus-style overlay→touch,
Mantis-style gamepad→touch, the two-phone combo unique to us), with the owner's
anti-bloat constraint as the design rule for any future build.

## previous-session review

v0.10.0 shipped (PR #41, first release born stable-signed — installs in place).
Owner tested the UI on-phone: "currently it looks very high end". Connected-mode
field test still pending (OA-004).

## What this build does

**A · Voice commands (free):** `VoiceControl.kt` — `VoiceCommand` (phrase +
action type/code + label), `VoiceStore` (prefs JSON, fail-soft like LayoutStore),
`VoiceDriver` (SpeechRecognizer: continuous listen loop with restart-on-result/
error backoff, offline-preferred, device locale — phrases are user-defined, so the
feature is language-agnostic by construction; matching = normalized containment
on FINAL results only, no partials → no false triggers). Fires as a ~120 ms tap
through the shared `resolveRaw` vocabulary (any key/combo/gamepad/media/mouse
action; macros excluded to keep semantics obvious). Foreground-only by design:
listening stops in onPause — private, battery-sane, simple to reason about.
RECORD_AUDIO requested only when the user enables the feature; deny → switch
stays off with an honest hint. UI = ONE Settings entry ("Voice commands…"):
enable switch + phrase list (tap to delete) + Add (phrase prompt → the existing
action pickers). No new chips, no new pads — anti-bloat.

**B · Overlay/mapper research doc:**
`products/phone-controller/docs/overlay-mapper-research-2026-07-24.md` — the
category map (Octopus overlay→touch, Mantis/Panda gamepad→touch via ADB-
privileged helpers, verified 2026-07-24), the two Android injection paths
(AccessibilityService dispatchGesture vs Shizuku-class wireless-debugging
helpers), the two-phone combo only we can do (phone A = our HID controller,
phone B = mapper), and a simplicity-first staging plan with explicit anti-bloat
rules. Research only — no overlay code this slice.

Wire format untouched — no descriptor change, no re-pair. Version 0.11.0
(versionCode 9). New manifest permission: RECORD_AUDIO (runtime, opt-in).

## Verification plan

hid-core suites unchanged (45/45 stay green); app compiles vs android.jar via the
saved pipeline; gate green; CI green on PR; merge → tag `phone-controller-v0.11.0`
→ stable-signed release verified.

## Result

Both deliverables shipped. Voice: VoiceControl.kt (store fail-soft, driver =
continuous SpeechRecognizer loop, FINAL results only, offline-preferred, one
action per utterance, foreground-only with onPause stop); ONE Settings entry
(switch + list + add via the existing pickStepAction pickers — zero new UI
concepts); RECORD_AUDIO requested only on enable, deny leaves the switch off
with an honest hint; fires as 120 ms taps via resolveRaw. Research doc:
docs/overlay-mapper-research-2026-07-24.md (category map w/ verified Mantis
mechanics, the two injection paths, the two-phone kit only we can build,
anti-bloat staging rules, explicit non-goals). Local verify green: 45/45 JVM
tests, app compiles vs android.jar (67 classes). v0.11.0 (versionCode 9),
stable-signed, no descriptor change → installs in place, no re-pair.
