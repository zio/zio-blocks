---
id: table-metadata
title: "TableMetadata"
description: "Reference for TableMetadata, the utility for deriving column metadata from a Schema."
keywords:
  - "Table Metadata"
  - "Column Metadata"
  - "Table Naming Policy"
  - "Schema DDL Column Derivation"
---

`TableMetadata` derives column metadata from a `Schema`. It returns a list of `ColumnMeta` instances, each describing a column's name, type, and nullability. `TableNamingPolicy` controls how Scala type names become table names.

## Core API

```scala
object TableMetadata {
  def columnsFor[A](
    schema: Schema[A],
    columnNameMapper: SqlNameMapper = SqlNameMapper.SnakeCase
  ): IndexedSeq[ColumnMeta]
}

final case class ColumnMeta(name: String, dbValue: DbValue, nullable: Boolean)

sealed trait TableNamingPolicy {
  def defaultName(typeName: String): String
}

object TableNamingPolicy {
  case object Singular                         extends TableNamingPolicy
  case object Plural                           extends TableNamingPolicy
  final case class Custom(f: String => String) extends TableNamingPolicy
}
```

## Usage

Derive column metadata from a schema:

```scala mdoc:reset
import zio.blocks.sql.{TableMetadata, SqlDialect}
import zio.blocks.schema.Schema

case class Product(id: Int, name: String, price: Option[BigDecimal])
object Product { given schema: Schema[Product] = Schema.derived }

val cols = TableMetadata.columnsFor(Product.schema)
cols

cols.map(_.name)
cols.map(_.nullable)
```

Use the column metadata to get DDL types:

```scala mdoc
cols.map(col => SqlDialect.PostgreSQL.typeName(col.dbValue))
```

Control table naming when deriving a Table:

```scala mdoc
import zio.blocks.sql.{Table, TableNamingPolicy}

// Singular table name (default)
val table1 = Table.derived[Product]
table1.name

// Plural table name
val table2 = Table.derived[Product](TableNamingPolicy.Plural)
table2.name

// Custom naming
val table3 = Table.derived[Product](TableNamingPolicy.Custom("t_" + _))
table3.name
```

## Key Points

**`columnsFor`** — Walks a schema's structure and returns metadata for each column, respecting `@Modifier.transient` (skip field), `@Modifier.rename` (override name), and `Option[A]` / `Maybe[A]` (mark nullable).

**ColumnMeta** — Holds the column name, a representative `DbValue` for type inference, and a nullable flag. The actual value in `dbValue` doesn't matter—only its variant is used by `SqlDialect#typeName`.

**TableNamingPolicy** — Controls default table naming: `Singular` converts `"UserAccount"` to `"user_account"`, `Plural` to `"user_accounts"`, `Custom(f)` applies function `f` directly.

## How It Works

`Table.derived` calls `columnsFor` to extract column metadata from the schema. For each `ColumnMeta`, the dialect's `typeName` method is called to get the SQL type string. The metadata tracks which columns are nullable based on `Option[A]` or `Maybe[A]` types. Fields annotated with `@Modifier.rename` use their explicit name instead of the mapper.

For how Table uses this metadata, see [Table](./table.md). For DDL generation, see [Ddl](./ddl.md).
