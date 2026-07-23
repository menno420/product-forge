# product-forge · status

updated: 2026-07-23T19:51:56Z
phase: phone-controller Slice 4 SHIPPED — usable controller app + downloadable-APK lanes; physical playtest pending
lane: builder (phone-controller) · owner-live session 2026-07-23
health: green

## This session (2026-07-23, owner-live directive)
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

⚑ OWNER-ACTION (OA-005, open, optional) — **stable release signing**: store repo
secrets `PC_RELEASE_KEYSTORE_B64` (base64 PKCS12) + `PC_RELEASE_KEYSTORE_PASSWORD`;
until then android-release signs each release with an ephemeral key (installs fine;
cross-release update needs uninstall/reinstall). Any product-forge-scoped agent session
can generate the keystore + set both secrets in one step (fleet capabilities ledger:
secrets-create verified).

⚑ OWNER-ACTION (OA-003, open, unchanged) — GitHub Pages toggle for games-web preview
(Settings → Pages → Source → "GitHub Actions"); deploy-pages runs 29126980391 /
29128667052 failed at configure-pages until enabled.

## blocked-on (external waits — not this lane's to clear)
- **superbot lane** — games-web phase-2 real-data API request still unanswered
  (`products/games-web/docs/phase2-data-api-proposal.md`).

## PRs
PRs #1–#32 all terminal (merged/closed). Slice 4 lands from branch
`claude/controller-app-android-apk-j7tv10`. Its diff touches `.github/workflows/**`,
so merge-on-green parks the PR (owner-merge-only rail) — the landing session merges it
directly on green under the owner's live directive (hub precedent PR #29), or splits
the workflows commit into a companion PR if strict rails are preferred (the commits
are already separated for that).

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
