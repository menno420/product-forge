# Session — phone-controller Slice 10: layout sharing, volume-key triggers, macros, turbo rate, scroll invert (builder lane)

> **Status:** `complete`

📊 Model: fable-5 · high · feature build

Time: 2026-07-24 · lane: builder (phone-controller · slice 10) · owner-live directive
(same conversation as Slices 4–9; owner: "Continue building the most valuable
pieces you can start on now" after the QoL-ideas rundown)

💡 Session idea: the highest-leverage free-tier batch from the ideas shelf — the
pieces that compound with the community-launch plan (shareable layouts) and the
emulator use-case (physical shoulder buttons, macros), plus two tiny long-promised
toggles (turbo rate, scroll invert). Voice commands deliberately deferred to their
own slice (mic permission + recognizer lifecycle deserve isolated testing).

## previous-session review

v0.9.0 shipped and RE-SIGNED with the stable keystore (OA-005 closed, PR #40);
in-place updates hold from v0.9.0 onward. Slice-9 guard recipes carried:
append-only Pad enum, fail-soft codecs, cancel-on-disconnect runners, label→handler
dialog lists.

## What this build does

**A · Layout share/import (free — community moat):** layout-manager grows Share…
(Android share sheet + copy-to-clipboard; versioned envelope
`{"pcl":1,"layout":{…}}`, compact JSON) and Import… (paste dialog → fail-soft
parse of envelope OR bare layout JSON → NEW id assigned, "(imported)" name suffix
on collision → saved + selectable). No QR: a QR encoder means a new dependency —
text share covers Discord/Reddit/WhatsApp, the actual sharing venues
(decide-and-flag: QR only if a no-dep encoder lands later).

**B · Volume buttons as triggers (free):** Settings picker OFF / L1+R1 / L2+R2 /
PgUp+PgDn (the presenter pair). onKeyDown/onKeyUp intercept VOLUME_UP/DOWN only
while enabled AND connected AND not editing; repeatCount>0 ignored (holds ride the
initial down/up pair); everything else passes through to the system.

**C · Macros (free):** `PadActionType.MACRO`, code = JSON step array
`[{"t","c","l","h","g"},…]` (action type/code/label, holdMs, gapMs). MacroRunner =
single background worker (one macro at a time, busy-guard; press/release per step
via the shared raw-action resolver; cancel on pad switch/disconnect/destroy —
same guard recipe as TextTyper/turbo). Editor: macro builder dialog (add action
step via the action picker, add pause, remove last, save); turbo is refused on
MACRO buttons (semantics collision). No nesting (MACRO excluded from step types).

**D · Turbo rate (free):** Settings slider 5–20 Hz (default 10) →
TurboEngine.halfPeriodMs.

**E · Scroll invert (free):** Settings toggle; TouchpadView flips notch sign;
applies to Touchpad, NDS, Presenter pointer strips via TouchpadConfig.

Wire format untouched — **no descriptor change, no re-pair**; share format is
versioned for forward compatibility. Version 0.10.0 (versionCode 8). Pad enum
untouched (no new pads).

## Verification plan

hid-core suites unchanged (45/45 must stay green); app compiles vs android.jar
via the saved local pipeline; bootstrap gate green; CI green on PR; merge → tag
`phone-controller-v0.10.0` → release verified (now stable-signed by default).

## Result

Shipped, all five blocks (A–E). Local verify green: 45/45 JVM tests (hid-core
untouched), app compiles vs android.jar (61 classes). Session survived a usage-
limit pause mid-slice — all edits were already on disk in the workspace clone
(born-red card committed first, per convention: nothing was lost).

Guard recipes: MacroRunner releases the in-flight step on cancel and is cancelled
on pad switch / disconnect / destroy (same contract as TextTyper/turbo); macro
steps are fail-soft parsed, capped at 64, and MACRO-in-MACRO is filtered at BOTH
the parser and the resolver; volume-key interception only while mapped AND
connected AND not editing (repeatCount>0 ignored), everything else passes to the
system; AlertDialog setMessage+setItems conflict avoided (summary rides as a list
row); layoutOptions rebuilt as label→handler pairs (drift-proof, same as
buttonConfigDialog); share import assigns a fresh id and suffixes colliding names.

v0.10.0 (versionCode 8) — first release born under stable signing (installs over
v0.9.0 in place). PR/tag/release: control/status.md heartbeat.
