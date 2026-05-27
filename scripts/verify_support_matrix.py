#!/usr/bin/env python3
"""Validate support declarations against build metadata and artifacts.

This is intentionally a release-claim gate, not a Minecraft server simulator.
It prevents the manifest and docs from advertising support that has not been
backed by the required evidence, and it verifies the exact artifacts currently
marked build-verifiable.
"""

import argparse
import hashlib
import io
import json
import re
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree


ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "support-matrix.json"
EVIDENCE_ROOT = ROOT / "verification" / "evidence"
GATEWAY_POM = ROOT / "ZeusGateway" / "pom.xml"
GATEWAY_LEGACY_POM = ROOT / "ZeusGatewayLegacy" / "pom.xml"
GATEWAY_LEGACY_SRC = ROOT / "ZeusGatewayLegacy" / "src" / "main"
GATEWAY_LEGACY_PLUGIN_YML = ROOT / "ZeusGatewayLegacy" / "src" / "main" / "resources" / "plugin.yml"
PROTOCOL_POM = ROOT / "ZeusProtocolJava" / "pom.xml"
PROTOCOL_ARTIFACT = ROOT / "ZeusProtocolJava" / "target" / "ZeusProtocolJava-1.0-SNAPSHOT.jar"
FABRIC_BUILD = ROOT / "ZeusFabric" / "build.gradle"
FABRIC_MOD_TEMPLATE = ROOT / "ZeusFabric" / "src" / "main" / "resources" / "fabric.mod.json"
WIRE_GOLDEN_TEST = ROOT / "ZeusProtocolJava" / "src" / "test" / "java" / "org" / "vennv" / "WireContractGoldenTest.java"
PROTOCOL_CONTRACT_RESOURCE = "zeus-protocol-contract.json"

PUBLICATION_GATES = [
    "artifact-build",
    "protocol-golden-fixtures",
    "server-startup-smoke",
    "core-scenario-smoke",
]
ALLOWED_STATUSES = {"planned", "adapter-required", "build-verifiable", "supported"}
MAVEN_NS = {"m": "http://maven.apache.org/POM/4.0.0"}
CORE_SCENARIO_REQUIRED_PACKET_IDS = {"0x09", "0x13", "0x22", "0x26", "0x27"}
REQUIRED_PROTOCOL_FIXTURE_TOKENS = {
    "PacketPlayerAttackEntity",
    "PacketPlayerSurroundingBlocks",
    "PacketPlayerVelocity",
    "PacketPlayerInventoryTransaction",
    "PacketPlayerExternalForce",
    "0x09",
    "0x13",
    "0x22",
    "0x26",
    "0x27",
}
LEGACY_FORBIDDEN_SOURCE_PATTERNS = {
    "io.papermc.": "Paper API must stay out of the legacy artifact",
    "com.destroystokyo.": "Paper API must stay out of the legacy artifact",
    "com.comphenix.": "ProtocolLib API must stay out of the legacy artifact until a 4.x adapter is isolated",
    "org.bukkit.util.BoundingBox": "BoundingBox does not exist on 1.8-1.13 legacy targets",
    "org.bukkit.block.data.": "BlockData does not exist on 1.8-1.12 legacy targets",
    "org.bukkit.attribute.": "Attribute API is not available across legacy targets",
    "org.bukkit.persistence.": "PersistentData API is not available across legacy targets",
    "org.bukkit.NamespacedKey": "NamespacedKey is not available across legacy targets",
    ".getBoundingBox(": "BoundingBox access is not legacy-safe",
    ".getBlockData(": "BlockData access is not legacy-safe",
    ".isAir(": "Material.isAir is not legacy-safe",
    ".getAttribute(": "Attribute access is not legacy-safe",
    ".getPersistentDataContainer(": "PersistentData access is not legacy-safe",
    ".getPose(": "Entity pose access is not legacy-safe",
    ".getHeight(": "Entity height access is not legacy-safe",
    ".getWidth(": "Entity width access is not legacy-safe",
    "PrePlayerAttackEntityEvent": "Paper attack event must stay out of legacy",
    "PlayerArmorChangeEvent": "Paper armor event must stay out of legacy",
    "RegionizedServer": "Folia classes must stay out of legacy",
    "ProtocolLibrary": "ProtocolLib runtime hooks must be isolated before legacy support",
    "ProtocolManager": "ProtocolLib runtime hooks must be isolated before legacy support",
    "PacketType.": "ProtocolLib packet constants must be isolated before legacy support",
    "List.of(": "Java 9 collection factory is not Java 8-compatible",
    "Map.of(": "Java 9 collection factory is not Java 8-compatible",
    "Set.of(": "Java 9 collection factory is not Java 8-compatible",
    "List.copyOf(": "Java 10 collection copy is not Java 8-compatible",
    "var ": "local variable type inference is not Java 8-compatible",
    "record ": "records are not Java 8-compatible",
}


class Verifier:
    def __init__(self):
        self.errors = []
        self.warnings = []

    def check(self, condition, message):
        if not condition:
            self.errors.append(message)

    def warn(self, condition, message):
        if not condition:
            self.warnings.append(message)

    def fail(self, message):
        self.errors.append(message)


def read_text(path):
    return path.read_text(encoding="utf-8")


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def sha256_bytes(data):
    return hashlib.sha256(data).hexdigest()


