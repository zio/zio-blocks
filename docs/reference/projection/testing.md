---
id: testing
title: "Testing"
sidebar_label: "Testing"
description: "In-memory helpers for testing projections without SQLite: TestEngine, InMemoryProjectionStore, TestContext, and TestProjectionEngine."
keywords:
  - "Event Sourcing"
  - "Testing"
  - "TestEngine"
  - "In-Memory Store"
  - "Projection Testing"
---

The projection module ships with in-memory implementations for unit tests, in the package `zio.blocks.projection.testing`. No SQLite is required, and nothing is written to disk. `TestEngine` wires a whole engine in one call, `InMemoryProjectionStore` is the store it uses, `TestContext` builds the `ProjectionContext` a handler receives, and `TestProjectionEngine` runs a handler on its own, without an engine.

## TestEngine

`TestEngine` replaces the full JVM wiring for unit and integration tests. It creates an `InMemoryProjectionStore[A]` per projection, an in-memory `EventStore` per source name, starts the underlying `ProjectionEngine`, and manages the whole lifecycle in a single `Scope`:

```scala
object TestEngine {
  def make(projections: Projection[_]*): ZIO[Scope, Nothing, TestEngine]
  def makeWithConfig(config: ProjectionEngineConfig, projections: Projection[_]*): ZIO[Scope, Nothing, TestEngine]
  def apply(projections: Projection[_]*): ZIO[Scope, Nothing, TestEngine] // same as make
}

final class TestEngine private (
  val projections: List[Projection[_]],
  val stores: Map[String, ProjectionStore[_]],
  val engine: ProjectionEngine
) {
  def append[E: Schema](entityId: String, event: E): Task[Unit]
  def append[E: Schema](entityId: String, event: E, source: String): Task[Unit]
  def query[A](projection: Projection[A], id: String): Task[Option[A]]
  def queryByName[A](specName: String, id: String): Task[Option[A]]
  def underlying: ProjectionEngine // same as engine
}
```

`make` uses the default `ProjectionEngineConfig`; `makeWithConfig` takes one of your own, and its `ringCapacity` sets the size of each source's `Hub`. The engine runs until the enclosing `Scope` closes.

```scala mdoc:compile-only
import zio.*
import zio.blocks.projection.*
import zio.blocks.projection.testing.TestEngine
import zio.blocks.schema.{Modifier, Schema}

case class UserProfile(@Modifier.id id: String, name: String, email: String)
object UserProfile {
  implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
  implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
}
case class UserCreated(name: String, email: String)
object UserCreated {
  implicit val schema: Schema[UserCreated] = Schema.derived[UserCreated]
}

val projection = Projection[UserProfile]("userProfiles")
  .from("users").routeToSelf
  .on[UserCreated]
  .insert((e, ctx) => UserProfile(ctx.entityId, e.name, e.email))

val test: ZIO[Scope, Throwable, Unit] = for {
  engine <- TestEngine.make(projection)
  _      <- engine.append("user-1", UserCreated("Alice", "alice@example.com"))
  result <- engine.query(projection, "user-1")
  // result == Some(UserProfile("user-1", "Alice", "alice@example.com"))
} yield ()
```

The two-argument `append` picks the source for you. With a single source the event goes to it. With several sources it goes to every source whose bindings declare a handler for the event's runtime class, or to all sources if none does. To direct an event to one source by name, use the three-argument `append(entityId, event, source)`; it fails if there is no source with that name.

`query` reads a row back by projection and ID, and `queryByName` does the same by the projection's name. The underlying `ProjectionEngine` is available as `engine.engine`, and the in-memory store of each projection as `engine.stores`, keyed by projection name.

:::caution
Processing is asynchronous. The engine applies events on background fibers, so a query issued immediately after `append` may return `None`. In tests, poll with a retry loop until the expected row appears. The projection examples do this with a small `pollUntil` helper, in `projection-examples/src/main/scala/projection/examples/Polling.scala`.
:::

## InMemoryProjectionStore

A `ProjectionStore[A]` backed by a `Ref[Map[String, A]]`. It supports every store operation: insert, upsert, `updateFields`, delete, truncate, the watermark and the schema hash. A `FieldUpdate` is applied by decoding the stored row into a `DynamicValue`, changing the field, and encoding it back, so counters behave as they do in SQLite.

```scala
object InMemoryProjectionStore {
  def make[A: Schema: EntityPath]: Task[InMemoryProjectionStore[A]]
  def makeWithRefs[A: Schema: EntityPath](
    map: Map[String, A] = Map.empty,
    seq: Long = 0L,
    hash: Option[String] = None
  ): Task[InMemoryProjectionStore[A]]
}
```

`make` creates an empty store. `makeWithRefs` creates one with rows, a watermark and a schema hash already in it, for a test that starts from a known state.

