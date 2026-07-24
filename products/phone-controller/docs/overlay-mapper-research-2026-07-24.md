# Overlay / local-mapper research — can this app improve games ON the phone it runs on?

Date: 2026-07-24 · owner ask: *"could we use it to direct the input [of local apps
and games]?"* + *"Mantis … is more for pairing a controller to your phone"* +
standing constraint: **"it shouldn't become bloated with features people won't
use or won't understand."**

## The category, precisely

Two distinct products hide under "mapper", and the distinction matters:

| Model | Input comes from | Goes to | Who does it today |
|---|---|---|---|
| **Overlay → touch** | on-screen overlay buttons we draw | synthetic touches on the game under them | Octopus (ads + VIP) |
| **Gamepad → touch** | a physical controller paired to the phone | synthetic touches where the game's own buttons sit | Mantis Gamepad Pro, Panda (freemium) |

Mantis (verified 2026-07-24): converts controller presses into touch inputs via a
privileged helper ("MantisBuddy") activated through **on-device Wireless
Debugging** on modern Android (PC-assisted on older); per-game setup = open the
game, drag button markers over the game's own on-screen controls; "Phases" =
multiple mappings per game. No root. Sources: mantispro.app, androidsis guide,
XDA thread (links in session card conversation).

## The one hard problem: injecting input into another app

Android forbids app→app touch injection by design. Exactly two sanctioned routes:

1. **AccessibilityService + `dispatchGesture`** (API 24+; held/continued strokes
   API 26+). Pros: works out of the box, one settings toggle, no extra hardware
   or per-boot ritual. Cons: added latency (tens of ms per dispatched stroke),
   multi-touch chords are limited, and Play Store policy scrutinizes
   accessibility-for-gaming (sideload/F-Droid unaffected — which is our channel).
   Good for: taps, holds, swipes, D-pad-ish input, menu-heavy and turn-based
   games, emulator touch UIs.
2. **Shizuku-class privileged helper** (wireless-debugging pairing, Android 11+
   on-device; the Mantis/Panda route). Pros: system-grade injection — low
   latency, true multi-touch, analog-quality drags. Cons: a real setup ritual
   (pairing; re-activation after reboot) that must be explained to users — this
   is THE bloat/complexity risk the owner flagged.

A third idea — feeding our own HID gamepad back into the same phone — is not
possible without root (Bluetooth cannot loopback; no userspace uinput). Noted so
it never gets re-litigated.

## The move nobody else has: the two-phone kit

Mantis assumes you own a hardware controller. **We ship the controller.** Phone A
runs this app as the BT-HID gamepad (built, field-tested); phone B runs the
mapper mode and translates gamepad → touch for games with no controller support.
Two phones = a complete couch kit, zero hardware bought, both halves one app.
Octopus can't do it (no controller side); Mantis can't do it (no HID side).

## Simplicity-first staging (the anti-bloat plan)

Rule 1: the mapper is INVISIBLE until entered — one entry point ("Play on this
phone…") beside the existing remote-control world; zero new chrome elsewhere.
Rule 2: stage 1 ships only what needs one permission toggle; the Shizuku ritual
is stage 2, opt-in, clearly labeled "for lower latency", never required.
Rule 3: per-game profiles reuse the existing layout editor + percent anchors —
one mental model app-wide ("drag buttons where you want them"), no new editor.

- **Stage 1 — overlay pads + accessibility injection.** Floating pad over the
  game (display-over-apps permission), buttons fire dispatchGesture taps/holds/
  swipes at anchors placed with the existing editor. Honest in-UI latency note.
  Ships alone; useful immediately for emulators + casual/turn-based games.
- **Stage 2 — gamepad→touch (the Mantis model) + optional Shizuku backend.**
  Phone B reads any connected gamepad (including phone A running this app) via
  standard Android input APIs and drives the same anchor profiles; Shizuku
  backend swaps in transparently when the user has activated it.
- **Explicit non-goals:** game cloning, anti-cheat evasion of any kind, root
  paths, auto-play/botting. Competitive-online titles are documented as
  unsupported rather than half-worked-around.

## Decision needed from the owner (later, not now)

Whether stage 1 is worth ~2 slices of build once voice commands + field testing
land. Nothing in the current app blocks or presupposes it; the layout editor and
percent-anchor model were confirmed reusable as-is.
