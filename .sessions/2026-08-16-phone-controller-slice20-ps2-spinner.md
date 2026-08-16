# Session — phone-controller Slice 20: PS2 pad in the layout spinner

> **Status:** `complete`

📊 Model: fable-5 · high · feature build

Time: 2026-08-16 · lane: builder (phone-controller · slice 20) · owner-live
directive (screenshot feedback on Slice 19), same conversation

💡 Session idea: owner sent the layout-spinner screenshot: *"It's not visible
here. Can you make it more easily accessible."* Slice 19 shipped PS2 as a
starter template only (New-layout picker) — a scope call his screenshot
overrules. Fix: **"PS2 pad" becomes a directly selectable spinner entry**,
rendered from the template with no create-and-save step, placed right after
NDS (console pads grouped). The template stays as the customization path.

## Previous-session review

Slice 19 shipped earlier today (pf #50 squash `3c462a6`, v0.19.0 released,
signer cert measured identical to v0.18.0). fm #864 merged `766207c` with the
Codex round that corrected the L3/R3 premise (descriptor declares Usage 1–16;
enum stops at 11 — stick-clicks would be enum-only). Heartbeat current at the
#50 merge; no open PRs.

## Scope (decide-and-flag)

- **Template-backed spinner entry, not a hand-coded built-in** (LOW, decided):
  the spinner row renders `CustomLayout.template("PS2 (DualShock)")` through
  the normal CustomPadView path — one source of truth for the layout, and the
  per-widget behavior config (Slice 18) plus per-host layout memory work
  unchanged. A ControllerPads hand-build would duplicate the geometry in a
  second dialect.
- **Selection key class `t:<kind>`** (LOW, decided): joins the existing `b:N`
  (built-in ordinal) and `c:<id>` (custom) key classes; keys are position-free
  strings, so splicing the row after NDS in the display list breaks no saved
  selections, and per-host memory / restore paths handle it via the existing
  `selectionExists` guard.
- **Only PS2 gets a spinner row** (LOW, decided): the other template kinds
  either mirror existing built-ins (GBA / Full gamepad / Analog / NDS) or are
  meaningless as a pad (Blank) — rows for them would duplicate the list the
  screenshot shows is already long.

Version: v0.20.0 (versionCode 18). No HID descriptor change.

## Verification plan

`compile-check.sh`; gate + heartbeat real exit codes; PR ready +
`do-not-automerge` park; Codex (wait against "answered"); land on green; tag
`phone-controller-v0.20.0`; verify release asset + sha256 + stable-keystore
log line + signer-cert match vs v0.19.0 (the parser from this session);
fm records follow.

## Result

Shipped as PR #51 (v0.20.0, versionCode 18). Codex one round (07:35:24Z, on
`07ee1c1`): **1 finding, [conceded] ×1** — the Settings-close refresh condition
covered `b:ANALOG` and `c:` but not the new `t:` class, so a template pad kept
a stale global deadzone / hold-time until a layout switch. The same staleness
class Codex caught on #49 for `c:`, recurring for the key class this slice
introduced; fixed in the flip commit (condition now names all three), which
lands dispositioned under the cap — its diff is this one-line fix + this
close-out. compile-check 103 classes / 22 files (both runs). Release
verification (tag, assets, stable-keystore line, signer-cert match vs
v0.19.0) recorded fm-side at tag time.
