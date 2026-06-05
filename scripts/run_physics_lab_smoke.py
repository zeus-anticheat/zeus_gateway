#!/usr/bin/env python3
"""Run a Paper 1.21.11 ZeusPhysicsLab route smoke with a real bot."""

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
DEFAULT_SERVER_JAR = ROOT / "versions" / "1.21.11" / "paper-1.21.11.jar"
DEFAULT_SERVER_DIR = ROOT / "versions" / "1.21.11" / "physics-lab-smoke-server"
DEFAULT_EVIDENCE = ROOT / "verification" / "evidence" / "physics-lab-smoke" / "paper-1.21.11.json"
DEFAULT_GATEWAY = ROOT / "ZeusGateway" / "target" / "ZeusGateway-modern-1.0-SNAPSHOT.jar"
DEFAULT_LAB = ROOT / "ZeusPhysicsLab" / "target" / "zeus_physics_lab-1.0-SNAPSHOT.jar"
DEFAULT_SCENARIO = ROOT / "scenarios" / "physics-lab-route.js"

ORIGIN_X = 0
ORIGIN_Y = 80
ORIGIN_Z = 0
STATION_LENGTH = 34
STATION_GAP = 4

FAILURE_PATTERNS = [
    r"Could not load 'plugins/.*\.jar'",
    r"Error occurred while enabling ZeusGateway",
    r"Error occurred while enabling ZeusPhysicsLab",
    r"Failed to start the minecraft server",
    r"java\.lang\.NoClassDefFoundError",
    r"java\.lang\.NoSuchMethodError",
    r"java\.lang\.UnsupportedClassVersionError",
    r"Exception in thread \"Server thread\"",
    r"Encountered an unexpected exception",
    r"Paper Watchdog Thread/ERROR",
    r"The server has not responded for",
]

REQUIRED_PACKET_IDS = [0x01, 0x03, 0x13, 0x14, 0x1E, 0x25, 0x26]


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
                "sampleHex": payload[:48].hex(),
            }
        )


def drain(output, lines, echo=False, prefix=None):
    drained = []
    while True:
        try:
            line = output.get_nowait()
        except queue.Empty:
            return drained
        lines.append(line)
        drained.append(line)
        if echo:
            print((prefix or "") + line)


def send_command(process, command, sent):
    sent.append(command)
    if process.stdin and process.poll() is None:
        process.stdin.write(command + "\n")
        process.stdin.flush()


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


