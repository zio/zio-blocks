---
name: zio-golem-integration-tests
description: "Run and debug Golem Scala SDK integration tests. Use when running golem integration tests, debugging test failures, or working with GolemExamplesIntegrationSpec."
---

# ZIO Golem Integration Tests

Integration tests for the Golem Scala SDK live in `golem/integration-tests/`. They exercise test agents against a real local Golem server.

## Prerequisites

1. **`golem-cli`** on PATH (v1.5.0-dev at `~/.cargo/bin/golem-cli`)
2. **TS packages built** — the Golem TypeScript SDK packages at the path pointed to by `GOLEM_TS_PACKAGES_PATH`
3. **Port 9881 free** — the test suite starts its own Golem server
4. **SDK published locally** — run from the zio-blocks monorepo root:

```bash
sbt --client '++3.8.2; set ThisBuild / version := "0.0.0-SNAPSHOT"; set ThisBuild / packageDoc / publishArtifact := false; set every (publish / skip) := false; zioGolemModelJVM/publishLocal; zioGolemModelJS/publishLocal; zioGolemMacros/publishLocal; zioGolemCoreJS/publishLocal'
```

## Running Tests

Always use `++3.8.2` (without `!` — lets upstream deps compile at their own version) and pass the TS packages path as a JVM system property:

```bash
# All integration tests
sbt --client '++3.8.2; set zioGolemIntegrationTests / Test / javaOptions += "-Dgolem.tsPackagesPath=/home/vigoo/projects/golem/sdks/ts/packages"; zioGolemIntegrationTests/test'

# Only HTTP endpoint tests
sbt --client '++3.8.2; set zioGolemIntegrationTests / Test / javaOptions += "-Dgolem.tsPackagesPath=/home/vigoo/projects/golem/sdks/ts/packages"; zioGolemIntegrationTests/testOnly -- -t http-'

# A specific test by name
sbt --client '++3.8.2; set zioGolemIntegrationTests / Test / javaOptions += "-Dgolem.tsPackagesPath=/home/vigoo/projects/golem/sdks/ts/packages"; zioGolemIntegrationTests/testOnly -- -t sync-return'
```

Use the standard AGENTS.md sbt logging pattern (redirect to log file, check exit code).

### Environment Variable

The TS packages path can be provided two ways:
- **System property** (works with `sbt --client`): `-Dgolem.tsPackagesPath=...` via `javaOptions`
- **Environment variable**: `GOLEM_TS_PACKAGES_PATH=...` (does NOT propagate through `sbt --client` to the forked test JVM — use the system property instead)

## Test Architecture

### Server Lifecycle

The `GolemServer.layer` (ZLayer) handles everything:

1. Checks `golem-cli` is on PATH
2. Checks `GOLEM_TS_PACKAGES_PATH` / `golem.tsPackagesPath` is set
3. Verifies port 9881 is free (fails if already in use — **kill any running golem server first**)
4. Cleans `golem-temp/` directory (stale REPL caches)
5. Starts `golem-cli -vvv server run --clean --disable-app-manifest-discovery`
6. Waits for port 9881 to accept connections (60s timeout)
7. Runs `golem-cli deploy` (with one retry)
8. On teardown: kills the server process tree

### Two Test Categories

1. **Sample tests** — TypeScript REPL scripts in `golem/test-agents/samples/*/repl-*.ts`. Each script is executed via `golem-cli repl scala:examples --language typescript --script-file <script>`. Output is checked for expected fragments.

2. **HTTP endpoint tests** — Direct HTTP calls to `localhost:9006` (configured in `golem.yaml`). Test code-first HTTP routes defined via `@agentDefinition(mount=...)` and `@endpoint(...)`.

### Key Files

| File | Purpose |
|------|---------|
| `golem/integration-tests/src/test/scala/golem/integration/GolemExamplesIntegrationSpec.scala` | All tests |
| `golem/test-agents/golem.yaml` | App manifest (components, HTTP deployments) |
| `golem/test-agents/src/main/scala/example/minimal/` | Agent definitions and implementations |
| `golem/test-agents/samples/` | TypeScript REPL test scripts |
| `golem/test-agents/.golem/` | Build output (created by `golem-cli deploy`) |
| `golem/test-agents/.generated/agent_guest.wasm` | Prebuilt QuickJS WASM runtime |
| `golem/test-agents/golem-temp/` | REPL caches, bridge SDKs (created at runtime) |

