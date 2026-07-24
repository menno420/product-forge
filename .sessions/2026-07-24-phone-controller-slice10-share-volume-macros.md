# Session — phone-controller Slice 10: layout sharing, volume-key triggers, macros, turbo rate, scroll invert (builder lane)

> **Status:** `in-progress`

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

_(fill on completion)_
