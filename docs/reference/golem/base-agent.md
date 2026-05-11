---
id: base-agent
title: "BaseAgent"
---

`BaseAgent` is the foundational trait that all Golem agents must extend. It provides access to agent identity information at runtime and serves as the type marker for agent definitions.

```scala mdoc:compile-only
trait BaseAgent {
  final def agentId: String = ???
  final def agentType: String = ???
  final def agentName: String = ???
}
```

## Overview

Every agent in ZIO-Golem implements `BaseAgent`. The trait is parameterless and provides three read-only fields injected by the Golem runtime:

| Field | Type | Purpose |
|-------|------|---------|
| `agentId` | `String` | Unique identifier for this agent instance |
| `agentType` | `String` | Type name from `@agentDefinition` |
| `agentName` | `String` | Human-readable name (derived from trait name) |

These fields are populated by the Golem host when an agent is instantiated.

**Important:** `BaseAgent` and all agent implementations are **Scala.js-only**. Agents must be compiled to WebAssembly. In `build.sbt`, use `%%%` (Scala.js cross-build) instead of `%%` when adding ZIO-Golem dependencies:

```scala
libraryDependencies += "dev.zio" %%% "zio-golem-core" % "@VERSION@"
libraryDependencies += "dev.zio" %%% "zio-golem-macros" % "@VERSION@"
```

## Defining an Agent

To create an agent, extend `BaseAgent` and decorate the trait with `@agentDefinition`:

```scala
import golem.runtime.annotations.agentDefinition
import golem.BaseAgent
import scala.concurrent.Future

@agentDefinition
trait MyAgent extends BaseAgent {
  def someMethod(): Future[String]
}
```

## Constructor Parameters

Agent constructor parameters are declared via an inner `class Id`:

```scala
import golem.runtime.annotations.agentDefinition
import golem.BaseAgent
import scala.concurrent.Future

@agentDefinition
trait Shard extends BaseAgent {
  class Id(val tableName: String, val shardId: Int)
  
  def getValue(key: String): Future[Option[String]]
}
```

The `Id` class defines what values the agent receives at construction time. When mounting over HTTP, these parameters become path variables:

```scala
@agentDefinition(mount = "/api/{tableName}/{shardId}")
trait Shard extends BaseAgent {
  class Id(val tableName: String, val shardId: Int)
  def getValue(key: String): Future[Option[String]]
}
```

## Agent Identity Access

Within an agent method, use the inherited fields to inspect the running agent:

```scala
import golem.runtime.annotations.agentImplementation
import golem.BaseAgent
import scala.concurrent.Future

@agentImplementation()
class MyAgentImpl() extends MyAgent {
  override def someMethod(): Future[String] = {
    val id = agentId
    val tpe = agentType
    val name = agentName
    
    Future.successful(s"Agent $name (type=$tpe, id=$id)")
  }
}
```

## Durability Modes

Agents can operate in two modes, specified via `@agentDefinition`:

```scala
import golem.runtime.annotations.{agentDefinition, DurabilityMode}
import golem.BaseAgent

// Default: Durable (state persists across invocations)
@agentDefinition(mode = DurabilityMode.Durable)
trait DurableAgent extends BaseAgent {
  def getState(): scala.concurrent.Future[String]
}

// Ephemeral (fresh instance per invocation)
@agentDefinition(mode = DurabilityMode.Ephemeral)
trait EphemeralAgent extends BaseAgent {
  def compute(): scala.concurrent.Future[Int]
}
```

**Durable agents** retain state across invocations and support snapshots and durability guarantees. **Ephemeral agents** are stateless and suitable for stateless computations.

## HTTP Mounting

When `mount` is specified in `@agentDefinition`, the agent becomes accessible via HTTP:

```scala
import golem.runtime.annotations.agentDefinition
import golem.BaseAgent

@agentDefinition(mount = "/orders/{orderId}")
trait OrderAgent extends BaseAgent {
  class Id(val orderId: String)
  
  def getStatus(): scala.concurrent.Future[String]
  def cancel(): scala.concurrent.Future[Unit]
}
```

Constructor parameters (`orderId`) become HTTP path variables. Method parameters become query strings or request body fields.

## Relation to Other Types

- **`@agentDefinition`** — Annotation placed on the trait to mark it as an agent type
- **`AgentDefinition[T]`** — Generated type-level metadata describing agent T
- **`BaseAgent`** — The trait you extend; provides `agentId`, `agentType`, `agentName`
- **`AgentImplementation`** — Registers your implementation; generates RPC handlers

The typical flow is:

1. Define trait extending `BaseAgent` (decorated with `@agentDefinition`)
2. Implement the trait (decorated with `@agentImplementation`)
3. Register via `AgentImplementation.registerClass[Trait, Impl]`
4. Macros generate: RPC handlers, schema codecs, WIT types, metadata

## Best Practices

- **Use descriptive agent names** — The trait name becomes the agent type name; choose clearly
- **Keep constructor simple** — Golem will serialize/deserialize the `Id` values; use primitive types where possible
- **Access identity sparingly** — `agentId`, `agentType`, `agentName` are for debugging/logging; don't build business logic on them
- **Prefer Future-returning methods** — Methods return `Future[A]`, which Golem uses to manage async execution and durability
