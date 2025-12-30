# Golem CLI Integration Tests (Template-Style)

This directory documents and scripts an end-to-end integration flow similar in spirit to the upstream Golem
repository's `golem-cli` integration tests (TypeScript/Rust).

## What it does

When enabled, the integration flow:

- Creates a **deterministic** app scaffold (owned by this repo’s tooling; avoids upstream drift)
- Creates a **deterministic** TS component scaffold (owned by this repo’s tooling; avoids upstream template drift)
- Copies the Scala.js bundle into the component (`src/scala-autowired.js`)
- Writes `src/main.ts` from the configured `BridgeSpec`
- Uses `golem-cli` to deploy/invoke/repl and **asserts** expected substrings in the CLI output

## How to run (sbt)

```bash
export GOLEM_SDK_INTEGRATION=1
./golem/integration-tests/run-golem-cli-integration.sh
```

### Cloud mode (optional, gated)

If you have `golem-cli` configured for Golem Cloud, you can run the same integration against cloud by supplying
`GOLEM_CLI_FLAGS`:

```bash
export GOLEM_CLI_FLAGS="--cloud -p my-profile"
export GOLEM_SDK_INTEGRATION=1
./golem/integration-tests/run-golem-cli-integration-cloud.sh
```

Wire-only (no `golem-cli` / no router):

```bash
sbt zioGolemExamplesJS/golemWire
```

The integration scripts above call into the repo’s example quickstart + agent2agent smoke tests.

## Counter agent REPL script (repo-local)

This repo includes a repo-local quickstart REPL script (`golem/quickstart/script-test.rib`) that exercises a few agent calls.

You can run it using the repo-local quickstart script:

```bash
./golem/quickstart/script-test.sh
```

## How to run (Mill)

### Mill plugin end-to-end harness (this repo)

This repo includes a minimal Mill build under `integration-tests/mill-e2e/` that exercises the published Mill plugin
end-to-end (scaffold/wire).

First publish the Mill plugin to **Ivy local** (preferred, avoids hardcoding `~/.m2` repositories):

```bash
sbt golemPublishMillPluginForE2E
```

Then run:

```bash
cd golem/integration-tests/mill-e2e
mill -i agents.golemWire   # just scaffold + wire (no golem-cli needed)
# then deploy via golem-cli from the app dir printed by golemWire
```

## sbt plugin external-style fixture

This repo also includes a minimal sbt build under `integration-tests/sbt-e2e/` that consumes the published sbt plugin
from Ivy local and validates bridge generation without requiring golem runtime.

```bash
sbt golemPublishSbtPluginForE2E
cd golem/integration-tests/sbt-e2e
sbt printBridge
```

## Notes

- **Wire-only** steps (`golemWire` / `printBridge`) do **not** require `golem-cli`.
- **Deploy/invoke/repl** steps use `golem-cli` directly (and a reachable router / `--base` depending on your flags).
- By default, they are **opt-in** so normal CI/dev runs do not require external tooling.




