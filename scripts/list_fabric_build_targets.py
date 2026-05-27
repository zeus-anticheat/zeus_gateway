#!/usr/bin/env python3
"""Print Fabric targets that the release gate must build."""

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "support-matrix.json"
BUILD_STATUSES = {"build-verifiable", "supported"}


def main():
    data = json.loads(MANIFEST.read_text(encoding="utf-8"))
    for target in data.get("fabric", {}).get("targets", []):
        if target.get("status") in BUILD_STATUSES:
            print(target["minecraft"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
