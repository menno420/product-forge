# product-forge · status

updated: 2026-07-24T12:23:13Z
phase: phone-controller Slice 11 IN PROGRESS — voice commands + overlay-mapper research
lane: builder (phone-controller) · owner-live session 2026-07-23
health: green

## This session (2026-07-23, owner-live directive)
**Slice 11 in progress (2026-07-24):** owner — voice commands + overlay research
(anti-bloat rule). VoiceControl (SpeechRecognizer, phrase→action, foreground-only,
opt-in mic) + docs/overlay-mapper-research-2026-07-24.md. Card:
`.sessions/2026-07-24-phone-controller-slice11-voice-commands.md`.
**Slice 10 SHIPPED (2026-07-24):** owner "continue building the most valuable
pieces" — layout Share/Import (versioned JSON envelope {"pcl":1}, share sheet +
clipboard + paste-import w/ fresh id + collision suffix), volume buttons as
trigger pairs (off/L1R1/L2R2/PgUpDn; intercept only while connected+mapped),
MACRO action type (JSON steps, WAIT pauses, 64-step cap, no nesting) + runner
(cancel-safe, one-at-a-time) + builder dialog, turbo rate 5-20 Hz, scroll invert
(all touch surfaces). No descriptor change → no re-pair; first release born
stable-signed (installs over v0.9.0 in place). v0.10.0 (versionCode 8). Verified
pre-push: 45/45 JVM tests, app compiles vs android.jar (61 classes). Card:
`.sessions/2026-07-24-phone-controller-slice10-share-volume-macros.md`.
**Slice 9 SHIPPED (2026-07-24):** owner-approved brainstorm + fair-IAP groundwork —
send-text dialog (phone IME incl. voice dictation → HID keystrokes; new KeyChars
map in hid-core, full printable-ASCII, tested), COMBO action type ("mask:usage") +
editor presets/custom builder + Shortcuts deck pad (serverless macro pad),
Presenter pad (PgUp/PgDn, F5/Shift+F5/Esc/B, elapsed-talk timer, pointer strip),
connectTo now disconnect-first (Connect… = clean host switcher), Supporter
scaffolding (gradient/glow style pack behind honest free preview toggle, fairness
promise in About + README, SUPPORT_URL placeholder → owner to create Ko-fi/
Sponsors). No descriptor change → no re-pair. v0.9.0 (versionCode 7). Verified
pre-push: 45/45 JVM tests, app compiles vs android.jar (56 classes). Card:
`.sessions/2026-07-24-phone-controller-slice9-text-shortcuts-supporter.md`.

**Slice 8 SHIPPED (2026-07-24):** owner screen-recording feedback on v0.7.0 —
dark controller theme (NoActionBar, chip styling, autosize labels — SELECT never
wraps), pure-controller focus mode (hide all chrome, translucent ⛶ exit chip,
auto-immersive), app-wide background picker, NDS pad (touch area with stylus
pen-mode + DS button set, portrait + landscape), swatch-grid color picker, honest
Bluetooth-off status. No descriptor change → no re-pair; saved layouts + ordinals
stable. v0.8.0. Verified pre-push: 38/38 JVM tests, app compiles vs android.jar
(49 classes). Card:
`.sessions/2026-07-24-phone-controller-slice8-dark-theme-focus-nds.md`.

**Slice 7 SHIPPED (2026-07-24, same conversation):** owner visual-customization ask —
per-button color (16 swatches, auto-contrast text, pressed-darken), shape
(rounded/circle/pill/square), opacity presets, text size, S–XL + 2% W/H steppers,
duplicate button + duplicate layout, per-layout pad background (incl. OLED black),
classic ABXY tints on built-in pads. Saved v0.6.0 layouts load unchanged (optional
JSON fields). No descriptor change — no re-pair. v0.7.0. Verified pre-push: app
compiles vs android.jar (48 classes); JVM suite unchanged 38/38. Card:
`.sessions/2026-07-24-phone-controller-slice7-visual-customization.md`.

