#!/usr/bin/env python3
"""Validate the committed mock game-state against the games-web contract.

Dependency-free: if `jsonschema` is installed it runs full-schema validation;
otherwise (and always, as a belt-and-suspenders pass) it runs targeted
structural assertions on the invariants the renderer relies on.
Exit 0 = contract holds. Exit 1 = mismatch.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SCHEMA = os.path.join(ROOT, "data", "schema", "game-state.schema.json")
MOCK = os.path.join(ROOT, "data", "mock", "mining-character.json")

RARITIES = {"common", "uncommon", "rare", "epic", "legendary"}
STRUCT_STATUS = {"idle", "working", "upgrading", "locked"}
GEAR_SLOTS = {"head", "chest", "hands", "legs", "feet", "main_hand", "off_hand", "trinket"}


def fail(msg):
    print("FAIL:", msg)
    sys.exit(1)


def main():
    with open(SCHEMA) as f:
        schema = json.load(f)
    with open(MOCK) as f:
        mock = json.load(f)
    print("ok: schema and mock both parse as JSON")

    try:
        import jsonschema
        jsonschema.validate(mock, schema)
        print("ok: jsonschema full-schema validation passed")
    except ImportError:
        print("note: jsonschema not installed - running structural checks only")

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

    print("ok: %d stats, 8 gear slots, %d skills, %d structures - all invariants hold" % (
        len(mock["stats"]), len(mock["skills"]), len(mock["structures"])))
    print("PASS: mock game-state satisfies games-web.character-sheet v" + mock["schema_version"])


if __name__ == "__main__":
    main()
