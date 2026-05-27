#!/usr/bin/env bash
# Wrapper to invoke the mineflayer scenario bot from run_core_scenario_smoke.py
# Args (env): SCENARIO_HOST, SCENARIO_PORT, SCENARIO_VERSION, SCENARIO_TIMEOUT
# Or positional: $1=host $2=port $3=version $4=timeout

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

HOST="${SCENARIO_HOST:-${1:-127.0.0.1}}"
PORT="${SCENARIO_PORT:-${2:-25565}}"
VERSION="${SCENARIO_VERSION:-${3:-}}"
TIMEOUT="${SCENARIO_TIMEOUT:-${4:-60}}"

ARGS=(--host "$HOST" --port "$PORT" --timeout "$TIMEOUT")
if [ -n "$VERSION" ]; then
  ARGS+=(--version "$VERSION")
fi

cd "$SCRIPT_DIR"
exec node compatibility-core.js "${ARGS[@]}"