**Slice 6 SHIPPED (same conversation):** the research gap plan, implemented — custom
layout editor (drag/resize/assign any action, per-button turbo 10 Hz, saved layouts in
the picker) · Analog-sticks pad (descriptor rev: right stick = Z/RZ; deadzone setting)
· gyro→right-stick toggle · haptics toggle · Dim + Full-screen modes · per-host layout
memory + silent auto-reconnect · stale-pairing warning (per-bond descriptor hash — the
owner's touchpad incident, field-confirmed fixed by re-pair mid-slice) · keyboard F-row
· measured input-path latency stat in Settings. v0.6.0 (re-pair required once —
descriptor grew). Verified pre-push: 38/38 JVM tests, app compiles vs android.jar.
Card: `.sessions/2026-07-23-phone-controller-slice6-editor-sticks-ergonomics.md`.

**Slice 5 SHIPPED (same owner-live conversation):** owner playtest of v0.4.0 against a
laptop succeeded (pairing + keyboard + emu-keys working; gamepad reports confirmed on a
HID tester — emulator-side binding is per-emulator config). Live feature asks delivered
as v0.5.0: **Report-4 relative mouse + Touchpad pad** (drag/tap/two-finger gestures,
sensitivity slider), **full QWERTY keyboard pad**, **layout presets** (Full gamepad ·
GBA pad · Touchpad · Keyboard · Emu keys · Media, persisted), **slide-over game pads**
(D-pad glide without lifting), **landscape layout + rotation-safe connection**
(configChanges — rotating no longer drops the HID registration). Verified pre-push:
36/36 pure-JVM tests, app compiles vs android.jar; card:
`.sessions/2026-07-23-phone-controller-slice5-touchpad-keyboard-layouts.md`.

Owner's live instruction: finish the controller app as a **downloadable APK** usable as
a Bluetooth-HID input device for other Android devices (emulator use case). Delivered
as Slice 4 — session card `.sessions/2026-07-23-phone-controller-slice4-apk.md`:

- **:hid-core** (new pure-JVM module, tested): combo HID descriptor — Report 1 media
  (Slice-2 bytes verbatim) · Report 2 keyboard · Report 3 gamepad (16 buttons,
  hat D-pad, X/Y) — + KeyboardState/GamepadState report builders.
- **Transport**: registers the combo device; hold-capable key/gamepad/dpad/media APIs;
  connect-to-bonded; all-release on disconnect/stop; SecurityException → error surface.
- **UI**: real controller — runtime BT permissions, live verdict/status line,
  Discoverable + Connect actions, three hold-capable pads (Gamepad / Keys / Media).
- **CI/release**: android-ci additionally runs :hid-core:test and uploads the debug-APK
  artifact; new android-release.yml publishes a signed APK + sha256 to a GitHub Release
  on a `phone-controller-v*` tag (repo-secret keystore preferred, ephemeral fallback).
- **Docs**: product README (download / install / pair / emulator mapping) + android
  README (module map, report table, signing) rewritten.

Verified this session (local, full log in the session card): Kotlin lanes
**29/29 tests green** (capability lockstep + HID wire format), Python canonical suite
**26/26**, app module compiles against android.jar, and a **signed v0.4.0 APK was
built and apksigner-verified** end-to-end (manual aapt2/kotlinc/d8/apksigner pipeline
mirroring the CI lanes).

## Orders
orders: acked=001,002,003,004 done=001,002,003,004 · Slice 4 = owner-live directive
2026-07-23 (no inbox ORDER number; supersedes the 2026-07-11 archive-ready posture per
fleet precedence — live instruction outranks stored close-out).

## ⚑ needs-owner
⚑ OWNER-ACTION (OA-004, open) — **playtest the controller on real hardware** (the one
step CI cannot prove; ~5 min, two Android devices):
1. Install the APK (Releases page, or the CI-run artifact) on the controller phone.
2. Open it → grant Nearby devices → status shows the capability verdict.
3. Tap Discoverable → pair from the target device → "Connected — controller is live".
4. Open an emulator on the target; Gamepad pad should show up as a controller
   (Keys pad = keyboard fallback). Hold a D-pad direction: movement must HOLD.
VERIFIED-WHEN: one full session driving an emulator; report the phone model + verdict
code (an `OEM_DISABLED` phone is the engine working as designed — try another phone).

⚑ RESOLVED 2026-07-24 (OA-005, was owner-optional; executed agent-side under the
live "please continue" directive) — **stable release signing is configured**: repo
secrets `PC_RELEASE_KEYSTORE_B64` + `PC_RELEASE_KEYSTORE_PASSWORD` set via
direct-egress REST (sealed-box encrypt; the Slice-4 PKCS12 keystore, alias
`phone-controller`); v0.9.0 re-signed with it via workflow_dispatch. Updates now
install in place from v0.9.0 onward (one final uninstall when coming from
ephemeral-signed ≤v0.8.0). Keystore lives ONLY in the repo secret.

⚑ OWNER-ACTION (OA-003, open, unchanged) — GitHub Pages toggle for games-web preview
(Settings → Pages → Source → "GitHub Actions"); deploy-pages runs 29126980391 /
29128667052 failed at configure-pages until enabled.

## blocked-on (external waits — not this lane's to clear)
- **superbot lane** — games-web phase-2 real-data API request still unanswered
  (`products/games-web/docs/phase2-data-api-proposal.md`).

## PRs
PRs #1–#39 all terminal (merged/closed) — #33–#39 are the Slice 4–9 lands from
branch `claude/controller-app-android-apk-j7tv10` (restarted from main per slice),
each squash-merged on green under the owner-live directive (hub precedent PR #29).

## Merge grant (owner authorization)
- 2026-07-10T21:07Z (session cse_01CiurDYKFjjTjn9E56pWBBF): standing merge/auto-merge grant,
  review-AFTER-completion model.
- 2026-07-10T22:08Z (coordinator session): "I explicitly grant you and all your agents full
  permissions to merge all PRs."
Full timeline: [`docs/retro/2026-07-11-owner-rulings-timeline.md`](../docs/retro/2026-07-11-owner-rulings-timeline.md).

## Continuous mode / wake state
No trigger armed (unchanged since the 2026-07-11 close-out). The lane runs on owner-live
sessions and hub dispatches; re-arm per `docs/retro/archive-ready-2026-07-11.md` only if
continuous mode is wanted again.

notes: phone-controller is the lane's active product (beta — usable controller,
hardware playtest pending, OA-004). games-web state unchanged (Pages pending OA-003).
