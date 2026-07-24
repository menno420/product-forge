# Session — phone-controller Slice 9: send-text + voice, combo shortcuts, presenter, supporter groundwork (builder lane)

> **Status:** `in-progress`

📊 Model: fable-5 · high · feature build

Time: 2026-07-24 · lane: builder (phone-controller · slice 9) · owner-live directive
(same conversation as Slices 4–8)

💡 Session idea: owner asked for related features we hadn't thought of, then approved
the shortlist ("yes go ahead with your ideas") plus the fair-monetization groundwork
(one-time ≤€1 supporter unlock, nothing functional ever gated, no ads, no subs —
competitor research in this card's References).

## previous-session review

v0.8.0 shipped same conversation (PR #38 merged, tag + release verified): dark
theme, focus mode, app background, NDS pen-mode pad, swatch picker. One CI red
mid-slice: a hand-typed FUTURE heartbeat timestamp (after a failed sed) — caught by
`check-heartbeat.py`; fix-forward restamp. Guard: never hand-type heartbeat stamps,
always `date -u`.

## What this build does

**A · Send text (free):** action chip → dialog with the phone's own IME (swipe,
autocorrect, and the mic button = voice dictation into any host). Text replays as
real HID keystrokes: new pure-JVM `KeyChars` map in :hid-core (full printable ASCII
→ usage + shift, unit-tested) + a paced background typer in the app (per-char
press/release, stop-on-disconnect). US-QWERTY host layout assumption documented.

**B · Combo actions + Shortcuts deck (free):** new `PadActionType.COMBO`
(`"mask:usage"` code, e.g. Ctrl+C) — resolver holds modifiers + key as one press,
releasing in reverse. Editor gains a combo picker (presets: copy/paste/cut/undo/
redo/select-all/find/save/alt-tab/close-tab/new-tab/reopen-tab/screenshot/lock +
custom builder: modifier checkboxes × key list). New built-in **Shortcuts** pad —
a serverless Stream-Deck-style grid of the everyday chords. KeyUsage grows
MOD_LEFT_GUI + PAGE_UP/DOWN + HOME/END + PRINT_SCREEN (all ≤0x65 → descriptor
byte-identical).

**C · Presenter pad (free):** Prev/Next (arrows + PgUp/PgDn variants), Start
(F5) / From-here (Shift+F5) / End (Esc), Blank (B), pointer strip (touchpad), and
an on-phone elapsed timer (tap = start/pause, long-press = reset).

**D · Host switching (free):** `connectTo` now disconnects the current host first —
the Connect… dialog becomes a clean multi-device switcher (per-host layouts already
restore on connect).

**E · Supporter groundwork (the fair-IAP scaffolding):** `Supporter` flag object
(pref-backed; real billing arrives only with a Play listing). Style Pack renders:
per-button GRADIENT fill + GLOW pressed effect in ButtonStyler behind the flag,
editor "Style…" entry, and an About dialog with the fairness promise (everything
free · no ads · no subscriptions · one-time ≤€1 cosmetics) + supporter-preview
toggle (honest free preview until billing exists) + Support link (repo page for
now — swaps to Ko-fi/Sponsors when the owner creates one).

Wire format untouched — **no descriptor change, no re-pair.** Version 0.9.0
(versionCode 7). Pads appended to the enum (NDS rule): PRESENTER, SHORTCUTS.

## References

Competitor monetization (verified 2026-07-24, live conversation): Appground Pro
$5.49 separate app, free version gates text-send/volume/custom layouts; Monect VIP
~$4.33 + ads in free; Remote Mouse $15/subscription, paywalls Ctrl/Esc; Unified
Remote ~$5 one-time. Our position: complete app free, €1 one-time cosmetics.

## Verification plan

hid-core suite grows (KeyChars round-trip, new usages, combo mask math); local
kotlinc pipeline (`build8/compile.sh` pattern) green pre-push; bootstrap gate
green; CI green on PR; merge → tag `phone-controller-v0.9.0` → release verified.

## Result

_(fill on completion)_
