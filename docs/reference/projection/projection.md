---
id: projection
title: "Projection[A]"
sidebar_label: "Projection[A]"
description: "Declarative descriptor for an event-sourced read model: defines event sources, routing, and handlers."
keywords:
  - "Event Sourcing"
  - "Read Models"
  - "Handler Routing"
  - "Entity Path"
  - "Projection Declaration"
---

`Projection[A]` is the declarative description of a read model `A` built from events. It encodes which event sources to listen to, how to route each event to a specific entity or shard, and what each event type does to the read model's shape. A `Projection` is pure data — it carries no effects and makes no I/O calls; a `ProjectionEngine` (or `TestEngine` for tests) reads it and drives the actual event processing. See the [projection overview](./index.md) for a guide to how all the module's types work together.

```scala
final class Projection[A] private[projection](
  val name: String,
  val schema: Schema[A],
  val entityPath: Option[EntityPath[A]],
  val isGlobal: Boolean,
  val bindings: List[SourceBinding[?, A]]
)
```

The `name` field doubles as the store file name. `entityPath` is `None` only for global projections created with `Projection.global`. `bindings` is the ordered list of source/routing/handler groups accumulated through the fluent builder API; every builder method returns a new `Projection[A]` rather than mutating the existing one.

## Creating a Projection

The `Projection` companion object provides three factory methods, all of which return a fresh, binding-free instance ready for the builder chain:

```scala
object Projection {
  def apply[A: Schema: EntityPath](name: String): Projection[A]
  def global[A: Schema](name: String): Projection[A]
  def apply[A](name: String, entityPath: EntityPath[A])(using schema: Schema[A]): Projection[A]
}
```

### Per-Entity Projection

`Projection[A](name)` creates a projection whose data is partitioned by entity ID. It requires both a `Schema[A]` and an `EntityPath[A]` in implicit scope — the schema describes the read model's structure, while the entity path tells the engine where to keep the SQLite files:

```scala
case class UserProfile(@Modifier.id id: String, name: String, email: String)
object UserProfile {
  implicit val schema: Schema[UserProfile]         = Schema.derived
  implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived
}

val proj = Projection[UserProfile]("userProfiles")
```

### Global Projection

`Projection.global[A](name)` creates a projection with one shared store for all events — useful for global totals and counters. Only `Schema[A]` is required; no `EntityPath` is needed because the data is not partitioned by entity:

```scala
case class DailyStats(@Modifier.id date: String, userCount: Int, repoCount: Int)
implicit val schema: Schema[DailyStats] = Schema.derived

val proj = Projection.global[DailyStats]("dailyStats")
```

### Explicit Entity Path

`Projection[A](name, entityPath)` accepts an `EntityPath[A]` value directly, bypassing implicit lookup. This is useful when the same type serves multiple projection layouts, each with a different folder structure:

```scala
val customPath = EntityPath[Order]("custom_orders", "orderId")
val proj       = Projection[Order]("orders", customPath)
```

## Entity Paths

`EntityPath[A]` describes where a projection's SQLite files live and which field of `A` holds the entity identifier. It is required for per-entity and cross-entity projections; global projections do not use it.

```scala
trait EntityPath[A] {
  def basePath: String       // folder name, e.g. "users"
  def entityIdField: String  // field name, e.g. "userId"
}
```

The engine uses `basePath` to construct the path to each SQLite file and `entityIdField` to locate the primary-key column in `A`'s schema. Both fields are exposed as read-only members and are set at construction time.

### Manual Construction

`EntityPath.apply[A]` accepts explicit strings for both fields, giving full control over the folder name and the id field without relying on derivation:

```scala
val ep = EntityPath[OrderRecord]("orders", "orderId")
```

### Derived Construction

`EntityPath.derived[A]` inspects `Schema[A]` and applies a four-step algorithm to produce both values automatically:

1. Find the id field: prefer the field annotated `@Modifier.id`, then fall back to any field named `"id"`.
2. Use that field's name as `entityIdField`.
3. Strip the `"Id"` suffix (case-insensitive) from the field name.
4. Convert the result to `snake_case` and pluralize it to obtain `basePath`.

The table below shows the outcome for three representative types:

| Type                                                            | Id Field   | Derived `basePath` |
|-----------------------------------------------------------------|------------|--------------------|
| `case class UserAccount(@Modifier.id userId: String, ...)`      | `userId`   | `"users"`          |
| `case class OrderRecord(@Modifier.id orderId: String, ...)`     | `orderId`  | `"orders"`         |
| `case class Item(@Modifier.id itemId: String, ...)`             | `itemId`   | `"items"`          |

