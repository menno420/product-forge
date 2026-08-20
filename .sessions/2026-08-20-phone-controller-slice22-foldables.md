# Session — phone-controller Slice 22: foldable support (fold-safe connection + per-screen layout memory)

> **Status:** `in-progress`

📊 Model: fable-5 · high · feature build

Time: 2026-08-20 · lane: builder (phone-controller · slice 22) · owner directive
2026-08-16 via continuation prompt — the same ask as Slice 21, second half:
*"There should also be some extra support for foldable phones etc."*

💡 Session idea: designed BEFORE code on the Slice-21 card
(`.sessions/2026-08-20-phone-controller-slice21-hardware-keyboard.md` § D5 —
options, choice, reasons; this card executes that design). What is actually
broken (REASONED from the platform contract + the manifest's own rotation
comment): fold/unfold changes `smallestScreenSize` (cover ↔ inner display)
and usually `density`, neither declared in `configChanges` — so the activity
RECREATES, `onDestroy → transport.stop()` unregisters the HID app, and the
live host connection drops mid-game. The percent layouts already reflow; the
missing piece is surviving the transition, plus one visible feature.

## Scope (decide-and-flag)

1. **Config-change hardening** (the foundation): `smallestScreenSize|density`
   join `configChanges`; the existing `onConfigurationChanged → buildUi()`
   path re-renders everything percent-based at the new size. AndroidX
   WindowManager posture APIs REJECTED (§ D5a) — the app is measured
   AndroidX-free (Slice-3 choice) and hinge-aware features are not worth
   bending that for a one-full-screen-pad app; recorded as the explicit
   non-bend, not silently skipped.
2. **Per-screen layout memory** (the visible feature, § D5c): the app
   remembers the last layout per screen-size bucket —
   `smallestScreenWidthDp < 600` (phone / cover screen) vs `>= 600`
   (unfolded inner / tablet) — exactly like per-host memory, and switches on
   fold/unfold: compact pad on the cover screen, full pad inside,
   automatically. `smallestScreenWidthDp` is rotation-invariant, so rotation
   can never trigger a spurious switch. Precedence is event-ordered (LOW,
   decided): connect applies host memory, fold applies screen memory, last
   event wins. The editor is never yanked (no switch while editing).
   Bucket memory stays OUT of backup (device-geometry-specific, same
   exclusion class as `desc_*` / `host_layout_*`).

Version: v0.22.0 (versionCode 20). No HID descriptor change → installs in
place, no re-pair.

## Verification plan

Same battery as Slice 21: Python 26/26 · gradle `:capability-core:test`
`:hid-core:test` · `compile-check.sh` (baseline now 112/24) · gate real exit
code · PR READY + `do-not-automerge` at creation · Codex to ANSWERED ·
land on green · tag `phone-controller-v0.22.0` → release verification
(assets + sha256 + stable-keystore line + signer-cert match vs v0.21.0).

Owner device steps (no foldable in this venue — the fix class is REASONED):
on a foldable, connect, fold and unfold mid-session — the connection must
survive and the pad re-lay; pick different layouts folded vs unfolded, fold
again — each screen restores its own last layout.

## Result

*(filled at close)*
