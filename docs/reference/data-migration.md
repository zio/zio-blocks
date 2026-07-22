---
id: data-migration
title: "SQL Data Migration"
---

The `zio.blocks.data.migration` module provides three execution models for evolving database schemas online. It builds on [`Migration[A, B]`](./schema/migration.md) for the typed row transformation and [`Repo[E, ID]`](./sql.md) for database access. No hand-written SQL, no XML, no Liquibase-style migration files.

## Overview

Data migration in ZIO Blocks is split into three execution tiers, each suited to a different scale of change:

| Model | Class | When to use |
|---|---|---|
| **A-Tiny** | `TinyMigrator` | DDL-only changes at startup (add column, rename table) |
| **A-Small** | `SmallMigrator` | Queue-based batch backfill for moderate tables |
| **B-Large** | `LargeMigrator` | Incremental worker with pause/resume and safe cutover for large tables |

All three share the same building blocks:

- `Migration[A, B]` from `zio.blocks.schema.migration` defines the typed transformation
- `Repo[E, ID]` from `zio.blocks.sql` provides database access for source and target
- `TargetStrategy` controls whether rows are updated in place or written to a shadow table
- `QueueTable` tracks dirty keys that need processing

The module is Scala 3 only.

## Execution Models

### A-Tiny: `TinyMigrator`

`TinyMigrator` runs DDL migrations at application startup through `Transactor.transact`. Extend it and override `run()` to issue schema changes:

```scala
import zio.blocks.data.migration._
import zio.blocks.sql.Transactor

object AddAgeColumn extends TinyMigrator {
  def run(using Transactor): Unit =
    // DDL statements executed at startup
    ???
}
```

Tiny migrations are appropriate when the schema change is fast, non-blocking, and can run before the application starts serving requests.

### A-Small: `SmallMigrator`

`SmallMigrator` is a queue-based batch worker for moderate-sized tables. It reads dirty keys from a `QueueTable`, fetches the source row, applies the `Migration[A, B]`, and writes the result to the target.

The lifecycle has three steps:

1. `init()` creates the queue table and capture triggers
2. `processBatch()` claims and processes a batch of dirty keys
3. `complete()` finalizes the migration (swaps shadow table if applicable)

Call `processBatch()` in a loop until the queue is empty, then call `complete()`:

```scala
import zio.blocks.data.migration._

// Assuming smallMigrator is already constructed (see full example below)
smallMigrator.init()

// Process batches until the queue drains
var pending = true
while (pending) {
  val count = smallMigrator.processBatch()
  pending = count > 0
}

smallMigrator.complete()
```

`SmallMigrator` supports both `TargetStrategy.InPlace` and `TargetStrategy.ShadowTable`.

### B-Large: `LargeMigrator`

`LargeMigrator` is an incremental worker designed for large tables where a full backfill would take too long. It adds pause/resume capability, concurrent worker support on PostgreSQL via `SKIP LOCKED`, and a safe completion protocol that guarantees no data is lost during cutover.

The lifecycle has four steps:

1. `init()` creates the queue table and capture triggers
2. `fence()` signals cutover and stops new producers from writing to the old table
3. `drain()` processes all remaining queue items until the queue is empty
4. `complete()` swaps the shadow table and finalizes the migration

```scala
import zio.blocks.data.migration._

// Assuming largeMigrator is already constructed (see full example below)
largeMigrator.init()

// ... application runs, dirty keys accumulate ...

// Cutover: stop writers, drain remaining, swap
largeMigrator.fence()
val totalDrained = largeMigrator.drain()
largeMigrator.complete()
```

`LargeMigrator` requires `TargetStrategy.ShadowTable`. The safe completion protocol ensures that every dirty key is processed before the shadow table replaces the source.

## Target Strategies

`TargetStrategy` controls where migrated rows are written:

- **`TargetStrategy.InPlace`** updates rows directly in the source table. Suitable when the schema change is backward compatible and the table structure does not change.
- **`TargetStrategy.ShadowTable(suffix)`** creates a shadow table with the target schema, writes migrated rows there, and swaps the shadow table in at completion. The suffix is appended to the source table name to derive the shadow table name. Suitable when the schema change is not backward compatible or when you need a clean cutover.

