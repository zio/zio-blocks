# ZIO-Golem

[![Scala 3](https://img.shields.io/badge/scala-3.3.x-red.svg)](https://www.scala-lang.org/)
[![Scala.js](https://img.shields.io/badge/scala.js-1.20.x-blue.svg)](https://www.scala-js.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**A minimal, type-safe Scala SDK for building Golem agents.**

ZIO-Golem brings the ergonomics of Scala to the Golem platform, enabling you to define agents as simple traits and
automatically derive all the serialization, RPC bindings, and metadata generation at compile time via Scala 3 macros.

## Features

- **Trait-based agent definitions** - Define your agent's interface as a Scala trait with annotated methods
- **Automatic schema derivation** - Derives schemas for component-model serialization
- **Macro-powered autowiring** - Compile-time generation of RPC handlers, WIT types, and metadata
- **Multimodal data support** - First-class support for text and binary segments with MIME/language constraints
- **Transaction helpers** - Both fallible and infallible transaction patterns with automatic rollback
- **Snapshot integration** - Simple hooks for state persistence across component instances

## Quick Start

### Prerequisites

1. **`golem-cli`** installed and on your `PATH`:
   ```bash
   curl -sSf https://join.golem.network/as-requestor | bash -
   ```

2. **Node.js** + **npm** installed (used internally by the component template; users do not write TypeScript)

3. **sbt** installed

4. A reachable Golem router/executor (local or cloud, depending on your `GOLEM_CLI_FLAGS`)

### Define Your Agent

Create a trait describing your agent's interface:

```scala
import cloud.golem.runtime.annotations.{DurabilityMode, agentDefinition, description}
import cloud.golem.sdk.BaseAgent
import zio.blocks.schema.Schema

import scala.concurrent.Future

// Define your data types
final case class Name(value: String)

object Name {
  implicit val schema: Schema[Name] = Schema.derived
}

// Define your agent trait (typeName is optional; when omitted, it is derived from the trait name)
@agentDefinition(mode = DurabilityMode.Durable)
@description("A simple name-processing agent")
trait NameAgent extends BaseAgent {
  type AgentInput = Unit // or a case class with a Schema

  @description("Reverse the provided name")
  def reverse(input: Name): Future[Name]
}
```

### Implement and Register

```scala
import cloud.golem.runtime.autowire._

object NameAgentModule {
  private class NameAgentImpl extends NameAgent {
    override def reverse(input: Name): Name =
      input.copy(value = input.value.reverse)
  }

  // Type name is derived from @agentDefinition(...) on the trait:
  val definition: AgentDefinition[NameAgent] =
    AgentImplementation.register[NameAgent](new NameAgentImpl)
}
```

### Connect as a Client

From another component, connect to a remote agent:

```scala
import cloud.golem.runtime.rpc.AgentClient
import scala.concurrent.Future

val plan = AgentClient.plan[NameAgent] // uses @agentDefinition + NameAgent.AgentInput

// Connect and invoke
val result: Future[NameAgent] = AgentClient.connect(plan, ())
```

### Optional companion ergonomics (Scala-only)

If you want `Shard.get(...)` / `Shard.getPhantom(...)` style ergonomics, Scala requires a companion `object Shard` to exist.
Today this is a one-liner:

```scala
import cloud.golem.runtime.annotations.{DurabilityMode, agentDefinition, agentImplementation, description}
import cloud.golem.runtime.autowire.{AgentDefinition, AgentImplementation}
import cloud.golem.sdk.{AgentCompanion, BaseAgent, Uuid}

import scala.concurrent.Future

@agentDefinition(mode = DurabilityMode.Durable)
trait Shard extends BaseAgent {
  type AgentInput = (String, Int)

  @description("Get a value from the table")
  def get(key: String): Future[Option[String]]

  @description("Set a value in the table")
  def set(key: String, value: String): Unit
}

object Shard extends AgentCompanion[Shard]

@agentImplementation()
final class ShardImpl(input: (String, Int)) extends Shard {
  override def get(key: String): Future[Option[String]] = Future.successful(None)
  override def set(key: String, value: String): Unit = ()
}

object ShardModule {
  val definition: AgentDefinition[Shard] =
    AgentImplementation.register[Shard, (String, Int)](in => new ShardImpl(in))
}

object Example {
  val shard1 = Shard.get("a", 1)
  val shard2 = Shard.getPhantom("a", 1, Uuid.random())
  // shard1.flatMap(_.set("a", "b")) ...
}
```

## Project structure (public vs. internal)

| Module   | Public? | Description |
|----------|---------|-------------|
| `model`  | yes     | Types + schemas + annotations + agent metadata |
| `core`   | yes     | Runtime client/server helpers (RPC, host API, transactions, snapshot helpers) |
| `macros` | yes     | Compile-time derivation (analogous to Rust’s `golem-rust-macro`) |
| `tools`  | no      | Repo-local JVM helpers/tests (not part of the SDK surface) |
| `tooling-core`, `sbt-plugin`, `mill-plugin` | no | Repo-local build/deploy plumbing (implementation detail; not user-facing Scala SDK) |
| `examples`, `host-tests`, `integration-tests` | no | Repo-local verification/harnesses (not user-facing) |

## Documentation

- **[Snapshot helpers](docs/snapshot.md)** - State persistence helpers
- **[Multimodal helpers](docs/multimodal.md)** - Text/binary segment schemas with constraints
- **[Transaction helpers](docs/transactions.md)** - Infallible and fallible transaction patterns
- **[Result helpers](docs/result.md)** - WIT-friendly `Result` type for error handling
- **[Supported versions](docs/supported-versions.md)** - Compatibility matrix

## Building

```bash
# Compile all modules
sbt compile

# Run tests
sbt test

# Build Scala.js bundle for examples
sbt examplesJS/fastLinkJS

# Run host-backed integration tests
GOLEM_HOST_TESTS=1 sbt hostTests/golemHostTests
```

## Key Concepts

### Agent Modes

Agents can operate in different modes:

- **`Durable`** - State persists across invocations (default)
- **`Ephemeral`** - Fresh instance per invocation

### Structured Schemas

ZIO-Golem uses a structured schema system that maps Scala types to WIT (WebAssembly Interface Types):

- **Component** - Standard WIT component-model types (records, enums, lists, etc.)
- **UnstructuredText** - Text with optional language constraints
- **UnstructuredBinary** - Binary data with MIME type constraints
- **Multimodal** - Composite payloads combining multiple modalities

### Annotations

Decorate your traits and methods with metadata:

```scala
import cloud.golem.runtime.annotations.DurabilityMode

@description("Human-readable description")
@prompt("LLM prompt for AI-driven invocation")
@mode(DurabilityMode.Ephemeral) // or DurabilityMode.Durable
trait MyAgent {
...
}
```

## Running on Golem

The sbt/Mill plugins provide a small set of **developer convenience tasks** so you can run your own Golem apps from your build tool:

- **sbt**:
  - `golemLocalUp` / `golemLocalDown`: start/stop a local `golem server run` (when `GOLEM_CLI_FLAGS=--local`)
  - `golemDeploy`: deploy your component (auto-starts local server when in `--local` mode)
  - `golemInvoke`: invoke an agent method (auto-starts local server when in `--local` mode)
  - `golemAppRun`: deploy (optional) then invoke a configured method (`golemRunAgentId` + `golemRunFunction`)
  - `golemAppRunScript <path-to.rib>`: deploy (optional) then run `golem-cli repl --script-file`
- **Mill**:
  - `golemLocalUp()` / `golemLocalDown()`: start/stop a local `golem server run` (when `GOLEM_CLI_FLAGS=--local`)
  - `golemDeploy()` / `golemInvoke()`: deploy/invoke (auto-starts local server when in `--local` mode)
  - `golemAppRun()`: deploy (optional) then invoke a configured method (`golemRunAgentId` + `golemRunFunction`)
  - `golemAppRunScript <path-to.rib>`: deploy (optional) then run `golem-cli repl --script-file`

Configure per-project:

- `golemComponent`: the qualified component name (e.g. `org:component`)
- `golemCliFlags`: defaults to `--local` (or set `GOLEM_CLI_FLAGS="--cloud -p my-profile"` for cloud)

Recommended defaults (copy/paste):

**sbt** (`build.sbt`):

```scala
// Required: which component to deploy
cloud.golem.sbt.GolemPlugin.autoImport.golemComponent := "org:component",

// Optional: "run" convenience (deploy + invoke)
cloud.golem.sbt.GolemPlugin.autoImport.golemRunAgentId := "org:component/my-agent()",
cloud.golem.sbt.GolemPlugin.autoImport.golemRunFunction := "org:component/my-agent.{ping}",
cloud.golem.sbt.GolemPlugin.autoImport.golemRunArgs := Nil,                 // WAVE literals
cloud.golem.sbt.GolemPlugin.autoImport.golemRunDeployFirst := true,         // default true
cloud.golem.sbt.GolemPlugin.autoImport.golemRunPublishFirst := false,       // default false
cloud.golem.sbt.GolemPlugin.autoImport.golemRunPublish := {},              // set to `publishLocal` in monorepo/snapshot workflows

// Optional: "run script" convenience (deploy + repl script)
cloud.golem.sbt.GolemPlugin.autoImport.golemReplDisableStream := true,      // default true
cloud.golem.sbt.GolemPlugin.autoImport.golemReplTimeoutSec := 60            // default 60 (or GOLEM_REPL_TIMEOUT_SEC)
```

Run:

```bash
sbt golemAppRun
sbt "golemAppRunScript path/to/script.rib"
```

**Mill** (`build.mill`):

```scala
object app extends ScalaJSModule with cloud.golem.mill.GolemModule {
  def golemComponent = "org:component"

  // Optional run convenience
  def golemRunAgentId = "org:component/my-agent()"
  def golemRunFunction = "org:component/my-agent.{ping}"
  def golemRunArgs = Seq.empty

  // Optional publish hook (monorepo/snapshot workflows)
  def golemRunPublishFirst = false
  def golemRunPublish = Task {}
}
```

Run:

```bash
mill -i app.golemAppRun
GOLEM_REPL_SCRIPT=path/to/script.rib mill -i app.golemAppRunScript
```

Local server defaults (override if needed):

- `GOLEM_ROUTER_HOST` (default `127.0.0.1`)
- `GOLEM_ROUTER_PORT` (default `9881`)
- `GOLEM_BIN` (default `golem`)
- `golemStartLocalServer := false` to disable auto-start behavior

## Dependencies

- **`zio-blocks-schema`** - Derivation of data types and WIT-compatible codecs
- **[Scala.js](https://www.scala-js.org/)** - Scala to JavaScript compilation

## Contributing

Contributions are welcome! Please ensure your changes:

1. Compile without warnings
2. Pass existing tests
3. Include ScalaDoc for new public APIs
4. Follow the existing code style

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

Built for the [Golem Cloud](https://golem.cloud/) platform. Special thanks to the ZIO ecosystem for the powerful schema
derivation capabilities.
