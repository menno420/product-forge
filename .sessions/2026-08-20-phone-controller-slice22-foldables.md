# Session — phone-controller Slice 22: foldable support (fold-safe connection + per-screen layout memory)

> **Status:** `complete`

📊 Model: fable-5 · high · feature build

Time: 2026-08-20 · lane: builder (phone-controller · slice 22) · owner directive
2026-08-16 via continuation prompt — the same ask as Slice 21, second half:
*"There should also be some extra support for foldable phones etc."*

## Previous-session review

Slice 21 shipped minutes ago, same session (pf #52 squash `e69afe5`,
v0.21.0): release verified in full — APK + sha256 assets (`feaf1d73…`
two-way), the "signing: stable repo keystore" line in run 32409734439, and
the signer cert MEASURED identical v0.20.0 ↔ v0.21.0 (sha256 `7bda3340…`,
v2 signing-block parse of both APKs). product-forge `main` = `e69afe5`;
this branch starts there. The heartbeat future-stamp red that hit #52's
`check` job (353 s ahead — heartbeat-guard rejects future stamps) is the
one CI red this session caused; stamped real UTC since.

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

Shipped as PR #53 (v0.22.0, versionCode 20). Both scope items landed:
`smallestScreenSize|density` configChanges hardening (the fold no longer
recreates the activity and drops the connection — REASONED fix class,
owner device steps above) and per-screen layout memory (sw600 bucket,
rotation-invariant, written by every showSelection, restored on bucket
flips through the full guard path).

**Codex: two rounds — round 1 two findings, [conceded] ×2; round 2 clean**
("Didn't find any major issues", on `8eeda27`). Round 1 (on `e743cf0`,
~6.5 min after the explicit trigger): (1) process death is not fold state —
onCreate restarting on the other screen overwrote that bucket's memory
with the global selection; fixed by retargeting at THIS screen's memory
via the shared `applyScreenBucketSelection()` before the first buildUi.
(2) a fold arriving while the editor was open silently consumed the
bucket flip, so cancelling wrote the old screen's pre-edit selection into
the new bucket; the flip now goes PENDING — cancel restores the new
screen's own memory, any explicit selection (Save / spinner pick)
consumes the flip, last event wins.

Verification: Python 26/26 · JVM 76/76 · compile-check OK 112/24 · gate
red exactly on this card until this flip (REAL exit codes — an earlier
in-session $?-after-a-pipe misread is named in the fm card, not hidden) ·
CI green on the feature head · squash-merge on green · tag
`phone-controller-v0.22.0` → release verification recorded at tag time
(assets + sha256 + stable-keystore line + signer-cert match vs v0.21.0).
