#!/usr/bin/env bash
# Setup and run smoke tests for a Fabric target.
# Usage: setup-fabric-smoke.sh <minecraft_version>

set -euo pipefail

VERSION="${1:?minecraft version required}"
DIR="/tmp/zeus-smoke/fabric-${VERSION}"
ROOT="/home/vennv/Documents/GIT/zeus_platform/zeus_plugins"

# Setup server dir
mkdir -p "$DIR/mods"
rm -rf "$DIR/world" "$DIR/world_nether" "$DIR/world_the_end"

# Download server jar if missing
if [ ! -f "$DIR/server.jar" ]; then
    curl -sL "https://meta.fabricmc.net/v2/versions/loader/${VERSION}/0.18.1/1.0.3/server/jar" -o "$DIR/server.jar"
fi

# Download Fabric API if missing
if [ ! -f "$DIR/mods/fabric-api.jar" ] || [ ! -s "$DIR/mods/fabric-api.jar" ]; then
    URL=$(curl -sL "https://api.modrinth.com/v2/project/P7dR8mSH/version?game_versions=%5B%22${VERSION}%22%5D&loaders=%5B%22fabric%22%5D" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d[0]['files'][0]['url'])")
    curl -sL -o "$DIR/mods/fabric-api.jar" "$URL"
fi

# Configure server (flat world for predictable spawn, offline, no secure profile)
cat > "$DIR/server.properties" <<EOF
online-mode=false
enforce-secure-profile=false
level-type=minecraft\:flat
spawn-protection=0
EOF
echo "eula=true" > "$DIR/eula.txt"

cd "$ROOT"

# Run startup smoke
echo "=== Startup smoke for ${VERSION} ==="
python3 scripts/run_startup_smoke.py fabric \
    --target "$VERSION" \
    --server-dir "$DIR" \
    --accept-eula \
    --command-line 'java -Xmx1G -jar server.jar nogui' \
    --timeout 240 || exit 1

# Run core-scenario smoke
echo "=== Core-scenario smoke for ${VERSION} ==="
rm -rf "$DIR/world" "$DIR/world_nether" "$DIR/world_the_end"
python3 scripts/run_core_scenario_smoke.py fabric \
    --target "$VERSION" \
    --server-dir "$DIR" \
    --accept-eula \
    --profile compatibility-core \
    --scenario-command-line "bash scenarios/compatibility-core.sh 127.0.0.1 25565 '' 90" \
    --scenario-cwd "$ROOT" \
    --scenario-timeout 120 \
    --capture-seconds 90 \
    --startup-timeout 240 \
    --stdin-command "op ZeusSmokeBot" \
    --stdin-command "gamerule doMobSpawning false" \
    --delayed-stdin-command "execute as ZeusSmokeBot at @s run summon minecraft:zombie ~ ~ ~" \
    --delayed-stdin-command "execute as ZeusSmokeBot at @s run summon minecraft:zombie ~1 ~ ~" \
    --delayed-stdin-command "execute as ZeusSmokeBot at @s run setblock ~ ~ ~2 chest" \
    --delayed-stdin-command "execute as ZeusSmokeBot at @s run setblock ~3 ~ ~ piston[facing=west]" \
    --delayed-stdin-command "execute as ZeusSmokeBot at @s run setblock ~4 ~ ~ redstone_block" \
    --delayed-stdin-wait 12 \
    --stdin-command-delay 3 \
    --command-line 'java -Xmx1G -jar server.jar nogui'