```scala mdoc:compile-only
import zio.*
import zio.blocks.projection.*
import zio.blocks.projection.testing.InMemoryProjectionStore
import zio.blocks.schema.{Modifier, Schema}

case class UserProfile(@Modifier.id id: String, name: String, email: String)
object UserProfile {
  implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
  implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
}

val test: ZIO[Any, Throwable, Unit] = for {
  store <- InMemoryProjectionStore.make[UserProfile]
  _     <- store.insert(UserProfile("u1", "Alice", "alice@example.com"))
  alice <- store.findById("u1")
  // alice == Some(UserProfile("u1", "Alice", "alice@example.com"))
} yield ()
```

## TestContext

A factory for the `ProjectionContext` values that handlers receive, so a handler can be called directly in a test:

```scala
object TestContext {
  def make(
    entityId: String = "test-1",
    seq: Long = 0L,
    timestamp: Instant = Instant.now(),
    sourceEntityId: Option[String] = None
  ): ProjectionContext
  def makeWithSource(entityId: String, sourceEntityId: String): ProjectionContext
  def withSeq(entityId: String, seq: Long): ProjectionContext
  def withTimestamp(entityId: String, timestamp: Instant): ProjectionContext
}
```

```scala mdoc:compile-only
import zio.blocks.projection.testing.TestContext
import java.time.Instant

val ctx1 = TestContext.make(entityId = "user-1")
// ProjectionContext("user-1", now, 0, None)

val ctx2 = TestContext.makeWithSource(entityId = "user-1", sourceEntityId = "src-1")
// ProjectionContext("user-1", now, 0, Some("src-1"))

val ctx3 = TestContext.withSeq(entityId = "user-1", seq = 42L)
// ProjectionContext("user-1", now, 42, None)

val ctx4 = TestContext.withTimestamp(entityId = "user-1", timestamp = Instant.EPOCH)
// ProjectionContext("user-1", 1970-01-01T00:00:00Z, 0, None)
```

## TestProjectionEngine

`TestProjectionEngine` runs handlers without an engine, a hub or a scope. It calls the projection's handler for an event and, if you give it a store, applies the returned action to that store at once. Because nothing happens in the background, there is nothing to wait for, which makes it the simplest way to test what a handler does:

```scala
object TestProjectionEngine {
  def make: TestProjectionEngine
}

class TestProjectionEngine {
  def processEvent[E, A](spec: Projection[A], event: E, ctx: ProjectionContext): Task[ProjectionAction[A]]
  def processEvents[E, A](spec: Projection[A], events: List[(E, ProjectionContext)]): Task[Unit]
  def processEvents[E, A](spec: Projection[A], events: List[(E, ProjectionContext)], store: ProjectionStore[A]): Task[Unit]
  def processEventsWithActions[E, A](spec: Projection[A], events: List[(E, ProjectionContext)]): Task[List[ProjectionAction[A]]]
  def applyAction[A](store: ProjectionStore[A], action: ProjectionAction[A], ctx: ProjectionContext): Task[Unit]
  def processAndApply[E, A](spec: Projection[A], event: E, ctx: ProjectionContext, store: ProjectionStore[A]): Task[ProjectionAction[A]]
}
```

- `processEvent` returns the action the handler produces, or `Noop` when no handler matches. It does not touch a store.
- `processAndApply` does the same and then applies the action to the store, returning the action. `processEvents` with a store does this for a list of events, and `processEventsWithActions` collects the actions of a list without applying them.
- `applyAction` applies one action to a store the way the engine would: `Insert` and `Upsert` write the row, `Update` changes fields of the row with `ctx.entityId`, `Delete` removes that row, `Truncate` empties the store, and `Noop` does nothing.

```scala mdoc:compile-only
import zio.*
import zio.blocks.projection.*
import zio.blocks.projection.testing.{InMemoryProjectionStore, TestContext, TestProjectionEngine}
import zio.blocks.schema.{Modifier, Schema}

case class UserProfile(@Modifier.id id: String, name: String, email: String)
object UserProfile {
  implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
  implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
}
case class UserCreated(name: String, email: String)
object UserCreated {
  implicit val schema: Schema[UserCreated] = Schema.derived[UserCreated]
}

val projection = Projection[UserProfile]("userProfiles")
  .from("users").routeToSelf
  .on[UserCreated]
  .insert((e, ctx) => UserProfile(ctx.entityId, e.name, e.email))

val test: ZIO[Any, Throwable, Unit] = for {
  store  <- InMemoryProjectionStore.make[UserProfile]
  runner  = TestProjectionEngine.make
  action <- runner.processAndApply(projection, UserCreated("Alice", "alice@example.com"), TestContext.make("user-1"), store)
  // action == ProjectionAction.Insert(UserProfile("user-1", "Alice", "alice@example.com"))
  alice  <- store.findById("user-1")
  // alice == Some(UserProfile("user-1", "Alice", "alice@example.com"))
} yield ()
```

## See Also

- [Projection module overview](./index.md) — how `Projection[A]`, `ProjectionEngine`, the stores and the test helpers fit together.
- [Projection[A]](./projection.md) — the descriptor the helpers run: sources, routing, handlers and `ProjectionAction`.
- [ProjectionEngine](./projection-engine.md) — the runtime that `TestEngine` wraps, and the SQLite stores used outside tests.
