#!/usr/bin/env python3
"""Validate the committed mock game-state against the games-web contract.

Dependency-free: if `jsonschema` is installed it runs full-schema validation;
otherwise (and always, as a belt-and-suspenders pass) it runs targeted
structural assertions on the invariants the renderer relies on.

Beyond the happy path, this also exercises the contract's *rejection* power:
a set of known-bad mutations of the mock must each be refused, so a regression
that silently weakens the contract is caught. Structural rejection always runs;
jsonschema rejection runs too when the library is installed.

Exit 0 = contract holds (good mock accepted, bad mocks rejected).
Exit 1 = mismatch.
"""
import copy
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SCHEMA = os.path.join(ROOT, "data", "schema", "game-state.schema.json")
MOCK = os.path.join(ROOT, "data", "mock", "mining-character.json")
# Every committed fixture must satisfy the happy path. The first (MOCK) is also
# the base for the known-bad mutations below.
MOCKS = [
    os.path.join(ROOT, "data", "mock", "mining-character.json"),
    os.path.join(ROOT, "data", "mock", "recruit-character.json"),
]

RARITIES = {"common", "uncommon", "rare", "epic", "legendary"}
STRUCT_STATUS = {"idle", "working", "upgrading", "locked"}
GEAR_SLOTS = {"head", "chest", "hands", "legs", "feet", "main_hand", "off_hand", "trinket"}


class ContractError(Exception):
    """Raised when a game-state violates the structural contract."""


def fail(msg):
    raise ContractError(msg)


def check_structural(mock):
    """Assert the invariants the renderer relies on. Raises ContractError on any violation."""
    for k in ("schema_version", "contract", "generated_at", "character", "stats", "gear", "skills", "structures"):
        if k not in mock:
            fail("missing top-level key: " + k)
    if mock["contract"] != "games-web.character-sheet":
        fail("contract must be games-web.character-sheet, got " + repr(mock["contract"]))
    if not re.match(r"^\d+\.\d+\.\d+$", mock["schema_version"]):
        fail("schema_version not semver: " + repr(mock["schema_version"]))

    c = mock["character"]
    for k in ("name", "class", "level", "title"):
        if k not in c:
            fail("character missing " + k)
    if not isinstance(c["level"], int) or c["level"] < 1:
        fail("character.level must be int >=1")

    if set(mock["gear"].keys()) != GEAR_SLOTS:
        fail("gear slots must be exactly " + str(sorted(GEAR_SLOTS)) + ", got " + str(sorted(mock["gear"].keys())))
    for slot, item in mock["gear"].items():
        if item is None:
            continue
        if "name" not in item:
            fail("gear." + slot + " missing name")
        if item.get("rarity") not in RARITIES:
            fail("gear." + slot + " bad rarity: " + repr(item.get("rarity")))

    if not mock["stats"]:
        fail("stats is empty")
    for s in mock["stats"]:
        for k in ("key", "label", "value"):
            if k not in s:
                fail("stat missing " + k)

    for sk in mock["skills"]:
        for k in ("key", "label", "level", "xp", "xp_max"):
            if k not in sk:
                fail("skill missing " + k)
        if sk["xp"] > sk["xp_max"]:
            fail("skill " + sk["key"] + " xp>xp_max")
        if sk["xp_max"] < 1:
            fail("skill " + sk["key"] + " xp_max<1")

    for st in mock["structures"]:
        for k in ("key", "label", "tier", "status"):
            if k not in st:
                fail("structure missing " + k)
        if st["status"] not in STRUCT_STATUS:
            fail("structure " + st["key"] + " bad status: " + repr(st["status"]))


# Known-bad mutations the contract MUST reject. Each takes a deep copy of the
# good mock and corrupts one invariant. name -> mutator.
def _drop_top_key(m):
    del m["structures"]


def _wrong_contract(m):
    m["contract"] = "games-web.something-else"


def _bad_semver(m):
    m["schema_version"] = "1.0"


def _level_zero(m):
    m["character"]["level"] = 0


def _missing_gear_slot(m):
    del m["gear"]["trinket"]


def _bad_rarity(m):
    m["gear"]["head"]["rarity"] = "mythic"


def _empty_stats(m):
    m["stats"] = []


def _xp_over_max(m):
    m["skills"][0]["xp"] = m["skills"][0]["xp_max"] + 1


def _bad_struct_status(m):
    m["structures"][0]["status"] = "exploding"


BAD_MUTATIONS = [
    ("missing top-level key (structures)", _drop_top_key),
    ("wrong contract id", _wrong_contract),
    ("non-semver schema_version", _bad_semver),
    ("character.level below minimum", _level_zero),
    ("missing gear slot", _missing_gear_slot),
    ("invalid gear rarity", _bad_rarity),
    ("empty stats array", _empty_stats),
    ("skill xp exceeds xp_max", _xp_over_max),
    ("invalid structure status", _bad_struct_status),
]


def main():
    with open(SCHEMA) as f:
        schema = json.load(f)
    print("ok: schema parses as JSON")

    have_jsonschema = False
    try:
        import jsonschema
        have_jsonschema = True
    except ImportError:
        print("note: jsonschema not installed - running structural checks only")

    # --- Positive path: EVERY committed fixture MUST be accepted. ---
    for path in MOCKS:
        with open(path) as f:
            fixture = json.load(f)
        name = os.path.basename(path)
        if have_jsonschema:
            jsonschema.validate(fixture, schema)
            print("ok: %s - jsonschema full-schema validation passed" % name)
        check_structural(fixture)
        print("ok: %s - %d stats, 8 gear slots, %d skills, %d structures - all invariants hold" % (
            name, len(fixture["stats"]), len(fixture["skills"]), len(fixture["structures"])))

    # The known-bad mutations below corrupt one invariant of the first fixture.
    with open(MOCK) as f:
        mock = json.load(f)

    # --- Negative path: each known-bad mutation MUST be rejected. ---
    for label, mutate in BAD_MUTATIONS:
        bad = copy.deepcopy(mock)
        mutate(bad)

        # Structural checks must reject it (dependency-free, always runs).
        try:
            check_structural(bad)
        except ContractError:
            pass
        else:
            fail("negative case NOT rejected by structural checks: " + label)

        # jsonschema, when present, must also reject it.
        if have_jsonschema:
            try:
                jsonschema.validate(bad, schema)
            except jsonschema.ValidationError:
                pass
            else:
                fail("negative case NOT rejected by jsonschema: " + label)

    print("ok: %d known-bad mutations all rejected%s" % (
        len(BAD_MUTATIONS), " (structural + jsonschema)" if have_jsonschema else " (structural)"))
    print("PASS: mock game-state satisfies games-web.character-sheet v" + mock["schema_version"]
          + "; contract rejects malformed states")


if __name__ == "__main__":
    try:
        main()
    except ContractError as e:
        print("FAIL:", e)
        sys.exit(1)