def load_manifest(verifier):
    if not MANIFEST.exists():
        verifier.fail("support-matrix.json is missing")
        return {}
    try:
        return json.loads(read_text(MANIFEST))
    except json.JSONDecodeError as exc:
        verifier.fail("support-matrix.json is not valid JSON: {0}".format(exc))
        return {}


def parse_pom(path, verifier):
    try:
        return ElementTree.parse(path).getroot()
    except (ElementTree.ParseError, FileNotFoundError) as exc:
        verifier.fail("{0} is not readable XML: {1}".format(path.relative_to(ROOT), exc))
        return None


def pom_text(root, path):
    if root is None:
        return None
    return root.findtext(path, namespaces=MAVEN_NS)


def verify_generated_docs(data, verifier):
    sys.path.insert(0, str(ROOT / "scripts"))
    try:
        import render_support_matrix
        import render_support_readiness
    except ImportError as exc:
        verifier.fail("cannot import generated-doc render script: {0}".format(exc))
        return

    generated_docs = [
        (
            ROOT / "docs" / "generated" / "support-matrix.md",
            render_support_matrix.render(data),
            "python3 scripts/render_support_matrix.py --write",
        ),
        (
            ROOT / "docs" / "generated" / "support-readiness.md",
            render_support_readiness.render(data),
            "python3 scripts/render_support_readiness.py --write",
        ),
    ]
    for output, expected, command in generated_docs:
        if not output.exists():
            verifier.fail("{0} is missing".format(output.relative_to(ROOT)))
            continue
        actual = read_text(output)
        verifier.check(
            actual == expected,
            "{0} is stale; run: {1}".format(output.relative_to(ROOT), command),
        )


def verify_supported_evidence(label, item, gates, verifier, expected_kind, expected_target):
    if item.get("status") != "supported":
        return
    evidence = item.get("evidence")
    if not isinstance(evidence, dict):
        verifier.fail("{0} is marked supported but has no evidence object".format(label))
        return
    for gate in gates:
        evidence_path = evidence.get(gate)
        if not evidence_path:
            verifier.fail("{0} is marked supported but evidence.{1} is missing".format(label, gate))
            continue
        verify_evidence_file(label, gate, evidence_path, verifier, expected_kind, expected_target)


def verify_evidence_file(label, gate, evidence_path, verifier, expected_kind, expected_target):
    if not isinstance(evidence_path, str):
        verifier.fail("{0} evidence.{1} must be a relative JSON path".format(label, gate))
        return
    path = Path(evidence_path)
    if path.is_absolute() or ".." in path.parts:
        verifier.fail("{0} evidence.{1} must stay inside the repository".format(label, gate))
        return
    full_path = ROOT / path
    if not full_path.exists():
        verifier.fail("{0} evidence.{1} file is missing: {2}".format(label, gate, evidence_path))
        return
    if EVIDENCE_ROOT not in full_path.resolve().parents and full_path.resolve() != EVIDENCE_ROOT.resolve():
        verifier.fail("{0} evidence.{1} must be under verification/evidence".format(label, gate))
        return
    try:
        data = json.loads(read_text(full_path))
    except json.JSONDecodeError as exc:
        verifier.fail("{0} evidence.{1} is not valid JSON: {2}".format(label, gate, exc))
        return
    verifier.check(data.get("schemaVersion") == 1, "{0} evidence.{1} schemaVersion must be 1".format(label, gate))
    verifier.check(data.get("gate") == gate, "{0} evidence.{1} gate mismatch".format(label, gate))
    verifier.check(data.get("result") == "passed", "{0} evidence.{1} must have result=passed".format(label, gate))
    verifier.check(data.get("kind") == expected_kind, "{0} evidence.{1} kind mismatch".format(label, gate))
    verifier.check(data.get("target") == expected_target, "{0} evidence.{1} target mismatch".format(label, gate))
    verifier.check(data.get("dryRun") is not True, "{0} evidence.{1} must not be dry-run output".format(label, gate))
    verifier.check(data.get("tool"), "{0} evidence.{1} must record generating tool".format(label, gate))
    if gate in {"artifact-build", "server-startup-smoke", "core-scenario-smoke"}:
        verify_evidence_artifact(label, gate, data, verifier)
    if gate == "artifact-build":
        verifier.check(int(data.get("artifactSizeBytes", 0)) > 0, "{0} evidence.{1} must record artifactSizeBytes".format(label, gate))
    if gate == "protocol-golden-fixtures":
        verify_protocol_fixture_evidence(label, gate, data, verifier)
    if gate == "server-startup-smoke":
        verifier.check(data.get("successSeen") is True, "{0} evidence.{1} must record successSeen=true".format(label, gate))
        if expected_kind == "gateway":
            verifier.check(data.get("serverTargetSeen") is True, "{0} evidence.{1} must prove its exact Gateway server target".format(label, gate))
        verifier.check(data.get("exitCode") == 0, "{0} evidence.{1} must record exitCode=0".format(label, gate))
    if gate == "core-scenario-smoke":
        required_ids = set(data.get("requiredPacketIds") or [])
        verifier.check(bool(required_ids), "{0} evidence.{1} must list requiredPacketIds".format(label, gate))
        verifier.check(
            CORE_SCENARIO_REQUIRED_PACKET_IDS.issubset(required_ids),
            "{0} evidence.{1} must require compatibility-core packets {2}".format(
                label,
                gate,
                ", ".join(sorted(CORE_SCENARIO_REQUIRED_PACKET_IDS)),
            ),
        )
        verifier.check(data.get("startupSeen") is True, "{0} evidence.{1} must record startupSeen=true".format(label, gate))
        if expected_kind == "gateway":
            verifier.check(data.get("serverTargetSeen") is True, "{0} evidence.{1} must prove its exact Gateway server target".format(label, gate))
        verifier.check(data.get("exitCode") == 0, "{0} evidence.{1} must record exitCode=0".format(label, gate))
        verifier.check(data.get("missingPacketIds") == [], "{0} evidence.{1} must have no missingPacketIds".format(label, gate))
        verifier.check(int(data.get("packetCount", 0)) > 0, "{0} evidence.{1} must capture UDP packets".format(label, gate))


