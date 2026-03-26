# ZIO Golem SDK — Agent Guidelines

## Skills

Load the relevant skill **before** starting work:

- **`zio-golem-development`** — Building, compiling, publishing, and project layout
- **`zio-golem-integration-tests`** — Running and writing integration tests
- **`zio-golem-base-image`** — WIT folder structure and regenerating `agent_guest.wasm`

## Running All Tests

Use these sbt aliases (from the monorepo root) to run all zio-golem tests:

| Alias | What it runs |
|-------|-------------|
| `sbt --client golemTest3` | All unit tests (JVM + JS) + examples compile + integration tests — **Scala 3** |
| `sbt --client golemTest2` | All unit tests (JVM + JS) + examples compile — **Scala 2** |
| `sbt --client golemTestAll` | Both of the above (Scala 3 then Scala 2) |

**Always run `golemTestAll` before considering a change complete.**

## Testing Requirements

Every change **must** include tests. No exceptions.

### Unit / Compile Tests (zio-test)

- All tests use **ZIO Test** (`zio.test._`). Do not use ScalaTest, MUnit, or any other framework.
- Place tests in the appropriate project's `src/test/scala/` directory (e.g., `core/js/src/test/scala/golem/`).
- Follow existing test patterns — look at neighboring spec files before writing new ones.
- **Run the tests** after writing them to confirm they pass. Use:
  ```
  sbt --client '++3.8.2; <project>/test'
  ```

### Test Agents (`golem/test-agents/`)

- Every **user-facing feature** must have a working test agent in `golem/test-agents/src/main/scala/example/`.
- Test agents must compile as part of `zioGolemTestAgents/fastLinkJS`.
- If the feature involves HTTP endpoints, add the agent to `golem/test-agents/golem.yaml`.
- If the feature involves REPL-testable behavior, add a TypeScript REPL script in `golem/test-agents/samples/`.

### Integration Tests (`golem/integration-tests/`)

- Every example **must** have a corresponding integration test in `GolemExamplesIntegrationSpec.scala`.
- Integration tests exercise examples against a real local Golem server.
- **Always run integration tests** after adding or changing them.

#### Before running integration tests, ask the user for:

1. **Path to the `golem` binary** — if `golem` is not already on `PATH`. Default location: `~/.cargo/bin/golem-cli`.
2. **Path to the TypeScript SDK packages directory** — needed for REPL tests. Example: `/home/vigoo/projects/golem/sdks/ts/packages`.

**Never skip running integration tests.** If the required paths are unknown, ask the user — do not silently omit the test run.

#### Running integration tests:

```bash
sbt --client '++3.8.2; set zioGolemIntegrationTests / Test / javaOptions += "-Dgolem.tsPackagesPath=<TS_PACKAGES_PATH>"; zioGolemIntegrationTests/test'
```

Use the standard sbt logging pattern from the root `AGENTS.md`.

## Cross-Version Compatibility

Every feature **must** work with both **Scala 2** and **Scala 3**. Run tests under both versions before considering a change complete. See the root `AGENTS.md` for cross-Scala verification steps.

## Workflow Summary

1. **Implement** the feature or fix in the appropriate module (`core/`, `model/`, `macros/`).
2. **Write unit tests** using ZIO Test. Run them — they must pass.
3. **Add a test agent** in `golem/test-agents/` demonstrating the feature.
4. **Add an integration test** covering the example. Run it — it must pass.
5. Follow the root `AGENTS.md` verify/format phases as usual.
