# `golem/gettingStarted`

This directory is a **standalone** SBT + Scala.js project that mirrors [`golem/docs/getting-started.md`](../docs/getting-started.md) as closely as possible.

It is intentionally structured like a third-party project:

- `scala/` — Scala.js + SBT build (uses published `zio-golem-*` artifacts + `zio-golem-sbt`)
- `app/` — `golem.yaml` app manifest and build template

## Using from this monorepo (local publish)

From the repository root:

```bash
sbt -batch -no-colors -Dsbt.supershell=false golemPublishLocal
```

Then follow the steps in `golem/docs/getting-started.md`, using this directory as the project root.

