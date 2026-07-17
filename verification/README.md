# Verification Evidence

`support-matrix.json` may mark a target `supported` only when every gate has
evidence:

- `artifact-build`
- `protocol-golden-fixtures`
- `server-startup-smoke`
- `core-scenario-smoke`

Evidence entries should point to JSON files under `verification/evidence/`.
Build and protocol fixture evidence for every build-verifiable target can be
written after successful Maven/Gradle builds with:

```bash
python3 scripts/write_release_evidence.py
```

This records artifact paths, SHA-256 digests and the golden fixture test
contract. It does not replace startup or scenario smoke evidence. The release
gate runs `render_support_readiness.py --require-non-smoke-gates` after writing
this evidence, so stale artifact/protocol evidence fails the build immediately.

Startup smoke evidence can be produced with:

```bash
python3 scripts/run_startup_smoke.py gateway \
  --server-dir /path/to/prepared-paper-server \
  --accept-eula \
  -- java -Xmx1G -jar paper.jar nogui
```

or:

```bash
python3 scripts/run_startup_smoke.py fabric \
  --server-dir /path/to/prepared-fabric-server \
  --accept-eula \
  -- java -Xmx1G -jar fabric-server-launch.jar nogui
```

The script copies the current artifact into `plugins/` or `mods/`, waits for
the Zeus startup success log line, sends `stop`, and writes evidence JSON. It
does not download server jars and does not promote any target to `supported`.
Dry-run output is explicitly rejected as support evidence.

Core scenario evidence can be produced with `run_core_scenario_smoke.py`. It
starts a local UDP listener, writes Zeus config so the plugin/mod sends packets
to that listener, runs the prepared server and optional scenario automation, and
passes only when all required packet IDs are observed:

```bash
python3 scripts/run_core_scenario_smoke.py gateway \
  --server-dir /path/to/prepared-paper-server \
  --profile compatibility-core \
  --scenario-command-line "/path/to/client-scenario.sh" \
  --accept-eula \
  -- java -Xmx1G -jar paper.jar nogui
```

The `compatibility-core` profile requires attack (`0x09`), velocity (`0x22`),
inventory transaction (`0x26`), and external force (`0x27`). A join-only smoke
is not enough for support evidence.

To run smoke tests as a target matrix, create a JSON config like
[`smoke-matrix.example.json`](smoke-matrix.example.json) and run:

```bash
python3 scripts/run_smoke_matrix.py \
  --config /path/to/smoke-matrix.json \
  --require-all-buildable
```

`--require-all-buildable` fails unless every `build-verifiable` or `supported`
target in `support-matrix.json` has a configured smoke entry. The release gate
also supports this through `ZEUS_SMOKE_MATRIX_CONFIG`; set
`ZEUS_SMOKE_MATRIX_REQUIRE_ALL=true` to require complete matrix coverage and
then fail unless every publication gate has passed for every buildable target.
