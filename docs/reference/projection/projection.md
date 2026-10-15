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

`Projection[A]` is the declarative description of a read model `A` built from events. It encodes which event sources to listen to, how to route each event to a specific entity or shard, and what each event type does to the read model's shape. A `Projection` is pure data — it carries no effects and makes no I/O calls; a `ProjectionEngine` reads it and drives the actual event processing.

## Creating a Projection

The `Projection` companion object provides three factory methods. Each returns a new projection with no sources yet, ready for the builder chain:

```scala
object Projection {
  def apply[A: Schema: EntityPath](name: String): Projection[A]
  def global[A: Schema](name: String): Projection[A]
  def apply[A](name: String, entityPath: EntityPath[A])(using schema: Schema[A]): Projection[A]
}
```

- `Projection[A](name)` keeps one row for each entity ID, in the folder of its [`EntityPath[A]`](#entity-paths). It needs a [`Schema[A]`](../schema/schema.md) and an `EntityPath[A]` in implicit scope.
- `Projection.global[A](name)` keeps every row in one shared store, `global/<name>.db`, which suits totals and counters. It needs only a `Schema[A]`. With no `EntityPath`, the store looks for an ID column called `id`, so name the ID field `id`.
- `Projection[A](name, entityPath)` is the first one with the `EntityPath[A]` passed explicitly, for example to keep a projection of the same type in another folder.

The `name` is also the file name of the store, so it may contain only letters, digits, `_` and `-`:

```scala mdoc:compile-only
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

case class UserProfile(@Modifier.id userId: String, name: String)
object UserProfile {
  implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
  implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
}

case class DailyStats(@Modifier.id id: String, userCount: Long)
object DailyStats {
  implicit val schema: Schema[DailyStats] = Schema.derived[DailyStats]
}

val profiles = Projection[UserProfile]("user_profiles")                                       // users/user_profiles.db
val stats    = Projection.global[DailyStats]("daily_stats")                                   // global/daily_stats.db
val archived = Projection[UserProfile]("old_profiles", EntityPath.derived[UserProfile]("archive")) // archive/old_profiles.db
```

## Entity Paths

`EntityPath[A]` describes where a projection's SQLite files live and which field of `A` holds the entity identifier. The ID field is marked with `@Modifier.id`, from `zio.blocks.schema.Modifier`. An entity path is required for per-entity and cross-entity projections; global projections do not use it, see [Projection Scope](#projection-scope).

```scala
trait EntityPath[A] {
  def basePath: String       // folder name, e.g. "users"
  def entityIdField: String  // field name, e.g. "userId"
}
```

The engine uses `basePath` to construct the path to each SQLite file and `entityIdField` to locate the primary-key column in `A`'s schema. Both fields are exposed as read-only members and are set at construction time.

### Manual Construction

`EntityPath.apply[A]` accepts explicit strings for both fields, giving full control over the folder name and the id field without relying on derivation:

```scala mdoc:compile-only
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

case class MyEntity(@Modifier.id id: String, value: Int)
object MyEntity {
  implicit val schema: Schema[MyEntity]         = Schema.derived[MyEntity]
  implicit val entityPath: EntityPath[MyEntity] = EntityPath[MyEntity]("my_entities", "id")
}
```

### Derived Construction

`EntityPath.derived[A]` inspects `Schema[A]` and applies a four-step algorithm to produce both values automatically:

1. Find the id field: prefer the field annotated `@Modifier.id`, then fall back to any field named `"id"`. If there is neither, `derived` fails with `Entity <type> must have @Modifier.id field or field named 'id'`. A type that is not a case class fails too.
2. Use that field's name as `entityIdField`.
3. Strip the `"Id"` suffix (case-insensitive) from the field name, and a `_` left in front of it, so `user_id` gives `user` as well. A field named exactly `id` is left alone.
4. Convert the result to `snake_case` and pluralize it to obtain `basePath`. The plural follows English for common endings: `box` becomes `boxes` and `category` becomes `categories`.

The table below shows the outcome for three representative types:

| Type                                                            | Id Field   | Derived `basePath` |
|-----------------------------------------------------------------|------------|--------------------|
| `case class UserAccount(@Modifier.id userId: String, ...)`      | `userId`   | `"users"`          |
| `case class OrderRecord(@Modifier.id orderId: String, ...)`     | `orderId`  | `"orders"`         |
| `case class Item(@Modifier.id itemId: String, ...)`             | `itemId`   | `"items"`          |

If the ID field is exactly `id`, `basePath` is `ids`. Prefer a name such as `userId`, or override the folder as shown in the next two subsections.

A concrete derivation looks like this:

```scala mdoc:compile-only
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

case class UserProfile(@Modifier.id userId: String, name: String, email: String)
object UserProfile {
  implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
  implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
}
// basePath = "users", entityIdField = "userId"
```

### Overriding the Folder Name

`EntityPath.derived[A](pathOverride)` keeps the standard id-field derivation but substitutes an explicit `basePath`, which is useful when the conventional pluralized name conflicts with an existing schema:

```scala
implicit val ep: EntityPath[OrderRecord] = EntityPath.derived[OrderRecord]("custom_orders")
// basePath      = "custom_orders"
// entityIdField = "orderId"   (still derived normally)
```

The path can have several folders, such as `"example2/orders"`.

### The `@path` Annotation

Placing `@path("folder")` on a case class type overrides the derived folder name at the declaration site rather than at each call to `derived`:

```scala mdoc:compile-only
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

@path("custom_users")
case class UserProfile(@Modifier.id id: String, name: String)
object UserProfile {
  implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
  implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
}
// basePath = "custom_users" (the annotation overrides the derived "ids"), entityIdField = "id"
```

The annotation takes precedence over the derivation algorithm, but not over an explicit `pathOverride` argument — when `pathOverride` is provided to `EntityPath.derived[A](pathOverride)`, it is used unconditionally and the annotation is ignored.

## Sources and Routing

A projection says which event sources it reads with `from`, and how the events of each source are routed with one of the routing methods that follow it.

### Defining Sources

```scala
final class Projection[A] {
  def from(sourceName: String): FromBuilder[A]
}
```

`from` adds a source to the projection: the event store that the engine is given under the name `sourceName`. It returns a `FromBuilder[A]`, on which you call one of the [routing methods](#routing-methods). The `on[E]` handlers after it belong to that source, up to the next `from`, so a projection that reads several sources calls `from` once for each:

```scala mdoc:compile-only
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

case class RepoCreated(ownerId: String, repoName: String)
object RepoCreated {
  implicit val schema: Schema[RepoCreated] = Schema.derived[RepoCreated]
}

case class IssueOpened(ownerId: String, title: String)
object IssueOpened {
  implicit val schema: Schema[IssueOpened] = Schema.derived[IssueOpened]
}

case class OwnerActivity(@Modifier.id id: String, ownerId: String, kind: String, text: String)
object OwnerActivity {
  implicit val schema: Schema[OwnerActivity]         = Schema.derived[OwnerActivity]
  implicit val entityPath: EntityPath[OwnerActivity] = EntityPath.derived[OwnerActivity]
}

val activity = Projection[OwnerActivity]("ownerActivity")
  .from("repos")                       // first source
  .routedBy[RepoCreated](_.ownerId)
  .on[RepoCreated]
  .insert((e, ctx) => OwnerActivity(ctx.entityId, e.ownerId, "repo", e.repoName))
  .from("issues")                      // second source
  .routedBy[IssueOpened](_.ownerId)
  .on[IssueOpened]
  .insert((e, ctx) => OwnerActivity(ctx.entityId, e.ownerId, "issue", e.title))

// activity.sourceNames == List("repos", "issues")
// activity.scope       == ProjectionScope.CrossEntity(...)
```

The engine then needs an event store for each name, here `"repos"` and `"issues"`. A projection with no `from` reads one source named `"_default"`, see [Default Source](#default-source).

### Defining Routes

After `from`, call one of the routing methods to finish the source binding. Each method returns a `Projection[A]` with the source added, so you can continue the builder chain with `on[E]` handlers or another `from`. The routing methods are:

```scala
final class FromBuilder[A] {
  def routeToSelf: Projection[A]
  def routeToAll: Projection[A]
  def routedBy[E](selector: E => String): Projection[A]
  def routedByField[E](selector: E => Any): Projection[A]   // inline macro
}
```

Each routing method finishes the `from` call and returns the projection. Only `routedBy` and `routedByField` choose where events go: they split the projection's rows by a key read from the event. `routeToSelf` and `routeToAll` choose no key: in a projection without `routedBy`, every event goes to the projection's one store and changes the row with the ID it was recorded under.

#### `routeToSelf`

`routeToSelf` is for a projection that keeps one row for each entity: each event changes the row of the entity it was recorded under. It is what a projection with no `from` uses:

```scala
Projection[UserProfile]("userProfiles")
  .from("users").routeToSelf
  .on[UserCreated].insert((e, ctx) => UserProfile(ctx.entityId, e.name, e.email))
```

#### `routeToAll`

`routeToAll` is for a source whose events all feed shared rows, typically in a projection made with `Projection.global[A]`. It does not make the projection global, which only `Projection.global` does, and it does not merge rows: to count everything in one row, record the events under one ID, such as a date:

```scala
Projection.global[DailyStats]("dailyStats")
  .from("users").routeToAll
  .on[UserCreated].aggregate(FieldUpdate.Increment("user_count", 1L))
```

#### `routedBy`

`routedBy[E]` reads a key from each event with `selector`. Events with the same key go to the same store, called a shard, `<basePath>/<name>_shard_<key>.db`, even when they were recorded under different entities. Within a shard, the row is still the one with the event's entity ID. Using it on any source makes the projection [`CrossEntity`](#projection-scope):

```scala
Projection[CustomerOrder]("customer_orders")
  .from("orders").routedBy[OrderEvent](_.customerId)
  .on[OrderPlaced].insert((e, ctx) => CustomerOrder(ctx.entityId, e.customerId, e.product))
```

#### `routedByField`

`routedByField[E]` is `routedBy` for a selector that returns any type, such as an `Int` or a `UUID`: the value is turned into the key with `toString`. `routedByField[E](_.customerId)` is the same as `routedBy[E](e => e.customerId.toString)`:

```scala
Projection[CustomerOrder]("customer_orders")
  .from("orders").routedByField[OrderEvent](_.customerId)
  .on[OrderPlaced].insert((e, ctx) => CustomerOrder(ctx.entityId, e.customerId, e.product))
```

The full example below reads two sources: it routes the orders by customer with `routedBy`, and feeds a global row of shop totals with `routeToAll`:

```scala mdoc:embed:projection-examples/src/main/scala/projection/examples/Example2CrossEntity.scala

```

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

When an event arrives, the projection looks for a handler by the event's class. A handler matches any event that is an instance of its event type, so a handler for a sealed trait also matches each of its cases. If more than one handler matches, the first one added wins. The engine searches the handlers of all the sources together, in the order the sources were added, and it does not narrow the search to the source the event came from. So when two sources have a handler for the same event type, the handler of the source added first handles the events of both.

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

The full example below creates three users, deletes one of them, and reads the rows back. `UserCreated` inserts a `UserProfile` and `UserDeleted` removes it:

```scala mdoc:embed:projection-examples/src/main/scala/projection/examples/Example9InsertAndDelete.scala

```

Running it prints one line for each user:

- `user-1`: `Alice <alice@example.com>`. The row was inserted and never deleted.
- `user-2`: `no row`. The row was inserted, then removed by `delete`, so the query returns `None`.
- `user-3`: `Charlie <charlie@example.com>`. The deletion of `user-2` did not touch it, because each `delete` removes only the row of the entity the event was recorded under.

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

The full example below registers a user and then changes one column at a time, using each of the three ways to name the column. `UserRenamed` uses the `update` macro, `EmailChanged` uses `updateWithField` with the column name as a string, and `UserVerified` uses `updateField`. Each handler changes only its own column:

```scala mdoc:embed:projection-examples/src/main/scala/projection/examples/Example8FieldUpdates.scala

```

Running it prints one line for each user:

- `user-1`: `Alicia <alicia@example.com>, verified`. The name, the email and the status were each changed by a separate event.
- `user-2`: `Robert <bob@example.com>, pending`. Only the name changed, so the email and the status are as they were registered.

### Aggregating Fields

`aggregate` and `aggregateField` apply one or more `FieldUpdate` operations, producing `ProjectionAction.Update`. This rewrites individual columns rather than the entire row, and numeric operations such as `Increment` are executed in SQL without reading the row first. The available `FieldUpdate` operations are `Set`, `Increment`, `Decrement`, `Max`, and `Min`. `aggregateField` is an alias for the single-update overload of `aggregate`:

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

To change several columns for one event, pass a `Chunk` of updates, as the `RepoCreated` handler above does. The SQLite store applies them in one transaction.

The `FieldUpdate` values are fixed when the projection is declared, so they cannot use data from the event. When an update needs the event's data, for example adding an order's total to `revenue`, build the update inside [`custom`](#custom-actions) with `ProjectionAction.Update`.

Each operation is one change to a column, run as SQL:

| Operation | SQL | Effect |
| --- | --- | --- |
| `Set(field, value)` | `SET col = ?` | Replace the value |
| `Increment(field, by)` | `SET col = COALESCE(col, 0) + ?` | Add to a counter |
| `Decrement(field, by)` | `SET col = COALESCE(col, 0) - ?` | Subtract from a counter |
| `Max(field, value)` | `SET col = MAX(COALESCE(col, ?), ?)` | Keep the highest value |
| `Min(field, value)` | `SET col = MIN(COALESCE(col, ?), ?)` | Keep the lowest value |

Before it applies the updates, the store runs `INSERT OR IGNORE`, which creates the row with the default value of each column if it is missing. Incrementing a counter of a row that does not exist yet therefore gives `1`, and `COALESCE` treats a missing value as `0`. The updates of one event run in one transaction, and the arithmetic happens inside SQL, so several fibers incrementing the same counter produce the correct total without lost updates.

A field can also be named with a selector instead of a string. `FieldUpdate.increment[DailyStats](_.userCount)` picks the field at compile time and maps it to the column `user_count`, as `update` does, so a typo does not compile. The factories are `setValue`, `increment`, `decrement`, `maxValue` and `minValue`.

The full example below counts users and repositories into one `DailyStats` row. Each source is bound with `routeToAll`, so the events of both sources land in the same row, and each handler adds one to its own counter with `FieldUpdate.Increment`. The row is created on the first event, with the other counters at zero:

```scala mdoc:embed:projection-examples/src/main/scala/projection/examples/Example3GlobalAggregate.scala

```

Running it appends five `UserCreated` and three `RepoCreated` events and reads back one row with `Users: 5, Repos: 3`.

### Custom Actions

`custom` accepts any `ProjectionAction[A]`, covering cases the other verbs do not — for example, `Upsert` (insert or update), `Noop` (no operation), or `Truncate` (clear all rows). Its signature is `custom(f: (E, ProjectionContext) => ProjectionAction[A]): Projection[A]`. Use it when the action depends on the event: the handler looks at the event and returns the action to apply.

The example below handles two events for a user profile, and each handler picks between actions:

- `ProfileImported` carries a `draft` flag. A draft is ignored with `Noop`, so no row is written. Anything else is written with `Upsert`, which creates the row or replaces it if it exists. That makes importing the same user twice safe, where `Insert` would not be.
- `AccountClosed` carries an `eraseData` flag. When it is set, the row is removed with `Delete`. Otherwise `Update` changes only the `status` column and keeps the rest of the row.

```scala mdoc:embed:projection-examples/src/main/scala/projection/examples/Example7CustomActions.scala

```

Running it prints one line for each user:

- `user-1`: `Alice B. <alice@example.com>, active`. The second import replaced the first.
- `user-2`: `no row`. The draft was a `Noop`, so no row was ever written.
- `user-3`: `Carol <carol@example.com>, closed`. Only `status` changed.
- `user-4`: `no row`. The row was created, then removed by `Delete`.

### Handlers Are Pure Functions

Handlers describe changes as `ProjectionAction` values and cannot perform effects. The engine applies each returned action to the projection store; the handler only decides which action to produce. This separation makes event replay deterministic: running the same sequence of events through the same handlers always yields the same read-model state.

### Default Source

Most projections read one event feed and keep each entity's row under its own ID. Writing `.from("some-name").routeToSelf` for that would mean inventing a name for a feed that has only one meaning, so the library lets you skip it. When you add the first handler to a projection that has no source binding yet, with `on[E]` and no `from()` before it, the library creates the binding for you:

- The source is named `"_default"`.
- The routing is `RouteToSelf`, so each event goes to the row of the entity it was recorded under (`ctx.entityId`).
- The handler is put in that binding.

```scala
Projection[UserProfile]("userProfiles")
  .on[UserCreated]   // implicitly creates "_default" binding with RouteToSelf
  .insert((e, ctx) => UserProfile(ctx.entityId, e.name, e.email))
```

This is the same projection as the one below, with the binding written out:

```scala
Projection[UserProfile]("userProfiles")
  .from("_default").routeToSelf
  .on[UserCreated]
  .insert((e, ctx) => UserProfile(ctx.entityId, e.name, e.email))
```

The shortcut is purely a convenience. It gives nothing that `.from("_default").routeToSelf` does not, and nothing requires you to use it.

The engine looks the source name up in the event stores it was given. For `"_default"` it uses the store registered under `"_default"` if there is one, and otherwise the first store it was given. With a single event store, such as the one behind `TestEngine`, the name never matters.

What the shortcut does and does not do:
- Only the first handler creates the binding. A second `on[E]` attaches to the binding that already exists, whether it is `"_default"` or one made by an earlier `from()`.
- A `from()` after the handlers adds a second binding. The projection then has two sources, `"_default"` and the new one, and needs an event store for each.
- It can only give `RouteToSelf`. For any other routing, write `from()` yourself.

## Projection Scope

A scope is the way the rows of a projection are organized. To see what that means, picture a shop that writes a note every time something happens: "Alice placed order 7", "order 7 shipped". These notes are the events. To answer questions quickly ("is order 7 shipped?", "how many orders so far?"), someone files each note into a cabinet as it arrives. The filed result is the read model, and the scope is how that cabinet is arranged.

`ProjectionScope` is the type that describes it. It answers two questions: which `ProjectionStore` an event is applied to, and which file that store lives in.

```scala
sealed trait ProjectionScope
object ProjectionScope {
  case object PerEntity                              extends ProjectionScope
  case object Global                                 extends ProjectionScope
  case class  CrossEntity(routedBy: Any => String)   extends ProjectionScope
}
```

You never set a scope directly. It is worked out from how the projection is built, and `projection.scope` returns it:

1. A projection made with `Projection.global` has the scope `Global`.
2. Otherwise, if any source uses `routedBy` or `routedByField`, it has the scope `CrossEntity`. The key function of the first such source is kept in the `CrossEntity` case.
3. Otherwise it has the scope `PerEntity`, which is the default.

Note that `routeToAll` alone does not make a projection global. It only sends the events of its source to the same store. Only `Projection.global` gives the scope `Global` and puts the store in the `global/` folder.

The three scopes keep their files in different places:

| Scope                   | File Location                              |
|-------------------------|--------------------------------------------|
| `PerEntity` (default)   | `<basePath>/<name>.db`                     |
| `CrossEntity(routedBy)` | `<basePath>/<name>_shard_<key>.db` per key |
| `Global`                | `global/<name>.db`                         |

```scala mdoc:compile-only
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

case class UserRegistered(name: String)
object UserRegistered {
  implicit val schema: Schema[UserRegistered] = Schema.derived[UserRegistered]
}

case class OrderPlaced(customerId: String, total: Long)
object OrderPlaced {
  implicit val schema: Schema[OrderPlaced] = Schema.derived[OrderPlaced]
}

case class UserProfile(@Modifier.id id: String, name: String)
object UserProfile {
  implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
  implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
}

case class CustomerOrder(@Modifier.id id: String, customerId: String, total: Long)
object CustomerOrder {
  implicit val schema: Schema[CustomerOrder]         = Schema.derived[CustomerOrder]
  implicit val entityPath: EntityPath[CustomerOrder] = EntityPath.derived[CustomerOrder]
}

case class OrderStats(@Modifier.id id: String, orders: Long)
object OrderStats {
  implicit val schema: Schema[OrderStats] = Schema.derived[OrderStats]
}

// PerEntity: one row for each entity ID.
val profiles = Projection[UserProfile]("userProfiles")
  .from("users")
  .routeToSelf
  .on[UserRegistered]
  .insert((e, ctx) => UserProfile(ctx.entityId, e.name))

// CrossEntity: events are grouped by a key read from the event.
val ordersByCustomer = Projection[CustomerOrder]("ordersByCustomer")
  .from("orders")
  .routedBy[OrderPlaced](_.customerId)
  .on[OrderPlaced]
  .insert((e, ctx) => CustomerOrder(ctx.entityId, e.customerId, e.total))

// Global: one shared store, here with a counter.
val stats = Projection
  .global[OrderStats]("orderStats")
  .from("orders")
  .routeToAll
  .on[OrderPlaced]
  .aggregate(FieldUpdate.Increment("orders", 1L))

// profiles.scope         == ProjectionScope.PerEntity
// ordersByCustomer.scope == ProjectionScope.CrossEntity(...)
// stats.scope            == ProjectionScope.Global
```

### PerEntity: one card per thing

Each order, user or product gets its own card, and a new note about order 7 updates the card of order 7 only. This scope answers the question "what is the state of this one thing?", for example "alice, keyboard, shipped".

Every entity has its own row, and the events recorded under an entity ID update that entity's row. The routing mode of the source is [`routeToSelf`](#routetoself), which files a note under the thing it was recorded under, and a handler reads that ID as `ProjectionContext#entityId`. The store is the file `<basePath>/<name>.db`, for example `users/userProfiles.db`. It is the right scope for state that belongs to one thing and is looked up by its own ID, such as a user's profile or the status of an order.

### CrossEntity: one drawer per group

Here the cabinet has a drawer for each customer. The note says "order 7 placed, customer Alice", so it is filed in Alice's drawer and not in a drawer for order 7. Inside the drawer each order still has its own card. This scope answers the question "what belongs together under this customer?".

Events are grouped by a key that is read from the event, not by the ID they were recorded under. You say which part of the event picks the drawer with the routing mode [`routedBy`](#routedby), for example `routedBy[OrderPlaced](_.customerId)`. Any source that uses `routedBy` makes the projection `CrossEntity`. Each routing key gets its own store, called a shard, next to the main one: `<basePath>/<name>_shard_<key>.db`. With SQLite each shard is its own file, such as `customer_orders_shard_alice.db`. In the file name the key is cut to 64 characters, and any character other than letters, digits, `_` and `-` is replaced by `_`. If the key cannot be read from an event, the engine uses the entity ID.

The routing key only chooses the shard. A row keeps its own ID, so two rows in the same shard still need different IDs. To read a row you give its ID: the engine looks in the main store first, and then in every shard. It uses the scope to know that there are shards to look in; it does not use the key to pick one.

It is the right scope for views that group things by a related entity, such as the orders of a customer or the repositories of an owner. See [Routing Methods](#routing-methods) for a full example.

### Global: one scoreboard on the wall

There is no card for each thing and no drawer for each group. Every note, whoever it is about, changes the numbers on one board: orders so far, shipped, pending. This scope answers the question "how many in total?".

All events go to one shared store, `global/<name>.db`, whichever entity or key they belong to, and from as many sources as the projection reads. The routing mode of its sources is [`routeToAll`](#routetoall), which does not split the events by entity or by key. It does not make a projection global by itself, only `Projection.global` does. It is the right scope for totals and counters, such as the number of orders or the revenue of a shop. See [Aggregating Fields](#aggregating-fields) for a full example.

Two details matter in practice:

- A counter update is applied to the row whose ID the event was recorded under. To keep one row of totals, record all the events under one fixed ID, for example a date or `"shop"`. Otherwise each event updates a different row.
- A global projection has no `EntityPath`, so its store looks for an ID column called `id`. Name the ID field of the row `id`.

## Event Tagging and Migration

Event tags identify variant cases for serialization. By default, the tag is the case class name (e.g., `"UserCreated"`). You can override with numeric tags or rename cases across versions.

### String tags (default)

For a sealed trait `UserEvent` with cases `UserCreated` and `UserDeleted`, the tags are `"UserCreated"` and `"UserDeleted"`.

### Numeric tags

Use the `@eventTag` annotation for stable numeric identifiers:

```scala mdoc:compile-only
import zio.blocks.projection.eventTag
import zio.blocks.schema.{Modifier, Schema}

sealed trait UserEvent
object UserEvent {
  @eventTag(1)
  case class Created(name: String) extends UserEvent
  object Created {
    implicit val schema: Schema[Created] = Schema.derived[Created]
  }

  @eventTag(2)
  case class Deleted(userId: String) extends UserEvent
  object Deleted {
    implicit val schema: Schema[Deleted] = Schema.derived[Deleted]
  }

  implicit val schema: Schema[UserEvent] = Schema.derived[UserEvent]
}
```

### Tag aliases via Migration

Every event is saved with a tag, the name of its event type, and the tag is how the store finds events again. Suppose your application has recorded `UserLoggedIn` events for a year, and then you rename the case to `UserAuthenticated`. The old events are still in the database with the old tag, so code that asks for `UserAuthenticated` would not find them, and a year of history would seem to be gone.

You fix this by describing the rename with a `Migration`, using `Migration.renameCase`, and opening the event store with it. Working out which tags an event type can have in the store is harder than it looks, because the answer is spread over several places:

- The tags the type has now come from its cases, or from their `@eventTag` numbers.
- The tags it had before are not in the type any more. A renamed case leaves nothing behind in the code, and the only record of the old name is the rename step in a `Migration`.
- A case renamed more than once leaves a chain, such as `UserLoggedIn` to `UserSignedIn` to `UserAuthenticated`, and the oldest name has to lead to the newest.

If every part of the store worked this out for itself, the parts could disagree. The code that decodes an event, the code that filters a read by tag, and the startup check would each need the same rules, and one of them getting a rule wrong would hide old events or report false warnings.

`TagResolver` does this work once. It reads the current tags from the `Schema` of the event type and the old tags from the `renameCase` steps of the `Migration[E, E]`, where `E` is the event type. It follows the chains and returns one `TagInfo`, a small cheat sheet that says "anything tagged `UserLoggedIn` is now called `UserAuthenticated`", which the whole store consults. So you describe a rename in one place, the migration, and you do not rewrite the stored events or change the code that reads them. The event stores call `TagResolver` when they are created, so you normally only pass a migration to the store and never call it yourself. Call it directly when you want to check what a migration does, for example in a test.

The example appends an event with the first version of the event type, then opens the same store with the second version and reads the old event back:

```scala mdoc:embed:projection-examples/src/main/scala/projection/examples/Example5TagRename.scala

```

#### What a TagInfo holds

You do not write a `TagInfo` yourself. `TagResolver.resolve` builds it, and the event store keeps it and uses it when it reads. It holds four things:

- `aliases`: the map from each old tag to the current tag it became.
- `currentTags`: the tags that the event type has now.
- `allTags`: the tags that may be in the database, which are the current tags and all the old ones.
- `migration`: the migration that turns an old event into the current shape.

```scala
final case class TagInfo(
  aliases: Map[String, String],
  allTags: Set[String],
  currentTags: Set[String],
  migration: DynamicMigration
) {
  def isOldTag(tag: String): Boolean
  def currentTagFor(oldTag: String): Option[String]
  def normalize(tag: String): String
  def migrateValue(dv: DynamicValue): Either[SchemaError, DynamicValue]
  def expandRequested(requested: Set[String]): Set[String]
  def unknownTags(distinctDbTags: Set[String]): Set[String]
}
```

- `isOldTag` says whether a tag is an old name that was renamed, and `currentTagFor` gives the tag it became.
- `normalize` returns the current tag for an old tag and any other tag unchanged, so you can compare tags without caring whether an event was saved before or after the rename.
- `migrateValue` runs the migration on the stored value of an old event, so that it can be decoded with the current schema. The event store calls it when it reads an event that was saved under an old tag.
- `expandRequested` widens a request so that nothing is missed: asking for `UserAuthenticated` also asks for `UserLoggedIn`. An empty request means all tags. This is what makes a filtered read return both the old and the new events.
- `unknownTags` finds the tags in the database that the `TagInfo` does not explain, which is the check behind the startup warning below.

#### Building a TagInfo

```scala
object TagResolver {
  def resolve[E: Schema]: TagInfo
  def resolve[E: Schema](migration: Migration[E, E]): TagInfo
  def resolveImplicit[E: Schema](implicit migration: Migration[E, E] = null): TagInfo
  inline def resolveAuto[E](using schema: Schema[E]): TagInfo
  def resolveOpt[E](opt: Option[Migration[E, E]])(using schema: Schema[E]): TagInfo
}
```

- `resolve[E]` is for an event type that was never renamed, so there are no aliases.
- `resolve[E](migration)` adds the aliases of the migration. This is the usual form.
- `resolveImplicit` and `resolveAuto` use a `Migration[E, E]` that is in scope, and no migration if there is none. `resolveImplicit` also works in Scala 2, and `resolveAuto` is Scala 3 only.
- `resolveOpt` takes the migration as an `Option`. The other four call it.

#### How tags and aliases are worked out

- **Current tags.** For a sealed event type there is one tag for each case: the number from its `@eventTag`, or the name of the case if it has none. For a single event type that is not sealed, the tag is its `@eventTag` number or the name of the type, and there is no tag if that name is empty, `Object` or `Any`.
- **Aliases.** Only the `renameCase` actions of the migration make aliases. Any other action in it is ignored, and the names are used exactly as written.
- **Chains.** If you rename `A` to `B` and then `B` to `C`, the alias of `A` is `C`, so every old tag points straight to its final name. A cycle of renames stops instead of looping. If the same old name is renamed twice, the last rename wins.
- **All tags.** `allTags` is the current tags plus every old name, so an old tag is still found even when it is no longer a case of the type.

```scala mdoc:silent
import zio.blocks.projection.TagResolver
import zio.blocks.schema.Schema
import zio.blocks.schema.migration.Migration

sealed trait LoginEvent
object LoginEvent {
  case class UserAuthenticated(userId: String) extends LoginEvent
  object UserAuthenticated {
    implicit val schema: Schema[UserAuthenticated] = Schema.derived
  }
  implicit val schema: Schema[LoginEvent] = Schema.derived
}

// UserLoggedIn was renamed to UserSignedIn, which was later renamed to UserAuthenticated
val migration = Migration
  .newBuilder[LoginEvent, LoginEvent]
  .renameCase("UserLoggedIn", "UserSignedIn")
  .renameCase("UserSignedIn", "UserAuthenticated")
  .build

val info = TagResolver.resolve[LoginEvent](migration)
```

```scala mdoc
info.currentTags
info.aliases
info.allTags
info.expandRequested(Set("UserAuthenticated"))
TagResolver.resolve[LoginEvent].aliases
```

#### Looking at the parts

The same results are available one at a time, without building a `TagInfo`:

```scala
object TagResolver {
  def currentTags[E](using schema: Schema[E]): Set[String]
  def allTags[E: Schema](migration: Migration[E, E] = null): Set[String]
  def aliases[E: Schema](migration: Migration[E, E] = null): Map[String, String]
  def aliasMap[E](migration: Migration[E, E]): Map[String, String]
  def aliasMapFromDynamic(dm: DynamicMigration): Map[String, String]
  def unknownTags(tagInfo: TagInfo, distinctDbTags: Set[String]): Set[String]
}
```

- `currentTags` are the tags the type produces now. `allTags` and `aliases` take a migration, and `null`, which is the default, means no migration.
- `aliasMap` gives the aliases of a migration with chains followed. `aliasMapFromDynamic` reads the same renames from a `DynamicMigration` but does not follow chains, so `A` stays mapped to `B`.
- `unknownTags` is the same as `TagInfo#unknownTags`: the tags in the database that no current tag or alias explains.

#### The TagResolver trait

```scala
trait TagResolver[E] {
  def tagInfo: TagInfo
  def aliases: Map[String, String]
  def allTags: Set[String]
  def currentTags: Set[String]
}
```

A `TagResolver[E]` is a view of the `TagInfo` of an event type with its parts as methods. You make one by implementing `tagInfo`, and the other three come from it. Most code uses the functions on the object and gets a `TagInfo` directly.

#### How the event store uses it

An event store is given a `TagInfo` when it is created. `SQLiteEventStore.make[E](transactor)` resolves one with no aliases, `make[E](transactor, migration)` resolves one from a migration, and `makeWithResolver[E](transactor, tagInfo)` takes one you built.

- **Writing.** A new event is saved under its current tag: the `@eventTag` number or the case name.
- **Reading.** An event saved under an old tag is decoded as the case it was renamed to, and its `EventEnvelope` carries the current tag, so the code that reads it only ever sees the new name.
- **Filtering.** A read that asks for some tags is widened with `expandRequested`, so a filter on the new tag also returns the old events.
- **Checking.** When the store starts, it compares the tags in the database with `allTags`, see the warning below.

### Startup warning

On startup, the `SQLiteEventStore` checks all distinct tags in the database against the known tag set. Unknown tags, found with `TagInfo#unknownTags`, produce a warning log, not an error. This catches typos or events from a different schema version without crashing the application.

## Inspecting a Projection

Several read-only members expose a completed projection's internal structure. These are primarily useful for debugging, validation, and building custom runners or middleware layers.

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

### Looking Up a Handler and a Routing Key

:::note[Advanced]
You do not need this section to define or run a projection. The engine calls `handle` and `routingKey` for you, so a normal application never calls them. They are described here to show how the engine decides what an event does and where it goes, and for anyone writing a test or a custom runner.
:::

Two methods show what the projection would do with an event, without an engine. They are what the engine calls for every event: the first finds the handler, and the second finds the key that decides which store gets the event.

```scala
final class Projection[A] {
  def handle(event: Any, ctx: ProjectionContext, source: Option[String] = None): Option[ProjectionAction[A]]
  def routingKey(event: Any, ctx: ProjectionContext, sourceName: String): Option[String]
}
```

- `handle` returns the `ProjectionAction` of the first handler that matches the event, or `None` if no handler matches. With `source = Some(name)` it searches only the handlers of that source. With `None`, which is what the engine uses, it searches all handlers, in the order the sources were added. It does not apply the action to a store, so it is a way to unit-test a handler without an engine.
- `routingKey` returns the key for an event in the named source: `ctx.entityId` for `routeToSelf`, the extracted key for `routedBy`, and `None` for `routeToAll` or when there is no source with that name. See [Binding Event Sources](#sources-and-routing).

The snippet below shows both methods on a projection with an `"orders"` source that is routed by customer. It is a fragment: `OrderPlaced`, `OrderShipped` and `Refunded` are event case classes, and `ctx` is a `ProjectionContext` for an event recorded under `"order-1"`:

```scala
val projection: Projection[CustomerOrder] =
  Projection[CustomerOrder]("customer_orders")
    .from("orders").routedBy[OrderPlaced](_.customerId)
    .on[OrderPlaced].insert((e, ctx) => CustomerOrder(ctx.entityId, e.customerId, e.product, "placed"))
    .on[OrderShipped].updateWithField("status", (_, _) => "shipped")

// Which action would each event produce?
projection.handle(OrderPlaced("alice", "keyboard"), ctx)
// Some(Insert(CustomerOrder(order-1,alice,keyboard,placed)))

projection.handle(OrderShipped("alice"), ctx)
// Some(Update(Chunk(Set(status,shipped))))

projection.handle(Refunded("alice"), ctx)
// None: no handler matches this event type

projection.handle(OrderShipped("alice"), ctx, Some("other"))
// None: there is no source named "other"

// Which key picks the shard of the event?
projection.routingKey(OrderPlaced("alice", "keyboard"), ctx, "orders")
// Some(alice): the customer id, because the source is routed by customer

projection.routingKey(OrderPlaced("alice", "keyboard"), ctx, "other")
// None: there is no source named "other"
```

Nothing was written anywhere by these calls. `handle` only says that the event would insert a row, or set `status`, and `routingKey` says it would go to the shard of `alice`.

## See Also

- [Projection module overview](./index.md) — module introduction, key concepts, and integration points with `zio-blocks-schema`, `zio-blocks-sql`, and ZIO Streams.
- [ProjectionEngine](./engine.md) — the runtime that executes `Projection[A]` descriptors: event dispatch, watermark tracking, schema-change detection, and the SQLite / in-memory store implementations.
