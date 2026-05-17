---
id: agent-definition
title: "AgentDefinition"
---

`AgentDefinition[T]` is the compile-time metadata descriptor for an agent type `T`. It encodes the agent's type name, durability mode, constructor, and method signatures. You don't construct `AgentDefinition` directly; the `@agentDefinition` macro generates it automatically.

```scala
type AgentDefinition[T] = golem.runtime.autowire.AgentDefinition[T]
```

## Overview

When you decorate a trait with `@agentDefinition`, the macro (available in both Scala 2.13 and Scala 3) generates:
1. An `AgentDefinition[Trait]` instance describing the agent type
2. RPC request/response handlers
3. WIT schema types and value codecs
4. Agent metadata (name, durability mode, constructor info)

You access the `AgentDefinition` when:
- **Registering an implementation** via `AgentImplementation.registerClass[Trait, Impl]`
- **Querying agent metadata** (type name, methods, parameters)
- **Connecting as a client** via `AgentClient.agentType[Trait]`

## Defining an Agent Type

The simplest agent definition requires only the trait name:

```scala
import golem.runtime.annotations.agentDefinition
import golem.BaseAgent

@agentDefinition
trait Counter extends BaseAgent {
  def increment(): scala.concurrent.Future[Int]
  def get(): scala.concurrent.Future[Int]
}
```

The macro infers:
- **Type name** — From trait name (`Counter`)
- **Durability mode** — Default is `Durable` (state persists)
- **Constructor** — From inner `class Id` (if present)
- **Methods** — All public methods returning `Future[A]`

## Configuration Options

### Mode: Durable vs. Ephemeral

```scala:passthrough mdoc:passthrough
import golem.runtime.annotations.{agentDefinition, DurabilityMode}
import golem.BaseAgent

// Durable (default): state persists, supports snapshots
@agentDefinition(mode = DurabilityMode.Durable)
trait StatefulCounter extends BaseAgent {
  def increment(): scala.concurrent.Future[Int]
}

// Ephemeral: fresh instance per invocation, stateless
@agentDefinition(mode = DurabilityMode.Ephemeral)
trait StatelessCompute extends BaseAgent {
  def compute(x: Int): scala.concurrent.Future[Int]
}
```

**Durable agents** are ideal for:
- Stateful applications (counters, accounts, queues)
- Operations requiring durability guarantees
- Agents using snapshots for persistence

**Ephemeral agents** are ideal for:
- Stateless computations (pure functions)
- Data transformations
- APIs with no persistent state

### Mount: HTTP Endpoint Mounting

Expose agent methods as HTTP endpoints:

```scala:passthrough mdoc:passthrough
import golem.runtime.annotations.agentDefinition
import golem.BaseAgent

@agentDefinition(mount = "/counters/{id}")
trait Counter extends BaseAgent {
  class Id(val id: String)
  def increment(): scala.concurrent.Future[Int]
}
```

With HTTP mounting:
- Constructor parameters become **path variables** (e.g., `{id}` → `id: String`)
- Methods can be exposed as HTTP endpoints using `@endpoint` annotations
- Method parameters become **query strings** or **request body** (depends on the method annotation)

### Custom Type Name

Override the inferred type name:

```scala:passthrough mdoc:passthrough
import golem.runtime.annotations.agentDefinition
import golem.BaseAgent

@agentDefinition(typeName = "custom-counter")
trait Counter extends BaseAgent {
  def get(): scala.concurrent.Future[Int]
}
```

The default type name is the trait name. Override when you need namespace separation (e.g., "my.company.Counter").

### Additional Annotation Parameters

The `@agentDefinition` annotation supports these additional parameters:

| Parameter | Type | Default | Purpose |
|-----------|------|---------|---------|
| `auth` | Boolean | false | Enable authentication enforcement for agent methods |
| `cors` | Array[String] | Array.empty | Allowed CORS origins (e.g., Array("https://example.com")); empty means CORS disabled |
| `phantomAgent` | Boolean | false | Mark this agent as a phantom agent (pre-provisioned instances) |
| `webhookSuffix` | String | "" | Append custom suffix to generated webhook URLs |
| `snapshotting` | String | "disabled" | Snapshot strategy: "disabled", "enabled", "periodic(duration)", or "every(count)" |

**Example with multiple parameters:**

