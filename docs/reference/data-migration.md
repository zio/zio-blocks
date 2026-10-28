---
id: data-migration
title: "Data Migration"
---

The `zio.blocks.data.migration` module provides three execution models for evolving database schemas online. It builds on [`Migration[A, B]`](./schema/migration.md) for the typed row transformation and [`Repo[E, ID]`](./sql/index.md) for database access. The row conversion is a typed `Migration`, not hand-written SQL, XML or Liquibase-style migration files; the schema changes themselves (DDL) are SQL statements you run, for example with `TinyMigrator`.

## Overview

Data migration in ZIO Blocks is split into three execution tiers, each suited to a different scale of change:

| Model       | Class           | When to use                                                            |
| ----------- | --------------- | ---------------------------------------------------------------------- |
| **A-Tiny**  | `TinyMigrator`  | DDL-only changes at startup (add column, rename table)                 |
| **A-Small** | `SmallMigrator` | Queue-based batch backfill for moderate tables                         |
| **B-Large** | `LargeMigrator` | Incremental worker with pause/resume and safe cutover for large tables |

All three share the same building blocks:

- `Migration[A, B]` from `zio.blocks.schema.migration` defines the typed transformation
- `Repo[E, ID]` from `zio.blocks.sql` provides database access for source and target
- `TargetStrategy` controls whether rows are updated in place or written to a shadow table
- `QueueTable` tracks dirty keys that need processing

The module is Scala 3 only.

## Execution Models

The examples on this page move a `users` table from `UserV1` rows to `UserV2` rows, which add an `age` column. Both repositories name the same table, `users`: the migrators read each row through the first and write it through the second, and for a shadow table they derive the shadow table's name from the second:

```scala mdoc:silent
import zio.blocks.schema._
import zio.blocks.schema.migration.Migration
import zio.blocks.data.migration._
import zio.blocks.sql._

case class UserV1(id: Long, name: String)
case class UserV2(id: Long, name: String, age: Int)

object UserV1 { implicit val schema: Schema[UserV1] = Schema.derived }
object UserV2 { implicit val schema: Schema[UserV2] = Schema.derived }

val usersV1 = Repo.derived[UserV1, Long]("users", "id", _.id)
val usersV2 = Repo.derived[UserV2, Long]("users", "id", _.id)

val addAge: Migration[UserV1, UserV2] =
  Migration.newBuilder[UserV1, UserV2].addField(_.age, SchemaExpr.literal(0)).build
```

We keep `import zio.blocks.sql._` after the other `zio.blocks` imports: if your project also uses the `sql-zio` module, it brings a package named `zio` into scope, and a `zio.blocks` import after it fails to compile.

### A-Tiny: `TinyMigrator`

`TinyMigrator` runs DDL at application startup, in one transaction through `Transactor#transact`. Extend it, put the statements in `TinyMigrator#run`, and call `TinyMigrator#migrate`:

```scala mdoc:silent
class AddAgeColumn(transactor: Transactor) extends TinyMigrator(transactor) {
  def run()(using DbTx): Unit =
    Frag.literal("ALTER TABLE users ADD COLUMN age INTEGER NOT NULL DEFAULT 0").update
}

def addAgeColumn(transactor: Transactor): Unit = AddAgeColumn(transactor).migrate()
```

Tiny migrations are appropriate when the schema change is fast, non-blocking, and can run before the application starts serving requests. They convert no rows.

### A-Small: `SmallMigrator`

`SmallMigrator` is a queue-based batch worker for moderate-sized tables. It reads dirty keys from a `QueueTable`, fetches the source rows, applies the `Migration[A, B]`, and writes the results to the target.

The lifecycle has three steps:

1. `SmallMigrator#init` prepares the target table: it creates the shadow table for `TargetStrategy.ShadowTable`, and installs capture triggers when `captureTriggers = true`. It doesn't create the queue table; `QueueTable.create` does.
2. `SmallMigrator#processBatch` claims a batch of keys and converts their rows in one transaction.
3. `SmallMigrator#complete` finalizes the migration (swaps the shadow table in, if there is one). It refuses to run while keys are still queued.

