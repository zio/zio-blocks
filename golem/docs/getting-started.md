# Getting started (Scala.js) — create a new project from scratch

This guide shows how to build a **new Scala.js agent project** that can be deployed with `golem-cli`.

You’ll create:

- A Scala.js project producing a single `scala.js` bundle
- A small `golem.yaml` app that tells `golem-cli` how to build + wrap your bundle into a component
- An exported registration entrypoint (`__golemRegisterAgents`) so Golem can discover your `@agentImplementation`s

## Prerequisites

- **`golem-cli`** on your `PATH`
- **Java + sbt**
- **A reachable Golem router/executor**
  - Local: run `golem server run` in another terminal
  - Cloud: configure a cloud profile (e.g. `golem-cli --cloud profile list`)

## 1) Create the Scala.js project

Create these directories:

```bash
mkdir -p scala/project scala/src/main/scala/demo
mkdir -p app/common-scala-js app/components-js/scala-demo/src app/wasm
```

Create `scala/project/plugins.sbt`:

```scala
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.20.2")
```

### Add the Golem SBT plugin (auto-registration)

The Scala.js SDK expects a top-level exported entrypoint named `__golemRegisterAgents` that registers all agent
implementations.

In this repo, we provide a tiny SBT plugin (`project/GolemPlugin.scala`) that generates that entrypoint by scanning for
`@agentImplementation` classes.

For a new project, copy this file into your build:

- From this repo: `project/GolemPlugin.scala`
- To your project: `scala/project/GolemPlugin.scala`

## 2) Add dependencies + enable plugins

Create `scala/build.sbt`:

```scala
import org.scalajs.linker.interface.ModuleKind

ThisBuild / scalaVersion := "3.3.7"

lazy val root = project
  .in(file("."))
  .enablePlugins(org.scalajs.sbtplugin.ScalaJSPlugin, GolemPlugin)
  .settings(
    scalaJSUseMainModuleInitializer := false,
    Compile / scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.ESModule)),
    scalacOptions += "-experimental",
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-golem-core"  % "<SDK_VERSION>",
      "dev.zio" %%% "zio-golem-model" % "<SDK_VERSION>",
      "dev.zio" %% "zio-golem-macros" % "<SDK_VERSION>"
    ),
    // Generate __golemRegisterAgents by scanning demo.* for @agentImplementation
    GolemPlugin.autoImport.golemAutoRegisterAgentsBasePackage := Some("demo")
  )
```

Notes:

- **`<SDK_VERSION>`** should be a released version from Maven Central.
- If you’re hacking **inside this monorepo**, you can use `0.0.0-SNAPSHOT` after running:

```bash
sbt -batch -no-colors -Dsbt.supershell=false golemPublishLocal
```

## 3) Write a minimal agent

Create `scala/src/main/scala/demo/CounterAgent.scala`:

```scala
package demo

import golem.runtime.annotations.{agentDefinition, description, prompt}
import golem.{AgentCompanion, BaseAgent}

import scala.concurrent.Future

@agentDefinition(typeName = "counter-agent")
trait CounterAgent extends BaseAgent {
  final type AgentInput = String

  @prompt("Increase the count by one")
  @description("Increases the count by one and returns the new value")
  def increment(): Future[Int]
}

object CounterAgent extends AgentCompanion[CounterAgent]
```

Create `scala/src/main/scala/demo/CounterAgentImpl.scala`:

```scala
package demo

import golem.runtime.annotations.agentImplementation

import scala.annotation.unused
import scala.concurrent.Future

@agentImplementation()
final class CounterAgentImpl(@unused private val name: String) extends CounterAgent {
  private var count: Int = 0

  override def increment(): Future[Int] =
    Future.successful {
      count += 1
      count
    }
}
```

## 4) Create a `golem.yaml` app with a `scala.js` template

Create `app/golem.yaml`:

```yaml
includes:
- common-*/golem.yaml
- components-*/*/golem.yaml
```

Create `app/common-scala-js/golem.yaml`:

```yaml
templates:
  scala.js:
    build:
    - command: bash ../../build-scalajs.sh {{ component_name }}
    - injectToPrebuiltQuickjs: ../../wasm/agent_guest.wasm
      module: src/scala.js
      moduleWasm: ../../golem-temp/agents/{{ component_name | to_snake_case }}.module.wasm
      into: ../../golem-temp/agents/{{ component_name | to_snake_case }}.dynamic.wasm
    - generateAgentWrapper: ../../golem-temp/agents/{{ component_name | to_snake_case }}.wrapper.wasm
      basedOnCompiledWasm: ../../golem-temp/agents/{{ component_name | to_snake_case }}.dynamic.wasm
    - composeAgentWrapper: ../../golem-temp/agents/{{ component_name | to_snake_case }}.wrapper.wasm
      withAgent: ../../golem-temp/agents/{{ component_name | to_snake_case }}.dynamic.wasm
      to: ../../golem-temp/agents/{{ component_name | to_snake_case }}.static.wasm
    sourceWit: ../../wasm/agent_guest.wasm
    generatedWit: ../../golem-temp/agents/{{ component_name | to_snake_case }}/wit-generated
    componentWasm: ../../golem-temp/agents/{{ component_name | to_snake_case }}.static.wasm
    linkedWasm: ../../golem-temp/agents/{{ component_name | to_snake_case }}.wasm
```

Create `app/components-js/scala-demo/golem.yaml`:

```yaml
components:
  scala:demo:
    template: scala.js
dependencies:
  scala:demo:
```

Create `app/build-scalajs.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

component="${1:-}"
if [[ -z "$component" ]]; then
  echo "usage: $0 <component_name>" >&2
  exit 2
fi

app_dir="$(cd "$(dirname "$0")" && pwd)"
repo_root="$(cd "$app_dir/../.." && pwd)"
component_dir="$PWD"

( cd "$repo_root/scala" && sbt -batch -no-colors -Dsbt.supershell=false \
    "compile" \
    "fastLinkJS" )

bundle="$(
  find "$repo_root/scala/target" -type f -name 'main.js' -path '*fastopt*' -printf '%T@ %p\n' 2>/dev/null \
    | sort -nr \
    | head -n 1 \
    | cut -d' ' -f2- \
    || true
)"
if [[ -z "$bundle" ]]; then
  echo "[scala.js] Could not locate Scala.js bundle under $repo_root/scala/target" >&2
  exit 1
fi

mkdir -p "$component_dir/src"
cp "$bundle" "$component_dir/src/scala.js"
```

Then:

```bash
chmod +x app/build-scalajs.sh
```

## 5) Provide the base guest runtime WASM (`agent_guest.wasm`)

You need a QuickJS-based guest runtime WASM compatible with your Golem server/CLI WIT surface.

- In this repo, you can copy a known-good one:

```bash
cp -f golem/quickstart/app/wasm/agent_guest.wasm app/wasm/agent_guest.wasm
```

## 6) Deploy + invoke

From `app/`:

```bash
env -u ARGV0 golem-cli --local --yes --app-manifest-path "$PWD/golem.yaml" deploy
```

Then run a non-interactive REPL script (recommended for automation):

```rib
let c = counter-agent("demo");
let a = c.increment();
let b = c.increment();
{ a: a, b: b }
```

```bash
env -u ARGV0 golem-cli --local --yes --app-manifest-path "$PWD/golem.yaml" \
  repl scala:demo --script-file repl-counter.rib --disable-stream < /dev/null
```
