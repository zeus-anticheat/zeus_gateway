#!/usr/bin/env python3
"""Run a real startup smoke test against a pre-provisioned server directory."""

import argparse
import hashlib
import json
import queue
import re
import shlex
import shutil
import subprocess
import sys
import threading
import time
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "support-matrix.json"

SUCCESS_PATTERNS = {
    "gateway": "[ZeusGateway] Plugin enabled successfully",
    "gateway-legacy": "[ZeusGatewayLegacy] Plugin enabled",
    "fabric": "[ZeusFabric] Server started. Zeus Anti-Cheat Fabric mod is now active.",
}

FAILURE_PATTERNS = [
    r"Error occurred while enabling ZeusGateway",
    r"Error occurred while enabling ZeusGatewayLegacy",
    r"Could not load 'plugins/.*ZeusGateway.*\.jar'",
    r"net\.fabricmc\.loader\.impl\.FormattedException",
    r"Mixin apply failed",
    r"Failed to start the minecraft server",
    r"java\.lang\.NoClassDefFoundError",
    r"java\.lang\.NoSuchMethodError",
    r"java\.lang\.UnsupportedClassVersionError",
    r"Exception in thread \"main\"",
]


def load_manifest():
    return json.loads(MANIFEST.read_text(encoding="utf-8"))


def default_fabric_target():
    return load_manifest()["fabric"]["defaultTarget"]


def gateway_target(target):
    for entry in load_manifest().get("gateway", {}).get("targets", []):
        if entry.get("id") == target:
            return entry
    raise SystemExit("unknown Gateway runtime target in support-matrix.json: {0}".format(target))


def default_gateway_target():
    for entry in load_manifest().get("gateway", {}).get("targets", []):
        if entry.get("id") == "paper-1.21.11":
            return entry["id"]
    raise SystemExit("support-matrix.json has no paper-1.21.11 Gateway runtime target")


def default_artifact(kind, target):
    if kind == "gateway":
        gateway_target(target)
        return ROOT / "ZeusGateway" / "target" / "ZeusGateway-1.0-SNAPSHOT.jar"
    return ROOT / "ZeusFabric" / "build" / "libs" / "ZeusFabric-{0}-1.0-SNAPSHOT.jar".format(target)


def default_success_pattern(kind, target):
    return SUCCESS_PATTERNS[kind]


def server_target_pattern(kind, target):
    if kind != "gateway":
        return None
    return gateway_target(target).get("startupLogPattern")


def external_dependency_pattern(kind):
    if kind != "gateway":
        return None
    return load_manifest().get("gateway", {}).get("packetEvents", {}).get("startupLogPattern")


def safe_name(value):
    return re.sub(r"[^A-Za-z0-9_.-]+", "_", value)


def default_evidence(kind, target):
    return ROOT / "verification" / "evidence" / "startup-smoke" / "{0}-{1}.json".format(kind, safe_name(target))


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def display_path(path):
    try:
        return str(path.relative_to(ROOT))
    except ValueError:
        return str(path)


def command_from_args(args):
    if args.command_line and args.command:
        raise SystemExit("use either --command-line or --command/--, not both")
    if args.command_line:
        return shlex.split(args.command_line)
    if args.command:
        command = list(args.command)
        if command and command[0] == "--":
            command = command[1:]
        return command
    raise SystemExit("missing server command; pass --command-line 'java -jar server.jar nogui' or -- java ...")


def copy_artifact(kind, artifact, server_dir):
    deploy_dir = server_dir / ("plugins" if kind == "gateway" else "mods")
    deploy_dir.mkdir(parents=True, exist_ok=True)
    destination = deploy_dir / artifact.name
    shutil.copy2(artifact, destination)
    return destination


def reader_thread(process, output):
    assert process.stdout is not None
    for line in process.stdout:
        output.put(line.rstrip("\n"))


def stop_process(process, timeout):
    if process.poll() is not None:
        return "already-exited"
    try:
        if process.stdin:
            process.stdin.write("stop\n")
            process.stdin.flush()
    except BrokenPipeError:
        pass
    try:
        process.wait(timeout=timeout)
        return "stopped"
    except subprocess.TimeoutExpired:
        process.terminate()
        try:
            process.wait(timeout=10)
            return "terminated"
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=10)
            return "killed"


