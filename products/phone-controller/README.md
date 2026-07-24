# phone-controller — turn a phone into a Bluetooth-HID controller

Make a phone act as a **customizable Bluetooth-HID controller** (keyboard / gamepad /
media remote) for other phones, tablets, TVs and PCs — no software installed on the
target. Based on the Ideas-Lab plan
[`ideas/product-forge/bt-controller-plan-2026-07-17.md`](https://github.com/menno420/idea-engine/blob/48a9d7d7f71371248a49e8ca4573e9ad10b1abd7/ideas/product-forge/bt-controller-plan-2026-07-17.md)
(menno420/idea-engine, blob `970edbfc`, HEAD `48a9d7d7`).

## Viability (from the idea doc's research pass)

- **Android — viable, but OEM-gated.** `BluetoothHidDevice` (Android 9 / API 28+) lets a
  phone *be* a true Classic BT-HID device, but the HID device role sits behind an OEM
  compile-time flag, so `registerApp()` fails on many shipped phones and support must be
  **probed at runtime, never assumed.**
- **iOS — not viable as a HID peripheral.** iOS blocks the HID role at the OS level; an
  iPhone can only be a *receiver* or a *network companion-controller*, never a BT-HID
  peripheral.

## State

**beta · field-verified against a real host.** Slices 1–13 are built and CI-proven: the
capability verdict engine (portable Python + lockstep Kotlin port), the real
`BluetoothHidDevice` transport, a **combo HID device** (keyboard + gamepad + mouse +
media remote), the controller UI (ten built-in layouts + a full custom-layout
editor, slide-over game pads, analog sticks + gyro, dark controller theme, focus
mode, landscape mode, rotation-safe connection), and a release pipeline that
publishes a signed, installable APK. Owner playtest 2026-07-23 (v0.4.0, laptop
host): pairing ✓, keyboard input ✓, GBA emulator driven via keys ✓, gamepad reports
confirmed live on a HID gamepad tester ✓ (emulator-side controller *binding* is
per-emulator configuration — map the buttons once in its input settings).

## Get the app (Android 9+)

- **Releases (recommended):** download `phone-controller-<version>.apk` from
  <https://github.com/menno420/product-forge/releases> (each release carries the APK +
  its sha256; new releases are cut by tagging `phone-controller-v*` —
  `.github/workflows/android-release.yml`).
- **Per-CI-run artifact:** every green `android-ci` run uploads a
  `phone-controller-debug-apk` artifact (Actions → run → Artifacts).

Install: open the downloaded APK on the phone and allow *install unknown apps* for the
browser/files app when prompted (normal sideload flow — this app is not on a store).

## Use it as a controller for another Android device (emulators etc.)

1. **Open the app** on the controller phone → grant the **Nearby devices / Bluetooth**
   permission it requests (Android 12+).
2. The status line shows the **capability verdict** from the shared decision engine.
   `SUPPORTED_CLASSIC_HID — Ready` means go; `OEM_DISABLED` means this phone's
   manufacturer compiled the HID-device role out (a known, device-specific platform
   gate — the app detects it honestly instead of half-working; try another phone as
   the controller).
3. Tap **Discoverable**, then on the **target** device: *Settings → Bluetooth → pair
   new device* → pair with **“Phone Controller”**. (Already paired once? Just tap
   **Connect…** and pick the device.)
