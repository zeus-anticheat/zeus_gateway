#!/usr/bin/env python3
"""Write release-gate evidence for artifacts and protocol fixtures."""

import argparse
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "support-matrix.json"
EVIDENCE_ROOT = ROOT / "verification" / "evidence"
WIRE_TEST = ROOT / "ZeusProtocolJava" / "src" / "test" / "java" / "org" / "vennv" / "WireContractGoldenTest.java"
PROTOCOL_ARTIFACT = ROOT / "ZeusProtocolJava" / "target" / "ZeusProtocolJava-1.0-SNAPSHOT.jar"
BUILDABLE_STATUSES = {"build-verifiable", "supported"}
REQUIRED_PACKET_IDS = ["0x09", "0x22", "0x26", "0x27"]
REQUIRED_FIXTURE_TOKENS = [
    "PacketPlayerAttackEntity",
    "PacketPlayerVelocity",
    "PacketPlayerInventoryTransaction",
    "PacketPlayerExternalForce",
] + REQUIRED_PACKET_IDS


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def rel(path):
    return str(path.relative_to(ROOT))


def safe_name(value):
    return re.sub(r"[^A-Za-z0-9_.-]+", "_", value)


def now():
    return datetime.now(timezone.utc).isoformat()


def load_manifest():
    return json.loads(MANIFEST.read_text(encoding="utf-8"))


def buildable_targets(data):
    for target in data.get("gateway", {}).get("targets", []):
        if target.get("status") in BUILDABLE_STATUSES:
            yield "gateway", target["id"]
    for target in data.get("fabric", {}).get("targets", []):
        if target.get("status") in BUILDABLE_STATUSES:
            yield "fabric", target["minecraft"]


def artifact_path(data, kind, target):
    if kind == "gateway":
        entry = next(
            (entry for entry in data["gateway"]["targets"] if entry["id"] == target),
            None,
        )
        if entry is None:
            raise SystemExit("unknown gateway target: {0}".format(target))
        if entry["artifact"] != "ZeusGateway":
            raise SystemExit("unknown gateway artifact: {0}".format(entry["artifact"]))
        return ROOT / "ZeusGateway" / "target" / "ZeusGateway-1.0-SNAPSHOT.jar"
    return ROOT / "ZeusFabric" / "build" / "libs" / "ZeusFabric-{0}-1.0-SNAPSHOT.jar".format(target)


def evidence_path(gate, kind, target):
    return EVIDENCE_ROOT / gate / "{0}-{1}.json".format(kind, safe_name(target))


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def assert_wire_test():
    if not WIRE_TEST.exists():
        raise SystemExit("wire fixture test is missing: {0}".format(WIRE_TEST))
    text = WIRE_TEST.read_text(encoding="utf-8")
    missing = [token for token in REQUIRED_FIXTURE_TOKENS if token not in text]
    if missing:
        raise SystemExit("wire fixture test is missing required tokens: {0}".format(", ".join(missing)))
    if not PROTOCOL_ARTIFACT.exists():
        raise SystemExit("protocol artifact is missing: {0}".format(PROTOCOL_ARTIFACT))


def artifact_build_evidence(kind, target, artifact, created_at):
    if not artifact.exists():
        raise SystemExit("artifact is missing: {0}".format(artifact))
    return {
        "schemaVersion": 1,
        "gate": "artifact-build",
        "kind": kind,
        "target": target,
        "result": "passed",
        "tool": "scripts/write_release_evidence.py",
        "dryRun": False,
        "createdAt": created_at,
        "artifact": rel(artifact),
        "artifactSha256": sha256(artifact),
        "artifactSizeBytes": artifact.stat().st_size,
    }


def protocol_fixture_evidence(data, kind, target, created_at):
    return {
        "schemaVersion": 1,
        "gate": "protocol-golden-fixtures",
        "kind": kind,
        "target": target,
        "result": "passed",
        "tool": "scripts/write_release_evidence.py",
        "dryRun": False,
        "createdAt": created_at,
        "wireContract": data["wireContract"],
        "fixtureTest": rel(WIRE_TEST),
        "requiredPacketIds": REQUIRED_PACKET_IDS,
        "requiredFixtureTokens": REQUIRED_FIXTURE_TOKENS,
        "testCommand": ["mvn", "-q", "-pl", "ZeusProtocolJava", "-am", "test"],
        "protocolArtifact": rel(PROTOCOL_ARTIFACT),
        "protocolArtifactSha256": sha256(PROTOCOL_ARTIFACT),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--print",
        action="store_true",
        help="print written evidence paths",
    )
    parser.add_argument(
        "--kind",
        choices=("gateway", "fabric"),
        help="write evidence only for one artifact kind",
    )
    args = parser.parse_args()

    data = load_manifest()
    assert_wire_test()
    created_at = now()
    written = []
    for kind, target in buildable_targets(data):
        if args.kind and kind != args.kind:
            continue
        artifact = artifact_path(data, kind, target)
        artifact_data = artifact_build_evidence(kind, target, artifact, created_at)
        protocol_data = protocol_fixture_evidence(data, kind, target, created_at)
        for gate, payload in (
            ("artifact-build", artifact_data),
            ("protocol-golden-fixtures", protocol_data),
        ):
            path = evidence_path(gate, kind, target)
            write_json(path, payload)
            written.append(path)

    if args.print:
        for path in written:
            print(rel(path))
    else:
        print("wrote {0} release evidence files".format(len(written)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
