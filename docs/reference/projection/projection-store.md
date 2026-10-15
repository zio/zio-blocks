---
id: projection-store
title: "ProjectionStore[A]"
sidebar_label: "ProjectionStore[A]"
description: "The read side of the projection module: ProjectionStore[A], where the rows of a read model, the watermark and the schema hash are kept, with FieldUpdate and the SQLite and in-memory implementations."
keywords:
  - "Event Sourcing"
  - "Read Models"
  - "Projection Store"
  - "FieldUpdate"
  - "SQLite"
---

`ProjectionStore[A]` is the read side of the module: a persistence contract for projection rows. Each `ProjectionStore[A]` also holds the engine's watermark and the schema hash alongside the entity rows:

```scala
import zio._
import zio.blocks.projection._

trait ProjectionStore[A] {
  // Row mutations
  def insert(a: A): Task[Unit]
  def upsert(a: A): Task[Unit]
  def updateFields(entityId: String, updates: Chunk[FieldUpdate]): Task[Unit]
  def delete(entityId: String): Task[Unit]
  def truncate: Task[Unit]

  // Row reads
  def findById(entityId: String): Task[Option[A]]

  // Watermark — last successfully processed event seq
  def getLastProcessedSeq: Task[Long]
  def updateLastProcessedSeq(seq: Long): Task[Unit]

  // Schema fingerprint — triggers rebuild when changed
  def getSchemaHash: Task[Option[String]]
  def updateSchemaHash(hash: String): Task[Unit]

  // Schema evolution — these two have defaults
  def recreateTable(): Task[Unit] = truncate
  def addColumn(columnName: String, sqlType: String): Task[Unit] = ZIO.unit
}
```

`ProjectionStore#upsert` uses `INSERT ... ON CONFLICT DO UPDATE` so no read-before-write is needed. `ProjectionStore#updateFields` runs inside a single transaction: it inserts a default row first with `INSERT OR IGNORE`, then runs one `UPDATE` for each `FieldUpdate`.

`recreateTable` and `addColumn` are what [schema evolution](./engine.md#schema-evolution) uses. A full rebuild calls `recreateTable` to start from an empty table, and the migration shortcut calls `addColumn` for each new field. The defaults only truncate the rows and do nothing, which is what the in-memory store uses. `SQLiteProjectionStore` overrides both: `recreateTable` drops and recreates the table, and `addColumn` adds the column if it does not exist yet, after checking the name against `^[a-zA-Z_][a-zA-Z0-9_]*$`.

## FieldUpdate

`FieldUpdate` describes a single field-level mutation and is used inside a `ProjectionAction.Update`:

```scala
import zio.blocks.projection._

enum FieldUpdate {
  case Set(field: String, value: Any)     extends FieldUpdate
  case Increment(field: String, by: Long) extends FieldUpdate
  case Decrement(field: String, by: Long) extends FieldUpdate
  case Max(field: String, value: Long)    extends FieldUpdate
  case Min(field: String, value: Long)    extends FieldUpdate
}
```

`Increment`, `Decrement`, `Max`, and `Min` are atomic in SQLite (`COALESCE(col, 0) + ?`, `MAX(col, ?)`, etc.), so no separate read is required. Field names can be given as raw snake_case strings (`FieldUpdate.Increment("user_count", 1L)`) or via a compile-time selector that maps Scala field names automatically (`FieldUpdate.increment[Stats](_.userCount)`).

## Implementations

`SQLiteProjectionStore[A]` is the production implementation on the JVM. `ProjectionEngine.make` creates one for each projection, storing it in a separate SQLite file under `<entityPath.basePath>/<name>.db`; it needs `sqlite-jdbc` on the runtime classpath. Each file runs in WAL mode so readers never block writers.

`InMemoryProjectionStore[A]` is a `Map`-backed implementation intended for tests. It simulates `FieldUpdate` operations by decoding the stored entity into a [`DynamicValue`](../schema/dynamic-value.md), applying the mutation, and re-encoding. `TestEngine.make` creates one automatically per projection.

## See Also

- [Projection module overview](./index.md) — how the event store, the projections, the engine and the projection stores fit together.
- [Projection[A]](./projection.md) — the handlers whose actions are applied to a projection store, and [Aggregating Fields](./projection.md#aggregating-fields) for `FieldUpdate` in a projection.
- [ProjectionEngine](./engine.md) — the runtime that writes to the projection stores and creates the SQLite ones.
- [Testing](./testing.md) — `InMemoryProjectionStore` and the other in-memory helpers.
