#!/usr/bin/env python3
"""Run a UDP-backed core scenario smoke test against a prepared server."""

import argparse
import hashlib
import json
import queue
import re
import shlex
import shutil
import socket
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

PROFILES = {
    "compatibility-core": [0x09, 0x13, 0x22, 0x26, 0x27],
    "online-minimal": [0x01, 0x03],
}


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
        if entry.get("artifact") == "ZeusGateway-modern":
            return entry["id"]
    raise SystemExit("support-matrix.json has no modern Gateway runtime target")


def default_artifact(kind, target):
    if kind == "gateway":
        artifact = gateway_target(target)["artifact"]
        if artifact == "ZeusGateway-legacy":
            return ROOT / "ZeusGatewayLegacy" / "target" / "ZeusGateway-legacy-1.0-SNAPSHOT.jar"
        if artifact == "ZeusGateway-modern":
            return ROOT / "ZeusGateway" / "target" / "ZeusGateway-modern-1.0-SNAPSHOT.jar"
        raise SystemExit("unknown Gateway artifact in support-matrix.json: {0}".format(artifact))
    return ROOT / "ZeusFabric" / "build" / "libs" / "ZeusFabric-{0}-1.0-SNAPSHOT.jar".format(target)


def default_success_pattern(kind, target):
    if kind == "gateway" and gateway_target(target)["artifact"] == "ZeusGateway-legacy":
        return SUCCESS_PATTERNS["gateway-legacy"]
    return SUCCESS_PATTERNS[kind]


def server_target_pattern(kind, target):
    if kind != "gateway":
        return None
    return gateway_target(target).get("startupLogPattern")


def safe_name(value):
    return re.sub(r"[^A-Za-z0-9_.-]+", "_", value)


def default_evidence(kind, target):
    return ROOT / "verification" / "evidence" / "core-scenario-smoke" / "{0}-{1}.json".format(kind, safe_name(target))


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


def parse_command(args):
    if args.command_line and args.command:
        raise SystemExit("use either --command-line or --command/--, not both")
    if args.command_line:
        return shlex.split(args.command_line)
    if args.command:
        return list(args.command)
    raise SystemExit("missing server command; pass --command-line 'java -jar server.jar nogui' or -- java ...")


def parse_packet_ids(values, profiles):
    result = []
    for profile in profiles:
        if profile not in PROFILES:
            raise SystemExit("unknown packet profile: {0}".format(profile))
        result.extend(PROFILES[profile])
    for value in values:
        for part in value.split(","):
            part = part.strip()
            if not part:
                continue
            packet_id = int(part, 0)
            if packet_id < 0 or packet_id > 255:
                raise SystemExit("packet id out of range: {0}".format(part))
            result.append(packet_id)
    if not result:
        raise SystemExit("at least one --profile or --expect-packet-id is required")
    return sorted(set(result))


def copy_artifact(kind, artifact, server_dir):
    deploy_dir = server_dir / ("plugins" if kind == "gateway" else "mods")
    deploy_dir.mkdir(parents=True, exist_ok=True)
    destination = deploy_dir / artifact.name
    shutil.copy2(artifact, destination)
    return destination


def write_proxy_config(kind, server_dir, host, port, batch_size):
    if kind == "gateway":
        config_dir = server_dir / "plugins" / "ZeusGateway"
        config_dir.mkdir(parents=True, exist_ok=True)
        config_path = config_dir / "config.yml"
        config_path.write_text(
            "\n".join(
                [
                    "proxy-ac:",
                    "    host: {0}".format(host),
                    "    port: {0}".format(port),
                    "",
                    "packets:",
                    "    batch-size: {0}".format(batch_size),
                    "",
                    "server-combat:",
                    "    reach-override: 0",
                    "    cooldown-override: -1",
                    "    max-cps: 0",
                    "",
                ]
            ),
            encoding="utf-8",
        )
        return config_path

    config_dir = server_dir / "config"
    config_dir.mkdir(parents=True, exist_ok=True)
    config_path = config_dir / "zeusfabric.properties"
    config_path.write_text(
        "\n".join(
            [
                "proxy-host={0}".format(host),
                "proxy-port={0}".format(port),
                "batch-size={0}".format(batch_size),
                "",
            ]
        ),
        encoding="utf-8",
    )
    return config_path


def reader_thread(process, output):
    assert process.stdout is not None
    for line in process.stdout:
        output.put(line.rstrip("\n"))


def udp_thread(sock, stop_event, packets):
    sock.settimeout(0.25)
    while not stop_event.is_set():
        try:
            payload, address = sock.recvfrom(65535)
        except socket.timeout:
            continue
        if not payload:
            continue
        packets.append(
            {
                "packetId": payload[0],
                "packetIdHex": "0x{0:02x}".format(payload[0]),
                "length": len(payload),
                "from": "{0}:{1}".format(address[0], address[1]),
                "sampleHex": payload[:32].hex(),
            }
        )


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


def drain_output(output, lines, echo):
    while True:
        try:
            line = output.get_nowait()
        except queue.Empty:
            return
        lines.append(line)
        if echo:
            print(line)


