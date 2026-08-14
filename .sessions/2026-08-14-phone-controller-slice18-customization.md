# Session — phone-controller Slice 18: deeper customization (per-widget config, long-press alternates, fine position, backup-everything)

> **Status:** `in-progress`

📊 Model: fable-5 · high · feature build

Time: 2026-08-14 · lane: builder (phone-controller · slice 18) · owner-live directive
(hub session booted in fleet-manager; product-forge attached mid-session — the
Slice-4 precedent)

💡 Session idea: owner ask, live: *"I want to further improve it this session, make
it more customizable etc"* — plus the repo question (new repo vs product-forge),
answered in-session: build here on the proven signed-release rails; graduation to a
dedicated repo stays the standing plan (fm program §2 + R2) as its own next step.

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

*(fill at close — this card is born red and flips complete as the deliberate last
step)*
