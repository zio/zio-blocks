---
id: migration
title: "Migrating Read Models and Events"
sidebar_label: "Migration"
description: "What happens when the row type of a projection or the stored events change: how the engine notices a changed read model and rebuilds it, the add-column shortcut, renamed event cases, changed event fields, and the limits of each."
keywords:
  - "Event Sourcing"
  - "Schema Evolution"
  - "Read Model Rebuild"
  - "Event Versioning"
  - "Migration"
---

Code changes after data exists. In an event-sourced application, two kinds of stored data can fall out of step with the code:

- the rows of a read model, the `A` of a `Projection[A]`, kept in a projection store;
- the events, kept in an event store.

They are handled in opposite ways. The rows are derived from the events, so they can be thrown away and rebuilt by replaying the events. The events are the source of truth and are never rewritten, so new code has to read old events as they were stored.

Find your change:

- You added, removed, renamed or changed a field of the row type: [Full Rebuild](#full-rebuild), or [Adding Fields Without a Rebuild](#adding-fields-without-a-rebuild--registermigration) when you only add fields.
- You changed what a handler does: [Rebuilding on Demand](#rebuilding-on-demand).
- You renamed the row class or the projection: [Renaming the Row Class or the Projection](#renaming-the-row-class-or-the-projection).
- You renamed a case of the event type: [Renaming an Event Case](#renaming-an-event-case).
- You added, removed or changed a field of an event: [Changing the Fields of an Event](#changing-the-fields-of-an-event).

This page is about the data a projection keeps. For the `Migration` type itself, see [Migration](../schema/migration.md); for migrating a relational database while it runs, see [Data Migration](../data-migration.md).

## Changing the Read Model

The rows of a projection are kept in a table named after the row class, in snake_case (`UserProfile` becomes `user_profile`), with one column for each field. On every `start`, the engine checks whether that table still matches the row type, so a change is handled when the new version of the application starts.

### How a Change Is Detected — `SchemaHash`

```scala
object SchemaHash {
  def compute[A: Schema]: String
}
```

`compute` returns a fingerprint of the shape of `A`: a SHA-256 hash, as 64 hex characters. The store keeps the fingerprint of the rows it holds next to the watermark, the sequence number of the last event applied, in a table called `_projection_meta`. On `start`, the engine compares the stored fingerprint with the current one:

- No fingerprint is stored, as in a new store: it stores the current one.
- The fingerprints are the same: nothing happens.
- They differ: the rows are out of date. The engine takes the [shortcut](#adding-fields-without-a-rebuild--registermigration) if one is registered and fits, and otherwise [rebuilds](#full-rebuild) the rows. With [`lazyRebuild`](#rebuilding-later--lazyrebuild) on, it only marks the projection for a later rebuild.

The fingerprint changes when you:

- rename the row class;
- add, remove, rename or reorder a field;
- change the type of a field, for example from `Int` to `Long`;
- change the fields of a case class used directly as the type of a field.

It does not change when you:

- change the fields of a case class held in an `Option`, a `List` or another collection;
- add a case to a sealed trait used as the type of a field;
- move `@Modifier.id` to another field;
- change a handler.

For those, see [Rebuilding on Demand](#rebuilding-on-demand).

### Full Rebuild

A full rebuild throws the rows away and computes them again from the stored events:

1. The table is dropped and created again, with the columns of the current row type.
2. The watermark is set back to 0.
3. Every event of the projection's event store is read, from the first one, and applied through the handlers.
4. The watermark and the new fingerprint are saved.
5. The engine then goes on as usual: it catches up on newer events and follows new ones.

`start` waits for the rebuild, so with a long history the application takes longer to start. In return, old rows get real values for new fields, computed from the events. An event whose change cannot be applied to the table is logged and skipped. An event that can no longer be decoded stops the rebuild; see [Changing the Fields of an Event](#changing-the-fields-of-an-event).

The example below runs two versions of an application on the same files. The first keeps `UserProfile(id, name)`; the second adds `email`, so its `start` rebuilds the table, and the old users get their email from the stored events:

```scala mdoc:embed:projection-examples/src/main/scala/projection/examples/Example7SchemaEvolution.scala

```

It prints `After rebuild, user-1: Alice <alice@example.com>`.

### Adding Fields Without a Rebuild — `registerMigration`

When the only change is new fields, the engine can add the columns instead of replaying the events. Describe the change with a `Migration` from the old row type to the new one, and register it on the engine:

```scala
final class ProjectionEngine {
  def registerMigration[Old, New](spec: Projection[New], migration: Migration[Old, New]): Task[Unit]
}
```

Build the migration with `Migration.newBuilder[Old, New]`, one `addField` for each new field, and `build`; see [Migration](../schema/migration.md). The rules:

- Register it before `start`, which is when the fingerprints are compared.
- The engine keeps it under the projection's name, so registering another one replaces it.
- It is used only when every step of the migration adds a top-level field. Any other step, such as a rename, means a full rebuild.
- Each new field becomes a column added with `ALTER TABLE ... ADD COLUMN`. The fingerprint is saved, the watermark is kept, and no event is replayed.

The old rows get no value in the new columns: the default given to `addField` is not written. They read back as `null` for a `String`, `None` for an `Option` and `0` for an `Int`, whatever the default was. So use the shortcut for fields that old rows can do without, such as an `Option`, and let the engine rebuild otherwise.

:::caution
The engine does not check that a registered migration describes the change it finds. While a migration made only of `addField` steps is registered, the engine uses it for any change of the fingerprint: it adds the migration's columns, saves the new fingerprint and does not rebuild, even when other fields changed too. The table is then left with missing columns. Register a migration only in the version of the application that makes that change, and remove it afterwards.
:::

The example below runs the two versions of the full rebuild example again, with a migration registered for the second version. The old users come back with an empty email:

```scala mdoc:embed:projection-examples/src/main/scala/projection/examples/Example8MigrationShortcut.scala

```

It prints `After migration, user-1: Alice <null>`.

### Rebuilding Later — `lazyRebuild`

```scala
final case class ProjectionEngineConfig(
  // ...
  rebuildParallelism: Int = 4,
  lazyRebuild: Boolean = false,
  evolution: SchemaEvolutionConfig = SchemaEvolutionConfig.default
)
```

By default, `start` rebuilds a changed projection before it returns. With `lazyRebuild = true`, it only marks the projection as pending, so the application starts at once:

- The first `query` of the projection waits while it is rebuilt, or while the shortcut runs.
- A background fiber rebuilds the pending projections one after the other, in groups of `rebuildParallelism`.
- `queryByName` does not trigger the rebuild.

Until then, the engine keeps applying events to the old table, and the events that no longer fit it fail and are logged. The rebuild applies them again from the stored events. The `evolution` settings are not read by the engine; see [`SchemaEvolution`](#schemaevolution). The other settings are described under [Configuration](./engine.md#configuration).

### Rebuilding on Demand

Some changes need a rebuild that the engine cannot detect: a handler that now computes something else, or a change the fingerprint does not see. There are two ways to rebuild:

- Delete the projection's store file while the application is stopped: `<folder>/<name>.db`, with its `-wal` and `-shm` files. For a cross-entity projection, delete its `<name>_shard_*.db` files too, and for a global projection the file is `global/<name>.db`. When the application starts, the empty store has no fingerprint and a watermark of 0, so the engine applies every stored event again as it catches up.
- In code, call `SchemaEvolution.rebuildWithHash` on the store before the engine starts. It empties the store and applies every stored event again, with the current handlers.

The example below runs three versions of an application on the same files. The second changes the handler to store the names in upper case, and its old rows keep their names, because nothing triggers a rebuild. The third rebuilds on demand, before the engine starts:

```scala mdoc:embed:projection-examples/src/main/scala/projection/examples/Example9RebuildOnDemand.scala

```

It prints `V2 user-1: Alice (not rebuilt)`, then `After rebuildWithHash, user-1: ALICE`.

Neither way works for a projection that reads several sources; see [Limitations](#limitations).

### Renaming the Row Class or the Projection

The name of the row class is also the name of its table. When you rename the class, the engine finds no table and no fingerprint under the new name: it creates an empty table and fills it by applying every stored event, as for a new projection. The old table stays in the file, unused.

To keep the table, give the renamed class its old table name with `@Modifier.config("sql.table_name", ...)`. The class name is part of the fingerprint, so the engine then rebuilds that table as for any other change:

```scala mdoc:compile-only
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

// Renamed from UserProfile; the annotation keeps the table `user_profile`
@Modifier.config("sql.table_name", "user_profile")
case class Profile(@Modifier.id userId: String, name: String)
object Profile {
  implicit val schema: Schema[Profile]         = Schema.derived[Profile]
  implicit val entityPath: EntityPath[Profile] = EntityPath.derived[Profile]
}
```

Renaming the projection, or moving it to another folder with its `EntityPath`, gives it a new file: the engine builds it from scratch from the stored events, and the old file stays. A migration registered with `registerMigration` is looked up under the new name.

## Changing Events

Events are never rewritten. Each stored event is a row of the event store's `events` table, with a sequence number, a tag and a payload. The tag says which case of the event type the event is, and the payload is the event as JSON, keyed by the case name:

```text
seq | tag          | payload
1   | UserLoggedIn | {"UserLoggedIn":{"userId":"user-1"}}
```

When events are read back, to catch up or to rebuild, the store keeps only the rows whose tag it knows, and decodes each payload with the current event type.

### Which Stored Events Are Read

The store knows the tags the event type has now, and the old names of renamed cases that you declared with a migration. Every read keeps only the events with those tags, including catching up and rebuilding. An event under any other tag, for example of a case that was removed, or renamed without a migration, is skipped without an error. When the store is created, it logs a warning that lists the tags it found in the database and cannot explain:

```text
Unknown event tags detected: Closed, Opened not in known tags Activated
```

So removing a case from the event type hides its stored events from every read, including a full rebuild.

### Renaming an Event Case

A renamed case keeps its old name in the stored events, in their tag and in their JSON. To go on reading them, describe the rename with a `Migration` of the event type to itself, and create the event store with it:

```scala
object SQLiteEventStore {
  def make[E: Schema](transactor: Transactor, migration: Migration[E, E]): Task[SQLiteEventStore[E]]
}
```

The other event store factories take a migration the same way; see [EventStore](./event-store.md#creating-a-store--make).

```scala mdoc:compile-only
import zio.*
import zio.blocks.projection.*
import zio.blocks.schema.Schema
import zio.blocks.schema.migration.Migration

sealed trait LoginEvent
object LoginEvent {
  case class UserAuthenticated(userId: String) extends LoginEvent // was UserLoggedIn
  implicit val schema: Schema[LoginEvent] = Schema.derived[LoginEvent]
}

val renames: Migration[LoginEvent, LoginEvent] =
  Migration.newBuilder[LoginEvent, LoginEvent].renameCase("UserLoggedIn", "UserAuthenticated").build

def openEvents(cache: TransactorCache): Task[SQLiteEventStore[LoginEvent]] =
  cache.get("events/logins.db").flatMap(tx => SQLiteEventStore.make[LoginEvent](tx, renames))
```

The store then:

- reads the events saved under the old name too, also when a read asks only for the new tag;
- decodes them as the renamed case;
- reports them under the new tag, so the handlers only need `on[UserAuthenticated]`;
- saves new events under the new name.

The stored events themselves are not changed. The rules:

- Only `renameCase` steps make old names readable. Do not rely on other steps of the migration to change stored events.
- If a case was renamed more than once, list every rename: `UserLoggedIn` to `UserSignedIn`, then `UserSignedIn` to `UserAuthenticated`, makes both old names read as `UserAuthenticated`.
- Keep the renames for as long as events with the old names exist.

The example below appends an event with the old version of the event type, then reads it back through a store created with the rename. It also prints the aliases the store works out:

```scala mdoc:embed:projection-examples/src/main/scala/projection/examples/Example10TagRename.scala

```

It prints `seq 1: tag UserAuthenticated, user-1, UserAuthenticated(user-1,10:00)` for the event stored as `UserLoggedIn`.

### Numeric Tags — `@eventTag`

```scala
final class eventTag(val value: Int) extends StaticAnnotation
```

By default, the tag of an event is the name of its case. `@eventTag(n)` on a case makes the tag the number `n` instead. Only the tag changes: the payload stays keyed by the case name. The numbers are not checked for uniqueness.

Renaming a numbered case needs care. The number still matches, but the stored payloads carry the old case name, so they no longer decode, and the read fails. `renameCase` does not help, because it works on tags, not on payloads. Instead, keep the old name readable with [`@Modifier.alias`](../schema/modifier.md#alias) on the renamed case:

```scala mdoc:compile-only
import zio.blocks.projection.eventTag
import zio.blocks.schema.{Modifier, Schema}

sealed trait LoginEvent
object LoginEvent {
  // Renamed from UserLoggedIn, which was stored with the tag 1
  @eventTag(1)
  @Modifier.alias("UserLoggedIn")
  case class UserAuthenticated(userId: String) extends LoginEvent

  @eventTag(2)
  case class UserLoggedOut(userId: String) extends LoginEvent

  implicit val schema: Schema[LoginEvent] = Schema.derived[LoginEvent]
}
```

Removing `@eventTag` from a case changes its tag back to the case name, so the events stored under the number are no longer read; see [Which Stored Events Are Read](#which-stored-events-are-read).

### Changing the Fields of an Event

Stored payloads are not migrated. An old event is decoded with the current case class, and the JSON codec decides what it becomes:

- **Added field:** it takes the field's default value, `None` for an `Option`, or an empty collection. A field with none of these makes the event unreadable, with `missing required field`.
- **Removed field:** its stored value is ignored.
- **Renamed field:** the new name counts as missing, unless the field keeps its old name as an alias: `@Modifier.alias("owner") holder: String`.
- **Changed type:** the event is unreadable unless the stored value still fits the new type, as a `String` fits an `Option[String]`.

:::caution
A field that holds its default value is not written to the payload. If you change the default later, the old events that had the default read back with the new one.
:::

So add a field as an `Option` or with a default, and never change a default or a type: add a new field or a new case instead.

The example below stores two events with the first version of an event type, then reads them back with two newer versions:

```scala mdoc:embed:projection-examples/src/main/scala/projection/examples/Example11EventFieldChanges.scala

```

It prints `V2 reads seq 1: AccountOpened(alice,EUR,None,List())`, and for the third version `missing required field "country"`.

An event that cannot be decoded fails the whole read:

- While catching up, the engine stops reading that source and logs `bind failed`. It does not follow the source's new events either, until the application is restarted with code that can read the event.
- During a full rebuild, the replay stops at that event and is logged. The rows hold only the events before it, yet the new fingerprint is saved and the watermark stays at 0. When the application next starts with fixed code, the engine catches up from the first event on top of those rows, so the events the rebuild had applied are applied twice. After fixing the code, [rebuild on demand](#rebuilding-on-demand) instead.

## Limitations

These cases are not handled yet:

- **Several sources.** A projection that reads several event stores keeps one watermark for all of them, although each store numbers its events from 1. An event appended to one source while the application is stopped can fall below the watermark set by another source, and is then never applied. A full rebuild replays only the first source, and the events of the others below the watermark are lost; deleting the store file does not help.
- **Cross-entity projections.** A full rebuild writes every row to the main file and leaves the shard files as they were. Later events for those rows fail with `no column named ...`, and queries return the stale rows of the main file. To rebuild such a projection, stop the application and delete the main file and its shard files.
- **Engines without event stores.** An engine created with `make` or `makeWithConfig` has no event stores, so it cannot rebuild: it logs `no EventStore for source ...`, keeps the old fingerprint, and queries fail on the changed table.
- **`TestEngine`.** It starts the engine inside `make`, on new in-memory stores, so a test can neither store an old fingerprint first nor register a migration before `start`. Test a migration with two engines in a row on SQLite files, as the examples on this page do.

## Advanced: Running the Steps Yourself

:::note[Advanced]
You do not need this section to migrate a projection: the engine runs these steps for you. It documents the building blocks, for tests and for custom runners.
:::

### `SchemaEvolution`

```scala
object SchemaEvolution {
  def needsRebuild[A: Schema](store: ProjectionStore[A]): Task[Boolean]
  def rebuild[A](store: ProjectionStore[A], eventStore: EventStore[_], spec: Projection[A], currentHash: String): Task[Unit]
  def rebuildWithHash[A: Schema: EntityPath](store: ProjectionStore[A], eventStore: EventStore[_], spec: Projection[A]): Task[Unit]
  def isSimpleAddFieldMigration(migration: Migration[?, ?]): Boolean
  def tryMigrationShortcut[A: Schema: EntityPath](
    store: ProjectionStore[A],
    migrationOpt: Option[Migration[?, A]],
    currentHash: String
  ): Task[Boolean]
  def checkAndRebuild[A: Schema: EntityPath](
    store: ProjectionStore[A],
    eventStore: EventStore[_],
    spec: Projection[A],
    config: SchemaEvolutionConfig = SchemaEvolutionConfig.default,
    migrationOpt: Option[Migration[?, A]] = None
  ): Task[Boolean]
}
```

- `needsRebuild` is `true` when a fingerprint is stored and differs from the current one. It is `false` for a new store.
- `rebuild` does the [full rebuild](#full-rebuild) and saves the fingerprint you pass; `rebuildWithHash` computes the fingerprint from `A`.
- `isSimpleAddFieldMigration` says whether a migration qualifies for the shortcut: it is not empty, and every step adds a top-level field. It is useful in a test.
- `tryMigrationShortcut` runs the shortcut and returns `true`, or returns `false` when there is no migration or it does not qualify.
- `checkAndRebuild` compares the fingerprints, then runs the shortcut or a rebuild, like the engine does on `start`. It returns `true` when it changed the store.

`SchemaEvolutionConfig` configures these helpers, not the engine:

```scala
final case class SchemaEvolutionConfig(
  rebuildParallelism: Int = 4,
  lazyRebuild: Boolean = false,
  enableMigrationShortcut: Boolean = true
)
```

- `enableMigrationShortcut = false` makes `checkAndRebuild` always rebuild.
- `rebuildParallelism` is how many projections `bulkRebuildPending` rebuilds at the same time.
- `lazyRebuild` is not read.

The other members of `SchemaEvolution`, `tryMigrationShortcutForSpec`, `registerMigration`, `lazyRebuildIfNeeded` and `bulkRebuildPending`, are the engine's own building blocks and are not meant to be called directly. A custom store takes part in a rebuild and in the shortcut through `recreateTable` and `addColumn`; see [ProjectionStore](./projection-store.md).

### `TagInfo` and `TagResolver`

The event store gathers what it knows about tags in a `TagInfo`, built by `TagResolver` from the event type and the migration:

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

- `aliases` maps each old tag to the current tag it became, with chains of renames followed.
- `currentTags` are the tags of the event type now, and `allTags` adds the old ones.
- `migration` is the migration the store was given.
- `isOldTag` and `currentTagFor` look a tag up in `aliases`. `normalize` returns the current tag for an old one, and any other tag unchanged.
- `expandRequested` widens the tags that a read asks for with their old names. An empty request means all the known tags.
- `unknownTags` returns the tags found in the database that are neither current nor old; they make the startup warning.
- `migrateValue` runs the migration on a decoded value. The store uses it only as a fallback, when a renamed event does not decode after its old case name was replaced.

```scala
object TagResolver {
  def resolve[E: Schema]: TagInfo
  def resolve[E: Schema](migration: Migration[E, E]): TagInfo
  def resolveOpt[E](opt: Option[Migration[E, E]])(using schema: Schema[E]): TagInfo
  def resolveImplicit[E: Schema](implicit migration: Migration[E, E] = null): TagInfo
  inline def resolveAuto[E](using schema: Schema[E]): TagInfo
  def currentTags[E](using schema: Schema[E]): Set[String]
  def allTags[E: Schema](migration: Migration[E, E] = null): Set[String]
  def aliases[E: Schema](migration: Migration[E, E] = null): Map[String, String]
  def aliasMap[E](migration: Migration[E, E]): Map[String, String]
  def aliasMapFromDynamic(dm: DynamicMigration): Map[String, String]
  def unknownTags(tagInfo: TagInfo, distinctDbTags: Set[String]): Set[String]
}
```

- `resolve` builds the `TagInfo` of an event type, with the aliases of a migration when you give one; `SQLiteEventStore.make` uses it. `resolveOpt` takes the migration as an `Option`, and `resolveImplicit` and `resolveAuto` use a migration found in implicit scope, if there is one.
- `currentTags`, `allTags` and `aliases` each return one part of the `TagInfo`. `aliasMap` gives the aliases of a migration with chains followed, and `aliasMapFromDynamic` the renames of a `DynamicMigration`, without following chains.
- `unknownTags` is the same as `TagInfo#unknownTags`.

The `TagResolver[E]` trait is a view of one `TagInfo`: implement `tagInfo`, and `aliases`, `allTags` and `currentTags` come from it. For how the event store uses its `TagInfo`, see [EventStore](./event-store.md#the-stores-tags--taginfo).

## See Also

- [ProjectionEngine](./engine.md) — `start`, `query` and the engine's configuration.
- [EventStore[E]](./event-store.md) — the event store and its factories.
- [ProjectionStore[A]](./projection-store.md) — the store hooks `recreateTable` and `addColumn`.
- [Testing](./testing.md) — the in-memory helpers.
- [Migration](../schema/migration.md) — building a `Migration`: `addField`, `renameCase` and the other steps.
- [Modifier](../schema/modifier.md) — `alias`, `rename` and `config`.
