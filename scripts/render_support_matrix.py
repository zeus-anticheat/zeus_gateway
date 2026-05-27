#!/usr/bin/env python3
"""Render the public support table from the release-gate manifest."""

import argparse
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "support-matrix.json"
OUTPUT = ROOT / "docs" / "generated" / "support-matrix.md"


def render(data):
    lines = [
        "# Verified Support Matrix",
        "",
        "Generated from [`support-matrix.json`](../../support-matrix.json). Do not edit this table manually.",
        "",
        "A target is public **Supported** only when its manifest status is `supported`, after all release gates pass:",
        ", ".join("`" + gate + "`" for gate in data["publicationGate"]) + ".",
        "",
        "## Gateway Artifacts",
        "",
        "| Artifact | Platforms | Java | Intended Range | Status |",
        "|----------|-----------|------|----------------|--------|",
    ]
    for artifact in data["gateway"]["artifacts"]:
        lines.append("| {id} | {platforms} | {java} | {range} | `{status}` |".format(**artifact))

    lines += [
        "",
        "## Gateway Exact-Version Verification Targets",
        "",
        "| Target | Artifact | Platform | Minecraft | Status |",
        "|--------|----------|----------|-----------|--------|",
    ]
    for target in data["gateway"]["targets"]:
        lines.append(
            "| {id} | {artifact} | {platform} | {minecraft} | `{status}` |".format(**target)
        )

    lines += [
        "",
        "## Fabric Exact-Version Artifacts",
        "",
        "| Minecraft | Artifact | Status |",
        "|-----------|----------|--------|",
    ]
    for target in data["fabric"]["targets"]:
        artifact = "ZeusFabric-{0}".format(target["minecraft"]) if target["status"] != "adapter-required" else "-"
        lines.append("| {0} | {1} | `{2}` |".format(target["minecraft"], artifact, target["status"]))

    lines += [
        "",
        "## Current Publication State",
        "",
        "No target is currently marked `supported`. `build-verifiable` identifies source/build wiring only; it is not a server compatibility claim.",
        "",
        "The shared wire contract is `{0}`. Golden fixtures protect attack, velocity, surrounding-block, inventory-transaction and external-force payloads.".format(data["wireContract"]),
        "",
    ]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true", help="update the generated Markdown file")
    args = parser.parse_args()

    expected = render(json.loads(MANIFEST.read_text(encoding="utf-8")))
    if args.write:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_text(expected, encoding="utf-8")
        return 0
    if not OUTPUT.exists() or OUTPUT.read_text(encoding="utf-8") != expected:
        print("Support matrix documentation is stale. Run: python3 scripts/render_support_matrix.py --write")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
