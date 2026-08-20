# Session — phone-controller Slice 21: physical-keyboard support (type-through + key bindings)

> **Status:** `in-progress`

📊 Model: fable-5 · high · feature build

Time: 2026-08-20 · lane: builder (phone-controller · slice 21) · owner directive
2026-08-16, relayed via continuation prompt (same conversation family as
Slices 18–20)

💡 Session idea: owner ask, verbatim (2026-08-16 — recorded here because it
exists nowhere but the handoff prompt until this card):

> "improving this app further to recognize keyboards etc that are added to
> tablets or phones etc, so you can use the keyboard as input/controls or just
> as a way to write. There should also be some extra support for foldable
> phones etc. The next session should think about this and try to implement it
> as properly as possible"

"Think about this" read as a design mandate: the § Design section below records
the options, the choices and the reasons BEFORE any app code. The work splits
into **two slices** (decided in § Design D5): this one (physical keyboards,
v0.21.0) and Slice 22 (foldables, v0.22.0) — two small finished slices over one
sprawling one (OD-6).

## Previous-session review

Slice 20 shipped 2026-08-16 (pf #51 squash `d5a412f`, v0.20.0 released,
stable-signed; signer cert measured identical v0.18.0→v0.20.0). Re-verified at
session start (2026-08-20): product-forge `main` = `d5a412f`, newest release
`phone-controller-v0.20.0`, 0 open PRs; fleet-manager `main` = `0f8a728`.
Layer-2 thread (fm `docs/repos/product-forge/README.md`) carries two queued
facts this slice consumes: the **unconditional Settings-close refresh** is the
next slice's first item (enumeration bug shipped twice, pf #49 → #51), and the
**L3/R3 premise correction** (descriptor already declares 16 button bits, enum
stops at 11 — stick-clicks would be enum-only, NO re-pair; the pf-side README
still carries the old wrong premise, corrected in this slice's README pass).
Gate baseline at `d5a412f`: `python3 bootstrap.py check --strict` exit 0 (one
advisory: heartbeat ~107 h stale — refreshed by this card's commit).

## Design (the owner's "think about this" — options · choice · reasons)

Certainty per the estate legend: everything below marked REASONED derives from
the committed sources named (AOSP-documented contracts + this repo's code read
in full this session); MEASURED items name their measurement. No Android device
exists in this venue — device-level claims stay REASONED with owner-verifiable
steps listed in § Verification plan.

### D1 · Where keyboard events are captured

- **(a) `Activity.onKeyDown/onKeyUp`** (the volume-key precedent,
  `MainActivity.kt:303/:312`) — REJECTED. The Activity callbacks see only keys
  the focused View declined. Play-mode pad buttons are ordinary focusable
  `Button`s (the `isFocusable = false` at `CustomPadView.kt:99` is the
  edit-mode branch), and a hardware keyboard's first arrow key pulls the UI out
  of touch mode and hands a View focus — from then on Enter/Space/arrows are
  consumed as UI navigation and never reach `onKeyDown`. Works for volume keys
  only because no View consumes those.
- **(b) `Activity.dispatchKeyEvent` with an explicit capture policy** —
  **CHOSEN.** It runs before the view tree for every key event in the
  activity's window (REASONED, AOSP dispatch order), so captured keys can never
  focus-wander or "click" a focused pad button. The policy: capture only when a
  mode is active AND the event comes from an external hardware keyboard device
  AND the host is connected AND the layout editor is closed. Dialogs need no
  policy at all: every `AlertDialog`/`Dialog` owns its own window, so the
  activity's `dispatchKeyEvent` never sees keys typed into one (REASONED) —
  capture self-suspends during every dialog, including this feature's own
  "press a key" binder.
- **(c) a focused `View.OnKeyListener` on the pad container** — REJECTED:
  delivery depends on that view holding focus, which is exactly the unstable
  thing.
- Never captured, in any mode: `VOLUME_UP/DOWN/MUTE` (the Slice-10 volume-keys
  seam keeps owning them — one seam per key class), `BACK`, `HOME`,
  `APP_SWITCH`, `POWER` (system navigation stays intact).
- Eligibility check: `event.device != null && !device.isVirtual` and sources
  carry `SOURCE_KEYBOARD`; on API 29+ additionally `device.isExternal` (public
  API only from 29; on API 28 the virtual+source check stands alone — REASONED,
  and the IME path arrives as the virtual keyboard device so soft-keyboard
  input is never captured).
- Auto-repeat (`repeatCount > 0`) is swallowed, never forwarded: a HID host
  generates its own repeats from the held report — forwarding Android's
  repeats would double them (same rule the volume seam already applies).
  6-key rollover is `KeyboardState`'s existing press-order semantics; a 7th
  concurrent key is refused there (MEASURED by its unit tests).

### D2 · What "use as controls" means

- **(a) per-pad-button hardware-key field in the layout model** — REJECTED.
  Only `CustomPadView` layouts have a spec model; the ten built-in pads are
  hand-coded views (`ControllerPads.kt`) with nothing to hang a key on —
  coverage would split the feature in half, plus a serialization change to
  every saved layout.
- **(b) a global keycode → action binding table resolved through the existing
  action vocabulary** — **CHOSEN.** One `KeyBinding(keyCode, keyLabel,
  actionType, actionCode, actionLabel)` list, stored exactly like
  `VoiceStore` (fail-soft JSON in prefs), fired through `resolveRaw` — the
  same single vocabulary pads, macros and voice commands already share, with
  real press/release semantics (down fires `action(true)`, up `action(false)`,
  so holds, D-pad chords and analog-style sustained inputs work). Uniform over
  every pad including built-ins, and over no pad at all.
- **(c) a hardcoded default map only** — REJECTED: not customizable, which
  contradicts the app's whole identity (the customization moat).
- Defaults exist but never install silently: "Load default bindings" (WASD +
  arrows → D-pad, IJKL → the diamond, Q/E → L1/R1, Z/C → L2/R2, Enter → START,
  Right-Shift → SELECT) replaces the table only behind a confirm.
- In BIND mode every eligible keyboard key is swallowed, bound or not
  (predictability + no focus-wandering); unbound keys are silent no-ops.

### D3 · What "just as a way to write" means

- **(a) reuse the Slice-9 `TextTyper` composer only** — REJECTED as the whole
  answer: composing in a dialog is not "using the keyboard to write"; it stays
  for what it is good at (IME swipe/dictation replay).
- **(b) live type-through** — **CHOSEN.** Each hardware key event maps
  Android keyCode → HID usage via a new pure-JVM `KeyEventMap` in hid-core
  (letters, digits, F1–F12, arrows, editing cluster, punctuation, numpad,
  caps/num/scroll lock — every usage within Report 2's 0x01..0x65 array range)
  and rides the existing `transport.key()/modifier()` path; modifier KEYS
  (both sides — the descriptor spans E0–E7, all 8 mask bits) ride the modifier
  byte, so the host applies its own layout/shortcuts exactly as for a real BT
  keyboard. Keyboard media keys (play/next/prev/stop/mute) tap the consumer
  report (`MediaButton`) — they are on most physical keyboards. Unmappable
  keys are swallowed silently (position-based HID, documented app-side since
  Slice 9). Type-through also IS "controls" for any host mapped to keyboard
  binds (the Emu-keys use case) — which is why BIND mode is gamepad-flavored
  rather than duplicating key-sending.

### D4 · Mode surface + discoverability (the Slice-20 lesson: invisible = nonexistent)

- Modes: `OFF → TYPE → PAD` (PAD = bindings), one preference, persisted.
- **(a) a ninth action button** — REJECTED: the 4+4 action rows are
  weight-balanced; a ninth squeezes every label.
- **(b) an overlay chip like the focus chip** — REJECTED: overlays the pad
  area in landscape.
- **(c) a compact ⌨ mode button in the spinner row (portrait) / side panel
  (landscape), present ONLY while a hardware keyboard is attached** —
  **CHOSEN** — tap cycles the mode, long-press opens the full editor; plus a
  Settings row ("Hardware keyboard…") that owns mode + bindings CRUD +
  defaults, VoiceCommands-dialog pattern. Detection: `InputManager` device
  add/remove listener + `Configuration.keyboard`; attach/detach refreshes the
  UI and drops a one-line detail hint on first attach.
- Manifest: `keyboard` joins `configChanges` — TODAY a keyboard hot-plug
  recreates MainActivity (only `keyboardHidden` is declared), and recreation
  runs `onDestroy → transport.stop()` which unregisters the HID app and drops
  the live host connection (REASONED from the manifest's own rotation comment
  + AOSP config-change contract; the fold/unfold twin of this defect is
  Slice 22's). Safety: all captured keys release on mode-off / detach /
  disconnect / `onPause` (the `releaseHeldInputs` guard recipe).

### D5 · Foldables (Slice 22 — designed here, built there)

- What is actually broken (REASONED, same contract as D4): fold/unfold changes
  `smallestScreenSize` (cover ↔ inner display) and usually `density`; neither
  is declared in `configChanges`, so the activity RECREATES and the live HID
  connection drops mid-game. The percent-based layouts already reflow — the
  missing piece is not layout, it is surviving the transition.
- **(a) Jetpack WindowManager posture APIs** — REJECTED: AndroidX, and the
  app is measured AndroidX-free (Slice-3 choice, zero references at
  `d5a412f`). Hinge/half-open table-top features are not worth bending that
  for a controller app whose UI is one full-screen pad; recorded as the
  explicit non-bend this prompt requires.
- **(b) platform config-change hardening** — **CHOSEN foundation:**
  `smallestScreenSize|density` join `configChanges`; the existing
  `onConfigurationChanged → buildUi()` path re-renders everything
  percent-based at the new size.
- **(c) + per-screen layout memory** — **CHOSEN as the visible feature:** the
  app remembers the last layout per screen-size bucket (`smallestScreenWidthDp`
  < 600 vs ≥ 600 — cover screen vs inner/tablet) exactly like per-host memory,
  and switches on fold/unfold: compact pad on the cover screen, full pad
  inside, automatically. Precedence is event-ordered and simple: connect
  applies host memory, fold applies screen memory, last event wins.

### D6 · Explicitly out (this slice)

- No HID descriptor change (no re-pair; `descriptorFingerprint` unchanged).
- L3/R3 stay out until the owner asks (enum-only when wanted — the corrected
  premise; this slice's README pass fixes the stale re-pair claim).
- No per-key turbo on bindings; no macro/gesture binding targets (same
  exclusion set + reasons as voice commands); BLE-HOGP untouched; R2 untouched.

## Scope (decide-and-flag)

1. **Unconditional Settings-close refresh** (the queued first item, LOW,
   decided): `openSettings`' OK handler drops the `b:ANALOG`/`c:`/`t:`
   enumeration for an unconditional `showSelection(currentSelection)` guarded
   only by `editingLayout == null` — the guard also closes a latent
   editor-clobber (the old enumeration could nuke an open editor when the
   pre-edit selection matched). No input is ever held while a dialog is up, so
   the rebuild is always safe.
2. **hid-core `KeyEventMap`** (new, pure JVM): Android keyCode ints (stable
   public API values, pinned by test) → keyboard usage / modifier mask /
   media button. Unit-tested in the SDK-free lane like `KeyChars`.
3. **App: `input/` package** — `KeyBindings.kt` (binding model + store, JSON
   fail-soft, public KEY for backup) and `HardwareKeyboard.kt` (presence
   detection + the dispatch-capture engine + pressed-set release safety).
4. **MainActivity wiring**: `dispatchKeyEvent` override; InputManager
   listener; ⌨ mode button (spinner row / landscape panel); Settings row +
   editor dialog; hint on attach; releases on pause/disconnect/mode-off.
5. **Backup/restore carries bindings + mode** (additive key inside the `pcb`
   v1 envelope, LOW, decide-and-flag): an old build restoring a new blob
   ignores the extra key and restores everything else — additive-in-v1 beats
   a version bump that would make old builds reject the whole blob.
6. **README pass**: keyboard feature + the L3/R3 premise correction.

Version: v0.21.0 (versionCode 19). No HID descriptor change → installs in
place, no re-pair.

## Verification plan

`./test.sh` (26 Python, regression only — untouched) · gradle
`:capability-core:test :hid-core:test` (new KeyEventMap suite joins the 59) ·
`android/compile-check.sh` (baseline 103 classes / 22 files at `d5a412f`,
MEASURED this session) · `python3 bootstrap.py check --strict` real exit code ·
PR READY + `do-not-automerge` at creation · Codex waited to ANSWERED (silence
is never evidence; 416 s–9.6 min measured range) · land on green
`substrate-gate` + `android-ci` · tag `phone-controller-v0.21.0` → verify
release (APK + sha256 assets, "stable repo keystore" signing log line,
signer-cert match vs v0.20.0 — signing untouched) · fm records after.

Owner device steps this venue cannot run (listed, not claimed): pair a USB-C or
BT keyboard, confirm the ⌨ chip appears; TYPE mode: live typing lands on the
host incl. shift/ctrl chords and media keys; PAD mode: defaults drive an
emulator; confirm keyboard hot-plug mid-session no longer drops the connection.

## Result

*(filled at close)*