def verify_evidence_artifact(label, gate, data, verifier):
    artifact = data.get("artifact")
    expected_digest = data.get("artifactSha256")
    if not artifact:
        verifier.fail("{0} evidence.{1} must record artifact".format(label, gate))
        return
    if not expected_digest:
        verifier.fail("{0} evidence.{1} must record artifactSha256".format(label, gate))
        return
    artifact_path = Path(artifact)
    if artifact_path.is_absolute() or ".." in artifact_path.parts:
        verifier.fail("{0} evidence.{1} artifact must be a repository-relative path".format(label, gate))
        return
    full_artifact_path = ROOT / artifact_path
    if not full_artifact_path.exists():
        verifier.fail("{0} evidence.{1} artifact is missing: {2}".format(label, gate, artifact))
        return
    actual_digest = sha256(full_artifact_path)
    verifier.check(
        actual_digest == expected_digest,
        "{0} evidence.{1} artifactSha256 is stale for {2}".format(label, gate, artifact),
    )


def verify_protocol_fixture_evidence(label, gate, data, verifier):
    verifier.check(
        data.get("wireContract") == "zeus-udp-v1-packet-ids-0x01-through-0x27",
        "{0} evidence.{1} wireContract mismatch".format(label, gate),
    )
    tokens = set(data.get("requiredFixtureTokens") or [])
    verifier.check(
        REQUIRED_PROTOCOL_FIXTURE_TOKENS.issubset(tokens),
        "{0} evidence.{1} must list all required fixture tokens".format(label, gate),
    )
    required_ids = set(data.get("requiredPacketIds") or [])
    verifier.check(
        CORE_SCENARIO_REQUIRED_PACKET_IDS.issubset(required_ids),
        "{0} evidence.{1} must list core packet IDs".format(label, gate),
    )
    fixture_test = data.get("fixtureTest")
    if not fixture_test:
        verifier.fail("{0} evidence.{1} must record fixtureTest".format(label, gate))
    else:
        path = ROOT / fixture_test
        verifier.check(path == WIRE_GOLDEN_TEST, "{0} evidence.{1} fixtureTest path mismatch".format(label, gate))
        verifier.check(path.exists(), "{0} evidence.{1} fixtureTest is missing".format(label, gate))
    command = data.get("testCommand")
    verifier.check(
        command == ["mvn", "-q", "-pl", "ZeusProtocolJava", "-am", "test"],
        "{0} evidence.{1} must record the protocol fixture test command".format(label, gate),
    )
    protocol_artifact = data.get("protocolArtifact")
    protocol_digest = data.get("protocolArtifactSha256")
    if not protocol_artifact:
        verifier.fail("{0} evidence.{1} must record protocolArtifact".format(label, gate))
        return
    path = Path(protocol_artifact)
    if path.is_absolute() or ".." in path.parts:
        verifier.fail("{0} evidence.{1} protocolArtifact must be repository-relative".format(label, gate))
        return
    full_path = ROOT / path
    if not full_path.exists():
        verifier.fail("{0} evidence.{1} protocolArtifact is missing: {2}".format(label, gate, protocol_artifact))
        return
    verifier.check(
        protocol_digest == sha256(full_path),
        "{0} evidence.{1} protocolArtifactSha256 is stale".format(label, gate),
    )


