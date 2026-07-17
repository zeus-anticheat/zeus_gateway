#!/usr/bin/env python3

import importlib.util
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent


def load_module(name):
    path = ROOT / "scripts" / (name + ".py")
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class SupportProfileTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = json.loads((ROOT / "support-matrix.json").read_text(encoding="utf-8"))
        cls.smoke = load_module("run_core_scenario_smoke")
        cls.verifier = load_module("verify_support_matrix")

    def required_ids(self, target):
        return self.verifier.required_profile_packet_ids(
            self.manifest, target, "compatibility-core")

    def test_gateway_targets_require_simulation_baseline(self):
        baseline = {"0x03", "0x25", "0x2b", "0x30"}
        for target in self.manifest["gateway"]["targets"]:
            with self.subTest(target=target["id"]):
                self.assertTrue(baseline <= self.required_ids(target))

    def test_capability_ids_are_target_conditional(self):
        targets = {target["id"]: target for target in self.manifest["gateway"]["targets"]}
        self.assertNotIn("0x2c", self.required_ids(targets["spigot-1.14.4"]))
        self.assertIn("0x2e", self.required_ids(targets["spigot-1.14.4"]))
        self.assertTrue(
            {"0x2c", "0x2e"} <= self.required_ids(targets["paper-1.21.11"])
        )

    def test_smoke_and_verifier_resolve_same_profile(self):
        for target in self.manifest["gateway"]["targets"]:
            with self.subTest(target=target["id"]):
                capabilities = set(target.get("evidenceCapabilities") or [])
                actual = {
                    "0x{0:02x}".format(packet_id)
                    for packet_id in self.smoke.profile_packet_ids(
                        "compatibility-core", capabilities, self.manifest
                    )
                }
                self.assertEqual(self.required_ids(target), actual)

    def test_extra_evidence_packet_ids_are_allowed(self):
        target = self.manifest["gateway"]["targets"][0]
        evidence = self.required_ids(target) | {"0x7f"}
        self.assertEqual(
            set(),
            self.verifier.missing_profile_packet_ids(
                self.manifest, target, "compatibility-core", evidence
            ),
        )


if __name__ == "__main__":
    unittest.main()