## Before Running Tests

### Kill existing golem processes

```bash
pkill -f "golem.*server" 2>/dev/null
```

### Clean build artifacts when SDK code changed

```bash
rm -rf golem/test-agents/.golem golem/test-agents/target
rm -rf golem/macros/target golem/model/.jvm/target golem/model/.js/target
rm -rf golem/core/js/target
```

The Golem CLI caches builds aggressively (`[UP-TO-DATE]`). If you changed macro or core logic, you MUST delete `.golem/` to force a rebuild.

### Ensure `.generated/agent_guest.wasm` exists

```bash
cp golem/sbt/src/main/resources/golem/wasm/agent_guest.wasm golem/test-agents/.generated/agent_guest.wasm
```

This is normally done by `sbt golemPrepare` but the integration test deploy command needs it in place.

## Common Failures

### `port 9881 is already in use`
A golem server is already running. Kill it: `pkill -f "golem.*server"`

### `GOLEM_TS_PACKAGES_PATH env var or golem.tsPackagesPath system property must be set`
Pass the system property via `javaOptions` in the sbt command (see Running Tests above).

### `Cannot find package '@golem/golem-ts-repl/index.js'`
The TypeScript SDK packages are not built. Build them in the golem repo, or check the path is correct.

### `golem deploy failed after retry`
Check the deploy output for the root cause. Common issues:
- Agent type discovery failure (JavaScript error during WASM initialization)
- Schema mismatch between mount path variables and constructor parameter names
- Missing `.generated/agent_guest.wasm`

### Build reported `[UP-TO-DATE]` but code changed
Delete `golem/test-agents/.golem/` to force a full rebuild.

### TypeScript REPL tests pass but HTTP tests fail (or vice versa)
These are independent. REPL tests use `golem-cli repl` with TS scripts. HTTP tests use direct HTTP calls to port 9006.

### `deleteRecursive` destroying files in external repos
The `golem-temp/repl/ts/node_modules/@golem/` contains symlinks to the TS SDK packages directory. The cleanup code in `GolemServer.layer` checks for symlinks before recursing to avoid deleting symlink targets. **Never use plain `rm -rf` on `golem-temp/`** — always delete symlinks first:

```bash
find golem/test-agents/golem-temp -type l -delete 2>/dev/null
rm -rf golem/test-agents/golem-temp
```

## Verifying Agent Schemas

After deploy, inspect the component to verify constructor and method schemas:

```bash
golem-cli component get scala:examples --local
```

Look for correct parameter names in the output, e.g.:
- `WeatherAgent.getWeather(city: string)` — not `(value: string)`
- `CatalogAgent(region: string, catalog: string)` — case class fields flattened
- `InventoryAgent(arg0: string, arg1: number)` — tuple positional names

## Adding New Tests

### HTTP endpoint test

1. Define agent trait with `@agentDefinition(mount=...)` and `@endpoint(...)` in `golem/test-agents/src/`
2. Add implementation class with `@agentImplementation()`
3. Add agent to `golem/test-agents/golem.yaml` under `httpApi.deployments.local[0].agents`
4. Add test in `GolemExamplesIntegrationSpec.scala`:
   ```scala
   test("http-my-test") {
     for {
       _ <- ZIO.service[GolemServer]
       (status, body) <- httpGet("/api/my-agent/my-key/endpoint")
     } yield assertTrue(status == 200) && assertTrue(body.contains("expected"))
   }
   ```
5. Add to the appropriate test sequence and ensure it's included in the spec

### TypeScript REPL test

1. Create `golem/test-agents/samples/my-test/repl-my-test.ts`
2. Register in the `samples` list in `GolemExamplesIntegrationSpec.scala`
3. The manifest coverage test (`manifest covers all sample scripts`) will fail if scripts exist without being registered