def verify_manifest(data, verifier):
    verifier.check(data.get("schemaVersion") == 1, "support-matrix schemaVersion must be 1")
    verifier.check(data.get("publicationGate") == PUBLICATION_GATES, "publicationGate must match the release gate contract")
    verifier.check(data.get("wireContract") == "zeus-udp-v1-packet-ids-0x01-through-0x27", "wireContract changed unexpectedly")

    seen_gateway_ids = set()
    for artifact in data.get("gateway", {}).get("artifacts", []):
        artifact_id = artifact.get("id", "<missing>")
        label = "gateway artifact {0}".format(artifact_id)
        verifier.check(artifact_id not in seen_gateway_ids, "{0} is duplicated".format(label))
        seen_gateway_ids.add(artifact_id)
        status = artifact.get("status")
        verifier.check(status in ALLOWED_STATUSES, "{0} has invalid status {1}".format(label, status))
        if status == "supported":
            verifier.fail("{0} cannot be supported at artifact level; publish exact Gateway targets".format(label))

    seen_gateway_targets = set()
    for target in data.get("gateway", {}).get("targets", []):
        target_id = target.get("id", "<missing>")
        label = "gateway target {0}".format(target_id)
        verifier.check(target_id not in seen_gateway_targets, "{0} is duplicated".format(label))
        seen_gateway_targets.add(target_id)
        verifier.check(target.get("artifact") in seen_gateway_ids, "{0} references unknown artifact".format(label))
        verifier.check(target.get("platform"), "{0} is missing platform".format(label))
        verifier.check(target.get("minecraft"), "{0} is missing minecraft version".format(label))
        status = target.get("status")
        verifier.check(status in ALLOWED_STATUSES, "{0} has invalid status {1}".format(label, status))
        if status in {"build-verifiable", "supported"}:
            verifier.check(
                target.get("startupLogPattern"),
                "{0} must define startupLogPattern to prove exact runtime target".format(label),
            )
        verify_supported_evidence(label, target, PUBLICATION_GATES, verifier, "gateway", target_id)

    verifier.check(bool(seen_gateway_targets), "gateway.targets must contain exact runtime verification targets")

    target_versions = set()
    default_target = data.get("fabric", {}).get("defaultTarget")
    for target in data.get("fabric", {}).get("targets", []):
        minecraft = target.get("minecraft", "<missing>")
        label = "fabric target {0}".format(minecraft)
        verifier.check(minecraft not in target_versions, "{0} is duplicated".format(label))
        target_versions.add(minecraft)
        status = target.get("status")
        verifier.check(status in ALLOWED_STATUSES, "{0} has invalid status {1}".format(label, status))
        if status in {"build-verifiable", "supported"}:
            for field in ("minecraftDependency", "loader", "yarn", "fabricApi"):
                verifier.check(target.get(field), "{0} is {1} but missing {2}".format(label, status, field))
            verifier.check(
                target.get("minecraftDependency") == minecraft,
                "{0} must use an exact minecraftDependency for this adapter".format(label),
            )
        verify_supported_evidence(label, target, PUBLICATION_GATES, verifier, "fabric", minecraft)

    verifier.check(default_target in target_versions, "fabric.defaultTarget must exist in fabric.targets")
    default_entries = [target for target in data.get("fabric", {}).get("targets", []) if target.get("minecraft") == default_target]
    if default_entries:
        verifier.check(
            default_entries[0].get("status") in {"build-verifiable", "supported"},
            "fabric.defaultTarget must be build-verifiable or supported",
        )


def verify_protocol_contract(verifier):
    pom = parse_pom(PROTOCOL_POM, verifier)
    if pom is not None:
        verifier.check(pom_text(pom, "m:artifactId") == "ZeusProtocolJava", "ZeusProtocolJava artifactId changed")
        verifier.check(
            pom_text(pom, "m:properties/m:maven.compiler.source") == "8",
            "ZeusProtocolJava must keep maven.compiler.source=8",
        )
        verifier.check(
            pom_text(pom, "m:properties/m:maven.compiler.target") == "8",
            "ZeusProtocolJava must keep maven.compiler.target=8",
        )
        verifier.check(
            pom_text(pom, "m:properties/m:maven.compiler.release") == "8",
            "ZeusProtocolJava must keep maven.compiler.release=8",
        )
        verifier.check(
            pom.find(".//m:plugin[m:artifactId='maven-compiler-plugin']//m:release", MAVEN_NS) is not None
            and pom.find(".//m:plugin[m:artifactId='maven-compiler-plugin']//m:release", MAVEN_NS).text == "8",
            "ZeusProtocolJava maven-compiler-plugin must release Java 8 bytecode",
        )

    if not WIRE_GOLDEN_TEST.exists():
        verifier.fail("WireContractGoldenTest is missing")
        return
    test = read_text(WIRE_GOLDEN_TEST)
    for required in (
        "PacketPlayerAttackEntity",
        "PacketPlayerSurroundingBlocks",
        "PacketPlayerVelocity",
        "PacketPlayerInventoryTransaction",
        "PacketPlayerExternalForce",
        "0x09",
        "0x13",
        "0x22",
        "0x26",
        "0x27",
    ):
        verifier.check(required in test, "WireContractGoldenTest missing {0}".format(required))