`SmallMigrator#processBatch` returns the number of rows it wrote, which is `0` for a batch whose rows were all deleted before it ran, even while more keys wait behind it. So we loop until `QueueTable.pending` finds the queue empty, not until a batch returns `0`. The following in-place backfill runs after `AddAgeColumn`: it queues every existing row, converts the rows, and completes:

```scala mdoc:silent
def backfillInPlace()(using transactor: Transactor, dialect: Dialect): Unit = {
  QueueTable.create[Long]("users_age_q", transactor)
  transactor.transact(QueueTable.enqueue("users_age_q", usersV1.all.map(_.id)))

  val migrator = SmallMigrator[UserV1, UserV2, Long, Long](
    repoV1 = usersV1,
    repoV2 = usersV2,
    migration = addAge,
    queueTable = "users_age_q",
    batchSize = 500,
    target = TargetStrategy.InPlace
  )

  migrator.init()
  while (transactor.connect(QueueTable.pending("users_age_q")) > 0) migrator.processBatch()
  migrator.complete()
}
```

While an in-place backfill runs, the application queues the key of every row it writes with `QueueTable.enqueue`, in the same transaction as the write. Capture triggers can't do it here, because they would also queue the migrator's own writes (see [Queue Primitives](#queue-primitives)).

`SmallMigrator` supports both `TargetStrategy.InPlace` and `TargetStrategy.ShadowTable`.

### B-Large: `LargeMigrator`

`LargeMigrator` is an incremental worker designed for large tables where a full backfill would take too long. It adds `LargeMigrator#run` with `LargeMigrator#pause` and `LargeMigrator#resume`, concurrent workers on PostgreSQL via `SKIP LOCKED`, and a cutover protocol that converts every key still queued before the tables are swapped.

The lifecycle has five steps:

1. `LargeMigrator#init` prepares the target table and, with `captureTriggers = true`, installs the capture triggers.
2. `LargeMigrator#run` converts batches while the application keeps writing, until a batch writes no rows.
3. `LargeMigrator#fence` moves the migrator into its cutover state. It doesn't block writes: the application stops writing to the source table before calling it.
4. `LargeMigrator#drain` converts batches until a count finds the queue empty.
5. `LargeMigrator#complete` swaps the shadow table in and finalizes the migration.

The cutover is the last three steps. In 0.0.56, `LargeMigrator#complete` doesn't check the queue (a known bug; `SmallMigrator#complete` does), so we check `LargeMigrator#pendingCount` right before it:

```scala mdoc:silent
def cutOver(migrator: LargeMigrator[UserV1, UserV2, Long, Long]): Unit = {
  // Every writer to `users` has stopped before this point
  migrator.fence()
  migrator.drain()
  require(migrator.pendingCount == 0, "a write reached the queue after drain(); stop all writers first")
  migrator.complete()
}
```

`LargeMigrator` is typically used with `TargetStrategy.ShadowTable` and capture triggers. [Full Example](#full-example) puts every step into one program for PostgreSQL.

## Target Strategies

`TargetStrategy` controls where migrated rows are written:

- **`TargetStrategy.InPlace`** updates rows in the table that both repositories name. The table needs the new columns before the first batch, for example from a `TinyMigrator`, so it suits changes where the old and new columns can live side by side for a while.
- **`TargetStrategy.ShadowTable(suffix)`** writes migrated rows into `<table>_<suffix>`, where `<table>` is the V2 repository's table, and at completion renames `<table>` to `<table>_old_<suffix>` and the shadow table to `<table>`. `init()` creates the shadow table with `CREATE TABLE IF NOT EXISTS <table>_<suffix> (LIKE <table> INCLUDING ALL)`, a copy of the table's current columns, not of the V2 columns. When the migration changes columns, create the shadow table yourself with the new columns before `init()`; `IF NOT EXISTS` then leaves it alone. Shadow tables need PostgreSQL.

With `usersV2` as the V2 repository, the two strategies look like this:

```scala mdoc:compile-only
val inPlace: TargetStrategy = TargetStrategy.InPlace
val shadow: TargetStrategy  = TargetStrategy.ShadowTable("v2") // shadow table: users_v2
```

## Completion Protocol (B-Large)

`LargeMigrator` keeps its cutover state in memory and moves through four states:

```
Initialized → Fenced → Drained → Completed
```

Each lifecycle method below moves it to the next state.

### `init()` (Initialized)

Prepares the target table. For `TargetStrategy.ShadowTable`, it creates the shadow table, as described in [Target Strategies](#target-strategies), and with `captureTriggers = true` it installs the capture triggers. The source table continues to accept writes normally. `init()` doesn't create the queue table, and it doesn't queue the rows that already exist: the triggers see only the writes made after `init()`. Create the queue with `QueueTable.create` before `init()`, and queue the existing keys after it, so that no write falls between the two.

### `fence()` (Fenced)

Moves the migrator into its cutover state. It changes nothing in the database and blocks no write: the application **must** stop writing to the source table before calling `fence()`. A write that still arrives is queued by its trigger, and `drain()` converts it if it lands before `drain()` returns.

### `drain()` (Drained)

Converts batches until a `COUNT` of the queue table finds it empty, then returns the number of rows it wrote. If batches come back empty while keys stay queued for 100 rounds in a row, for example because another worker holds their rows locked, `drain()` throws an `IllegalStateException`. A call to `pause()` makes `drain()` return early, and the migrator stays fenced.

### `complete()` (Completed)

For `TargetStrategy.ShadowTable`, renames the source table to `<table>_old_<suffix>` and the shadow table to `<table>` in one transaction; for `TargetStrategy.InPlace`, it changes no table. In 0.0.56, `complete()` doesn't check the queue (a known bug), so a write that lands after `drain()` returns is missing from the new table: check `LargeMigrator#pendingCount` first. The migrator leaves the old table, the queue table and the trigger function behind; drop them after verifying the result.

The protocol converts every key that is in the queue when `drain()` makes its final count. Rows that existed before `init()` are converted only if their keys were queued, and writes that land after `drain()` returns are not converted. The queue table survives a crash, so a restarted worker continues from the keys still queued. The cutover state does not: a new `LargeMigrator` starts in `Initialized` and needs its own `init()`, `fence()`, `drain()` and `complete()`. `init()` is safe to run again, because the shadow table's DDL uses `CREATE TABLE IF NOT EXISTS` and the triggers use `CREATE OR REPLACE TRIGGER`.

## ID Type Flexibility

`LargeMigrator` and `SmallMigrator` accept separate `ID1` and `ID2` type parameters for the source and target repos, but construction requires evidence that they are the same type (`ID1 =:= ID2`), as the examples above do with `SmallMigrator[UserV1, UserV2, Long, Long]`.

The queue table stores V1 IDs (`ID1`). The `ID1` type must have a `DbCodec` instance available as a given. The equality constraint keeps delete propagation type-safe: when a source row disappears between dequeue and read, the same key is reinterpreted for the target repo via the type evidence rather than a cast. Migrations that change the primary key type are out of scope for the built-in migrators.

## Queue Primitives

`QueueTable` is the dirty-key tracking mechanism shared by `SmallMigrator` and `LargeMigrator`. The queue table has three columns:

| Column    | Type                        | Description                                                                   |
| --------- | --------------------------- | ----------------------------------------------------------------------------- |
| `id`      | `TEXT NOT NULL PRIMARY KEY` | The primary key of the affected source row                                    |
| `op`      | `TEXT NOT NULL DEFAULT 'I'` | The operation type: `'I'` (insert), `'U'` (update), or `'D'` (delete)         |
| `payload` | `TEXT`                      | JSON-serialized V1 row data, captured only for `'D'` operations on PostgreSQL |

Queue entries are coalesced by primary key. There are two ways keys enter the queue:

1. **Manual enqueue** — the application calls `QueueTable.enqueue` for every row it writes (or for a batch of rows to backfill).
2. **Capture triggers** — pass `captureTriggers = true` when constructing `SmallMigrator` or `LargeMigrator`; `init()` then installs triggers via `QueueTable.installTriggers` so every source INSERT/UPDATE/DELETE upserts the affected key in the writer's own transaction. Trigger installation is idempotent (PostgreSQL requires PG 14+ for `CREATE OR REPLACE TRIGGER`). Do not enable capture triggers when the migrator writes to the same physical table it captures from: the worker's own writes would re-enqueue processed keys forever.

For `INSERT` and `UPDATE`, only the key and operation type are stored. For `DELETE`, PostgreSQL additionally captures the full row as JSON via `row_to_json(OLD)::text` into the `payload` column; the payload is reserved for future delete-recovery and is not read by the current workers. SQLite does not support `row_to_json`, so its delete entries carry no payload.

| Operation                    | Description                                                 |
| ---------------------------- | ----------------------------------------------------------- |
| `QueueTable.create`          | Creates the queue table (id, op, payload columns)           |
| `QueueTable.enqueue`         | Inserts the IDs into the queue, skipping IDs already queued |
| `QueueTable.dequeue`         | Claims and removes a batch of IDs, returning `List[ID]`     |
| `QueueTable.pending`         | Returns the number of unprocessed keys                      |
| `QueueTable.installTriggers` | Installs capture triggers on the source table               |

PostgreSQL uses `FOR UPDATE SKIP LOCKED` for concurrent worker claims. SQLite uses a single consumer with `BEGIN IMMEDIATE` and a busy timeout. Queue IDs are stored as TEXT, so batches are claimed in lexicographic order (`'10'` before `'9'`); this affects claim order only, never correctness.

A worker claims the dirty key and, in one transaction, looks up the source row by ID, applies the `Migration[A, B]`, and writes the result to the target. If the source row is missing at processing time (deleted between enqueue and dequeue), the corresponding target row is deleted — `findAll` returns only rows that still exist.

## Database Support

| Feature                                        | PostgreSQL         | SQLite              |
| ---------------------------------------------- | ------------------ | ------------------- |
| `TinyMigrator`                                 | Yes                | Yes                 |
| `SmallMigrator`                                | Yes                | Yes                 |
| `LargeMigrator`                                | Yes (multi-worker) | Yes (single-worker) |
| `SKIP LOCKED`                                  | Yes                | No                  |
| `ShadowTable` (CREATE TABLE LIKE, atomic swap) | Yes                | No                  |
| `InPlace` updates                              | Yes                | Yes                 |

PostgreSQL supports the full feature set including concurrent workers, shadow table creation via `CREATE TABLE LIKE`, and atomic DDL swap. SQLite supports queue primitives and in-place updates but does not support shadow tables or concurrent workers.

## Contextual Requirements

`TinyMigrator` takes a `Transactor` as a constructor parameter. `SmallMigrator` and `LargeMigrator` take four context parameters after their arguments: the `Transactor`, a `DbCodec[ID1]` for the queue table's key column, the `Dialect` whose SQL they generate, and the evidence that `ID1 =:= ID2`:

```scala
final class LargeMigrator[A, B, ID1, ID2](
  repoV1: Repo[A, ID1],
  repoV2: Repo[B, ID2],
  migration: Migration[A, B],
  queueTable: String,
  batchSize: Int,
  target: TargetStrategy,
  captureTriggers: Boolean = false
)(using transactor: Transactor, codecId: DbCodec[ID1], dialect: Dialect, ev: ID1 =:= ID2)
```

`SmallMigrator` has the same parameters.

## Full Example

The following program migrates the live `users` table on PostgreSQL to the `UserV2` shape through a shadow table, while the application keeps writing until the cutover. It creates the shadow table with the new columns and the queue table first, installs the capture triggers with `init()`, queues the rows that already exist, copies them with `run()`, and cuts over once the application's writers have stopped:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.migration.Migration
import zio.blocks.data.migration._
import zio.blocks.sql._

case class UserV1(id: Long, name: String)
case class UserV2(id: Long, name: String, age: Int)

object UserV1 { implicit val schema: Schema[UserV1] = Schema.derived }
object UserV2 { implicit val schema: Schema[UserV2] = Schema.derived }

// Both repositories name the live table
val usersV1 = Repo.derived[UserV1, Long]("users", "id", _.id)
val usersV2 = Repo.derived[UserV2, Long]("users", "id", _.id)

val addAge: Migration[UserV1, UserV2] =
  Migration.newBuilder[UserV1, UserV2].addField(_.age, SchemaExpr.literal(0)).build

def migrateUsers(stopWriters: () => Unit): Unit = {
  given transactor: Transactor =
    JdbcTransactor.fromUrl("jdbc:postgresql://localhost:5432/shop", "shop", "shop", SqlDialect.PostgreSQL)
  given Dialect = Dialect.Postgres

  // 1. The shadow table, with the V2 columns, and the queue table
  transactor.transact {
    Frag
      .literal("CREATE TABLE users_v2 (id BIGINT PRIMARY KEY, name TEXT NOT NULL, age INTEGER NOT NULL)")
      .update
  }
  QueueTable.create[Long]("users_age_q", transactor)

  val migrator = LargeMigrator[UserV1, UserV2, Long, Long](
    repoV1 = usersV1,
    repoV2 = usersV2,
    migration = addAge,
    queueTable = "users_age_q",
    batchSize = 500,
    target = TargetStrategy.ShadowTable("v2"),
    captureTriggers = true
  )

  // 2. Install the triggers, then queue the rows that already exist
  migrator.init()
  transactor.transact {
    Frag.literal("INSERT INTO users_age_q (id) SELECT CAST(id AS TEXT) FROM users ON CONFLICT DO NOTHING").update
  }

  // 3. Copy while the application writes; the triggers queue its writes
  migrator.run()

  // 4. Cut over: stop the writers, fence, drain, check the queue, swap
  stopWriters()
  migrator.fence()
  migrator.drain()
  require(migrator.pendingCount == 0, "a write reached the queue after drain(); stop all writers first")
  migrator.complete()
}
```

After `complete()`, `users` holds `UserV2` rows and the old rows are in `users_old_v2`. The queue stores keys as text, hence the `CAST`, and `ON CONFLICT DO NOTHING` skips a key that a trigger queued first. [How to Migrate Table Rows to a New Version Online](../guides/migrate-table-rows-online.md) runs this flow against PostgreSQL 16 with a writer thread, shows its output, and drops the old table, the queue table and the trigger function afterwards.

## Failure Policy

When converting a row fails, the migrator throws a `RuntimeException` (`Migration failed: ...`); a database error propagates as its own exception. Either way, the batch's transaction rolls back and every key of the batch stays in the queue, including the keys of rows that converted fine. No data is written for that batch, and no automatic retry or dead-letter configuration is applied: fix the cause, for example with a corrected migration in a new migrator, and process the queue again. The guide's [Recover from a Failed Batch and Restart](../guides/migrate-table-rows-online.md#recover-from-a-failed-batch-and-restart) section shows this.

## Architecture Decisions

For a detailed record of architecture decisions, see the [ADR](../adr/2026-07-18-data-migration.md).

## See Also

- [How to Migrate Table Rows to a New Version Online](../guides/migrate-table-rows-online.md) — a how-to guide that moves a table to a new row shape while the application keeps running.
- [Projection](./projection/index.md) — event-sourced read models that complement schema-migration workflows: while `DataMigration` evolves the write-side schema, `ProjectionEngine` rebuilds read-side views from the event log without touching source tables.
- [SQL Module](./sql/index.md) — the `Transactor`, `Repo`, and `Table` types shared by both the data-migration and projection modules.