def wait_for_startup(process, output, lines, timeout, success_pattern, target_pattern, failure_regexes, echo):
    deadline = time.monotonic() + timeout
    success_seen = False
    target_seen = target_pattern is None
    while time.monotonic() < deadline:
        try:
            line = output.get(timeout=0.25)
        except queue.Empty:
            if process.poll() is not None:
                return False, target_seen, "server exited before startup success"
            continue
        lines.append(line)
        if echo:
            print(line)
        if target_pattern and target_pattern in line:
            target_seen = True
        if success_pattern in line:
            success_seen = True
            if target_seen:
                return True, True, None
        for failure_regex in failure_regexes:
            if failure_regex.search(line):
                return False, target_seen, line
    if success_seen and not target_seen:
        return False, False, "plugin enabled but exact server target log fingerprint was not observed"
    return False, target_seen, "startup timeout after {0}s".format(timeout)


def run_scenario_command(command_line, cwd, timeout):
    if not command_line:
        return None
    command = shlex.split(command_line)
    started = time.monotonic()
    try:
        process = subprocess.run(
            command,
            cwd=str(cwd) if cwd else None,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=timeout,
        )
        return {
            "command": command,
            "cwd": str(cwd) if cwd else None,
            "exitCode": process.returncode,
            "durationSeconds": round(time.monotonic() - started, 3),
            "outputTail": process.stdout.splitlines()[-100:],
        }
    except subprocess.TimeoutExpired as exc:
        output = exc.stdout or ""
        if isinstance(output, bytes):
            output = output.decode("utf-8", errors="replace")
        return {
            "command": command,
            "cwd": str(cwd) if cwd else None,
            "exitCode": -1,
            "durationSeconds": round(time.monotonic() - started, 3),
            "timedOut": True,
            "outputTail": output.splitlines()[-100:],
        }


