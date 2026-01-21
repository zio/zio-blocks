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

If you see authentication / login errors (especially when switching between local and remote setups),
reset the local server state:

```bash
golem-cli server run --clean --local
```

## Remote invocation variants (await/trigger/schedule)

All agent methods support three invocation styles. Use `getRemote(...)` plus
`RemoteAgentOps` to access them:

```scala
import golem.{Datetime, RemoteAgentOps}
import golem.RemoteAgentOps.*

val remote = CounterAgent.getRemote("shard-id")

// Await (always invoke-and-await)
remote.flatMap(_.rpc.call_increment())

// Fire-and-forget trigger
remote.flatMap(_.rpc.trigger_increment())

// Schedule (run 5 seconds later)
remote.flatMap(_.rpc.schedule_increment(Datetime.afterSeconds(5)))
```

Notes:

- Works in Scala 2.13 and Scala 3.
- `trigger_*` / `schedule_*` always return `Future[Unit]` by design.
- `remote.api.increment()` still performs the “normal” call style, while
  `remote.rpc.call_increment()` always invokes the await path.
