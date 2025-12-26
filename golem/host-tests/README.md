# Host-Backed RPC Test Harness

This module provides integration testing against a real Golem executor. It compiles a Scala.js guest that exercises the
`AgentHostApi` bindings, deploys it to a local Golem instance, runs RPC round-trips, and validates the results.

## What It Tests

The test agent (`scala-host-tests`) exposes a single method, `runtests`, which executes a battery of integration
checks:

| Test               | Description                                                      |
|--------------------|------------------------------------------------------------------|
| **Metadata**       | Fetch self metadata and enumerate registered agent types         |
| **Retry Policies** | Round-trip retry policies, persistence levels, idempotence flags |
| **Oplog Markers**  | Exercise oplog markers and idempotency-key generation            |
| **Promises**       | Validate promise lifecycle (create → subscribe → complete)       |
| **Agent ID**       | Parse and validate the agent ID returned by the host             |

## Prerequisites

1. **Golem binaries** (`golem` and `golem-cli` 1.3.0+) on your `PATH`
2. The task launches a local executor via `golem server run`
3. No pre-existing Golem instance required - the task manages its own

## Running the Tests

### Via sbt

```bash
# Run the repo-local task (uses public primitives; fails fast on port conflicts, manages server PID)
GOLEM_HOST_TESTS=1 env -u ARGV0 sbt hostTests/golemHostTests

# Or reuse Test / test, which delegates to the same task when GOLEM_HOST_TESTS=1
GOLEM_HOST_TESTS=1 env -u ARGV0 sbt hostTests/test
```

The sbt task now performs the full flow (server lifecycle, scaffold, build/deploy, invoke) without shelling out to the
script. It fast-links the Scala.js bundle, generates the internal wiring from sbt settings (`golemExports`), builds,
deploys, invokes the harness, and tears down the server it started.

Note: this harness is **repo-local** and is not exposed as a public Mill plugin task (public plugins are primitives-only).

## What the Task Does

1. **Scaffold** - Creates `.golem-apps/scala-host-tests` on first run
2. **Register** - Creates `scala:host-tests` component
3. **Wire** - Wires the component to the Scala.js bundle (generated internally from settings)
4. **Build & Deploy** - `golem app build && deploy`
5. **Invoke** - Runs `scala:host-tests/scala-host-tests().runtests`
6. **Report** - Returns JSON with test results
7. **Cleanup** - Tears down the local executor

## Configuration (env vars)

| Variable                | Default         | Purpose                                              |
|-------------------------|-----------------|------------------------------------------------------|
| `GOLEM_HOST_START_SERVER` | `true`        | Set to `false` to reuse an existing executor         |
| `GOLEM_BIN`             | `golem`         | Path to `golem` binary                               |
| `GOLEM_ROUTER_HOST`     | `127.0.0.1`     | Router host                                          |
| `GOLEM_ROUTER_PORT`     | `9881`          | Router port                                          |
| `GOLEM_CLI_FLAGS`       | `--local`       | Flags passed to `golem-cli` (e.g., `--cloud -p ...`)  |
| `GOLEM_HOST_DATA_DIR`   | `.golem-local`  | Local server data directory                           |
| `GOLEM_HOST_PAYLOAD`    | `{ tests: none }` | Override test selection (WAVE syntax)               |
| `GOLEM_HOST_INVOKE_FLAGS` | *(empty)*     | Extra `golem-cli agent invoke` flags (e.g., `--stream`) |

## Output Format

### Success

```json
{
  "status": "passed",
  "total": 6,
  "passed": 6,
  "failed": 0,
  "results": [
    ...
  ]
}
```

### Failure

```json
{
  "status": "failed",
  "total": 6,
  "passed": 4,
  "failed": 2,
  "results": [
    {
      "name": "metadata",
      "status": "passed"
    },
    {
      "name": "retry-policies",
      "status": "failed",
      "error": "..."
    },
    ...
  ]
}
```

## Troubleshooting

### Streaming Output

Watch each test as it runs by adding an invoke flag:

```bash
GOLEM_HOST_TESTS=1 GOLEM_HOST_INVOKE_FLAGS="--stream" sbt hostTests/golemHostTests
```

### Isolate a Single Test

Run only a specific test by overriding the payload:

```bash
GOLEM_HOST_TESTS=1 GOLEM_HOST_PAYLOAD='some(["metadata"])' sbt hostTests/golemHostTests
```

### Common Issues

| Problem                      | Solution                                              |
|------------------------------|-------------------------------------------------------|
| **Wire/build errors**        | Scala bundle missing - run `sbt hostTests/fastLinkJS` |
| **Deploy errors**            | Check `.golem-local/server.log` for WIT/schema issues |
| **Runtime failures**         | Use `--stream` flag to see detailed logs              |
| **Hanging run**              | Cancel and delete `.golem-local`, then rerun          |

### Manual Cleanup

If you cancel mid-run and the executor persists:

```bash
rm -rf .golem-local
GOLEM_HOST_TESTS=1 env -u ARGV0 sbt hostTests/golemHostTests
```

## Data Directory

The task uses `.golem-local` as the data directory for the local executor. This is automatically created and can be
safely deleted to reset state.

## Comparison to Rust SDK

This harness provides the same confidence level as:

```bash
cargo test -p golem-worker-service
```

Both validate RPC round-trips, host API bindings, and serialization against a real executor.

## Related Documentation

- **[Transaction Helpers](../docs/transactions.md)** - Transaction patterns tested here
- **[Main README](../README.md)** - Full project documentation
