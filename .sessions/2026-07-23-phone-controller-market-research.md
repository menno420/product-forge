# Session — phone-controller market research: competitor sweep + gap plan (builder lane)

> **Status:** `complete`

📊 Model: fable-5 · high · research

Time: 2026-07-23 · lane: builder (phone-controller) · owner-live directive (same
conversation as Slices 4–5; owner is field-testing v0.5.0 in parallel)

💡 Session idea: owner ask — *"do some research to find out what people are asking
for online related to this … I want to make sure that ours is more customizable and
works better than all of them."* Web sweep of the serverless BT-HID app landscape +
user-ask signals, distilled into a sourced product doc with a priority gap plan.

## previous-session review

Slice 5 (PR #34, squash `e5214b7`, tag `phone-controller-v0.5.0`, Release live)
shipped touchpad-mouse, full keyboard, layout presets, slide-over pads, landscape.
Owner field results on v0.4.0: laptop pairing/keyboard/emu-keys ✓, gamepad confirmed
on a HID tester (emulator binding = per-emulator config).

## What this commit does (docs-only)

Adds `products/phone-controller/docs/market-research-2026-07-23.md`:

- **Landscape:** Appground splits keyboard/mouse (9M installs, no gamepad) from a
  weak gamepad app (3.18★, missing-stick complaints); Pocket-Pad (OSS) is the
  strongest gamepad-only rival (sticks/triggers/deadzones/battery modes, no
  keyboard/mouse, no layout editor); Monect et al. are feature-rich but
  server-based; hardware pads set the turbo/remap expectation bar.
- **User asks (signals):** both analog sticks + deadzone/sensitivity, custom layout
  editing, turbo, gyro/tilt, latency, battery modes, haptics, F-row, console
  support (an identity-whitelist platform condition — documented, not promised).
- **Our unique combo already:** the only serverless keyboard+mouse+gamepad+media
  single-device app, with presets, glide pads, rotation-safe landscape, and honest
  per-device verdicts.
- **Gap plan (priority-ordered):** P1 layout editor + per-button turbo + haptics ·
  P1 on-screen analog sticks (+RX/RY/Z/RZ in ONE descriptor rev = one re-pair) ·
  P2 gyro-as-axes · P2 session ergonomics (OLED-black, immersive, per-host
  profiles) · P3 keyboard reach/macros · latency measured, not asserted.

No code changes; README ladder already points at the same next slices (#6–#10) —
the doc grounds their order in observed demand.

## Verify

```bash
ls products/phone-controller/docs/market-research-2026-07-23.md
python3 bootstrap.py check --strict
```