def run_smoke(args):
    target = args.target or (default_gateway_target() if args.kind == "gateway" else default_fabric_target())
    artifact = Path(args.artifact) if args.artifact else default_artifact(args.kind, target)
    artifact = artifact if artifact.is_absolute() else ROOT / artifact
    server_dir = Path(args.server_dir)
    evidence = Path(args.evidence) if args.evidence else default_evidence(args.kind, target)
    evidence = evidence if evidence.is_absolute() else ROOT / evidence
    command = command_from_args(args)
    success_pattern = args.success_pattern or default_success_pattern(args.kind, target)
    target_pattern = server_target_pattern(args.kind, target)
    dependency_pattern = external_dependency_pattern(args.kind)
    failure_regexes = [re.compile(pattern) for pattern in FAILURE_PATTERNS + list(args.failure_pattern)]

    if not artifact.exists():
        raise SystemExit("artifact does not exist: {0}".format(artifact))
    if not server_dir.exists():
        raise SystemExit("server dir does not exist: {0}".format(server_dir))
    if not server_dir.is_dir():
        raise SystemExit("server dir is not a directory: {0}".format(server_dir))

    deploy_dir = server_dir / ("plugins" if args.kind == "gateway" else "mods")
    deployed_artifact = deploy_dir / artifact.name
    artifact_digest = sha256(artifact)

    plan = {
        "schemaVersion": 1,
        "gate": "server-startup-smoke",
        "kind": args.kind,
        "target": target,
        "tool": "scripts/run_startup_smoke.py",
        "dryRun": args.dry_run,
        "artifact": display_path(artifact),
        "artifactSha256": artifact_digest,
        "deployedArtifact": str(deployed_artifact),
        "serverDir": str(server_dir),
        "command": command,
        "successPattern": success_pattern,
        "serverTargetPattern": target_pattern,
        "externalDependencyPattern": dependency_pattern,
        "failurePatterns": FAILURE_PATTERNS + list(args.failure_pattern),
    }
    if args.dry_run:
        print(json.dumps(plan, indent=2, sort_keys=True))
        return 0

    if args.accept_eula:
        (server_dir / "eula.txt").write_text("eula=true\n", encoding="utf-8")

    deployed_artifact = copy_artifact(args.kind, artifact, server_dir)

    start = time.monotonic()
    started_at = datetime.now(timezone.utc).isoformat()
    output = queue.Queue()
    lines = []
    success_seen = False
    server_target_seen = target_pattern is None
    external_dependency_seen = dependency_pattern is None
    failure_seen = None
    stop_action = None

    process = subprocess.Popen(
        command,
        cwd=str(server_dir),
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
    )
    thread = threading.Thread(target=reader_thread, args=(process, output), daemon=True)
    thread.start()

    try:
        while time.monotonic() - start < args.timeout:
            try:
                line = output.get(timeout=0.25)
            except queue.Empty:
                if process.poll() is not None:
                    break
                continue
            lines.append(line)
            if args.echo:
                print(line)
            if target_pattern and target_pattern in line:
                server_target_seen = True
            if dependency_pattern and dependency_pattern in line:
                external_dependency_seen = True
            if success_pattern in line:
                success_seen = True
            if success_seen and server_target_seen and external_dependency_seen:
                time.sleep(args.idle_after_success)
                stop_action = stop_process(process, args.stop_timeout)
                break
            for failure_regex in failure_regexes:
                if failure_regex.search(line):
                    failure_seen = line
                    stop_action = stop_process(process, args.stop_timeout)
                    break
            if failure_seen:
                break
        else:
            stop_action = stop_process(process, args.stop_timeout)

        while not output.empty():
            lines.append(output.get())
    finally:
        if process.poll() is None:
            stop_action = stop_process(process, args.stop_timeout)

    exit_code = process.poll()
    duration = round(time.monotonic() - start, 3)
    passed = (success_seen and server_target_seen and external_dependency_seen
              and failure_seen is None and exit_code == 0)
    result = {
        **plan,
        "startedAt": started_at,
        "durationSeconds": duration,
        "successSeen": success_seen,
        "serverTargetSeen": server_target_seen,
        "externalDependencySeen": external_dependency_seen,
        "failureSeen": failure_seen,
        "stopAction": stop_action,
        "exitCode": exit_code,
        "result": "passed" if passed else "failed",
        "logTail": lines[-200:],
    }
    evidence.parent.mkdir(parents=True, exist_ok=True)
    evidence.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print("startup smoke {0}: {1}".format(result["result"], evidence))
    return 0 if passed else 1


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("kind", choices=["gateway", "fabric"])
    parser.add_argument("--target", help="exact runtime target id from support-matrix.json, or fabric.defaultTarget")
    parser.add_argument("--artifact", help="artifact jar to deploy")
    parser.add_argument("--server-dir", required=True, help="pre-provisioned server directory")
    parser.add_argument("--evidence", help="output evidence JSON")
    parser.add_argument("--success-pattern", help="log substring required for startup success")
    parser.add_argument("--failure-pattern", action="append", default=[], help="additional regex that fails the smoke test")
    parser.add_argument("--timeout", type=float, default=90.0)
    parser.add_argument("--stop-timeout", type=float, default=30.0)
    parser.add_argument("--idle-after-success", type=float, default=3.0)
    parser.add_argument("--accept-eula", action="store_true", help="write eula=true into the server dir before launch")
    parser.add_argument("--dry-run", action="store_true", help="validate inputs and print the smoke plan without starting the server")
    parser.add_argument("--echo", action="store_true", help="stream server output to stdout")
    parser.add_argument("--command-line", help="server command as a single shell-style string, parsed without a shell")
    parser.epilog = "Alternatively, pass the server command after a standalone --."

    argv = sys.argv[1:]
    command = []
    if "--" in argv:
        separator = argv.index("--")
        command = argv[separator + 1:]
        argv = argv[:separator]
    args = parser.parse_args(argv)
    args.command = command
    return run_smoke(args)


if __name__ == "__main__":
    raise SystemExit(main())