4. When the status reads **Connected — controller is live**, pick a layout (the
   spinner; your choice persists — globally AND per connected host):
   - **Full gamepad** — D-pad + A/B/X/Y + L1/R1 + Select/Start. The target sees a
     standard HID gamepad (`KEYCODE_BUTTON_A`…, D-pad from the hat switch).
   - **GBA pad** — D-pad + B/A + L/R + Select/Start in the console's arrangement.
   - **Analog sticks** — two real thumbsticks (left = X/Y, right = Z/RZ, deadzone
     configurable in Settings), face buttons, L1/L2/R2/R1, and a **Gyro** toggle
     that maps phone tilt onto an input. **Settings → Gyro…** picks what tilt drives
     (right stick / left stick / **mouse pointer**), sets sensitivity and axis
     inversion, and shows a **live visualizer** so you can see exactly what your tilt
     is sending — even before connecting. Neutral "0 input" is wherever you hold the
     phone when you enable gyro; **long-press the Gyro button to recenter** mid-game,
     or use the Recenter button in Settings → Gyro….
   - **Your own layouts** — Settings → *Layouts…* → New: **start from a template**
     (Blank / GBA / Full gamepad / Analog + sticks), then drag anything anywhere.
     Add **buttons** (any action — gamepad / D-pad / keys / modifiers / media /
     mouse / key combos / **macros**) *and* **widgets** via **+ Stick / D-pad…**:
     a **left/right analog stick**, an **8-way D-pad** (with diagonals — press
     between side and front for two directions at once), a **touchpad**, or a
     **gyro toggle** — so a custom layout can be as full-featured as the built-in
     Analog pad. Per-button **color** (16-swatch palette, auto-contrast text),
     **shape** (rounded / circle / pill / square), **opacity**, **text size**,
     **turbo ⚡** (rate settable 5–20/s); duplicate buttons or whole layouts; pick a
     pad **background** (incl. OLED black). Custom layouts join this spinner — and
     can be **shared as text** (Layouts → your layout → Share…) and imported by
     anyone via Layouts → *Import layout…* (paste).
   - **Touchpad** — drag to move the host's pointer (on an Android target a system
     cursor appears), tap = click, two-finger tap = right-click, two-finger drag =
     scroll (natural direction), hold LEFT + drag = drag-select; speed slider below.
   - **NDS (touch + pad)** — a Nintendo-DS-style combo: touch-screen area plus
     D-pad, X/A/B/Y (Nintendo positions), L/R, Start/Select. The touch area
     defaults to **Pen** mode — finger contact = held left button, so drags DRAW
     on the emulator's touch screen like a stylus; toggle Pen off to hover the
     cursor without pressing. Portrait stacks screen-over-buttons like a held DS;
     landscape puts the clusters beside the screen.
   - **Presenter** — a slide clicker: Prev/Next (PageUp/PageDown), Start (F5) /
     from-here (Shift+F5) / End (Esc) / Blank (B), an on-phone elapsed-talk timer
     (tap = start/pause, long-press = reset) and a pointer strip.
   - **Shortcuts deck** — a serverless Stream-Deck: a 4×4 grid of everyday chords
     (Copy/Paste/Undo/Redo, Alt+Tab, tab controls, Screenshot, Lock…) sent as real
     key combos — no host software, unlike every macro-deck app. Any custom-layout
     button can carry a combo too (presets or a fully custom modifiers×key build).
   - **Keyboard** — full QWERTY with digits, punctuation, arrows and hold-capable
     Shift/Ctrl/Alt (two-thumb chords); held keys auto-repeat via the host OS.
   - **Emu keys** — arrows, Z/X/A/S, Enter/Space/Shift/Esc/Tab (the classic emulator
     default binds).
   - **Media** — play/pause, next/prev, stop, volume, mute (the Slice-2 remote).
   Buttons **hold** (a held HID report, not repeated taps), and on the game pads you
   can **slide between buttons without lifting** — glide across the D-pad like on a
   real controller. Landscape is fully supported (side panel + full-height pad), and
   rotating does not drop the connection. The whole app wears a dark controller
   theme; **Settings → App background…** recolors it (custom layouts can still
   override per-layout), and the **Focus** button enters pure controller mode —
   every control except the pad disappears (plus immersive full-screen) until you
   tap the small ⛶ chip at the top. **Text…** opens a box that replays whatever you
   type — with your phone keyboard's swipe, autocorrect and **mic (voice
   dictation)** — as real keystrokes on the host (US-QWERTY hosts; great for TV
   search boxes and passwords). The **Connect…** dialog doubles as a host
   switcher: picking another paired device hops the connection (and its remembered
   layout) over cleanly.

   In Settings you can also map the **hardware volume buttons** to inputs
   (L1/R1, L2/R2, or PgUp/PgDn — real physical shoulder buttons in landscape;
   volume behaves normally when disconnected), set the **turbo rate**,
   **invert touchpad scrolling**, and define **voice commands** — say a phrase
   in your own language and any action fires on the host (great as a hands-free
   media remote or for accessibility; recognition takes ~a second, so it's for
   menus and media, not twitch gameplay; mic is live only while the app is on
   screen, recognition prefers on-device).

   **Fairness promise:** the app is complete for free — every key, pad and editor
   feature, no ads, no subscriptions. A one-time ~€1 supporter pack (cosmetic
   button styles: gradient/glow fills) is planned for a future Play Store listing;
   until then Settings → About & Support offers the styles as a free preview.