def verify_gateway_build(verifier):
    pom = parse_pom(GATEWAY_POM, verifier)
    if pom is not None:
        verifier.check(pom_text(pom, "m:artifactId") == "zeus_gateway_modern", "ZeusGateway artifactId must be zeus_gateway_modern")
        verifier.check(pom_text(pom, "m:name") == "ZeusGateway Modern", "ZeusGateway name must identify the modern artifact")
        verifier.check(
            pom_text(pom, "m:build/m:finalName") == "ZeusGateway-modern-${project.version}",
            "ZeusGateway finalName must produce ZeusGateway-modern",
        )
        verifier.check(pom_text(pom, "m:properties/m:java.version") == "8", "ZeusGateway-modern must emit Java 8-compatible bytecode for the 1.14 boundary")

    legacy_pom = parse_pom(GATEWAY_LEGACY_POM, verifier)
    if legacy_pom is not None:
        verifier.check(
            pom_text(legacy_pom, "m:artifactId") == "zeus_gateway_legacy",
            "ZeusGatewayLegacy artifactId must be zeus_gateway_legacy",
        )
        verifier.check(
            pom_text(legacy_pom, "m:name") == "ZeusGateway Legacy",
            "ZeusGatewayLegacy name must identify the legacy artifact",
        )
        verifier.check(
            pom_text(legacy_pom, "m:build/m:finalName") == "ZeusGateway-legacy-${project.version}",
            "ZeusGatewayLegacy finalName must produce ZeusGateway-legacy",
        )
        verifier.check(
            pom_text(legacy_pom, "m:properties/m:java.version") == "8",
            "ZeusGatewayLegacy must build with Java 8 release",
        )
        verifier.check(
            legacy_pom.findtext(
                ".//m:dependency[m:groupId='org.spigotmc'][m:artifactId='spigot-api']/m:version",
                namespaces=MAVEN_NS,
            ) == "1.8.8-R0.1-SNAPSHOT",
            "ZeusGatewayLegacy must compile against the lowest supported Spigot API baseline 1.8.8",
        )
        repository_urls = {
            (node.text or "").strip()
            for node in legacy_pom.findall(".//m:repositories/m:repository/m:url", MAVEN_NS)
        }
        verifier.check(
            "https://hub.spigotmc.org/nexus/content/repositories/public/" in repository_urls,
            "ZeusGatewayLegacy must include Spigot public repo for legacy bungeecord-chat snapshots",
        )
        release_node = legacy_pom.find(".//m:plugin[m:artifactId='maven-compiler-plugin']//m:release", MAVEN_NS)
        verifier.check(
            release_node is not None and release_node.text in {"8", "${java.version}"},
            "ZeusGatewayLegacy maven-compiler-plugin must release Java 8 bytecode",
        )
    if not GATEWAY_LEGACY_PLUGIN_YML.exists():
        verifier.fail("ZeusGatewayLegacy plugin.yml is missing")
    else:
        legacy_plugin = read_text(GATEWAY_LEGACY_PLUGIN_YML)
        verifier.check(
            "api-version:" not in legacy_plugin,
            "ZeusGatewayLegacy plugin.yml must not declare api-version for 1.8 compatibility",
        )
        verifier.check(
            "main: org.vennv.zeusGatewayLegacy.ZeusGatewayLegacy" in legacy_plugin,
            "ZeusGatewayLegacy plugin.yml main class mismatch",
        )
    verify_gateway_legacy_source(verifier)


def verify_gateway_legacy_source(verifier):
    if not GATEWAY_LEGACY_SRC.exists():
        verifier.fail("ZeusGatewayLegacy source tree is missing")
        return
    java_files = list((GATEWAY_LEGACY_SRC / "java").rglob("*.java"))
    verifier.check(bool(java_files), "ZeusGatewayLegacy must contain Java sources")
    for path in list(GATEWAY_LEGACY_SRC.rglob("*.java")) + list(GATEWAY_LEGACY_SRC.rglob("*.yml")):
        text = read_text(path)
        relative = path.relative_to(ROOT)
        for pattern, reason in LEGACY_FORBIDDEN_SOURCE_PATTERNS.items():
            verifier.check(
                pattern not in text,
                "{0} contains legacy-forbidden pattern {1!r}: {2}".format(relative, pattern, reason),
            )
        if path.suffix == ".java":
            verifier.check(
                " -> " not in text and "->" not in text,
                "{0} contains lambda/switch arrow syntax; keep legacy source Java 8-baseline simple".format(relative),
            )


def verify_fabric_build(data, verifier):
    build = read_text(FABRIC_BUILD) if FABRIC_BUILD.exists() else ""
    template = read_text(FABRIC_MOD_TEMPLATE) if FABRIC_MOD_TEMPLATE.exists() else ""
    verifier.check("support-matrix.json" in build, "ZeusFabric build.gradle must read support-matrix.json")
    verifier.check(
        "target.status in ['build-verifiable', 'supported']" in build,
        "ZeusFabric build.gradle must build only build-verifiable or supported targets",
    )
    verifier.check('archivesName = "ZeusFabric-${minecraftTarget}"' in build, "ZeusFabric artifact name must include mcTarget")
    verifier.check('"minecraft": "=${minecraft_target}"' in template, "fabric.mod.json must use exact minecraft dependency template")
    verifier.check('">=${minecraft_target}"' not in template, "fabric.mod.json must not use open-ended minecraft dependency")

    buildable = [target for target in data.get("fabric", {}).get("targets", []) if target.get("status") in {"build-verifiable", "supported"}]
    verifier.check(len(buildable) >= 1, "at least one Fabric target should be build-verifiable or supported, or the Gradle module has no valid target")