```scala
TargetStrategy.InPlace
TargetStrategy.ShadowTable("_v2")
```

## Safe Completion Protocol (B-Large)

`LargeMigrator` follows a stateful protocol to guarantee zero data loss during cutover. The protocol moves through four states:

```
Initialized → Fenced → Drained → Completed
```

### `init()` (Initialized)

Creates the queue table and capture triggers on the source table. While initialized, every `INSERT`, `UPDATE`, or `DELETE` on the source table upserts the affected primary key into the queue table in the same transaction. The source table remains authoritative.

### `fence()` (Fenced)

Signals that cutover is starting. After fencing, source-table triggers abort new writes. On PostgreSQL, the worker acquires the source-table write-conflicting lock before marking the state as fenced. On SQLite, `BEGIN IMMEDIATE` serializes this step. No new dirty keys are produced after fencing.

### `drain()` (Drained)

Processes all remaining dirty keys until the queue is empty. Returns the total number of rows processed. Because fencing stopped new writes, the queue is guaranteed to reach zero.

### `complete()` (Completed)

Swaps the shadow table to replace the source table and cleans up capture triggers and the queue table. After completion, the migration is done and the target table is live.

The protocol ensures that every row modified between `init()` and `complete()` is migrated, even if the application crashes and restarts mid-migration. The queue table persists across restarts.

## ID Type Flexibility

`LargeMigrator` and `SmallMigrator` accept separate `ID1` and `ID2` type parameters, allowing the V1 and V2 repos to use different ID types during migration:

```scala
SmallMigrator[UserV1, UserV2, Int, Long](...)
LargeMigrator[UserV1, UserV2, Int, Long](...)
```

The queue table stores V1 IDs (`ID1`). The `ID1` type must have a `DbCodec` instance available as a given. This flexibility lets you migrate between repos that use different key types without forcing a unified key schema.

## Queue Primitives

`QueueTable` is the dirty-key tracking mechanism shared by `SmallMigrator` and `LargeMigrator`. The queue table has three columns:

| Column | Type | Description |
|---|---|---|
| `id` | `TEXT NOT NULL PRIMARY KEY` | The primary key of the affected source row |
| `op` | `TEXT NOT NULL DEFAULT 'I'` | The operation type: `'I'` (insert), `'U'` (update), or `'D'` (delete) |
| `payload` | `TEXT` | JSON-serialized V1 row data, captured only for `'D'` operations on PostgreSQL |

Queue entries are coalesced by primary key. When a source row is inserted, updated, or deleted, capture triggers upsert the affected key into the queue table in the same transaction. For `INSERT` and `UPDATE`, only the key and operation type are stored. For `DELETE`, PostgreSQL captures the full row as JSON via `row_to_json(OLD.*)::text` so the worker can recover the V1 data even though the source row is gone. SQLite does not support `row_to_json`, so the payload is `None` for delete entries on that dialect.

| Operation | Description |
|---|---|
| `QueueTable.create` | Creates the queue table and installs capture triggers on the source table |
| `enqueue` | Inserts the ID into the queue (the trigger fills `op` and `payload` automatically) |
| `dequeue` | Claims a batch of entries, returning `(id, op, payload)` tuples |
| `pending` | Returns the number of unprocessed keys |

PostgreSQL uses `FOR UPDATE SKIP LOCKED` for concurrent worker claims. SQLite uses a single consumer with `BEGIN IMMEDIATE` and a busy timeout.

A worker claims the dirty key and, in one transaction, processes the entry based on its operation type. For `'I'` and `'U'` entries, the worker rereads the source row without taking a source-row lock, applies the `Migration[A, B]`, and upserts the result into the target. For `'D'` entries, the worker deserializes the stored payload JSON via `migration.sourceSchema.jsonCodec` to recover the V1 entity, applies the migration to produce the V2 identity, and then handles the delete according to the target strategy:

