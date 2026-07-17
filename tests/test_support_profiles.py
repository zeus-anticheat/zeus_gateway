#!/usr/bin/env python3

import importlib.util
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


smoke = load_module("run_core_scenario_smoke", ROOT / "scripts" / "run_core_scenario_smoke.py")
verifier_module = load_module("verify_support_matrix", ROOT / "scripts" / "verify_support_matrix.py")


class SupportProfileTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = json.loads((ROOT / "support-matrix.json").read_text(encoding="utf-8"))

    def target(self, section, key, value):
        return next(item for item in self.manifest[section]["targets"] if item.get(key) == value)

    def test_compatibility_core_always_requires_simulation_streams(self):
        required = verifier_module.required_profile_packet_ids(
            self.manifest, self.target("gateway", "id", "spigot-1.8.8"), "compatibility-core")
        self.assertTrue({"0x03", "0x25", "0x2b", "0x2d"}.issubset(required))
        self.assertNotIn("0x2c", required)
        self.assertNotIn("0x2e", required)

    def test_target_capabilities_add_only_available_packets(self):
        paper = verifier_module.required_profile_packet_ids(
            self.manifest, self.target("gateway", "id", "paper-1.21.11"), "compatibility-core")
        fabric = verifier_module.required_profile_packet_ids(
            self.manifest, self.target("fabric", "minecraft", "1.21.11"), "compatibility-core")
        self.assertTrue({"0x2c", "0x2e"}.issubset(paper))
        self.assertIn("0x2c", fabric)
        self.assertNotIn("0x2e", fabric)

    def test_movement_attribute_capability_has_scenario_action(self):
        self.assertEqual(
            ["effect give ZeusSmokeBot minecraft:speed 5 1 true"],
            smoke.capability_scenario_commands({"movement-attributes"}),
        )
        self.assertEqual([], smoke.capability_scenario_commands({"trusted-input"}))

    def test_smoke_profile_matches_verifier_and_allows_extra_assertions(self):
        target = self.target("gateway", "id", "paper-1.21.11")
        expected = verifier_module.required_profile_packet_ids(
            self.manifest, target, "compatibility-core")
        parsed = smoke.parse_packet_ids(["0x2f"], ["compatibility-core"], "gateway", "paper-1.21.11")
        parsed_hex = {"0x{0:02x}".format(packet_id) for packet_id in parsed}
        self.assertFalse(verifier_module.missing_profile_packet_ids(
            self.manifest, target, "compatibility-core", parsed_hex))
        self.assertTrue({int(packet_id, 0) for packet_id in expected}.issubset(parsed))
        self.assertIn(0x2F, parsed)


if __name__ == "__main__":
    unittest.main()
