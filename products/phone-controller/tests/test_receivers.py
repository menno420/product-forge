#!/usr/bin/env python3
"""Tests for the receiver-compatibility matrix."""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from src import receivers as rx  # noqa: E402


class TestMatrixIntegrity(unittest.TestCase):
    def test_matrix_is_nonempty_and_matches_idea_doc_row_count(self):
        # The idea doc's table has exactly 7 target rows.
        self.assertEqual(len(rx.RECEIVERS), 7)

    def test_all_support_values_are_valid_tokens(self):
        for r in rx.RECEIVERS:
            for field in ("keyboard", "mouse", "gamepad"):
                self.assertIn(getattr(r, field), rx.SUPPORT_VALUES,
                              "%s.%s = %r not a valid token" % (r.target, field, getattr(r, field)))

    def test_every_target_name_is_unique(self):
        names = [r.target for r in rx.RECEIVERS]
        self.assertEqual(len(names), len(set(names)))

    def test_every_row_has_a_needs_app_note(self):
        for r in rx.RECEIVERS:
            self.assertTrue(r.needs_app_note.strip(), "%s missing needs_app_note" % r.target)


class TestLookups(unittest.TestCase):
    def test_lookup_known_target(self):
        pc = rx.lookup("Windows / macOS / Linux PC")
        self.assertEqual(pc.keyboard, rx.YES)
        self.assertEqual(pc.mouse, rx.YES)
        self.assertTrue(pc.out_of_box)

    def test_lookup_unknown_target_raises(self):
        with self.assertRaises(KeyError):
            rx.lookup("Nintendo Switch")

    def test_tvos_mouse_is_not_applicable(self):
        tv = rx.lookup("Smart TV — Apple tvOS")
        self.assertEqual(tv.mouse, rx.NA)

    def test_out_of_box_targets_all_flagged(self):
        oob = rx.out_of_box_targets()
        # Every row in the idea doc's matrix pairs out-of-box for keyboard/mouse.
        self.assertEqual(len(oob), 7)
        for name in oob:
            self.assertTrue(rx.lookup(name).out_of_box)

    def test_targets_supporting_keyboard_excludes_none(self):
        kb = rx.targets_supporting("keyboard")
        # All 7 rows support keyboard at yes/mostly.
        self.assertEqual(len(kb), 7)

    def test_targets_supporting_mouse_excludes_na_row(self):
        mouse = rx.targets_supporting("mouse")
        # tvOS mouse is n/a -> excluded.
        self.assertNotIn("Smart TV — Apple tvOS", mouse)
        self.assertEqual(len(mouse), 6)

    def test_targets_supporting_rejects_bad_capability(self):
        with self.assertRaises(ValueError):
            rx.targets_supporting("touchscreen")


if __name__ == "__main__":
    unittest.main()
