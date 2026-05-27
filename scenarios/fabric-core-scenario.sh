#!/usr/bin/env bash
# Fabric scenario wrapper: starts bot, waits for join, then sends server commands via FIFO.
# Usage: fabric-core-scenario.sh <server_stdin_fifo> [host] [port] [version] [timeout]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_FIFO="${1:?server stdin fifo path required}"
HOST="${2:-127.0.0.1}"
PORT="${3:-25565}"
VERSION="${4:-}"
TIMEOUT="${5:-60}"

ARGS=(--host "$HOST" --port "$PORT" --timeout "$TIMEOUT")
if [ -n "$VERSION" ]; then
  ARGS+=(--version "$VERSION")
fi

# Start bot in background
cd "$SCRIPT_DIR"
node compatibility-core.js "${ARGS[@]}" &
BOT_PID=$!

# Wait for bot to connect and spawn (give it 10 seconds)
sleep 10

# Send server commands via FIFO to summon entities/blocks near bot
if [ -p "$SERVER_FIFO" ]; then
  echo "execute as ZeusSmokeBot at @s run summon minecraft:zombie ~ ~ ~" > "$SERVER_FIFO"
  sleep 1
  echo "execute as ZeusSmokeBot at @s run summon minecraft:zombie ~1 ~ ~" > "$SERVER_FIFO"
  sleep 1
  echo "execute as ZeusSmokeBot at @s run setblock ~ ~ ~2 chest" > "$SERVER_FIFO"
  sleep 1
  echo "execute as ZeusSmokeBot at @s run setblock ~3 ~ ~ piston[facing=west]" > "$SERVER_FIFO"
  sleep 1
  echo "execute as ZeusSmokeBot at @s run setblock ~4 ~ ~ redstone_block" > "$SERVER_FIFO"
fi

wait $BOT_PID
exit $?
