Examples overview

This directory ships a single Scala.js component (`scala:examples`) and a set of
focused sample TypeScript scripts under `samples/`. Each script exercises a specific
workflow and keeps the code path small.

Implementation details (component templates, build wiring) live in `golem.yaml`
and tracked `components-js/*/golem.yaml` manifests. Keep `golem.yaml` in the
module root (the directory you run `golem-cli` from); `.golem/` is generated.

Scala sources live under `shared/`, `js/`, and `jvm/` (shared traits + JS/JVM implementations).

## Running examples

### Automated (integration tests)

The examples are exercised automatically by the `zioGolemIntegrationTests` sbt project
in `golem/integration-tests/`. The test suite starts a local Golem server, deploys the
component, runs each sample script, and asserts on the output.

Prerequisites:
- `golem` executable on PATH
- `GOLEM_TS_PACKAGES_PATH` env var set to the TypeScript SDK packages directory

```bash
sbt "++3.7.4; zioGolemTestAgents/golemPrepare" "++3.7.4; zioGolemIntegrationTests/test"
```

### Manual

1) Build and deploy the component:

```bash
env -u ARGV0 golem --yes --local --app-manifest-path "$PWD/golem.yaml" deploy
```

2) Invoke a sample with its TypeScript script:

```bash
env -u ARGV0 golem --yes --local --app-manifest-path "$PWD/golem.yaml" \
  repl scala:examples --language typescript --script-file "samples/<sample>/repl-<name>.ts" --disable-stream < /dev/null
```

## Sample scripts

| Sample | Script |
| --- | --- |
| Simple RPC | `samples/simple-rpc/repl-counter.ts` |
| Agent-to-agent | `samples/agent-to-agent/repl-minimal-agent-to-agent.ts` |
| JSON tasks | `samples/json-tasks/repl-json-tasks.ts` |
| Snapshot counter | `samples/snapshot-counter/repl-snapshot-counter.ts` |
| Human-in-the-loop | `samples/human-in-the-loop/repl-human-in-the-loop.ts` |

If you see `The environment was changed concurrently while diffing` during deploy,
retry the deploy command once; it is safe and typically resolves the race.