A concrete derivation call looks like this:

```scala
implicit val ep: EntityPath[OrderRecord] = EntityPath.derived[OrderRecord]
// basePath      = "orders"
// entityIdField = "orderId"
```

### Overriding the Folder Name

`EntityPath.derived[A](pathOverride)` keeps the standard id-field derivation but substitutes an explicit `basePath`, which is useful when the conventional pluralized name conflicts with an existing schema:

```scala
implicit val ep: EntityPath[OrderRecord] = EntityPath.derived[OrderRecord]("custom_orders")
// basePath      = "custom_orders"
// entityIdField = "orderId"   (still derived normally)
```

### The `@path` Annotation

Placing `@path("folder")` on a case class type overrides the derived folder name at the declaration site rather than at each call to `derived`:

```scala
import zio.blocks.projection.path

@path("inventory")
case class Stock(@Modifier.id itemId: String, quantity: Int)

implicit val ep: EntityPath[Stock] = EntityPath.derived[Stock]
// basePath      = "inventory"   (annotation overrides the derived "stocks")
// entityIdField = "itemId"
```

The annotation takes precedence over the derivation algorithm, but not over an explicit `pathOverride` argument — when `pathOverride` is provided to `EntityPath.derived[A](pathOverride)`, it is used unconditionally and the annotation is ignored.

## Binding Event Sources

`from(sourceName)` opens a new source binding and returns a `FromBuilder[A]`. One of the four routing methods on `FromBuilder` must be called to close the binding and return a new `Projection[A]`; subsequent `on[E]` handler calls then attach to that binding:

```scala
final class Projection[A] {
  def from(sourceName: String): FromBuilder[A]
}

final class FromBuilder[A] {
  def routeToSelf: Projection[A]
  def routeToAll: Projection[A]
  def routedBy[E](selector: E => String): Projection[A]
  def routedByField[E](selector: E => Any): Projection[A]   // inline macro
}
```

Each routing call appends one `SourceBinding` to `bindings`. Multiple `from()` calls can appear on the same `Projection`, each with its own routing mode and its own set of handlers.

### `routeToSelf`

`routeToSelf` routes each event to the store for the entity it was recorded under — the entity whose ID matches `ctx.entityId`. This is the standard choice for projections that track each entity's own history:

```scala
Projection[UserProfile]("userProfiles")
  .from("users").routeToSelf
  .on[UserCreated].insert((e, ctx) => UserProfile(ctx.entityId, e.name, e.email))
```

### `routeToAll`

`routeToAll` sends every event from this source to the same shared store, ignoring entity IDs entirely. It is typically used with `Projection.global[A]` to accumulate totals across all entities:

```scala
Projection.global[DailyStats]("dailyStats")
  .from("users").routeToAll
  .on[UserCreated].aggregate(FieldUpdate.Increment("user_count", 1L))
```

### `routedBy`

`routedBy[E]` shards events by a key extracted from the event itself. Events with the same key go to the same shard file, even when they originate from different entities. This is the right choice when a projection groups data across entity boundaries:

```scala
Projection[CustomerOrder]("customer_orders")
  .from("orders").routedBy[OrderEvent](_.customerId)
  .on[OrderPlaced].insert((e, ctx) => CustomerOrder(ctx.entityId, e.customerId, e.product))
```

The resulting `RoutingMode.RoutedBy[E]` value holds the extractor and exposes `extractAny(value: Any): String`, which the engine calls with untyped event values to determine the shard key. Handlers are still typed; only the routing call is erased to `Any`.

### `routedByField`

`routedByField[E]` is the inline-macro variant of `routedBy`. It takes a selector that returns any type, such as an `Int` or a `UUID`, and uses the field's value, converted with `toString`, as the shard key. `routedByField[E](_.customerId)` is shorthand for `routedBy[E](e => e.customerId.toString)`. A typo in `_.fieldName` is a compile error, because the selector is type-checked. The macro does not read the field's name, so it has no effect on column names:

```scala
Projection[CustomerOrder]("customer_orders")
  .from("orders").routedByField[OrderEvent](_.customerId)
  .on[OrderPlaced].insert((e, ctx) => CustomerOrder(ctx.entityId, e.customerId, e.product))
```

### Putting the Bindings Together