- **`TargetStrategy.InPlace`**: The source row is already gone, so the delete is a no-op.
- **`TargetStrategy.ShadowTable`**: The worker deletes the corresponding V2 row from the shadow table using the recovered identity.

If the source row is missing at processing time for an `'I'` or `'U'` entry (deleted between enqueue and dequeue), the entry is silently skipped since `findAll` returns only the rows that still exist.

## Database Support

| Feature | PostgreSQL | SQLite |
|---|---|---|
| `TinyMigrator` | Yes | Yes |
| `SmallMigrator` | Yes | Yes |
| `LargeMigrator` | Yes (multi-worker) | Yes (single-worker) |
| `SKIP LOCKED` | Yes | No |
| `ShadowTable` (CREATE TABLE LIKE, atomic swap) | Yes | No |
| `InPlace` updates | Yes | Yes |

PostgreSQL supports the full feature set including concurrent workers, shadow table creation via `CREATE TABLE LIKE`, and atomic DDL swap. SQLite supports queue primitives and in-place updates but does not support shadow tables or concurrent workers.

## Contextual Requirements

`TinyMigrator`, `SmallMigrator`, and `LargeMigrator` require a `Transactor` as an implicit constructor parameter. `SmallMigrator` and `LargeMigrator` also require a `DbCodec[ID1]` given for the queue table's primary key column.

## Full Example

```scala
import zio.blocks.schema._
import zio.blocks.schema.migration.Migration
import zio.blocks.sql.{DbCodec, DbCodecDeriver, Repo, Table, Transactor}
import zio.blocks.data.migration._

case class UserV1(id: Int, name: String)
case class UserV2(id: Int, name: String, age: Int)

object UserV1 { implicit val schema: Schema[UserV1] = Schema.derived }
object UserV2 { implicit val schema: Schema[UserV2] = Schema.derived }

val v1Table = Table.derived[UserV1]
val v2Table = Table.derived[UserV2]

implicit val codecInt: DbCodec[Int] = implicitly[Schema[Int]].deriving(DbCodecDeriver).derive
implicit val codecV1: DbCodec[UserV1] = UserV1.schema.deriving(DbCodecDeriver).derive
implicit val codecV2: DbCodec[UserV2] = UserV2.schema.deriving(DbCodecDeriver).derive

val v1Repo = Repo(v1Table, "id", summon[DbCodec[Int]], (_: UserV1).id)
val v2Repo = Repo(v2Table, "id", summon[DbCodec[Int]], (_: UserV2).id)

// Migration that transforms UserV1 to UserV2
val migration: Migration[UserV1, UserV2] = Migration
  .newBuilder[UserV1, UserV2]
  .addField(_.age, SchemaExpr.literal(0))
  .build

// SmallMigrator: queue-based batch processing
val smallMigrator = SmallMigrator[UserV1, UserV2, Int, Int](
  repoV1 = v1Repo,
  repoV2 = v2Repo,
  migration = migration,
  queueTable = "user_migration_q",
  batchSize = 100,
  target = TargetStrategy.ShadowTable("_v2")
)(using ???, summon[DbCodec[Int]])

// Lifecycle: init, process batches, complete
smallMigrator.init()
// ... loop processBatch() ...
smallMigrator.complete()

// LargeMigrator: incremental worker with safe completion protocol
val largeMigrator = LargeMigrator[UserV1, UserV2, Int, Int](
  repoV1 = v1Repo,
  repoV2 = v2Repo,
  migration = migration,
  queueTable = "user_migration_q",
  batchSize = 100,
  target = TargetStrategy.ShadowTable("_v2")
)(using ???, summon[DbCodec[Int]])

// Safe lifecycle: init, fence, drain, complete
largeMigrator.init()
largeMigrator.fence()
val total = largeMigrator.drain()
largeMigrator.complete()
```

## Failure Policy

When a worker transaction fails, the transaction is rolled back and the dirty key is retained in the queue table. The failure is returned to the caller. No data is silently lost, and no automatic retry or dead-letter configuration is applied. The caller decides how to handle the failure.

## Architecture Decisions

For a detailed record of architecture decisions, see the [ADR](../adr/2026-07-18-data-migration.md).
