# sbt plugin external-style fixture (manifest + shim generation)

This directory is a **minimal sbt build** that acts like an *external repo* consuming the sbt plugin via **Ivy local**.

## What it validates

- The published sbt plugin can be resolved by sbt
- The plugin can **ensure a BridgeSpec manifest** and **generate the Scala shim** (no golem runtime required)
- This is a **wire-only** check: it does not require `golem-cli`

## How to run

From the repo root:

```bash
sbt golemPublishSbtPluginForE2E
```

Then:

```bash
cd golem/integration-tests/sbt-e2e
sbt printManifest printShim
```

This writes the BridgeSpec manifest to `target/bridge-spec.properties` and logs the generated shim file(s).

Notes:

If you’re looking for the recommended, Scala-only configuration path (`golemExports`), see:

- `golem/quickstart` (standalone sbt project; uses `golemExports`)
- `golem/integration-tests/repl-counter-test.sh` (end-to-end deploy + REPL assertions)


