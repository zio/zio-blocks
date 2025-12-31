# Getting started (Scala.js)

This is a minimal, `golem-cli`-driven workflow for running Scala.js agents.

## Prerequisites

- **`golem-cli`** on your `PATH`
- **Java + sbt** (or Mill)
- **A reachable Golem router/executor**
  - Local: run `golem server run` in another terminal
  - Cloud: configure a cloud profile (e.g. `golem-cli --cloud profile list`)

## 1) Create a Scala.js agent project

Create these directories:

```bash
mkdir -p scala/project scala/src/main/scala/demo
mkdir -p app/common-scala-js app/components-js/scala-demo/src app/wasm
```

Create `scala/project/plugins.sbt` with the following contents:

```scala
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.20.1")
addSbtPlugin("dev.zio" % "zio-golem-sbt-plugin" % "<SDK_VERSION>")
```

Create `scala/build.sbt` with the following contents:

```scala
import org.scalajs.linker.interface.ModuleKind

ThisBuild / scalaVersion := "3.7.4"

lazy val root = project
  .in(file("."))
  .enablePlugins(org.scalajs.sbtplugin.ScalaJSPlugin, cloud.golem.sbt.GolemPlugin)
  .settings(
    scalaJSUseMainModuleInitializer := false,
    Compile / scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.ESModule)),
    scalacOptions += "-experimental",
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-golem-core"  % "<SDK_VERSION>",
      "dev.zio" %%% "zio-golem-model" % "<SDK_VERSION>",
      "dev.zio" %% "zio-golem-macros" % "<SDK_VERSION>"
    ),
    cloud.golem.sbt.GolemPlugin.autoImport.golemBundleFileName := "scala.js",
    cloud.golem.sbt.GolemPlugin.autoImport.golemAgentGuestWasmFile :=
      (baseDirectory.value / ".." / "app" / "wasm" / "agent_guest.wasm").getCanonicalFile
  )
```

## 2) Write a minimal agent

Create `scala/src/main/scala/demo/CounterAgent.scala` with the following contents:

```scala
package demo

import cloud.golem.runtime.annotations.{agentDefinition, description, prompt}
import cloud.golem.sdk.{AgentCompanion, BaseAgent}

import scala.concurrent.Future

@agentDefinition("counter-agent")
trait CounterAgent extends BaseAgent {
  final type AgentInput = String

  @prompt("Increase the count by one")
  @description("Increases the count by one and returns the new value")
  def increment(): Future[Int]
}

object CounterAgent extends AgentCompanion[CounterAgent]
```

Create `scala/src/main/scala/demo/CounterAgentImpl.scala` with the following contents:

```scala
package demo

import cloud.golem.runtime.annotations.agentImplementation

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

## 3) Create a `golem.yaml` app with a `scala.js` template

Create `app/golem.yaml` with the following contents:

```yaml
includes:
- common-*/golem.yaml
- components-*/*/golem.yaml
```

Create `app/common-scala-js/golem.yaml` with the following contents:

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

Create `app/components-js/scala-demo/golem.yaml` with the following contents:
Create `app/components-js/scala-demo/golem.yaml` with the following contents:

```yaml
components:
  scala:demo:
    template: scala.js
dependencies:
  scala:demo:
```

Create `app/build-scalajs.sh` with the following contents:

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
    "golemEnsureAgentGuestWasm" \
    "golemEnsureBridgeSpecManifest" \
    "golemGenerateScalaShim" \
    "fastLinkJS" )

bundle="$(
  find "$repo_root/scala/target" -type f -name 'main.js' -path '*fastopt*' -printf '%T@ %p\n' 2>/dev/null \
    | sort -nr \
    | head -n 1 \
    | cut -d' ' -f2- \
    || true
)"
if [[ -z "$bundle" ]]; then
  bundle="$(
    find "$repo_root/scala/target" -type f -name 'main.js' -path '*fullopt*' -printf '%T@ %p\n' 2>/dev/null \
      | sort -nr \
      | head -n 1 \
      | cut -d' ' -f2- \
      || true
  )"
fi
if [[ -z "$bundle" ]]; then
  echo "[scala.js] Could not locate Scala.js bundle under $repo_root/scala/target" >&2
  exit 1
fi

mkdir -p "$component_dir/src"
cp "$bundle" "$component_dir/src/scala.js"
```

## 4) Prepare the base WASM once

`golem-cli` reads `sourceWit` / `injectToPrebuiltQuickjs` inputs **before** it runs any build steps, so ensure the
prebuilt guest runtime exists:

```bash
cd scala
sbt -batch -no-colors -Dsbt.supershell=false golemEnsureAgentGuestWasm
cd ..
```

## Deploy + invoke

From `app/`:

```bash
golem-cli --local --yes app deploy scala:demo
golem-cli --local --yes agent invoke 'scala:demo/counter-agent(\"demo\")' 'scala:demo/counter-agent.{increment}'
```