The snippets above show one routing call each. The example below uses them together on one domain: orders are recorded per order (`order-1`, `order-2`, ...), each event carries the `customerId`, and we want two views of the same events. The first view keeps one row per order, grouped by customer, so it binds the `"orders"` source with `routedBy`. The second view is a single row of shop-wide counters, so it binds a `"shop"` source with `routeToAll`:

```scala
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

sealed trait OrderEvent { def customerId: String }
object OrderEvent {
  case class OrderPlaced(customerId: String, product: String, total: Long) extends OrderEvent
  object OrderPlaced { implicit val schema: Schema[OrderPlaced] = Schema.derived[OrderPlaced] }

  case class OrderShipped(customerId: String) extends OrderEvent
  object OrderShipped { implicit val schema: Schema[OrderShipped] = Schema.derived[OrderShipped] }

  implicit val schema: Schema[OrderEvent] = Schema.derived[OrderEvent]
}
import OrderEvent.*

case class CustomerOrder(@Modifier.id id: String, customerId: String, product: String, total: Long, status: String)
object CustomerOrder {
  implicit val schema: Schema[CustomerOrder]         = Schema.derived[CustomerOrder]
  implicit val entityPath: EntityPath[CustomerOrder] = EntityPath.derived[CustomerOrder]("customer_orders")
}

case class ShopStats(@Modifier.id id: String, orders: Long, shipped: Long, revenue: Long)
object ShopStats {
  implicit val schema: Schema[ShopStats]         = Schema.derived[ShopStats]
  implicit val entityPath: EntityPath[ShopStats] = EntityPath.derived[ShopStats]
}

// One row per order, kept in the shard of its customer.
val customerOrders: Projection[CustomerOrder] =
  Projection[CustomerOrder]("customer_orders")
    .from("orders").routedBy[OrderEvent](_.customerId)
    .on[OrderPlaced].insert((e, ctx) => CustomerOrder(ctx.entityId, e.customerId, e.product, e.total, "placed"))
    .on[OrderShipped].updateWithField("status", (_, _) => "shipped")

// One row for the whole shop: every event goes to the same store, whatever its entity or customer.
val shopStats: Projection[ShopStats] =
  Projection.global[ShopStats]("shop_stats")
    .from("shop").routeToAll
    .on[OrderPlaced].aggregate(FieldUpdate.Increment("orders", 1L))
    .on[OrderShipped].aggregate(FieldUpdate.Increment("shipped", 1L))
```

Reading the two definitions side by side:

- `customerOrders` has one binding, `"orders"`. Both of its handlers sit under that binding's `routedBy`, so `OrderPlaced` and `OrderShipped` for the same customer reach the same shard. That is what lets `OrderShipped` update a row that `OrderPlaced` created earlier.
- `shopStats` has one binding, `"shop"`, with `routeToAll`. The customer in the event is never looked at.
- The two bindings name different sources, so the engine is given two event stores, one under each name. See [`ProjectionEngine`](./engine.md) for how sources are connected.

A runnable version of this example, with SQLite stores and a second counter that goes up and down, is `Example2CrossEntity` in the projection examples.

## Defining Handlers

`on[E]` opens a handler for events of type `E` and returns a `HandlerBuilder[E, A]`. Each handler verb on the builder closes it and returns a new `Projection[A]` with the handler added to the most recent source binding:

```scala
final class Projection[A] {
  def on[E: Schema: ClassTag]: HandlerBuilder[E, A]
}

final class HandlerBuilder[E, A] {
  def insert(f: (E, ProjectionContext) => A): Projection[A]
  def delete: Projection[A]
  def custom(f: (E, ProjectionContext) => ProjectionAction[A]): Projection[A]
  def aggregate(update: FieldUpdate): Projection[A]
  def aggregate(updates: Chunk[FieldUpdate]): Projection[A]
  def aggregateField(update: FieldUpdate): Projection[A]
  def updateWithField(fieldName: String, f: (E, ProjectionContext) => Any): Projection[A]
  def updateField(fieldName: String)(f: (E, ProjectionContext) => Any): Projection[A]
  def update(selector: A => Any)(f: (E, ProjectionContext) => Any): Projection[A] // inline macro
}
```

### ProjectionContext

Every handler function receives a `ProjectionContext` alongside the event, providing the metadata the engine attached when it processed the event envelope:

```scala
final case class ProjectionContext(
  entityId:       String,
  timestamp:      java.time.Instant,
  seq:            Long,
  sourceEntityId: Option[String]
)
```