5. **In the emulator** (e.g. RetroArch): Settings → Input → Port 1 Controls → map each
   button by pressing it on the phone — or use the Emu-keys pad with the emulator's
   default keyboard binds and map nothing at all. (Emulators generally don't
   auto-bind an unknown gamepad; one manual mapping pass is normal and persists.)

Receiver compatibility beyond Android (PCs, TVs): `./run.sh` prints the Slice-1
sourced matrix; its first row (*Android phone/tablet: keyboard/mouse/gamepad — yes,
out-of-box*) is exactly this use case.

## Play on THIS phone (overlay mode — no second device)

Beyond driving *other* devices, the app can float your controls over a game running
on the same phone and tap the game for you — useful when a game's own on-screen
controls are awkward. **Settings → Play on this phone…**: pick a custom layout,
grant *draw over other apps* and enable the *Phone Controller* accessibility service
(both one-time, both opt-in), then Start. Your buttons float over the game; pressing
one taps (or holds) the game at that button's position, so **place the buttons where
the game's controls are** using the normal layout editor. Drag the red handle to
move it; tap it or the notification to stop.

The accessibility service is used **only to send taps you trigger — it never reads
your screen** (`canRetrieveWindowContent=false`). This is the same mechanism
auto-clickers and gamepad-mappers use. Competitive online games are not supported.

**Recorded gestures (swipes & combos).** A button can replay a gesture you record
once. In the layout editor, set a button's action to **Gesture → Record new…**,
then perform the swipe/tap (or several in a row) on the full-screen recorder and
name it — pressing that button in overlay mode replays it on the game. Manage saved
gestures under **Settings → Recorded gestures…** (rename / delete / record). The
recorder captures the path in screen-percent with real timing, so a saved gesture
plays back at the same spot and speed on any screen size.

## Run (portable verdict core — no phone needed)

```bash
./run.sh                 # verdicts for representative device scenarios + the matrix
# evaluate one explicit probe:
./run.sh --platform android --api 34 --hid-role --ble-peripheral
./run.sh --platform android --api 33 --no-hid-role --ble-peripheral
./run.sh --platform ios
```

Stdlib-only — no dependencies, no network.

## Test

```bash
./test.sh                                     # Python decision table (canonical)
cd android && gradle :capability-core:test :hid-core:test   # Kotlin lockstep + HID model
```

26 Python tests covering every verdict branch; the Kotlin suites prove the Android port
stays in lockstep and pin the HID wire format (descriptor invariants, report bit-math,
6-key rollover, D-pad→hat combine). Exit 0 = green.

## Files

```
products/phone-controller/
├── src/capability.py     # capability-probe verdict engine (canonical decision table)
├── src/receivers.py      # receiver-compatibility matrix + lookups
├── cli.py · run.sh · test.sh · tests/
└── android/              # the app (see android/README.md)
    ├── capability-core/  # pure-JVM Kotlin port of the verdict engine (lockstep tests)
    ├── hid-core/         # pure-JVM combo HID descriptor + report builders (tests)
    └── app/              # the controller app: transport + pads UI (APK)
```

## Build ladder / next slices

Slices 1–8 done: scaffold → working core → tests → README → release artifact (S4) →
mouse + full keyboard + layout presets + slide-over + landscape (S5) → **custom
layout editor** + turbo + analog sticks + gyro + per-host memory + stale-pairing
warning (S6, the idea doc's items 6/7/8/10) → per-button colors / shapes / opacity /
text size + duplicates + pad backgrounds (S7) → dark controller theme + focus mode +
app-wide background + NDS touch-pad + pen mode (S8, owner recording feedback) →
send-text+voice, combo shortcuts deck, presenter pad, host switching, supporter
groundwork (S9, owner-approved brainstorm + fair-IAP research).
Remaining candidates:

- Foreground volume-key-as-input mapping (idea doc item 9).
- Scroll-direction invert toggle; macros (multi-key sequences on one button);
  long-press key alternates; layout import/export (share as text).
- BLE-HOGP fallback transport for `BLE_HOGP_FALLBACK`-verdict devices.

`iOS-as-controller` is deferred (network companion-receiver only); background
hardware-button capture is blocked by platform policy. See the idea doc for sources and
the latency budget.
