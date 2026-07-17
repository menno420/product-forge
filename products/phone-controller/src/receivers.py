"""Receiver-compatibility matrix — which targets accept a standard BT-HID device.

Transcribed from the idea doc's sourced compatibility table
(menno420/idea-engine : ideas/product-forge/bt-controller-plan-2026-07-17.md, blob
970edbfc, § "Receiver-side compatibility matrix"). This answers, per target device:
does it accept a standard Bluetooth HID keyboard / mouse / gamepad with NO companion app?

Support values:
  * "yes"    — works out of the box
  * "mostly" — supported but coverage depends on model year / firmware (idea doc "Varies";
               esp. 2022+ TVs)
  * "no"     — not accepted
  * "n/a"    — not applicable to that target

The three per-capability fields (keyboard/mouse/gamepad) use those values. ``out_of_box``
captures the idea doc's "Out-of-box (no companion app)?" column at a whole-device level.
"""

from __future__ import annotations

from dataclasses import dataclass

YES = "yes"
MOSTLY = "mostly"
NO = "no"
NA = "n/a"

SUPPORT_VALUES = frozenset({YES, MOSTLY, NO, NA})


@dataclass(frozen=True)
class Receiver:
    """One target device row from the compatibility matrix."""

    target: str
    keyboard: str
    mouse: str
    gamepad: str
    out_of_box: bool          # accepts a std BT-HID keyboard/mouse with no companion app
    needs_app_note: str       # the "Needs app?" caveat from the idea doc


# The matrix, in the idea doc's row order.
RECEIVERS = (
    Receiver("Android phone/tablet", YES, YES, YES, True,
             "No — pairs as a standard HID device."),
    Receiver("iPhone / iPad", YES, YES, YES, True,
             "Keyboard/mouse: no. Gamepad: needs a GameController-framework-aware app on "
             "the target to adopt it (iOS 13 / iPadOS 13.4 cursor for mouse)."),
    Receiver("Smart TV — Android/Google TV", MOSTLY, MOSTLY, YES, True,
             "Platform-dependent; mostly out-of-box on 2022+ sets."),
    Receiver("Smart TV — LG webOS", YES, YES, YES, True,
             "No — accepts standard BT HID."),
    Receiver("Smart TV — Samsung Tizen", MOSTLY, MOSTLY, YES, True,
             "Mostly out-of-box, esp. 2022+ sets."),
    Receiver("Smart TV — Apple tvOS", YES, NA, YES, True,
             "Keyboard: no. Gamepad: via the GameController framework."),
    Receiver("Windows / macOS / Linux PC", YES, YES, YES, True,
             "No — generic HID, including a generic gamepad fallback."),
)

# Fast lookup by exact target name.
_BY_TARGET = {r.target: r for r in RECEIVERS}


def lookup(target: str) -> Receiver:
    """Return the Receiver row for ``target`` (exact name). Raises KeyError if unknown."""
    return _BY_TARGET[target]


def out_of_box_targets() -> list:
    """Targets that accept a standard BT-HID device with no companion app."""
    return [r.target for r in RECEIVERS if r.out_of_box]


def targets_supporting(capability: str) -> list:
    """Targets where ``capability`` ('keyboard'|'mouse'|'gamepad') is 'yes' or 'mostly'.

    Excludes 'no' and 'n/a'.
    """
    if capability not in ("keyboard", "mouse", "gamepad"):
        raise ValueError("capability must be one of keyboard, mouse, gamepad")
    hits = []
    for r in RECEIVERS:
        value = getattr(r, capability)
        if value in (YES, MOSTLY):
            hits.append(r.target)
    return hits