def verify_thread_safety_patterns(verifier):
    async_patterns = ("CompletableFuture", "runAsync", "supplyAsync", "ExecutorService", "Executors.")
    roots = [
        ROOT / "ZeusGateway" / "src" / "main" / "java" / "org" / "vennv" / "zeusGateway" / "listener",
        ROOT / "ZeusFabric" / "src" / "main" / "java" / "org" / "vennv" / "zeusFabric",
    ]
    for root in roots:
        if not root.exists():
            verifier.fail("{0} is missing".format(root.relative_to(ROOT)))
            continue
        for path in root.rglob("*.java"):
            text = read_text(path)
            for pattern in async_patterns:
                verifier.check(
                    pattern not in text,
                    "{0} contains async capture pattern {1}".format(path.relative_to(ROOT), pattern),
                )

    gateway_listener = ROOT / "ZeusGateway" / "src" / "main" / "java" / "org" / "vennv" / "zeusGateway" / "listener"
    for path in gateway_listener.rglob("*.java"):
        text = read_text(path)
        verifier.check(
            "org.bukkit.util.BoundingBox" not in text,
            "{0} directly imports modern Bukkit BoundingBox".format(path.relative_to(ROOT)),
        )


def verify_public_docs(verifier):
    forbidden = [
        re.compile(r"single\s+Gateway\s+JAR", re.IGNORECASE),
        re.compile(r"one\s+Gateway\s+JAR", re.IGNORECASE),
        re.compile(r"1\.8\s*[-\u2013]\s*1\.21(?:\.x|x)?", re.IGNORECASE),
        re.compile(r"1\.8\s+(?:to|through)\s+1\.21", re.IGNORECASE),
        re.compile(r"Fabric[^\n]{0,80}1\.21\.x[^\n]{0,80}support", re.IGNORECASE),
        re.compile(r"minecraft\"\s*:\s*\">=1\.21", re.IGNORECASE),
    ]
    public_files = [ROOT / "README.md"] + list((ROOT / "docs").rglob("*.md"))
    for path in public_files:
        if not path.exists():
            continue
        text = read_text(path)
        for pattern in forbidden:
            match = pattern.search(text)
            verifier.check(
                match is None,
                "{0} contains forbidden broad support claim: {1}".format(path.relative_to(ROOT), match.group(0) if match else ""),
            )


def verify_build_scripts(verifier):
    sh = read_text(ROOT / "build.sh") if (ROOT / "build.sh").exists() else ""
    cmd = read_text(ROOT / "build.cmd") if (ROOT / "build.cmd").exists() else ""
    release_gate = read_text(ROOT / "scripts" / "verify_release_gate.sh") if (ROOT / "scripts" / "verify_release_gate.sh").exists() else ""
    verifier.check("scripts/verify_support_matrix.py" in sh, "build.sh must run verify_support_matrix.py")
    verifier.check(r"scripts\verify_support_matrix.py" in cmd, "build.cmd must run verify_support_matrix.py")
    verifier.check("scripts/render_support_readiness.py" in sh, "build.sh must check generated support readiness")
    verifier.check(r"scripts\render_support_readiness.py" in cmd, "build.cmd must check generated support readiness")
    verifier.check("scripts/list_fabric_build_targets.py" in sh, "build.sh must build Fabric targets from support-matrix.json")
    verifier.check(r"scripts\list_fabric_build_targets.py" in cmd, "build.cmd must build Fabric targets from support-matrix.json")
    verifier.check("list_fabric_build_targets.py" in release_gate, "verify_release_gate.sh must build Fabric targets from support-matrix.json")
    verifier.check("scripts/write_release_evidence.py" in release_gate, "verify_release_gate.sh must write release evidence")
    verifier.check("scripts/run_smoke_matrix.py" in release_gate, "verify_release_gate.sh must expose matrix smoke hooks")
    verifier.check("scripts/run_startup_smoke.py" in release_gate, "verify_release_gate.sh must expose startup smoke hooks")
    verifier.check("scripts/run_core_scenario_smoke.py" in release_gate, "verify_release_gate.sh must expose core scenario smoke hooks")


def read_protocol_reference(require_artifacts, verifier):
    if not PROTOCOL_ARTIFACT.exists():
        if require_artifacts:
            verifier.fail("required artifact missing: {0}".format(PROTOCOL_ARTIFACT.relative_to(ROOT)))
        return None, {}, None
    try:
        artifact_bytes = PROTOCOL_ARTIFACT.read_bytes()
        with zipfile.ZipFile(io.BytesIO(artifact_bytes)) as zf:
            classes = {
                entry: zf.read(entry)
                for entry in sorted(zf.namelist())
                if entry.startswith("org/vennv/") and entry.endswith(".class")
            }
            contract = zf.read(PROTOCOL_CONTRACT_RESOURCE) if zip_contains(zf, PROTOCOL_CONTRACT_RESOURCE) else None
    except zipfile.BadZipFile as exc:
        verifier.fail("{0} is not a readable jar: {1}".format(PROTOCOL_ARTIFACT.relative_to(ROOT), exc))
        return None, {}, None
    verifier.check(bool(classes), "ZeusProtocolJava artifact contains no protocol classes")
    verifier.check(contract is not None, "ZeusProtocolJava artifact missing {0}".format(PROTOCOL_CONTRACT_RESOURCE))
    if contract is not None:
        verify_protocol_contract_resource("ZeusProtocolJava artifact", contract, verifier)
    return sha256_bytes(artifact_bytes), classes, contract


