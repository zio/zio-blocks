# sbt plugin external-style fixture (provider-class BridgeSpec)

This directory is a **minimal sbt build** that acts like an *external repo* consuming the sbt plugin via **Ivy local**.

## What it validates

- The published sbt plugin can be resolved by sbt
- The plugin’s **provider-class BridgeSpec** generator works end-to-end (no golem runtime required)
- This is a **wire-only** check: it does not require `golem-cli`

## How to run

From the repo root:

```bash
sbt golemPublishSbtPluginForE2E
```

Then:

```bash
cd golem/integration-tests/sbt-e2e
sbt printBridge
```

This writes the generated TypeScript bridge to `target/generated-main.ts`.

Notes:
- This fixture includes a provider class in `src/main/scala` which depends on `zio-golem-tooling-core` to reference
  `BridgeSpec`/`AgentSpec` types.

If you’re looking for the recommended, Scala-only configuration path (`golemExports`), see:

- `golem/quickstart` (standalone sbt project; uses `golemExports`)
- `golem/integration-tests/repl-counter-test.sh` (end-to-end deploy + REPL assertions)


