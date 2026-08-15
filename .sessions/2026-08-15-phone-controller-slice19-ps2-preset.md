# Session — phone-controller Slice 19: PS2 (DualShock) preset

> **Status:** `complete`

📊 Model: fable-5 · high · feature build

Time: 2026-08-15 · lane: builder (phone-controller · slice 19) · owner-live
directive, same conversation as Slice 18

💡 Session idea: owner, live: *"Improve the controller so it has a ps2 preset."*
Reading (stated back, per the app's own vocabulary since Slice 17): a
**DualShock-2 starter template** in the New-layout picker — PS diamond with the
glyph colors (△ ○ ✕ □), D-pad, BOTH analog sticks, all four shoulders
(L1/L2/R1/R2), Start/Select — fully customizable like every preset.

## Previous-session review

Slice 18 shipped earlier today (PR #49 squash `6c33382`, v0.18.0 released,
stable-signed, Codex 5/5 conceded pre-merge). Heartbeat current at that merge;
no open PRs. This slice builds directly on it: the template pre-uses Slice 18's
per-widget config surface (sticks arrive with no overrides — global deadzone
applies — and every widget is configurable after creation).

## Scope (decide-and-flag)

- **Template, not a new built-in pad** (LOW, decided): "preset" in this app =
  the New-layout starter set (`CustomLayout.template()` — the customize-a-preset
  mechanic, Slice 17). Three taps make it a live spinner entry. A built-in
  `Pad` enum row would duplicate what the template + spinner already deliver
  and add ordinal-stability surface for no gain.
- **No L3/R3 stick-click buttons** (LOW, decided, recorded): `GamepadButton`
  carries bits 0–11 with **no BTN_THUMBL/THUMBR**
  (`hid-core/.../ComboHidDescriptor.kt:228-239`) — adding them is a HID
  descriptor revision, which forces re-pair on every bonded host (the Slice-6
  descriptor-cache incident class). Deliberately out of a preset slice; a
  descriptor-rev slice can add them if the owner asks.
- **Mapping** (LOW, decided): positional convention — ✕→A (south), ○→B (east),
  □→X (west-bit, left position), △→Y (north-bit, top position) — matching the
  enum's BTN_SOUTH/EAST/NORTH/WEST comments; emulators bind per-button anyway
  (one mapping pass, persists). Glyph colors per DualShock: △ green, ○ red,
  ✕ blue, □ pink; CIRCLE shape for the diamond (DS2 buttons are round).
- **All four shoulders digital** — L2(8)/R2(9) already in the descriptor, so
  **no descriptor change: v0.19.0 installs in place, no re-pair.**

Version: v0.19.0 (versionCode 17).

## Verification plan

`compile-check.sh` (the committed no-SDK proof); JVM suites (untouched modules —
regression); gate + heartbeat check real exit codes; PR ready + `do-not-automerge`
park; Codex (fires on open; wait against "answered", not a number — 416 s
same-basis measured this morning); land on green; tag `phone-controller-v0.19.0`;
verify release asset + sha256 + stable-keystore log line; poll to terminal.

## Result

Shipped as PR #50 (v0.19.0, versionCode 17). Codex two rounds:

- **Round 1 (04:58:50Z, on `8607603`): 3 findings, [conceded] ×3** —
  Select/Start under the focus-exit chip (moved to y=0.13); CIRCLE rendered as
  an oval off the 1.5 pad aspect (ButtonStyler now inscribes a TRUE centered
  min-dimension circle when both laid-out dims are known — visual only, touch
  bounds untouched); D-pad/left-stick 5 % overlap with the stick winning
  touches by FrameLayout z-order (geometry re-cut, **all 13 rects proven
  pairwise disjoint — by a one-off session script, NOT a committed test**: it
  binds the committed numbers only, and any future geometry edit must re-run
  it by hand; committing it as a repo checker was considered and deferred —
  the rect table would live twice until the app module has a test harness).
- **Round 2 (05:05:22Z, on `83c8874`): 1 finding, [conceded] ×1** — the
  overlay call site (`OverlayPlayService.kt:101`) still passed height only, so
  Play-on-this-phone circles would have stayed ovals (no later onLayout pass
  there). Independently surfaced by the estate's owner-review hook minutes
  earlier — the fix (pass the already-computed `bw`) was already in hand and
  lands in THIS flip commit, dispositioned under the two-round cap, stated
  not inferred. **Lesson recorded:** the round-1 fix was described "app-wide"
  after verifying two of three `ButtonStyler.apply` call sites — the word
  outran the grep; the grep is now run and the third site fixed.
- **One deliberate behavior change to flag:** CIRCLE buttons in EXISTING user
  layouts change visually (stretched oval → true centered circle, remote pads
  and overlay alike). Judgment call, recorded: a shape named CIRCLE drawing an
  ellipse is nearer defect than contract; hit-areas are unchanged.

Verified: compile-check 102 classes / 22 files (three runs: feature, round-1
fixes, overlay fix) · CI green on the PR heads · release verification appended
to the fm card at tag time (this card flips before the tag exists).