def verify_protocol_contract_resource(label, data, verifier):
    try:
        contract = json.loads(data.decode("utf-8"))
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        verifier.fail("{0} has invalid {1}: {2}".format(label, PROTOCOL_CONTRACT_RESOURCE, exc))
        return
    verifier.check(contract.get("schemaVersion") == 1, "{0} protocol contract schemaVersion must be 1".format(label))
    verifier.check(
        contract.get("wireContract") == "zeus-udp-v1-packet-ids-0x01-through-0x27",
        "{0} protocol contract wireContract mismatch".format(label),
    )
    ids = contract.get("requiredPacketIds") or {}
    for packet, packet_id in {
        "PacketPlayerAttackEntity": "0x09",
        "PacketPlayerSurroundingBlocks": "0x13",
        "PacketPlayerVelocity": "0x22",
        "PacketPlayerInventoryTransaction": "0x26",
        "PacketPlayerExternalForce": "0x27",
    }.items():
        verifier.check(
            ids.get(packet) == packet_id,
            "{0} protocol contract missing {1}={2}".format(label, packet, packet_id),
        )


def zip_contains(zf, path):
    return path in set(zf.namelist())


def verify_gateway_artifact(require_artifacts, verifier, protocol_classes, protocol_contract):
    artifacts = [
        (
            ROOT / "ZeusGateway" / "target" / "ZeusGateway-modern-1.0-SNAPSHOT.jar",
            ("plugin.yml", "paper-plugin.yml"),
        ),
        (
            ROOT / "ZeusGatewayLegacy" / "target" / "ZeusGateway-legacy-1.0-SNAPSHOT.jar",
            ("plugin.yml",),
        ),
    ]
    for jar, descriptors in artifacts:
        verify_gateway_artifact_file(jar, descriptors, require_artifacts, verifier, protocol_classes, protocol_contract)


def verify_gateway_artifact_file(jar, descriptors, require_artifacts, verifier, protocol_classes, protocol_contract):
    if not jar.exists():
        if require_artifacts:
            verifier.fail("required artifact missing: {0}".format(jar.relative_to(ROOT)))
        return
    try:
        with zipfile.ZipFile(jar) as zf:
            for entry in descriptors + (
                "org/vennv/PacketId.class",
                "org/vennv/packets/PacketPlayerInventoryTransaction.class",
                "org/vennv/packets/PacketPlayerExternalForce.class",
            ):
                verifier.check(zip_contains(zf, entry), "{0} missing {1}".format(jar.relative_to(ROOT), entry))
            verify_embedded_protocol_surface(jar.relative_to(ROOT), zf, protocol_classes, protocol_contract, verifier)
            if "ZeusGateway-legacy" in jar.name:
                verify_legacy_gateway_artifact(jar, zf, verifier)
            if "ZeusGateway-modern" in jar.name:
                verify_modern_optional_dependency_boundary(jar, zf, verifier)
    except zipfile.BadZipFile as exc:
        verifier.fail("{0} is not a readable jar: {1}".format(jar.relative_to(ROOT), exc))


def verify_modern_optional_dependency_boundary(jar, zf, verifier):
    for entry in zf.namelist():
        if entry.endswith(".class"):
            major = class_major_version(zf.read(entry))
            verifier.check(
                major <= 52,
                "{0}!/{1} has classfile major {2}, expected Java 8-compatible modern artifact".format(
                    jar.relative_to(ROOT), entry, major
                ),
            )
    shared_entries = (
        "org/vennv/zeusGateway/ZeusGateway.class",
        "org/vennv/zeusGateway/init/ZeusLoader.class",
        "org/vennv/zeusGateway/listener/event/EventListener.class",
    )
    for entry in shared_entries:
        if not zip_contains(zf, entry):
            verifier.fail("{0} missing shared bootstrap class {1}".format(jar.relative_to(ROOT), entry))
            continue
        data = zf.read(entry)
        verifier.check(
            b"com/comphenix/protocol/" not in data,
            "{0}!/{1} directly resolves ProtocolLib despite optional runtime dependency".format(
                jar.relative_to(ROOT), entry
            ),
        )
        verifier.check(
            b"org/vennv/zeusGateway/listener/packets/" not in data,
            "{0}!/{1} directly resolves ProtocolLib packet adapters despite fallback mode".format(
                jar.relative_to(ROOT), entry
            ),
        )


def verify_legacy_gateway_artifact(jar, zf, verifier):
    names = set(zf.namelist())
    verifier.check("paper-plugin.yml" not in names, "{0} must not contain paper-plugin.yml".format(jar.relative_to(ROOT)))
    if "plugin.yml" in names:
        plugin_yml = zf.read("plugin.yml").decode("utf-8")
        verifier.check("api-version:" not in plugin_yml, "{0} plugin.yml must not declare api-version".format(jar.relative_to(ROOT)))
        verifier.check(
            "main: org.vennv.zeusGatewayLegacy.ZeusGatewayLegacy" in plugin_yml,
            "{0} plugin.yml main class mismatch".format(jar.relative_to(ROOT)),
        )
    forbidden_entries = (
        "io/papermc/",
        "com/destroystokyo/",
        "com/comphenix/",
        "org/bukkit/util/BoundingBox",
    )
    for entry in names:
        for forbidden in forbidden_entries:
            verifier.check(
                forbidden not in entry,
                "{0} contains legacy-forbidden class/resource {1}".format(jar.relative_to(ROOT), entry),
            )
        if entry.endswith(".class"):
            major = class_major_version(zf.read(entry))
            verifier.check(
                major <= 52,
                "{0}!/{1} has classfile major {2}, expected Java 8 or lower".format(jar.relative_to(ROOT), entry, major),
            )


