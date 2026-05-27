#!/bin/bash

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 scripts/render_support_matrix.py
python3 scripts/render_support_readiness.py
python3 scripts/verify_support_matrix.py

mvn -q -pl ZeusProtocolJava -am install
mvn -q -pl ZeusGatewayLegacy -am package
mvn -q -pl ZeusGateway -am package

mapfile -t FABRIC_TARGETS < <(python3 scripts/list_fabric_build_targets.py)
for target in "${FABRIC_TARGETS[@]}"; do
    (
        cd ZeusFabric
        ./gradlew --no-daemon build -PmcTarget="$target"
    )
done

python3 scripts/write_release_evidence.py
python3 scripts/render_support_readiness.py --write
python3 scripts/render_support_readiness.py --require-non-smoke-gates
python3 scripts/verify_support_matrix.py --require-artifacts

SMOKE_EULA_ARGS=()
SMOKE_RAN=false
if [ "${ZEUS_SMOKE_ACCEPT_EULA:-false}" = "true" ]; then
    SMOKE_EULA_ARGS+=(--accept-eula)
fi

if [ -n "${ZEUS_GATEWAY_SMOKE_DIR:-}" ]; then
    if [ -z "${ZEUS_GATEWAY_SMOKE_COMMAND:-}" ]; then
        echo "ZEUS_GATEWAY_SMOKE_DIR is set, but ZEUS_GATEWAY_SMOKE_COMMAND is missing." >&2
        exit 1
    fi
    python3 scripts/run_startup_smoke.py gateway \
        --target "${ZEUS_GATEWAY_SMOKE_TARGET:-paper-1.21.7}" \
        --server-dir "$ZEUS_GATEWAY_SMOKE_DIR" \
        "${SMOKE_EULA_ARGS[@]}" \
        --command-line "$ZEUS_GATEWAY_SMOKE_COMMAND"
    SMOKE_RAN=true
fi

if [ -n "${ZEUS_FABRIC_SMOKE_DIR:-}" ]; then
    if [ -z "${ZEUS_FABRIC_SMOKE_COMMAND:-}" ]; then
        echo "ZEUS_FABRIC_SMOKE_DIR is set, but ZEUS_FABRIC_SMOKE_COMMAND is missing." >&2
        exit 1
    fi
    python3 scripts/run_startup_smoke.py fabric \
        --target "${ZEUS_FABRIC_SMOKE_TARGET:-1.21.11}" \
        --server-dir "$ZEUS_FABRIC_SMOKE_DIR" \
        "${SMOKE_EULA_ARGS[@]}" \
        --command-line "$ZEUS_FABRIC_SMOKE_COMMAND"
    SMOKE_RAN=true
fi

if [ -n "${ZEUS_GATEWAY_CORE_SMOKE_DIR:-}" ]; then
    if [ -z "${ZEUS_GATEWAY_CORE_SMOKE_COMMAND:-}" ]; then
        echo "ZEUS_GATEWAY_CORE_SMOKE_DIR is set, but ZEUS_GATEWAY_CORE_SMOKE_COMMAND is missing." >&2
        exit 1
    fi
    GATEWAY_SCENARIO_ARGS=()
    if [ -n "${ZEUS_GATEWAY_SCENARIO_COMMAND:-}" ]; then
        GATEWAY_SCENARIO_ARGS+=(--scenario-command-line "$ZEUS_GATEWAY_SCENARIO_COMMAND")
    fi
    if [ -n "${ZEUS_GATEWAY_CORE_EXPECT_IDS:-}" ]; then
        GATEWAY_SCENARIO_ARGS+=(--expect-packet-id "$ZEUS_GATEWAY_CORE_EXPECT_IDS")
    fi
    python3 scripts/run_core_scenario_smoke.py gateway \
        --target "${ZEUS_GATEWAY_CORE_SMOKE_TARGET:-paper-1.21.7}" \
        --server-dir "$ZEUS_GATEWAY_CORE_SMOKE_DIR" \
        --profile "${ZEUS_GATEWAY_CORE_PROFILE:-compatibility-core}" \
        "${SMOKE_EULA_ARGS[@]}" \
        "${GATEWAY_SCENARIO_ARGS[@]}" \
        --command-line "$ZEUS_GATEWAY_CORE_SMOKE_COMMAND"
    SMOKE_RAN=true
fi

if [ -n "${ZEUS_FABRIC_CORE_SMOKE_DIR:-}" ]; then
    if [ -z "${ZEUS_FABRIC_CORE_SMOKE_COMMAND:-}" ]; then
        echo "ZEUS_FABRIC_CORE_SMOKE_DIR is set, but ZEUS_FABRIC_CORE_SMOKE_COMMAND is missing." >&2
        exit 1
    fi
    FABRIC_SCENARIO_ARGS=()
    if [ -n "${ZEUS_FABRIC_SCENARIO_COMMAND:-}" ]; then
        FABRIC_SCENARIO_ARGS+=(--scenario-command-line "$ZEUS_FABRIC_SCENARIO_COMMAND")
    fi
    if [ -n "${ZEUS_FABRIC_CORE_EXPECT_IDS:-}" ]; then
        FABRIC_SCENARIO_ARGS+=(--expect-packet-id "$ZEUS_FABRIC_CORE_EXPECT_IDS")
    fi
    python3 scripts/run_core_scenario_smoke.py fabric \
        --target "${ZEUS_FABRIC_CORE_SMOKE_TARGET:-1.21.11}" \
        --server-dir "$ZEUS_FABRIC_CORE_SMOKE_DIR" \
        --profile "${ZEUS_FABRIC_CORE_PROFILE:-compatibility-core}" \
        "${SMOKE_EULA_ARGS[@]}" \
        "${FABRIC_SCENARIO_ARGS[@]}" \
        --command-line "$ZEUS_FABRIC_CORE_SMOKE_COMMAND"
    SMOKE_RAN=true
fi

if [ -n "${ZEUS_SMOKE_MATRIX_CONFIG:-}" ]; then
    SMOKE_MATRIX_ARGS=(--config "$ZEUS_SMOKE_MATRIX_CONFIG")
    if [ "${ZEUS_SMOKE_MATRIX_REQUIRE_ALL:-false}" = "true" ]; then
        SMOKE_MATRIX_ARGS+=(--require-all-buildable)
    fi
    if [ "${ZEUS_SMOKE_ACCEPT_EULA:-false}" = "true" ]; then
        SMOKE_MATRIX_ARGS+=(--accept-eula)
    fi
    if [ "${ZEUS_SMOKE_ECHO:-false}" = "true" ]; then
        SMOKE_MATRIX_ARGS+=(--echo)
    fi
    python3 scripts/run_smoke_matrix.py "${SMOKE_MATRIX_ARGS[@]}"
    SMOKE_RAN=true
fi

if [ "$SMOKE_RAN" = "true" ]; then
    python3 scripts/render_support_readiness.py --write
    if [ "${ZEUS_SMOKE_MATRIX_REQUIRE_ALL:-false}" = "true" ]; then
        python3 scripts/render_support_readiness.py --require-all-gates
    else
        python3 scripts/render_support_readiness.py --require-non-smoke-gates
    fi
    python3 scripts/verify_support_matrix.py --require-artifacts
fi

if [ -f "../Cargo.toml" ]; then
    (
        cd ..
        cargo test -p protocol -p network
    )
fi
