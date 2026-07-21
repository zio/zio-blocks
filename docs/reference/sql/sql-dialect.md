---
id: sql-dialect
title: "SqlDialect"
description: "Reference for SqlDialect, the interface for database-specific SQL type names and parameter placeholders."
keywords:
  - "SQL Dialect"
  - "PostgreSQL Dialect"
  - "SQLite Dialect"
---

`SqlDialect` is a sealed trait that encodes database-specific SQL details: `typeName` maps Scala types to DDL column type strings for `CREATE TABLE`, and `paramPlaceholder` returns the SQL token for bound parameters. Two built-in implementations are provided: `SqlDialect.PostgreSQL` and `SqlDialect.SQLite`.

## Core API

The core API is a sealed trait with two case objects for the built-in dialects:

```scala
sealed trait SqlDialect {
  def name: String
  def typeName(dbValue: DbValue): String
  def paramPlaceholder(index: Int): String
}

object SqlDialect {
  case object PostgreSQL extends SqlDialect
  case object SQLite     extends SqlDialect
}
```

## Usage

Access the dialect through `DbCon#dialect` in a transaction:

```scala
import zio.blocks.sql._

tx.connect { given con: DbCon =>
  val dialect = con.dialect  // PostgreSQL or SQLite
  
  // Get DDL type string for a value
  val intType = dialect.typeName(DbValue.DbInt(0))        // "INTEGER"
  val uuidType = dialect.typeName(DbValue.DbUUID(...))    // "UUID" (PostgreSQL) or "TEXT" (SQLite)
  
  // Get parameter placeholder (both return "?")
  val placeholder = dialect.paramPlaceholder(0)
}
```

## Key Differences

| Type           | PostgreSQL    | SQLite    |
|----------------|---------------|-----------|
| `DbLong`       | `BIGINT`      | `INTEGER` |
| `DbBoolean`    | `BOOLEAN`     | `INTEGER` |
| `DbInstant`    | `TIMESTAMPTZ` | `TEXT`    |
| `DbLocalDate`  | `DATE`        | `TEXT`    |
| `DbUUID`       | `UUID`        | `TEXT`    |
| `DbBigDecimal` | `NUMERIC`     | `TEXT`    |
| `DbBytes`      | `BYTEA`       | `BLOB`    |

PostgreSQL uses native types for most types; SQLite uses `INTEGER` for all integers, `TEXT` for temporal types and UUIDs, and `BLOB` for binary data.

## Operations

**`typeName(dbValue)`** — Returns the DDL type string for the given `DbValue` variant:

```scala
dialect.typeName(DbValue.DbInt(0))      // "INTEGER"
dialect.typeName(DbValue.DbString(""))  // "TEXT"
```

Used by `Table#createTable` to generate column definitions. The actual value wrapped in `DbValue` doesn't matter — only the variant determines the type.

**`paramPlaceholder(index)`** — Returns the SQL token for a bound parameter at the given zero-based index:

```scala
dialect.paramPlaceholder(0)  // "?"
dialect.paramPlaceholder(1)  // "?"
```

Both built-in dialects always return `"?"`. The method exists to support future dialects that use different conventions (e.g., `$1`, `$2`).

## How It Works

When you create a `Transactor`, you specify a `SqlDialect`. Inside a `connect` or `transact` block, that dialect is carried in the `DbCon` and used automatically by all SQL operations. When `Frag#sql(dialect)` renders a fragment to SQL, it calls `paramPlaceholder` for each parameter placeholder. When `Table#createTable(dialect)` generates DDL, it calls `typeName` for each column to look up the correct SQL type string. This way, the same Scala code produces correct SQL for either PostgreSQL or SQLite without any changes.

You select the dialect when creating your `Transactor`:

```scala
val tx = JdbcTransactor.fromUrl("jdbc:postgresql://...", SqlDialect.PostgreSQL)
// or
val tx = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
```