def run_smoke(args):
    target = args.target or (default_gateway_target() if args.kind == "gateway" else default_fabric_target())
    artifact = Path(args.artifact) if args.artifact else default_artifact(args.kind, target)
    artifact = artifact if artifact.is_absolute() else ROOT / artifact
    server_dir = Path(args.server_dir)
    evidence = Path(args.evidence) if args.evidence else default_evidence(args.kind, target)
    evidence = evidence if evidence.is_absolute() else ROOT / evidence
    required_ids = parse_packet_ids(args.expect_packet_id, args.profile)
    command = parse_command(args)
    success_pattern = args.success_pattern or default_success_pattern(args.kind, target)
    target_pattern = server_target_pattern(args.kind, target)
    failure_regexes = [re.compile(pattern) for pattern in FAILURE_PATTERNS + list(args.failure_pattern)]

    if not artifact.exists():
        raise SystemExit("artifact does not exist: {0}".format(artifact))
    if not server_dir.exists():
        raise SystemExit("server dir does not exist: {0}".format(server_dir))
    if not server_dir.is_dir():
        raise SystemExit("server dir is not a directory: {0}".format(server_dir))

    planned_port = args.proxy_port if args.proxy_port else "<ephemeral>"
    plan = {
        "schemaVersion": 1,
        "gate": "core-scenario-smoke",
        "kind": args.kind,
        "target": target,
        "tool": "scripts/run_core_scenario_smoke.py",
        "dryRun": args.dry_run,
        "artifact": display_path(artifact),
        "artifactSha256": sha256(artifact),
        "serverDir": str(server_dir),
        "command": command,
        "scenarioCommand": shlex.split(args.scenario_command_line) if args.scenario_command_line else None,
        "profiles": args.profile,
        "requiredPacketIds": ["0x{0:02x}".format(packet_id) for packet_id in required_ids],
        "proxyHost": args.proxy_host,
        "proxyPort": planned_port,
        "captureSeconds": args.capture_seconds,
        "successPattern": success_pattern,
        "serverTargetPattern": target_pattern,
        "failurePatterns": FAILURE_PATTERNS + list(args.failure_pattern),
    }
    if args.dry_run:
        print(json.dumps(plan, indent=2, sort_keys=True))
        return 0

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind((args.proxy_host, args.proxy_port))
    proxy_port = sock.getsockname()[1]
    packets = []
    udp_stop = threading.Event()
    udp_reader = threading.Thread(target=udp_thread, args=(sock, udp_stop, packets), daemon=True)
    udp_reader.start()

    deployed_artifact = copy_artifact(args.kind, artifact, server_dir)
    config_path = write_proxy_config(args.kind, server_dir, args.proxy_host, proxy_port, args.batch_size)
    if args.accept_eula:
        (server_dir / "eula.txt").write_text("eula=true\n", encoding="utf-8")

    start = time.monotonic()
    started_at = datetime.now(timezone.utc).isoformat()
    output = queue.Queue()
    lines = []
    stop_action = None
    scenario_result = None

    process = subprocess.Popen(
        command,
        cwd=str(server_dir),
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
    )
    log_reader = threading.Thread(target=reader_thread, args=(process, output), daemon=True)
    log_reader.start()

    try:
        startup_ok, server_target_seen, startup_failure = wait_for_startup(
            process,
            output,
            lines,
            args.startup_timeout,
            success_pattern,
            target_pattern,
            failure_regexes,
            args.echo,
        )
        if startup_ok:
            for stdin_command in args.stdin_command:
                if process.stdin:
                    process.stdin.write(stdin_command + "\n")
                    process.stdin.flush()
                time.sleep(args.stdin_command_delay)

            if args.scenario_command_line:
                scenario_cwd = Path(args.scenario_cwd) if args.scenario_cwd else server_dir

                def delayed_stdin():
                    time.sleep(args.delayed_stdin_wait)
                    for cmd in args.delayed_stdin_command:
                        if process.stdin and process.poll() is None:
                            process.stdin.write(cmd + "\n")
                            process.stdin.flush()
                        time.sleep(args.stdin_command_delay)

                delayed_thread = None
                if args.delayed_stdin_command:
                    delayed_thread = threading.Thread(target=delayed_stdin, daemon=True)
                    delayed_thread.start()

                scenario_result = run_scenario_command(args.scenario_command_line, scenario_cwd, args.scenario_timeout)

                if delayed_thread:
                    delayed_thread.join(timeout=5)

            capture_deadline = time.monotonic() + args.capture_seconds
            while time.monotonic() < capture_deadline:
                drain_output(output, lines, args.echo)
                if process.poll() is not None:
                    break
                time.sleep(0.25)
        else:
            startup_failure = startup_failure or "startup failed"
    finally:
        stop_action = stop_process(process, args.stop_timeout)
        udp_stop.set()
        udp_reader.join(timeout=1)
        sock.close()
        drain_output(output, lines, args.echo)

    observed_ids = sorted({packet["packetId"] for packet in packets})
    missing_ids = [packet_id for packet_id in required_ids if packet_id not in observed_ids]
    scenario_exit_ok = scenario_result is None or scenario_result["exitCode"] == 0
    passed = startup_ok and not missing_ids and scenario_exit_ok and process.poll() == 0
    result = {
        **plan,
        "proxyPort": proxy_port,
        "deployedArtifact": str(deployed_artifact),
        "configPath": str(config_path),
        "startedAt": started_at,
        "durationSeconds": round(time.monotonic() - start, 3),
        "startupSeen": startup_ok,
        "serverTargetSeen": server_target_seen,
        "startupFailure": startup_failure,
        "scenarioResult": scenario_result,
        "stopAction": stop_action,
        "exitCode": process.poll(),
        "packetCount": len(packets),
        "observedPacketIds": ["0x{0:02x}".format(packet_id) for packet_id in observed_ids],
        "missingPacketIds": ["0x{0:02x}".format(packet_id) for packet_id in missing_ids],
        "packetSamples": packets[:200],
        "logTail": lines[-200:],
        "result": "passed" if passed else "failed",
    }
    evidence.parent.mkdir(parents=True, exist_ok=True)
    evidence.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print("core scenario smoke {0}: {1}".format(result["result"], evidence))
    return 0 if passed else 1


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("kind", choices=["gateway", "fabric"])
    parser.add_argument("--target", help="exact runtime target id from support-matrix.json, or fabric.defaultTarget")
    parser.add_argument("--artifact", help="artifact jar to deploy")
    parser.add_argument("--server-dir", required=True, help="pre-provisioned server directory")
    parser.add_argument("--evidence", help="output evidence JSON")
    parser.add_argument(
        "--profile",
        action="append",
        default=[],
        choices=sorted(PROFILES),
        help="packet expectation profile; compatibility-core is required for support evidence",
    )
    parser.add_argument("--expect-packet-id", action="append", default=[], help="required packet id, hex or decimal; can be repeated or comma-separated")
    parser.add_argument("--proxy-host", default="127.0.0.1")
    parser.add_argument("--proxy-port", type=int, default=0, help="UDP capture port; 0 chooses an ephemeral port")
    parser.add_argument("--batch-size", type=int, default=1)
    parser.add_argument("--success-pattern", help="log substring required for startup success")
    parser.add_argument("--failure-pattern", action="append", default=[], help="additional regex that fails the smoke test")
    parser.add_argument("--startup-timeout", type=float, default=120.0)
    parser.add_argument("--capture-seconds", type=float, default=30.0)
    parser.add_argument("--stop-timeout", type=float, default=30.0)
    parser.add_argument("--scenario-command-line", help="external scenario command to run after startup")
    parser.add_argument("--scenario-cwd", help="working directory for scenario command, default server dir")
    parser.add_argument("--scenario-timeout", type=float, default=120.0)
    parser.add_argument("--stdin-command", action="append", default=[], help="server console command to send after startup")
    parser.add_argument("--stdin-command-delay", type=float, default=1.0)
    parser.add_argument("--delayed-stdin-command", action="append", default=[], help="server console command to send after the scenario has started (so a connected bot can be referenced)")
    parser.add_argument("--delayed-stdin-wait", type=float, default=10.0, help="seconds to wait after scenario start before sending delayed stdin commands")
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