`entityId` is the entity the event was recorded under and determines the routing target for `RouteToSelf`. `timestamp` is the wall-clock time at which the event was appended. `seq` is the monotonic sequence number that the engine uses as a resume watermark after a restart. `sourceEntityId` is populated when a cross-entity routing key differs from the originating entity.

### Inserting and Deleting Rows

`insert` builds a complete row from the event and context and produces `ProjectionAction.Insert`, creating a new entry in the projection store:

```scala
Projection[UserProfile]("userProfiles")
  .from("users").routeToSelf
  .on[UserCreated]
  .insert((e, ctx) => UserProfile(ctx.entityId, e.name, e.email))
  .on[UserDeleted]
  .delete
```

`delete` removes the entity's row unconditionally, producing `ProjectionAction.Delete`. It takes no function argument because the target row is always identified by `ctx.entityId`.

### Field Updates

`updateWithField` (and its alias `updateField`) sets a named column to a value computed from the event. The column name is passed as a raw string and must already be in `snake_case` to match the generated schema:

```scala
  .on[OrderShipped]
  .updateWithField("status", (_, _) => "shipped")
```

`update` is the compile-time macro variant. A selector such as `_.firstName` is resolved to the column name at compile time, and the macro converts the Scala field name to `snake_case` automatically — a mistyped field name is a compile error rather than a runtime failure:

```scala
  .on[UserRenamed]
  .update(_.firstName)((e, _) => e.newName)
  // _.firstName resolved to column "first_name" at compile time
```

### Aggregating Fields

`aggregate` and `aggregateField` apply one or more `FieldUpdate` operations, producing `ProjectionAction.Update`. This rewrites individual columns rather than the entire row, and numeric operations such as `Increment` are executed atomically in SQL:

```scala
Projection.global[DailyStats]("dailyStats")
  .from("users").routeToAll
  .on[UserCreated]
  .aggregate(FieldUpdate.Increment("user_count", 1L))
  .from("repos").routeToAll
  .on[RepoCreated]
  .aggregate(Chunk(
    FieldUpdate.Increment("repo_count", 1L),
    FieldUpdate.Max("peak", 100L)
  ))
```

`aggregateField` is an alias for the single-update overload of `aggregate`. The available `FieldUpdate` operations are `Set`, `Increment`, `Decrement`, `Max`, and `Min`.

### Custom Actions

`custom` accepts any `ProjectionAction[A]`, covering cases the other verbs do not — for example, `Upsert` (insert or update), `Noop` (no operation), or `Truncate` (clear all rows):

```scala
  .on[RareEvent]
  .custom { (e, ctx) =>
    if (e.isFirstSeen) ProjectionAction.Upsert(buildRow(e, ctx))
    else               ProjectionAction.Noop
  }
```

### Handlers Are Pure Functions

Handlers describe changes as `ProjectionAction` values and cannot perform effects. The engine applies each returned action to the projection store; the handler only decides which action to produce. This separation makes event replay deterministic: running the same sequence of events through the same handlers always yields the same read-model state.

### Default Source Binding

Calling `on[E]` without a preceding `from()` automatically creates a source binding named `"_default"` with `RouteToSelf` routing. This shorthand suits projections that listen to a single implicit source and do not need an explicit name:

```scala
Projection[UserProfile]("userProfiles")
  .on[UserCreated]   // implicitly creates "_default" binding with RouteToSelf
  .insert((e, ctx) => UserProfile(ctx.entityId, e.name, e.email))
```

## Projection Scope

`ProjectionScope` describes how a projection's data is divided across store files. It is derived automatically from the projection's bindings by calling `projection.scope`; it is never set directly.

```scala
sealed trait ProjectionScope
object ProjectionScope {
  case object PerEntity                              extends ProjectionScope
  case object Global                                 extends ProjectionScope
  case class  CrossEntity(routedBy: Any => String)   extends ProjectionScope
}
```

The three cases map to distinct file layouts on disk:

| Scope                   | Trigger                                   | File Location                              |
|-------------------------|-------------------------------------------|--------------------------------------------|
| `PerEntity` (default)   | No `routedBy`/`routedByField` binding, and not a global projection | `<basePath>/<name>.db`                     |
| `Global`                | `Projection.global[A]` constructor        | `global/<name>.db`                         |
| `CrossEntity(routedBy)` | Any `routedBy` or `routedByField` binding | `<basePath>/<name>_shard_<key>.db` per key |

