---
id: projection-store
title: "ProjectionStore[A]"
sidebar_label: "ProjectionStore[A]"
description: "Where the engine keeps the rows of a read model, with its watermark and schema hash: ProjectionStore[A] and its SQLite and in-memory stores."
keywords:
  - "Event Sourcing"
  - "Read Models"
  - "SQLite"
  - "ProjectionStore"
---

`ProjectionStore[A]` is where the rows of one read model `A` are kept. Next to the rows, it keeps the watermark (the number of the last event applied) and the schema hash (a fingerprint of the row type):

```scala
trait ProjectionStore[A] {
  // Rows
  def insert(a: A): Task[Unit]
  def upsert(a: A): Task[Unit]
  def updateFields(entityId: String, updates: Chunk[FieldUpdate]): Task[Unit]
  def delete(entityId: String): Task[Unit]
  def truncate: Task[Unit]
  def findById(entityId: String): Task[Option[A]]

  // Watermark and schema hash
  def getLastProcessedSeq: Task[Long]
  def updateLastProcessedSeq(seq: Long): Task[Unit]
  def getSchemaHash: Task[Option[String]]
  def updateSchemaHash(hash: String): Task[Unit]

  // Used when the row type changes
  def recreateTable(): Task[Unit] = truncate
  def addColumn(columnName: String, sqlType: String): Task[Unit] = ZIO.unit
}
```

:::note
You rarely call these methods yourself: the engine does. You create a store to pass to `ProjectionEngine.makeWithStores`, or implement the trait for another database.
:::

## Motivation

Events record everything that happened, but they answer questions such as "what is Alice's email now?" badly: you would have to replay all of her events every time. A read model keeps the current answer instead, one row per user, and updates it as events arrive.

`ProjectionStore[A]` is where those rows live. Next to the rows it keeps the watermark, so that the engine knows where to continue after a restart, and a fingerprint of the row type, so that the engine notices when the row class changes. Handlers never write SQL: the engine turns their actions into calls on the store. The same interface works on a SQLite file in production and on an in-memory map in tests.

## Usage

There are two stores:

- `SQLiteProjectionStore` keeps the rows in a SQLite file. Create one with `SQLiteProjectionStore.make[A](path, cache)` to pass to `ProjectionEngine.makeWithStores`, as the [ProjectionEngine example](./projection-engine.md#usage) does. The folder of `path` must exist.
- `InMemoryProjectionStore` keeps the rows in memory, for tests; see [Testing](./testing.md#inmemoryprojectionstore).

The other engine factories create their SQLite stores themselves.

:::note[Writing your own store]
`recreateTable` and `addColumn` have defaults that only empty the rows and do nothing. That is enough for a store without tables, such as the in-memory one. A store with tables must override both, or a changed row type will not fit its table; see [Changing the Read Model](./migration.md#changing-the-read-model).
:::

## See Also

- [Projection module overview](./index.md) — how the event store, the projections, the engine and the projection stores fit together.
- [Projection[A]](./projection.md) — the handlers whose actions the engine applies to a store, and [Aggregating Fields](./projection.md#aggregating-fields) for `FieldUpdate`.
- [ProjectionEngine](./projection-engine.md) — the runtime that calls the store, and creates the SQLite stores for most factories.
- [Testing](./testing.md) — `InMemoryProjectionStore` and the other in-memory helpers.
- [Migration](./migration.md) — the full rebuild and the add-column shortcut, which call `recreateTable` and `addColumn`.
