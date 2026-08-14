# Session — phone-controller Slice 18: deeper customization (per-widget config, long-press alternates, fine position, backup-everything)

> **Status:** `complete`

📊 Model: fable-5 · high · feature build

Time: 2026-08-14 · lane: builder (phone-controller · slice 18) · owner-live directive
(hub session booted in fleet-manager; product-forge attached mid-session — the
Slice-4 precedent)

💡 Session idea: owner ask, live: *"I want to further improve it this session, make
it more customizable etc"* — plus the repo question (new repo vs product-forge),
answered in-session: build here on the proven signed-release rails; graduation to a
dedicated repo stays the standing plan (fm program §2 + R2) as its own next step.

## Previous-session review

Slice 17 shipped 2026-07-24 (PR #48, v0.17.0, tag + stable-signed release live —
14 releases v0.4.0→v0.17.0 verified on the releases page): editor discard,
customize-a-preset, fine size sliders. `control/status.md` heartbeat matched HEAD
`81b65bd` exactly; no open PRs, no parked work. The repo has been quiet since —
the fleet program closed 2026-07-21 and the estate's work moved to fleet-manager
(kit v1.21.0 rollout finished 2026-08-14). The README's "Remaining candidates"
list predated Slices 10–17 and still offered five shipped features; corrected in
this slice's README pass (verified against code comments: voice = Slice 11,
overlay = Slice 12, per their own file headers).

## Scope (decide-and-flag, owner scope-freedom ruling)

Theme: the parts of a layout you still cannot customize, plus config portability.

1. **Per-widget behavior config** (PadWidgetSpec gains optional fields; JSON keys
   written only when non-default so old layouts round-trip unchanged):
   - Sticks: deadzone override (else the global Settings value) + invert Y.
   - D-pad: 4-way/8-way toggle (diagonals off for games that mis-read corners).
   - Touchpad: speed override + pen mode (DS-stylus semantics) per widget.
   - Fixes a real gap found reading the tree: custom-layout touchpads never
     received the global sensitivity/scroll-invert settings at all
     (CustomPadView built a bare TouchpadView; the built-in pads wire
     TouchpadConfig) — now they follow global settings with per-widget override.
2. **Long-press alternate action per button** (the one open item from the README's
   recorded candidate list): opt-in second action fired by holding past a
   threshold; tap still fires the primary. Turbo and alternates are mutually
   exclusive on one button (turbo's pulse would re-trigger the hold timer).
   MACRO/GESTURE excluded as alternates (timed-sequence-on-hold semantics are
   ambiguous; gestures are inert in HID mode) — same exclusion set as macro steps.
3. **Fine position sliders** — "Position…" in the button/widget dialog (X/Y live
   sliders), completing Slice 17's fine-size direction; drag stays the fast path.
4. **Backup all / Restore all** — one text blob (versioned `pcb` envelope) carrying
   every custom layout + gestures + voice commands + the Settings whitelist.
   Restore is id-preserving for layouts (a true restore, not an import-copy).
   This is also deliberate insurance for the R2 graduation: if the release
   signature ever changes (new repo, new keystore), uninstall→reinstall no longer
   loses the owner's layouts.

Version: v0.18.0 (versionCode 16). No HID descriptor change → installs in place,
no re-pair.

## Verification plan

`./test.sh` (26 Python, regression only — untouched); gradle
`:capability-core:test :hid-core:test` locally; app-module compile check against
an android-all jar locally (best-effort — CI's `assembleDebug` is the
authoritative app-module proof); `python3 bootstrap.py check --strict` real exit
code; PR → @codex review (wait for the ~5.5-min relay, read inline comments at the
exact head SHA); land on green `substrate-gate` (the required check) + green
`android-ci`; tag `phone-controller-v0.18.0` → verify the stable-signed release
asset + sha256 exist; poll check-runs to a terminal state.

## Result

Shipped as PR #49 (v0.18.0, versionCode 16). Local verification all green
before push: JVM suites **59/59**, Python **26/26**, `compile-check.sh` **100
classes / 22 source files** (the previously session-local no-SDK pipeline, now
committed at `android/compile-check.sh`), gate exit 0. CI on the feature head
`395e15b`: substrate-gate ✓ · check ✓ · capability-core ✓ · assemble-app ✓.

**Codex answered at 10:09:57Z — five findings (1×P1, 4×P2), all conceded and
fixed — and this card briefly carried a wrong conclusion worth keeping.** At
~10:13, with both documented triggers ~10 minutes unanswered against the 335 s
relay measured on fm #812, this session wrote *"the app is not installed here"*
into this card, `control/status.md` and `review-queue.md`, resting on a real
measurement (0 historical codex comments in product-forge vs 37 in
fleet-manager) that supported a weaker claim than the one written: **absence of
past use is not absence of installation, and one slow first-ever review is not a
wall.** The review landed while the close commit was being pushed; its own body
says the repo is set up. **What contained the error:** the PR was label-parked
(`do-not-automerge`) from creation, so nothing could merge during the wrong-belief
window — the fm #828 flip-trap discipline turned a wrong inference into a
records-only correction instead of a merged-before-review repeat of the recorded
2026-08-07 failure. Dispositions: **[conceded] ×5** — full-replace restore
semantics (layouts `replaceAll`, explicit-empty store blobs, whitelist reset
before apply) · strict `pcb` version gate · alt-action code validation before
hold-mode engages · voice-driver reconciliation + mic-permission surfacing after
restore (the P1) · Settings-close now rebuilds active custom pads so hold-time
and global-deadzone changes apply immediately. Re-verified: compile-check 102
classes / 22 files. Relay latency datum: first-ever review in a repo took ~9.6
min from PR-open — plan waits against "answered", never against 335 s.

Deliberately excluded from backup/restore: bond/device-specific prefs
(`desc_*`, `host_layout_*`, `last_host`, selection) and the supporter-preview
flag (per-install honesty). A detached-view stray release after pad-switch is
guarded (`isAttachedToWindow` before an alt engages; a late primary-release is
a no-op report).
