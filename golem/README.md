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

1. **`golem-cli`** installed and on your `PATH` (see the official Golem Cloud docs for installation).

2. **sbt** installed

3. A reachable Golem router/executor (local or cloud, depending on your `GOLEM_CLI_FLAGS`)

### Define Your Agent

Create a trait describing your agent's interface:

```scala
import golem.runtime.annotations.{DurabilityMode, agentDefinition, description}
import golem.BaseAgent
import zio.blocks.schema.Schema

import scala.concurrent.Future

// Define your data types
final case class Name(value: String)

object Name {
  implicit val schema: Schema[Name] = Schema.derived
}

// Note on custom types:
// - The SDK requires a `golem.data.GolemSchema[T]` for any input/output types used in agent methods.
// - You typically do NOT define `GolemSchema` yourself; it is derived automatically from `zio.blocks.schema.Schema`.
// - If you see a compile error like "Unable to summon GolemSchema ...", add/derive an implicit `Schema[T]` instead.
//   Scala 3: `final case class MyType(...) derives Schema`
//   Scala 2: `implicit val schema: Schema[MyType] = Schema.derived`

// Define your agent trait (typeName is optional; when omitted, it is derived from the trait name)
@agentDefinition(mode = DurabilityMode.Durable)
@description("A simple name-processing agent")
trait NameAgent extends BaseAgent[Unit] {

  @description("Reverse the provided name")
  def reverse(input: Name): Future[Name]
}
```

### Implement and Register

```scala
import golem.runtime.autowire._

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
import golem.runtime.rpc.AgentClient
import scala.concurrent.Future

val agentType = AgentClient.agentType[NameAgent] // uses @agentDefinition + NameAgent input type

// Connect and invoke
val result: Future[NameAgent] = AgentClient.connect(agentType, ())
```

### Optional companion ergonomics (Scala-only)

If you want `Shard.get(...)` / `Shard.getPhantom(...)` style ergonomics, Scala requires a companion `object Shard` to exist.
Today this is a one-liner:

```scala
import golem.runtime.annotations.{DurabilityMode, agentDefinition, agentImplementation, description}
import golem.runtime.autowire.{AgentDefinition, AgentImplementation}
import golem.{AgentCompanion, BaseAgent, Uuid}

import scala.concurrent.Future

@agentDefinition(mode = DurabilityMode.Durable)
trait Shard extends BaseAgent[(String, Int)] {

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
| `examples` | no | Repo-local verification/harnesses (not user-facing) |

## Documentation

- **[Getting started](gettingStarted/README.md)** - Minimal end-to-end project setup (Scala.js + golem-cli)
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
import golem.runtime.annotations.DurabilityMode

@description("Human-readable description")
@prompt("LLM prompt for AI-driven invocation")
@agentDefinition(mode = DurabilityMode.Ephemeral) // or DurabilityMode.Durable
trait MyAgent {
...
}
```

## Running on Golem

The Scala sbt/Mill plugins are **build adapters**: they generate the Scala.js bundle plus internal registration/shim artifacts used by the Scala.js guest runtime.

**`golem-cli` is the driver** for scaffolding/build/deploy/invoke/repl (this matches how golem apps are intended to be operated).

### Base guest runtime (agent_guest.wasm)

The QuickJS guest runtime (`agent_guest.wasm`) is an SDK artifact, not a user project file. The sbt/Mill plugins embed a known-good copy and automatically write it to `golem-temp/agent_guest.wasm` when you compile or link Scala.js.

To regenerate the base runtime (when upgrading Golem/WIT versions):

```bash
./golem/tools/generate-agent-guest-wasm.sh v1.4.1
```

This script expects a local checkout of the Golem WIT definitions at:
`golem/tools/wit-<tag>/wit` (or set `GOLEM_WIT_DIR` to a custom path).

That script stages a deterministic WIT package using `golem/tools/agent-wit/agent.wit` plus its dependencies, then runs `wasm-rquickjs generate-wrapper-crate` (with `--js-modules 'user=@composition'`) and `cargo component build`. This mirrors the TypeScript flow: wrapper crate → component build → compose in golem-cli.

The base WIT definition is owned by the SDK (`golem/tools/agent-wit/agent.wit`), so user projects do not need a local "base WIT directory".

### sbt (example)

In a Golem app manifest (`golem.yaml`), define a Scala template (e.g. `template: scala.js`) whose first build step
invokes sbt to compile Scala to a single JS module, then run the standard QuickJS wrapping steps unchanged.

Then run golem-cli from the app directory:

```bash
GOLEM_CLI_FLAGS="${GOLEM_CLI_FLAGS:---local}"
cd <your-app-dir>
env -u ARGV0 golem-cli $GOLEM_CLI_FLAGS --yes app deploy org:component
env -u ARGV0 golem-cli $GOLEM_CLI_FLAGS --yes repl org:component --disable-stream
```

### Mill (example)

The same approach applies: the `scala.js` template can invoke `mill` instead of `sbt` to produce the JS module.

### Golem AI provider dependencies

Golem AI exposes a unified API, but you must still add the provider WASM as a
component dependency in your app manifest. Add a `dependencies:` section under
your component entry in `components-js/<component>/golem.yaml`.

Example (Ollama provider for LLMs):

```yaml
components:
  scala:demo:
    templates: scala.js
    dependencies:
    - type: wasm
      url: https://github.com/golemcloud/golem-ai/releases/download/v0.4.0/golem_llm_ollama.wasm
```

## Host API surface (Scala.js)

The Scala SDK exposes host APIs in two layers:

1) **Typed Scala wrapper**: `golem.HostApi` (idiomatic Scala helpers over `golem:api/host@1.3.0`).
2) **Raw host modules** (forward‑compatible, mirrors JS/WIT surface):
   - `golem.host.OplogApi` → `golem:api/oplog@1.3.0`
   - `golem.host.ContextApi` → `golem:api/context@1.3.0`
   - `golem.host.DurabilityApi` → `golem:durability/durability@1.3.0`
   - `golem.host.Rdbms` → `golem:rdbms/*@0.0.1`

Example (typed `HostApi`):

```scala
import golem.HostApi

val begin = HostApi.markBeginOperation()
// ... do work ...
HostApi.markEndOperation(begin)
```

Example (raw module access):

```scala
import golem.host.{ContextApi, Rdbms}

val span = ContextApi.startSpan("my-span")
val pg   = Rdbms.postgresRaw // raw JS/WIT module (use provider-specific API)
```

Notes:

- Raw modules intentionally return `Any` to avoid leaking `scala.scalajs.js.*` in public APIs.
- If you need structured helpers, prefer `HostApi` where available; raw modules provide parity with TS/Rust when helpers are not yet wrapped.

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
