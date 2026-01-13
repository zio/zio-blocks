# Getting started (Scala.js)

This repo includes a working Scala.js quickstart and runnable example scripts. This guide shows how to run them end-to-end
using `golem-cli`.

## Prerequisites

- **`golem-cli`** on your `PATH`
- **Java + sbt**
- **A reachable Golem router/executor**
  - Local: run `golem server run` in another terminal
  - Cloud: configure a cloud profile (e.g. `golem-cli --cloud profile list`)

## Run the in-repo Scala.js quickstart (counter agent)

The quickstart lives in:

- **Agent trait**: `golem/quickstart/shared/src/main/scala/golem/quickstart/counter/CounterAgent.scala`
- **Agent implementation**: `golem/quickstart/js/src/main/scala/golem/quickstart/counter/CounterAgentImpl.scala`
- **App manifest**: `golem/quickstart/app/golem.yaml`

### Deploy locally

From the **repo root**:

```bash
GOLEM_CLI_FLAGS="${GOLEM_CLI_FLAGS:---local}"
app_dir="$PWD/golem/quickstart/app"
env -u ARGV0 golem-cli $GOLEM_CLI_FLAGS --yes --app-manifest-path "$app_dir/golem.yaml" deploy
```

### Smoke test (recommended)

From the **repo root**:

```bash
bash golem/quickstart/script-test.sh
```

## Run the in-repo examples (agent-to-agent, JSON, snapshotting, promises, …)

From the **repo root**:

```bash
bash golem/examples/agent2agent-local-repl.sh
bash golem/examples/counter-local-repl.sh
bash golem/examples/json-tasks-local-repl.sh
bash golem/examples/snapshot-counter-local-repl.sh
bash golem/examples/hitl-local-repl.sh
```

The example scripts deploy + run a `.rib` script non-interactively (`< /dev/null`) and fail fast if the REPL output
contains an error.
