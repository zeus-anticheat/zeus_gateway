#!/usr/bin/env python3
"""Render support readiness from the manifest and evidence files."""

import argparse
import importlib.util
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "support-matrix.json"
OUTPUT = ROOT / "docs" / "generated" / "support-readiness.md"
PUBLICATION_GATES = [
    "artifact-build",
    "protocol-golden-fixtures",
    "server-startup-smoke",
    "core-scenario-smoke",
]
NON_SMOKE_GATES = {"artifact-build", "protocol-golden-fixtures"}
BUILDABLE_STATUSES = {"build-verifiable", "supported"}
EVIDENCE_DIRS = {
    "artifact-build": "artifact-build",
    "protocol-golden-fixtures": "protocol-golden-fixtures",
    "server-startup-smoke": "startup-smoke",
    "core-scenario-smoke": "core-scenario-smoke",
}


def load_verifier_module():
    path = ROOT / "scripts" / "verify_support_matrix.py"
    spec = importlib.util.spec_from_file_location("verify_support_matrix", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def safe_name(value):
    return "".join(ch if ch.isalnum() or ch in "._-" else "_" for ch in value)


def evidence_path(kind, target, gate):
    directory = EVIDENCE_DIRS[gate]
    return Path("verification") / "evidence" / directory / "{0}-{1}.json".format(kind, safe_name(target))


def targets(data):
    for target in data.get("gateway", {}).get("targets", []):
        if target.get("status") in BUILDABLE_STATUSES:
            yield {
                "kind": "gateway",
                "target": target["id"],
                "label": "{0} ({1})".format(target["id"], target["artifact"]),
                "status": target["status"],
            }
    for target in data.get("fabric", {}).get("targets", []):
        if target.get("status") in BUILDABLE_STATUSES:
            yield {
                "kind": "fabric",
                "target": target["minecraft"],
                "label": "ZeusFabric-{0}".format(target["minecraft"]),
                "status": target["status"],
            }


def gate_state(verifier_module, kind, target, gate):
    path = evidence_path(kind, target, gate)
    if not (ROOT / path).exists():
        return {
            "gate": gate,
            "state": "missing",
            "path": str(path),
            "errors": ["evidence file is missing"],
        }

    verifier = verifier_module.Verifier()
    verifier_module.verify_evidence_file(target, gate, str(path), verifier, kind, target)
    if verifier.errors:
        return {
            "gate": gate,
            "state": "invalid",
            "path": str(path),
            "errors": verifier.errors,
        }
    return {
        "gate": gate,
        "state": "passed",
        "path": str(path),
        "errors": [],
    }


def readiness(data):
    verifier_module = load_verifier_module()
    rows = []
    for target in targets(data):
        gates = [gate_state(verifier_module, target["kind"], target["target"], gate) for gate in PUBLICATION_GATES]
        missing = [gate["gate"] for gate in gates if gate["state"] != "passed"]
        rows.append({
            **target,
            "gates": gates,
            "ready": not missing,
            "nextMissingGate": missing[0] if missing else "",
        })
    return rows


def marker(state):
    if state == "passed":
        return "pass"
    if state == "missing":
        return "missing"
    return "invalid"


def render(data):
    rows = readiness(data)
    lines = [
        "# Support Readiness",
        "",
        "Generated from [`support-matrix.json`](../../support-matrix.json) and `verification/evidence/`.",
        "A target can be marked `supported` only when every gate below is `pass`.",
        "",
        "| Target | Status | Artifact Build | Protocol Fixtures | Startup Smoke | Core Scenario | Next Missing Gate |",
        "|--------|--------|----------------|-------------------|---------------|---------------|-------------------|",
    ]
    for row in rows:
        states = {gate["gate"]: marker(gate["state"]) for gate in row["gates"]}
        lines.append(
            "| {label} | `{status}` | {artifact} | {protocol} | {startup} | {core} | {next_gate} |".format(
                label=row["label"],
                status=row["status"],
                artifact=states["artifact-build"],
                protocol=states["protocol-golden-fixtures"],
                startup=states["server-startup-smoke"],
                core=states["core-scenario-smoke"],
                next_gate=row["nextMissingGate"] or "-",
            )
        )

    lines += [
        "",
        "## Evidence Paths",
        "",
    ]
    for row in rows:
        lines.append("### {0}".format(row["label"]))
        for gate in row["gates"]:
            if gate["state"] == "passed":
                detail = gate["path"]
            else:
                detail = "{0} ({1})".format(gate["path"], "; ".join(gate["errors"]))
            lines.append("- `{0}`: {1}".format(gate["gate"], detail))
        lines.append("")
    return "\n".join(lines)


def parse_required_gates(values, require_all):
    if require_all:
        return set(PUBLICATION_GATES)
    gates = set()
    for value in values:
        for gate in value.split(","):
            gate = gate.strip()
            if not gate:
                continue
            if gate not in PUBLICATION_GATES:
                raise SystemExit("unknown readiness gate: {0}".format(gate))
            gates.add(gate)
    return gates


def verify_required_gates(rows, required_gates):
    errors = []
    for row in rows:
        by_gate = {gate["gate"]: gate for gate in row["gates"]}
        for gate in sorted(required_gates):
            state = by_gate[gate]["state"]
            if state != "passed":
                errors.append("{0} {1} is {2}: {3}".format(
                    row["label"],
                    gate,
                    state,
                    "; ".join(by_gate[gate]["errors"]),
                ))
    return errors


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true", help="update the generated readiness report")
    parser.add_argument("--json", action="store_true", help="print machine-readable readiness JSON")
    parser.add_argument(
        "--require-gate",
        action="append",
        default=[],
        help="fail unless this gate has passed for every buildable target; can be repeated or comma-separated",
    )
    parser.add_argument("--require-non-smoke-gates", action="store_true", help="require artifact-build and protocol-golden-fixtures")
    parser.add_argument("--require-all-gates", action="store_true", help="require every publication gate")
    args = parser.parse_args()

    data = json.loads(MANIFEST.read_text(encoding="utf-8"))
    rows = readiness(data)
    if args.json:
        print(json.dumps(rows, indent=2, sort_keys=True))
        return 0

    expected = render(data)
    if args.write:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_text(expected, encoding="utf-8")
        return 0
    if not OUTPUT.exists() or OUTPUT.read_text(encoding="utf-8") != expected:
        print("Support readiness documentation is stale. Run: python3 scripts/render_support_readiness.py --write")
        return 1
    required_gates = parse_required_gates(args.require_gate, args.require_all_gates)
    if args.require_non_smoke_gates:
        required_gates.update(NON_SMOKE_GATES)
    errors = verify_required_gates(rows, required_gates)
    if errors:
        for error in errors:
            print("error: {0}".format(error))
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
