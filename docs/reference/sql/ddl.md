---
id: ddl
title: "Ddl"
description: "Reference for Ddl, the helper singleton that generates CREATE TABLE and DROP TABLE SQL fragments."
keywords:
  - "Ddl createTable"
  - "Ddl dropTable"
  - "ColumnDef SQL type"
  - "CREATE TABLE IF NOT EXISTS"
  - "DROP TABLE IF EXISTS"
---

`Ddl` is a helper object that generates DDL (Data Definition Language) SQL fragments for creating and dropping tables. `ColumnDef` is a simple data class pairing a column name with a SQL type string and a nullability flag.

Normally you don't call `Ddl` directly — [`Table#createTable`](./table.md) and `Table#dropTable` use it internally. You may need `Ddl` directly when building custom DDL tooling.

## Core API

To generate CREATE TABLE and DROP TABLE fragments, call the two methods on `Ddl`, with column metadata described by `ColumnDef`:

```scala
object Ddl {
  def createTable(tableName: String, columns: IndexedSeq[ColumnDef]): Frag
  def dropTable(tableName: String): Frag
}

final case class ColumnDef(name: String, sqlType: String, nullable: Boolean)
```

## Usage

Create a `ColumnDef` for each column, then pass them to `Ddl.createTable`:

```scala mdoc:reset
import zio.blocks.sql._
```

```scala mdoc:silent
val transactor: Transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
```

```scala mdoc
val columns = IndexedSeq(
  ColumnDef("id",         "INTEGER", nullable = false),
  ColumnDef("name",       "TEXT",    nullable = false),
  ColumnDef("created_at", "TEXT",    nullable = true)
)

val createFrag = Ddl.createTable("users", columns)
val dropFrag   = Ddl.dropTable("users")

// Execute the fragments
transactor.transact {
  createFrag.update
  // ... do work ...
  dropFrag.update
}
```

## How It Works

`Ddl` is typically used by `Table#createTable`, which derives `ColumnDef` from schema metadata:

1. `Table.derived[A]` builds a schema.
2. `Table#createTable(dialect)` converts schema columns to `ColumnDef` using `dialect.typeName`.
3. `Ddl.createTable` receives the `ColumnDef` list and generates the SQL fragment.

The generated statement declares no `PRIMARY KEY` or `UNIQUE` constraint. This is a known bug in 0.0.56: the ID column that [`Repo#insertOrUpdate`](./repo.md) relies on needs one, so until the bug is fixed, write the `CREATE TABLE` statement yourself with [`Frag.literal`](./frag.md).

You call `Ddl` directly only when you need custom DDL that doesn't fit the `Table` abstraction.

See [Table](./table.md) for the high-level DDL API.
