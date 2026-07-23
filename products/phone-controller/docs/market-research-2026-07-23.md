# Market research — phone-as-controller apps (2026-07-23)

> **Status:** `reference`
>
> Owner ask (live session 2026-07-23): *"find out what people are asking for online
> related to this … I want to make sure that ours is more customizable and works
> better than all of them."* Web sweep of the Play-Store/GitHub landscape + user
> asks in reviews/discussions. Claims below carry their source; review-derived
> "asks" are signals, not measurements.

## The landscape (who else turns a phone into an input device)

**Serverless (true BT-HID, no host software — our category):**

| App | Scope | Standing | Notable |
|---|---|---|---|
| [Bluetooth Keyboard & Mouse](https://play.google.com/store/apps/details?id=io.appground.blek) (Appground, `io.appground.blek`) | keyboard + mouse/trackpad | ~9M downloads — the category's mouse/keyboard benchmark | serverless positioning is the sell; pro tier gates extras; **no real gamepad** in the main app |
| [Serverless Bluetooth Gamepad](https://appagg.com/android/tools/serverless-bluetooth-gamepad-35129887.html?hl=en) (Appground, separate app) | gamepad only | **3.18★ / ~120 ratings** | shipped without a left stick; users asked for stick toggles/layouts ("really need a left analog stick also an easy toggle…") — the serverless-gamepad niche is visibly under-served |
| [Pocket-Pad](https://github.com/jobrobse/Pocket-Pad) (OSS, ~105★) | gamepad only | strongest gamepad rival | analog sticks + triggers + thumb-clicks, "hair triggers", per-stick deadzones, OLED-black battery mode, "exclusive button mode", compact-report latency pitch; **no keyboard/mouse/media, no custom layout editor**; Switch explicitly unsupported |
| [WearMouse](https://github.com/ginkage/wearmouse) (Google OSS) | air-mouse + keys (Wear OS) | the `BluetoothHidDevice` reference app | proof the combo-descriptor approach is the sanctioned pattern |
| [BleHidJoystick](https://github.com/Jung-Max/BleHidJoystick), [bluehid](https://github.com/ralismark/bluehid) | experiments | PoCs | evidence of demand from the maker side |

**Server-based (need software on the host — the users we can convert):**
[Monect PC Remote](https://play.google.com/store/apps/details?id=com.monect.portable&hl=en_IN)
(genre gamepad presets + editable custom layouts, **gyro aiming / tilt steering**,
**macros/scripts**, media & file extras), MAXJoypad, DroidJoy, Unified Remote. Rich
features, but PC-only hosts + a server install — the #1 complaint our category
exists to remove, and useless for driving *another phone/tablet/TV*.

**Hardware controllers** (8BitDo / GameSir, per [reviews](https://androidguys.com/news/gamesir-pocket-taco-and-gamesir-g8-retro-mobile-gaming-and-console-style-control/))
set the feature expectations users carry into software: **turbo/auto-fire**,
remappable buttons, stick sensitivity, back buttons.

## What people ask for (review/issue/discussion signals)

1. **Real analog sticks** — both of them, with **deadzone + sensitivity** controls
   (the direct cause of Appground-gamepad's weak rating; Pocket-Pad's headline).
2. **Custom layouts** — editable button placement/size, per-game presets, quick
   layout toggle (Appground reviews; Monect's most-copied feature).
3. **Turbo / auto-fire** per button (hardware-controller table stakes).
4. **Gyro / tilt** input — aiming + racing-wheel steering (Monect's moat; nobody
   serverless has it).
5. **Latency** — "tiny bit of latency but works really well" is the acceptance bar;
   compact reports + a measurable number beat vibes.
6. **Battery-friendly long sessions** — screen dim / OLED-black mode (Pocket-Pad).
7. **Stay-active ergonomics** — controls keep working while the phone does other
   things (Pocket-Pad's "exclusive button mode"); screen-on + rotation-safe (ours).
8. **No host software, works on Android TV / another phone** — the category's
   founding ask; every server-based review thread has a "without installing
   anything?" comment.
9. **Haptics** — button-press vibration for eyes-off play.
10. **Full keyboard reach** — F-row/system keys, media keys from the keyboard,
    multi-layout language support.
11. **Consoles (Switch/PS/Xbox)** — recurring ask; consoles whitelist controller
    identities, so generic BT-HID pads are rejected (Pocket-Pad states Switch
    unsupported). Honest platform condition to document, not promise.

## Where v0.5.0 already stands (unique combo)

No competitor ships **serverless keyboard + touchpad-mouse + gamepad + media in one
BT device** with layout presets, slide-over game pads, landscape + rotation-safe
connection, and an honest per-device capability verdict. Appground splits it across
two apps (gamepad one weak); Pocket-Pad is gamepad-only; Monect needs a server. Our
receiver-side matrix + OEM_DISABLED honesty is a differentiator nobody markets.

## Gap plan — "more customizable and works better than all of them"

Priority order for the next slices (folds into the README ladder):

- **P1 · Layout editor** (ladder #6, the moat): drag/resize/assign buttons over the
  existing preset registry; per-layout save = beats Appground + Pocket-Pad on
  customization outright. Include **per-button turbo** (repeat-while-held at N Hz —
  trivial in the transport) and **haptic feedback** toggle (one-line per press).
- **P1 · On-screen analog sticks** (ladder #8): descriptor already carries X/Y
  centered; add RX/RY (+ Z/RZ triggers) in the same descriptor rev to cover both
  sticks + analog triggers, with per-stick deadzone/sensitivity settings. One
  descriptor bump = one re-pair, so ship sticks + triggers together. Closes the #1
  ask that sank the direct competitor.
- **P2 · Gyro/tilt as stick axes**: SensorManager → gamepad axes (aim-assist +
  steering layout). Nobody serverless has it; converts Monect users.
- **P2 · Session ergonomics**: OLED-black/dim mode, optional immersive full-screen
  pad, per-host profile memory + one-tap reconnect.
- **P3 · Keyboard reach**: F-row page, media keys on the keyboard layout,
  long-press symbol alternates; macro buttons (record key sequence).
- **Measurement, not vibes** (ladder #10): on-device input→report latency counter
  surfaced in the UI; publish the number in the README.

Non-goals (honest walls, documented): console hosts (identity-whitelisted),
iOS-as-controller (OS-blocked — receiver/companion only), OEM_DISABLED phones
(verdict engine already reports).

## Sources

- https://play.google.com/store/apps/details?id=io.appground.blek
- https://www.appbrain.com/app/serverless-bluetooth-keyboard-mouse-for-pc-phone/io.appground.blek
- https://appagg.com/android/tools/serverless-bluetooth-gamepad-35129887.html?hl=en
- https://apkpure.com/serverless-bluetooth-gamepad/io.appground.gamepad
- https://github.com/jobrobse/Pocket-Pad
- https://github.com/ginkage/wearmouse
- https://github.com/Jung-Max/BleHidJoystick · https://github.com/ralismark/bluehid
- https://play.google.com/store/apps/details?id=com.monect.portable&hl=en_IN
- https://sourceforge.net/app/monect-pc-remote/
- https://androidguys.com/news/gamesir-pocket-taco-and-gamesir-g8-retro-mobile-gaming-and-console-style-control/
- https://alternativeto.net/feature/bluetooth-gamepad