```scala:passthrough mdoc:passthrough
import golem.runtime.annotations.agentDefinition
import golem.BaseAgent

@agentDefinition(
  mode = DurabilityMode.Durable,
  mount = "/api/{id}",
  auth = true,
  cors = Array("https://example.com"),
  snapshotting = "enabled"
)
trait SecureStatefulAgent extends BaseAgent {
  class Id(val id: String)
  def getData(): scala.concurrent.Future[String]
}
```

- **`auth = true`** — Methods require authentication; principal context is available via `@Principal` injection
- **`cors = Array("https://allowed.com")`** — HTTP responses include CORS headers for specified origins; empty array disables CORS
- **`phantomAgent = true`** — Agent instances can be pre-provisioned with phantom IDs (UUIDs)
- **`webhookSuffix`** — Customize webhook URL path (e.g., "v2" produces "/api/{id}/method/v2")
- **`snapshotting = "enabled"`** — Snapshot strategy: "disabled" (no snapshots), "enabled" (after execution), "periodic(duration)" (at regular intervals), or "every(count)" (every N calls)

## Method Annotations

Decorate methods with metadata:

```scala:passthrough mdoc:passthrough
import golem.runtime.annotations.{agentDefinition, description, prompt, endpoint}
import golem.BaseAgent

@agentDefinition
trait MyAgent extends BaseAgent {
  @description("Increment the counter by one")
  def increment(): scala.concurrent.Future[Int]
  
  @prompt("Summarize the state of the agent")
  def summarize(): scala.concurrent.Future[String]
  
  @endpoint("GET", "/api/status")
  def getStatus(): scala.concurrent.Future[String]
}
```

| Annotation | Purpose |
|------------|---------|
| `@description` | Human-readable method description (appears in metadata) |
| `@prompt` | LLM prompt hint (for AI-driven agent invocation) |
| `@endpoint` | HTTP endpoint (method, path) for HTTP mounting |

## Custom Constructor Types

Agents can require constructor parameters by declaring an inner `class Id`:

```scala:passthrough mdoc:passthrough
import golem.runtime.annotations.agentDefinition
import golem.BaseAgent
import zio.blocks.schema.Schema

case class UserId(value: String) derives Schema

@agentDefinition
trait UserAgent extends BaseAgent {
  class Id(val userId: UserId)
  def getName(): scala.concurrent.Future[String]
}
```

Constructor parameters must have `Schema` instances (derive via `derives Schema` or `Schema.derived`).

## Registering an Implementation

Once you have an `AgentDefinition`, register the implementation:

```scala:passthrough mdoc:passthrough
import golem.runtime.annotations.agentImplementation
import golem.runtime.autowire.{AgentDefinition, AgentImplementation}
import golem.BaseAgent

@agentDefinition
trait Counter extends BaseAgent {
  def increment(): scala.concurrent.Future[Int]
}

@agentImplementation()
class CounterImpl() extends Counter {
  private var count = 0
  override def increment(): scala.concurrent.Future[Int] =
    scala.concurrent.Future.successful { count += 1; count }
}

object CounterModule {
  val definition: AgentDefinition[Counter] =
    AgentImplementation.registerClass[Counter, CounterImpl]
}
```

The `registerClass` macro:
1. Validates that `CounterImpl` implements all methods of `Counter`
2. Generates RPC request/response handlers
3. Encodes the schema for all parameter/return types
4. Returns the `AgentDefinition[Counter]` for agent registration

## Querying Agent Type Information

Access metadata from the generated definition:

```scala:passthrough mdoc:passthrough
import golem.runtime.autowire.AgentDefinition

def printAgentInfo[T](defn: AgentDefinition[T]): Unit = {
  println(s"Type: ${defn.typeName}")
  println(s"Methods: ${defn.methodMetadata}")
}
```

## Client-Side Usage

When connecting to a remote agent, use the agent type:

```scala:passthrough mdoc:passthrough
import golem.runtime.rpc.AgentClient

val agentType = AgentClient.agentType[Counter]
// agentType provides: type name, method signatures, schema info
```

## Relation to Other Types

- **`BaseAgent`** — The trait you extend; decorated with `@agentDefinition`
- **`AgentImplementation`** — Used to register implementations and generate RPC handlers
- **`AgentClient`** — Uses the agent type to construct remote client proxies
- **`GolemSchema`** — Encodes/decodes all parameter and return types

## Best Practices

- **Keep agents focused** — One responsibility per agent type
- **Use descriptive names** — Trait names become type names; choose clearly
- **Document with `@description`** — Helps other developers and tooling understand your agent
- **Use `@prompt` for AI agents** — Provides context for LLM-driven invocation
- **Prefer constructor simplicity** — Use primitive/simple types in `Id` class