def class_major_version(data):
    if len(data) < 8 or data[:4] != b"\xca\xfe\xba\xbe":
        return 999
    return int.from_bytes(data[6:8], byteorder="big")


def verify_embedded_protocol_surface(label, zf, protocol_classes, protocol_contract, verifier):
    if not protocol_classes:
        return
    for entry in protocol_classes:
        if not zip_contains(zf, entry):
            verifier.fail("{0} missing protocol class {1}".format(label, entry))
    if protocol_contract is None:
        return
    if not zip_contains(zf, PROTOCOL_CONTRACT_RESOURCE):
        verifier.fail("{0} missing {1}".format(label, PROTOCOL_CONTRACT_RESOURCE))
        return
    actual_contract = zf.read(PROTOCOL_CONTRACT_RESOURCE)
    verifier.check(
        actual_contract == protocol_contract,
        "{0} {1} does not match ZeusProtocolJava artifact".format(label, PROTOCOL_CONTRACT_RESOURCE),
    )
    verify_protocol_contract_resource(str(label), actual_contract, verifier)


def verify_fabric_artifacts(data, require_artifacts, verifier, protocol_digest, protocol_classes, protocol_contract):
    for target in data.get("fabric", {}).get("targets", []):
        if target.get("status") != "build-verifiable":
            continue
        minecraft = target["minecraft"]
        jar = ROOT / "ZeusFabric" / "build" / "libs" / "ZeusFabric-{0}-1.0-SNAPSHOT.jar".format(minecraft)
        if not jar.exists():
            if require_artifacts:
                verifier.fail("required artifact missing: {0}".format(jar.relative_to(ROOT)))
            continue
        try:
            with zipfile.ZipFile(jar) as zf:
                verifier.check(zip_contains(zf, "fabric.mod.json"), "{0} missing fabric.mod.json".format(jar.relative_to(ROOT)))
                if zip_contains(zf, "fabric.mod.json"):
                    mod = json.loads(zf.read("fabric.mod.json").decode("utf-8"))
                    verifier.check(
                        mod.get("depends", {}).get("minecraft") == "={0}".format(minecraft),
                        "{0} fabric.mod.json must depend on Minecraft ={1}".format(jar.relative_to(ROOT), minecraft),
                    )
                    nested_files = [entry.get("file") for entry in mod.get("jars", []) if isinstance(entry, dict)]
                    verifier.check(
                        "META-INF/jars/ZeusProtocolJava-1.0-SNAPSHOT.jar" in nested_files,
                        "{0} fabric.mod.json must declare nested ZeusProtocolJava jar".format(jar.relative_to(ROOT)),
                    )
                nested = "META-INF/jars/ZeusProtocolJava-1.0-SNAPSHOT.jar"
                verifier.check(zip_contains(zf, nested), "{0} missing nested ZeusProtocolJava jar".format(jar.relative_to(ROOT)))
                if zip_contains(zf, nested):
                    nested_bytes = zf.read(nested)
                    with zipfile.ZipFile(io.BytesIO(nested_bytes)) as protocol_zf:
                        for entry in (
                            "org/vennv/PacketId.class",
                            "org/vennv/packets/PacketPlayerInventoryTransaction.class",
                            "org/vennv/packets/PacketPlayerExternalForce.class",
                        ):
                            verifier.check(
                                zip_contains(protocol_zf, entry),
                                "{0}!/{1} missing {2}".format(jar.relative_to(ROOT), nested, entry),
                            )
                        verify_embedded_protocol_surface(
                            "{0}!/{1}".format(jar.relative_to(ROOT), nested),
                            protocol_zf,
                            protocol_classes,
                            protocol_contract,
                            verifier,
                        )
        except (zipfile.BadZipFile, json.JSONDecodeError) as exc:
            verifier.fail("{0} is not a readable Fabric artifact: {1}".format(jar.relative_to(ROOT), exc))


def print_results(verifier):
    for warning in verifier.warnings:
        print("warning: {0}".format(warning))
    if verifier.errors:
        for error in verifier.errors:
            print("error: {0}".format(error))
        return 1
    print("support-matrix verification passed")
    return 0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--require-artifacts",
        action="store_true",
        help="fail if build-verifiable artifacts are not present under target/build/libs",
    )
    args = parser.parse_args()

    verifier = Verifier()
    data = load_manifest(verifier)
    protocol_digest, protocol_classes, protocol_contract = read_protocol_reference(args.require_artifacts, verifier)
    if data:
        verify_manifest(data, verifier)
        verify_generated_docs(data, verifier)
        verify_fabric_build(data, verifier)
        verify_fabric_artifacts(data, args.require_artifacts, verifier, protocol_digest, protocol_classes, protocol_contract)
    verify_protocol_contract(verifier)
    verify_gateway_build(verifier)
    verify_gateway_artifact(args.require_artifacts, verifier, protocol_classes, protocol_contract)
    verify_thread_safety_patterns(verifier)
    verify_public_docs(verifier)
    verify_build_scripts(verifier)
    return print_results(verifier)


if __name__ == "__main__":
    raise SystemExit(main())
