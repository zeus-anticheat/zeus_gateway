#!/usr/bin/env python3
"""Run startup and core scenario smoke tests from a target matrix config."""

import argparse
import json
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "support-matrix.json"
BUILDABLE_STATUSES = {"build-verifiable", "supported"}


def load_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def buildable_targets():
    data = load_json(MANIFEST)
    targets = []
    for target in data.get("gateway", {}).get("targets", []):
        if target.get("status") in BUILDABLE_STATUSES:
            targets.append(("gateway", target["id"]))
    for target in data.get("fabric", {}).get("targets", []):
        if target.get("status") in BUILDABLE_STATUSES:
            targets.append(("fabric", target["minecraft"]))
    return targets


def as_list(value):
    if value is None:
        return []
    if isinstance(value, list):
        return value
    return [value]


def bool_value(entry, key, default=False):
    value = entry.get(key, default)
    if isinstance(value, bool):
        return value
    raise SystemExit("{0} must be a boolean".format(key))


def require_string(entry, key, label):
    value = entry.get(key)
    if not isinstance(value, str) or not value.strip():
        raise SystemExit("{0} must define non-empty {1}".format(label, key))
    return value


def optional_string(entry, key):
    value = entry.get(key)
    if value is None:
        return None
    if not isinstance(value, str) or not value.strip():
        raise SystemExit("{0} must be a non-empty string when present".format(key))
    return value


def common_args(entry, args, label):
    command = [
        "--target",
        require_string(entry, "target", label),
        "--server-dir",
        require_string(entry, "serverDir", label),
        "--command-line",
        require_string(entry, "commandLine", label),
    ]
    for key, flag in (
        ("artifact", "--artifact"),
        ("successPattern", "--success-pattern"),
    ):
        value = optional_string(entry, key)
        if value:
            command.extend([flag, value])
    for pattern in as_list(entry.get("failurePattern")):
        command.extend(["--failure-pattern", str(pattern)])
    if bool_value(entry, "acceptEula", False) or args.accept_eula:
        command.append("--accept-eula")
    if args.echo or bool_value(entry, "echo", False):
        command.append("--echo")
    return command


def startup_args(entry, args, label):
    gate = entry.get("startup")
    gate = {} if gate is True or gate is None else gate
    if gate is False:
        return None
    if not isinstance(gate, dict):
        raise SystemExit("{0}.startup must be an object or boolean".format(label))

    command = [
        sys.executable,
        str(ROOT / "scripts" / "run_startup_smoke.py"),
        require_string(entry, "kind", label),
    ]
    command.extend(common_args(entry, args, label))
    for key, flag in (
        ("evidence", "--evidence"),
        ("timeout", "--timeout"),
        ("stopTimeout", "--stop-timeout"),
        ("idleAfterSuccess", "--idle-after-success"),
    ):
        value = gate.get(key)
        if value is not None:
            command.extend([flag, str(value)])
    if args.dry_run:
        command.append("--dry-run")
    return command


def core_args(entry, args, label):
    gate = entry.get("coreScenario")
    gate = {} if gate is True or gate is None else gate
    if gate is False:
        return None
    if not isinstance(gate, dict):
        raise SystemExit("{0}.coreScenario must be an object or boolean".format(label))

    command = [
        sys.executable,
        str(ROOT / "scripts" / "run_core_scenario_smoke.py"),
        require_string(entry, "kind", label),
    ]
    command.extend(common_args(entry, args, label))

    profiles = as_list(gate.get("profile") or ["compatibility-core"])
    for profile in profiles:
        command.extend(["--profile", str(profile)])
    for packet_id in as_list(gate.get("expectPacketId")):
        command.extend(["--expect-packet-id", str(packet_id)])
    for stdin_command in as_list(gate.get("stdinCommand")):
        command.extend(["--stdin-command", str(stdin_command)])

    for key, flag in (
        ("evidence", "--evidence"),
        ("proxyHost", "--proxy-host"),
        ("proxyPort", "--proxy-port"),
        ("batchSize", "--batch-size"),
        ("startupTimeout", "--startup-timeout"),
        ("captureSeconds", "--capture-seconds"),
        ("stopTimeout", "--stop-timeout"),
        ("scenarioCommandLine", "--scenario-command-line"),
        ("scenarioCwd", "--scenario-cwd"),
        ("scenarioTimeout", "--scenario-timeout"),
        ("stdinCommandDelay", "--stdin-command-delay"),
    ):
        value = gate.get(key)
        if value is not None:
            command.extend([flag, str(value)])
    if args.dry_run:
        command.append("--dry-run")
    return command


def validate_entries(entries, require_all):
    buildable = set(buildable_targets())
    seen = set()
    for index, entry in enumerate(entries):
        label = "targets[{0}]".format(index)
        kind = require_string(entry, "kind", label)
        target = require_string(entry, "target", label)
        key = (kind, target)
        if key in seen:
            raise SystemExit("duplicate smoke matrix entry: {0}/{1}".format(kind, target))
        seen.add(key)
        if key not in buildable:
            raise SystemExit("smoke matrix entry is not buildable in support-matrix.json: {0}/{1}".format(kind, target))

    if require_all:
        missing = sorted(buildable - seen)
        if missing:
            formatted = ", ".join("{0}/{1}".format(kind, target) for kind, target in missing)
            raise SystemExit("smoke matrix is missing build-verifiable targets: {0}".format(formatted))


def run_command(command, dry_run):
    if dry_run:
        print(" ".join(command))
        return 0
    return subprocess.run(command, cwd=str(ROOT)).returncode


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", required=True, help="JSON smoke matrix config")
    parser.add_argument("--require-all-buildable", action="store_true", help="fail unless every buildable target is configured")
    parser.add_argument("--accept-eula", action="store_true", help="pass --accept-eula to every smoke run")
    parser.add_argument("--echo", action="store_true", help="stream server output for every smoke run")
    parser.add_argument("--dry-run", action="store_true", help="print smoke commands instead of running them")
    args = parser.parse_args()

    config = load_json(args.config)
    if config.get("schemaVersion") != 1:
        raise SystemExit("smoke matrix config schemaVersion must be 1")
    entries = config.get("targets")
    if not isinstance(entries, list) or not entries:
        raise SystemExit("smoke matrix config must contain non-empty targets list")
    validate_entries(entries, args.require_all_buildable)

    commands = []
    for index, entry in enumerate(entries):
        label = "targets[{0}]".format(index)
        startup = startup_args(entry, args, label)
        core = core_args(entry, args, label)
        if startup is None and core is None:
            raise SystemExit("{0} disables both startup and coreScenario".format(label))
        for command in (startup, core):
            if command is not None:
                commands.append(command)

    for command in commands:
        result = run_command(command, args.dry_run)
        if result != 0:
            return result
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