One important subtlety: `routeToAll` alone does not produce `Global` scope. It only routes all events within the source to the same store. To obtain a `Global` scope — and have the store placed in the `global/` folder — use `Projection.global[A]` at construction time. `CrossEntity` takes precedence over `PerEntity` whenever any binding uses `routedBy`; the extractor from the first such binding is stored in the `CrossEntity` case and used by the engine to shard queries.

## Inspecting a Projection

Several read-only members expose a completed projection's internal structure. These are primarily useful for debugging, validation, and building custom runners or middleware layers.

### Structural Accessors

`scope` computes the derived `ProjectionScope` from the current binding configuration, as described in the previous section.

`bindings` (also accessible as `sources`) returns the list of `SourceBinding[?, A]` values in the order they were added. Each binding carries a source name, its routing mode, and its handlers:

```scala
final case class SourceBinding[E, A](
  sourceName: String,
  routing:    RoutingMode,
  handlers:   List[Handler[?, A]]
)
```

`allHandlers` flattens all handlers from every binding into a single list, in binding order. `sourceNames` extracts just the source name from each binding in the same order.

`toString` returns a concise summary: `"Projection(name=<name>, isGlobal=<bool>, bindings=<count>, handlers=<count>)"`.

### Validation

`validate()` checks the projection for structural problems and returns a list of warning strings, empty when the projection is well-formed:

```scala
val warnings = proj.validate()
// Possible warnings (with <name> replaced by the projection's name):
//   "Projection <name> has no bindings"
//   "Projection <name> has no handlers"
//   "Projection <name> has binding with empty sourceName"
//   "Handler for <tag> has null class"
```

Running `validate()` before passing a projection to an engine helps catch misconfigured projections early, before any I/O occurs.

### Handler Structure

A `Handler[E, A]` is a named, runtime-typed function from event to action. Handlers are created by `HandlerBuilder` and stored inside `SourceBinding`; they are not constructed by hand:

```scala
final case class Handler[E, A](
  eventClass: Class[E],
  schema:     Schema[E],
  tag:        String,
  fn:         (E, ProjectionContext) => ProjectionAction[A]
) {
  def matches(event: Any): Boolean
  def runAny(event: Any, ctx: ProjectionContext): ProjectionAction[A]
}
```

`matches` tests whether a runtime event is an instance of `eventClass`. `runAny` casts the event and invokes `fn`. Both are used internally by the dispatch algorithm described below.

## Handler Dispatch

The dispatch algorithm finds and invokes the handler for a given event at runtime. `handle` is the primary method; `dispatch` is a binary-compatible alias that delegates to it:

```scala
final class Projection[A] {
  def handle(
    event:  Any,
    ctx:    ProjectionContext,
    source: Option[String] = None
  ): Option[ProjectionAction[A]]

  def dispatch(
    event:  Any,
    ctx:    ProjectionContext,
    source: Option[String] = None
  ): Option[ProjectionAction[A]]

  def routingKey(
    event:      Any,
    ctx:        ProjectionContext,
    sourceName: String
  ): Option[String]

  def routingKeyForDefault(
    event: Any,
    ctx:   ProjectionContext
  ): Option[String]
}
```

When `source` is `Some(name)`, `handle` locates the first `SourceBinding` whose `sourceName` matches, then searches that binding's handlers in order and returns the result of the first one whose `eventClass.isInstance(event)` is true. When `source` is `None`, `handle` searches all handlers across all bindings in binding order and returns the first match. In both cases, `None` is returned when no handler matches.

`routingKey(event, ctx, sourceName)` determines the routing key for an event in a named source, following that binding's `RoutingMode`:

- `RouteToSelf` → `Some(ctx.entityId)`
- `RouteToAll` → `None` (no key; all events share one store)
- `RoutedBy(extractor)` → `Some(extractor(event))` (the extracted key as a string)

If the named source is not found in `bindings`, `routingKey` returns `None`.

`routingKeyForDefault(event, ctx)` applies the same logic to the most recently added binding, falling back to `Some(ctx.entityId)` when there are no bindings yet. The engine calls these methods to determine which store file receives an event before it calls `handle` to produce the action to apply.

## See Also

- [Projection module overview](./index.md) — module introduction, key concepts, and integration points with `zio-blocks-schema`, `zio-blocks-sql`, and ZIO Streams.
- [ProjectionEngine](./engine.md) — the runtime that executes `Projection[A]` descriptors: event dispatch, watermark tracking, schema-change detection, and the SQLite / in-memory store implementations.
