# `golem/gettingStarted`

This directory is a **standalone** Scala.js + SBT + Golem app that mirrors
[`golem/docs/getting-started.md`](../docs/getting-started.md) as closely as possible.

It is intentionally structured like a third-party project, using the **app-root** layout:

- `golem.yaml` + `common-scala-js/` + `components-js/` + `wasm/` - the Golem app root (what `golem-cli` consumes)
- `scala/` - the Scala.js + SBT build (uses published `zio-golem-*` artifacts + `zio-golem-sbt`)

## Using from this monorepo (local publish)

From the repository root:

```bash
sbt -batch -no-colors -Dsbt.supershell=false golemPublishLocal
```

Then you can run this example end-to-end:

```bash
bash golem/gettingStarted/run.sh
```