def write_server_files(server_dir, server_port, proxy_host, proxy_port, batch_size):
    server_dir.mkdir(parents=True, exist_ok=True)
    (server_dir / "plugins").mkdir(parents=True, exist_ok=True)
    (server_dir / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (server_dir / "server.properties").write_text(
        "\n".join(
            [
                "server-port={0}".format(server_port),
                "online-mode=false",
                "enforce-secure-profile=false",
                "enable-command-block=true",
                "allow-flight=true",
                "spawn-protection=0",
                "difficulty=peaceful",
                "gamemode=survival",
                "force-gamemode=false",
                "view-distance=8",
                "simulation-distance=6",
                "level-type=flat",
                "generator-settings={\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1},{\"block\":\"minecraft:dirt\",\"height\":2},{\"block\":\"minecraft:grass_block\",\"height\":1}],\"biome\":\"minecraft:plains\"}",
                "motd=Zeus Physics Lab Smoke",
                "",
            ]
        ),
        encoding="utf-8",
    )
    config_dir = server_dir / "plugins" / "ZeusGateway"
    config_dir.mkdir(parents=True, exist_ok=True)
    config_path = config_dir / "config.yml"
    config_path.write_text(
        "\n".join(
            [
                "proxy-ac:",
                "    host: {0}".format(proxy_host),
                "    port: {0}".format(proxy_port),
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
    lab_config_dir = server_dir / "plugins" / "ZeusPhysicsLab"
    lab_config_dir.mkdir(parents=True, exist_ok=True)
    (lab_config_dir / "config.yml").write_text(
        "\n".join(
            [
                "origin:",
                "  explicit: true",
                "  x: {0}".format(ORIGIN_X),
                "  y: {0}".format(ORIGIN_Y),
                "  z: {0}".format(ORIGIN_Z),
                "world: world",
                "",
            ]
        ),
        encoding="utf-8",
    )
    return config_path


def copy_plugin(artifact, server_dir):
    destination = server_dir / "plugins" / artifact.name
    shutil.copy2(artifact, destination)
    return destination


def wait_for_server(process, output, lines, timeout, echo):
    deadline = time.monotonic() + timeout
    seen = {
        "paperVersion": False,
        "gateway": False,
        "lab": False,
        "done": False,
    }
    failure_regexes = [re.compile(pattern) for pattern in FAILURE_PATTERNS]
    failure = None
    while time.monotonic() < deadline:
        try:
            line = output.get(timeout=0.25)
        except queue.Empty:
            if process.poll() is not None:
                return False, seen, failure or "server exited during startup"
            continue
        lines.append(line)
        if echo:
            print(line)
        if "1.21.11" in line and ("Paper" in line or "minecraft server version" in line):
            seen["paperVersion"] = True
        if "[ZeusGateway] Plugin enabled successfully" in line:
            seen["gateway"] = True
        if "ZeusPhysicsLab loaded with" in line:
            seen["lab"] = True
        if "Done (" in line and "For help, type" in line:
            seen["done"] = True
        for regex in failure_regexes:
            if regex.search(line):
                failure = line
                return False, seen, failure
        if all(seen.values()):
            return True, seen, None
    missing = [key for key, value in seen.items() if not value]
    return False, seen, "startup timeout; missing " + ",".join(missing)


def wait_for_lab_verify(process, output, lines, sent_commands, timeout, echo):
    preflight_commands = [
        "difficulty peaceful",
        "op ZeusLabBot",
        "kill @e[tag=zeus_lab_vehicle]",
        "kill @e[tag=zeus_lab_target]",
    ]
    for command in preflight_commands:
        send_command(process, command, sent_commands)
        time.sleep(0.4)

    deadline = time.monotonic() + timeout
    failure_regexes = [re.compile(pattern) for pattern in FAILURE_PATTERNS]
    verify = {
        "expectedStations": None,
        "stationSignsFound": None,
        "startCommandBlocksFound": None,
        "passed": False,
    }
    manifest_written = False
    phase = "reset"
    send_command(process, "zeuslab reset", sent_commands)
    while time.monotonic() < deadline:
        for line in drain(output, lines, echo):
            for regex in failure_regexes:
                if regex.search(line):
                    return verify, manifest_written, line
            if phase == "reset" and "Zeus Physics Lab route cleared." in line:
                phase = "generate"
                send_command(process, "zeuslab generate", sent_commands)
                continue
            if phase == "generate" and "Zeus Physics Lab generated as one long forward route" in line:
                phase = "verify"
                send_command(process, "zeuslab verify", sent_commands)
                time.sleep(0.2)
                send_command(process, "zeuslab manifest", sent_commands)
                continue
            match = re.search(r"Stations expected: (\d+)", line)
            if match:
                verify["expectedStations"] = int(match.group(1))
            match = re.search(r"Station signs found: (\d+)", line)
            if match:
                verify["stationSignsFound"] = int(match.group(1))
            match = re.search(r"Start command blocks found: (\d+)", line)
            if match:
                verify["startCommandBlocksFound"] = int(match.group(1))
            if "Verification passed." in line:
                verify["passed"] = True
            if "Manifest written to" in line:
                manifest_written = True
        if verify["passed"] and manifest_written:
            return verify, True, None
        if process.poll() is not None:
            return verify, manifest_written, "server exited while generating lab"
        time.sleep(0.25)
    return verify, manifest_written, "lab verify timeout during {0}".format(phase)


def station_location(station_number, category=None):
    origin_z = ORIGIN_Z + 8 + (station_number - 1) * (STATION_LENGTH + STATION_GAP)
    if category == "TRANSACTION":
        return ORIGIN_X - 1.5, ORIGIN_Y + 1.0, origin_z + 9.5
    return ORIGIN_X + 0.5, ORIGIN_Y + 1.0, origin_z + 3.5


def setup_bot(process, sent_commands):
    commands = [
        "gamemode survival ZeusLabBot",
        "effect give ZeusLabBot saturation 180 1 true",
        "effect give ZeusLabBot resistance 180 1 true",
        "effect give ZeusLabBot water_breathing 180 0 true",
        "give ZeusLabBot diamond_sword 1",
        "give ZeusLabBot diamond_pickaxe 1",
        "give ZeusLabBot stone 64",
        "give ZeusLabBot bread 16",
        "give ZeusLabBot shield 1",
    ]
    for command in commands:
        send_command(process, command, sent_commands)
        time.sleep(0.15)


def handle_scenario_line(line, server_process, sent_commands, sample_teleports):
    if line == "ZEUSLAB_READY":
        setup_bot(server_process, sent_commands)
        return
    if not line.startswith("ZEUSLAB_REQUEST_TP "):
        return
    payload = line[len("ZEUSLAB_REQUEST_TP "):]
    sample = json.loads(payload)
    x, y, z = station_location(sample["number"], sample.get("category"))
    command = "tp ZeusLabBot {0:.3f} {1:.3f} {2:.3f} 0 0".format(x, y, z)
    sample_teleports.append({**sample, "x": x, "y": y, "z": z})
    send_command(server_process, command, sent_commands)
    send_command(server_process, "effect give ZeusLabBot saturation 60 1 true", sent_commands)
    if sample.get("category") in ("EXTERNAL_FORCE", "LIQUID_CLIMB_SPECIAL"):
        send_command(server_process, "effect give ZeusLabBot resistance 60 1 true", sent_commands)
        send_command(server_process, "effect give ZeusLabBot water_breathing 60 0 true", sent_commands)


def run_scenario(args, server_process, server_output, server_lines, sent_commands):
    command = shlex.split(args.scenario_command_line) if args.scenario_command_line else [
        "node",
        str(DEFAULT_SCENARIO),
        "--host",
        args.bot_host,
        "--port",
        str(args.server_port),
        "--version",
        args.minecraft_version,
        "--timeout",
        str(int(args.scenario_timeout)),
    ]
    started = time.monotonic()
    process = subprocess.Popen(
        command,
        cwd=str(args.scenario_cwd) if args.scenario_cwd else str(ROOT),
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
    )
    output = queue.Queue()
    thread = threading.Thread(target=reader_thread, args=(process, output), daemon=True)
    thread.start()
    lines = []
    sample_teleports = []
    deadline = time.monotonic() + args.scenario_timeout
    while time.monotonic() < deadline:
        drain(server_output, server_lines, args.echo)
        for line in drain(output, lines, args.echo, prefix="[bot] "):
            try:
                handle_scenario_line(line, server_process, sent_commands, sample_teleports)
            except Exception as exc:
                lines.append("RUNNER_HANDLE_ERROR: {0}".format(exc))
        if process.poll() is not None:
            break
        time.sleep(0.1)
    timed_out = process.poll() is None
    if timed_out:
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)
    drain(output, lines, args.echo, prefix="[bot] ")
    return {
        "command": command,
        "cwd": str(args.scenario_cwd) if args.scenario_cwd else str(ROOT),
        "exitCode": process.poll(),
        "durationSeconds": round(time.monotonic() - started, 3),
        "timedOut": timed_out,
        "sampleTeleports": sample_teleports,
        "outputTail": lines[-160:],
    }


def read_manifest(server_dir):
    manifest = server_dir / "plugins" / "ZeusPhysicsLab" / "zeus_physics_lab_manifest.json"
    if not manifest.exists():
        return {"path": str(manifest), "exists": False}
    data = json.loads(manifest.read_text(encoding="utf-8"))
    return {
        "path": str(manifest),
        "exists": True,
        "world": data.get("world"),
        "origin": data.get("origin"),
        "stationCount": data.get("station_count"),
        "firstStation": data.get("stations", [{}])[0].get("station_id") if data.get("stations") else None,
        "lastStation": data.get("stations", [{}])[-1].get("station_id") if data.get("stations") else None,
    }


def run(args):
    server_jar = Path(args.server_jar)
    server_dir = Path(args.server_dir)
    gateway_artifact = Path(args.gateway_artifact)
    lab_artifact = Path(args.lab_artifact)
    evidence = Path(args.evidence)

    for path in [server_jar, gateway_artifact, lab_artifact, DEFAULT_SCENARIO]:
        if not path.exists():
            raise SystemExit("missing required file: {0}".format(path))

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind((args.proxy_host, args.proxy_port))
    proxy_port = sock.getsockname()[1]
    packets = []
    udp_stop = threading.Event()
    udp_reader = threading.Thread(target=udp_thread, args=(sock, udp_stop, packets), daemon=True)
    udp_reader.start()

    config_path = write_server_files(server_dir, args.server_port, args.proxy_host, proxy_port, args.batch_size)
    deployed_gateway = copy_plugin(gateway_artifact, server_dir)
    deployed_lab = copy_plugin(lab_artifact, server_dir)

    command = [
        args.java,
        "-Xms{0}".format(args.memory),
        "-Xmx{0}".format(args.memory),
        "-jar",
        str(server_jar),
        "nogui",
    ]
    started_at = datetime.now(timezone.utc).isoformat()
    start = time.monotonic()
    sent_commands = []
    server_lines = []
    scenario_result = None
    lab_verify = None
    manifest = None
    stop_action = None
    startup_ok = False
    startup_seen = {}
    startup_failure = None
    lab_manifest_written = False
    lab_failure = None

    process = subprocess.Popen(
        command,
        cwd=str(server_dir),
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
    )
    server_output = queue.Queue()
    log_reader = threading.Thread(target=reader_thread, args=(process, server_output), daemon=True)
    log_reader.start()

    try:
        startup_ok, startup_seen, startup_failure = wait_for_server(
            process,
            server_output,
            server_lines,
            args.startup_timeout,
            args.echo,
        )
        if startup_ok:
            lab_verify, lab_manifest_written, lab_failure = wait_for_lab_verify(
                process,
                server_output,
                server_lines,
                sent_commands,
                args.lab_timeout,
                args.echo,
            )
            manifest = read_manifest(server_dir)
            if lab_verify and lab_verify.get("passed"):
                scenario_result = run_scenario(args, process, server_output, server_lines, sent_commands)
                capture_deadline = time.monotonic() + args.capture_seconds
                while time.monotonic() < capture_deadline:
                    drain(server_output, server_lines, args.echo)
                    if process.poll() is not None:
                        break
                    time.sleep(0.25)
    finally:
        stop_action = stop_process(process, args.stop_timeout)
        udp_stop.set()
        udp_reader.join(timeout=1)
        sock.close()
        drain(server_output, server_lines, args.echo)

    observed_ids = sorted({packet["packetId"] for packet in packets})
    missing_ids = [packet_id for packet_id in REQUIRED_PACKET_IDS if packet_id not in observed_ids]
    failure_regexes = [re.compile(pattern) for pattern in FAILURE_PATTERNS]
    failure_lines = [
        line for line in server_lines
        if any(regex.search(line) for regex in failure_regexes)
    ]
    scenario_ok = scenario_result is not None and scenario_result.get("exitCode") == 0
    manifest_ok = bool(manifest and manifest.get("exists") and manifest.get("stationCount") == 137)
    lab_ok = bool(lab_verify and lab_verify.get("passed") and lab_manifest_written and manifest_ok)
    packet_ok = not missing_ids and len(packets) >= args.min_packets
    passed = startup_ok and lab_ok and scenario_ok and packet_ok and not failure_lines and process.poll() == 0

    result = {
        "schemaVersion": 1,
        "gate": "physics-lab-smoke",
        "software": "paper",
        "minecraftVersion": args.minecraft_version,
        "serverJar": display_path(server_jar),
        "serverJarSha256": sha256(server_jar),
        "serverDir": str(server_dir),
        "command": command,
        "gatewayArtifact": display_path(gateway_artifact),
        "gatewayArtifactSha256": sha256(gateway_artifact),
        "labArtifact": display_path(lab_artifact),
        "labArtifactSha256": sha256(lab_artifact),
        "deployedGateway": str(deployed_gateway),
        "deployedLab": str(deployed_lab),
        "configPath": str(config_path),
        "startedAt": started_at,
        "durationSeconds": round(time.monotonic() - start, 3),
        "startupOk": startup_ok,
        "startupSeen": startup_seen,
        "startupFailure": startup_failure,
        "labVerify": lab_verify,
        "labManifestWritten": lab_manifest_written,
        "labFailure": lab_failure,
        "manifest": manifest,
        "scenarioResult": scenario_result,
        "sentCommands": sent_commands,
        "stopAction": stop_action,
        "exitCode": process.poll(),
        "packetCount": len(packets),
        "minPackets": args.min_packets,
        "requiredPacketIds": ["0x{0:02x}".format(packet_id) for packet_id in REQUIRED_PACKET_IDS],
        "observedPacketIds": ["0x{0:02x}".format(packet_id) for packet_id in observed_ids],
        "missingPacketIds": ["0x{0:02x}".format(packet_id) for packet_id in missing_ids],
        "packetSamples": packets[:240],
        "failureLines": failure_lines[-40:],
        "logTail": server_lines[-240:],
        "result": "passed" if passed else "failed",
    }
    evidence.parent.mkdir(parents=True, exist_ok=True)
    evidence.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print("physics lab smoke {0}: {1}".format(result["result"], evidence))
    return 0 if passed else 1


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--server-jar", default=str(DEFAULT_SERVER_JAR))
    parser.add_argument("--server-dir", default=str(DEFAULT_SERVER_DIR))
    parser.add_argument("--gateway-artifact", default=str(DEFAULT_GATEWAY))
    parser.add_argument("--lab-artifact", default=str(DEFAULT_LAB))
    parser.add_argument("--evidence", default=str(DEFAULT_EVIDENCE))
    parser.add_argument("--java", default="java")
    parser.add_argument("--memory", default="2G")
    parser.add_argument("--minecraft-version", default="1.21.11")
    parser.add_argument("--server-port", type=int, default=25577)
    parser.add_argument("--bot-host", default="127.0.0.1")
    parser.add_argument("--proxy-host", default="127.0.0.1")
    parser.add_argument("--proxy-port", type=int, default=0)
    parser.add_argument("--batch-size", type=int, default=1)
    parser.add_argument("--startup-timeout", type=float, default=160.0)
    parser.add_argument("--lab-timeout", type=float, default=240.0)
    parser.add_argument("--scenario-command-line")
    parser.add_argument("--scenario-cwd")
    parser.add_argument("--scenario-timeout", type=float, default=180.0)
    parser.add_argument("--capture-seconds", type=float, default=10.0)
    parser.add_argument("--stop-timeout", type=float, default=45.0)
    parser.add_argument("--min-packets", type=int, default=35)
    parser.add_argument("--echo", action="store_true")
    return run(parser.parse_args())


if __name__ == "__main__":
    raise SystemExit(main())
