# Session — phone-controller Slice 17: editor discard + customize-a-preset + fine size sliders (builder lane)

> **Status:** `complete`

📊 Model: fable-5 · high · feature build

Time: 2026-07-24 · lane: builder (phone-controller · slice 17) · owner-live directive
(same conversation as Slices 4–16)

💡 Session idea: owner editor feedback — "when editing a layout it's not easy to
quick/discard your current edit," and "I'd like an easy edit button for every preset,
so you could change button size etc as detailed as you want with a slider etc instead
of small/big."

## previous-session review

v0.16.0 shipped (PR #47): the critical multi-touch fix (per-button consuming touch,
4+ simultaneous inputs). Stable signing since v0.9.0; installs land in place. Owner
is actively field-testing (this is editor-UX feedback from that).

## What this build does

**A · Discard / Cancel editing:** the editor toolbar gains a **Cancel ✕** button
(confirm dialog → discard). New layouts are no longer pre-saved to the store — a
layout only persists on **Save ✓** — so discarding a brand-new layout leaves nothing
behind, and discarding edits to an existing layout reverts to the stored version
(the editor mutates a throwaway parsed copy). `startEditor` remembers the pre-edit
selection to return to.

**B · Customize a preset:** the New-layout flow's "Start from…" picker now covers the
controller presets — **Blank / GBA / Full gamepad / Analog + sticks / NDS** — so any
of them can be started as an editable custom copy and then tuned freely (the honest
answer to "edit a preset," since the built-in pads are code, not data). NDS template
added (touch widget + D-pad widget + X/A/B/Y + L/R + Select/Start).

**C · Fine size sliders:** the Size dialog is now **Width + Height SeekBars with live
preview** (the button/widget resizes behind the dialog as you drag), replacing the
S/M/L/XL-only flow — quick S–XL presets stay as one-tap buttons inside. One shared
`pickSizeSliders(PadPositioned, ranges)` drives both buttons (5–60%) and widgets
(10–90%), reusing the Slice-15 PadPositioned interface.

Wire format untouched — no HID descriptor change, no re-pair. Version 0.17.0
(versionCode 15).

## Verification plan

hid-core unchanged (59/59 stay green — app-only); app compiles vs android.jar via the
saved pipeline; gate green; CI green on PR; merge → tag `phone-controller-v0.17.0` →
stable-signed release.

## Result

Shipped, all three blocks. Discard: editor toolbar gains Cancel ✕ (confirm dialog,
message differs for new vs existing); new layouts no longer pre-saved (startEditor
isNew flag; only Save persists), so discard leaves nothing behind; discardEditor()
returns to the remembered pre-edit selection. Fine sizing: one shared
sizeSliderDialog(PadPositioned, ranges) with live-preview Width/Height SeekBars +
S/M/L/XL quick buttons, used by both buttons (5-60%) and widgets (10-90%). Preset
editing: New "Start from" now includes NDS (touch widget + D-pad widget + X/A/B/Y +
L/R + Select/Start) alongside Blank/GBA/Full gamepad/Analog — start any controller
preset as an editable copy.

Guard: PadPositioned reused for the shared slider (buttons + widgets); clampToPad
keeps sizes in range live; editingLayout nulled before Save's showSelection so it
doesn't re-enter the editor on rebuild. hid-core 59/59 (unchanged); app vs
android.jar 92 classes. v0.17.0 (versionCode 15), stable-signed, no HID descriptor
change → installs in place, no re-pair. PR/tag/release: control/status.md heartbeat.
