---
id: table-metadata
title: "TableMetadata"
description: "Reference for TableMetadata, the singleton utility that derives ColumnMeta instances from a Schema, and for the supporting ColumnMeta and TableNamingPolicy types."
keywords:
  - "TableMetadata columnsFor"
  - "ColumnMeta column metadata"
  - "TableNamingPolicy table naming"
  - "Schema DDL column derivation"
  - "SqlNameMapper column names"
  - "Table.derived internals"
  - "DDL column type inference"
---

`TableMetadata` is a singleton utility object with one public method, `columnsFor`, that walks the `Reflect` tree of a `Schema[A]` and returns an `IndexedSeq[ColumnMeta]` describing every column that a `DbCodec[A]` will read or write. It defines two companion types: `ColumnMeta`, a `final case class` holding a column's name, a representative `DbValue` for DDL type inference, and a nullability flag; and `TableNamingPolicy`, a sealed trait controlling how a Scala type name becomes a SQL table name. `Table.derived` calls `columnsFor` internally; application code rarely calls it directly.

Key properties:
- **Schema-driven** — `columnsFor` reads the `Schema[A]`'s `Reflect` tree at runtime; no macro or compile-time expansion is needed beyond what `Schema.derived` already does.
- **Annotation-aware** — fields annotated with `@Modifier.transient` are skipped; fields annotated with `@Modifier.rename` use the annotation's value rather than the mapper's output.
- **Nullable marking** — fields whose type is `Option[A]` or `Maybe[A]` produce a `ColumnMeta` with `nullable = true`; all other fields produce `nullable = false`.
- **Representative samples** — `ColumnMeta#dbValue` is a zero-value instance (e.g., `DbInt(0)`, `DbString("")`) used exclusively by `SqlDialect#typeName` for DDL generation; the actual runtime value it carries is irrelevant.

The structural shape of the types in this file is:

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

## Quick Showcase

The following example derives column metadata from a schema and inspects each `ColumnMeta` to show the column name, representative type, and nullability:

```scala
import zio.blocks.sql.{TableMetadata, SqlDialect}
import zio.blocks.schema.Schema

case class Product(id: Int, name: String, price: Option[BigDecimal])
object Product { implicit val schema: Schema[Product] = Schema.derived }

val cols = TableMetadata.columnsFor(Product.schema)
// IndexedSeq(
//   ColumnMeta("id",    DbValue.DbInt(0),              nullable = false),
//   ColumnMeta("name",  DbValue.DbString(""),           nullable = false),
//   ColumnMeta("price", DbValue.DbBigDecimal(BigDecimal(0)), nullable = true)
// )

cols.map(_.name)         // IndexedSeq("id", "name", "price")
cols.map(_.nullable)     // IndexedSeq(false, false, true)

// Use the representative dbValue to obtain the DDL type for each column
cols.map(col => SqlDialect.PostgreSQL.typeName(col.dbValue))
// IndexedSeq("INTEGER", "TEXT", "NUMERIC")
```

## Construction / Creating Instances

`TableMetadata` is a plain Scala `object` — it has no constructor and no instances. Call `TableMetadata.columnsFor` as a static method.

`ColumnMeta` is a `final case class`; construct it directly when building a `Table` by hand:

```scala
import zio.blocks.sql.{ColumnMeta, DbValue}

val col = ColumnMeta(name = "user_id", dbValue = DbValue.DbInt(0), nullable = false)
col.name     // "user_id"
col.nullable // false
```

`TableNamingPolicy` singletons (`Singular`, `Plural`) are `case object`s; reference them by name. `Custom` takes a `String => String`:

```scala
import zio.blocks.sql.TableNamingPolicy

TableNamingPolicy.Singular.defaultName("UserAccount") // "user_account"
TableNamingPolicy.Plural.defaultName("UserAccount")   // "user_accounts"
TableNamingPolicy.Custom(_.toUpperCase).defaultName("order") // "ORDER"
```

## Core Operations

### `columnsFor` — Derive column metadata from a schema

`columnsFor[A](schema: Schema[A], columnNameMapper: SqlNameMapper = SqlNameMapper.SnakeCase): IndexedSeq[ColumnMeta]` walks the `Reflect` tree rooted at `schema.reflect` and returns one `ColumnMeta` per database column in codec order.

The traversal rules mirror those of `DbCodecDeriver#deriveRecord`:

- **Transient fields** — a field annotated with `@Modifier.transient` is skipped entirely; no `ColumnMeta` is emitted for it.
- **Renamed fields** — a field annotated with `@Modifier.rename("col")` uses `"col"` as the column name regardless of the mapper.
- **Nullable fields** — a field whose type is `Option[A]` or `Maybe[A]` produces a `ColumnMeta` with `nullable = true`; the inner type `A` determines the `dbValue`.
- **Nested records** — a non-inline nested record field is represented as a single JSONB `TEXT` column by `DbCodecDeriver`, but `columnsFor` does not replicate this behaviour — it recurses into record fields using the `prefix_fieldName` convention. Use `columnsFor` for DDL discovery; rely on `DbCodec#columns` for the exact column list at query time.

```scala
import zio.blocks.sql.{TableMetadata, SqlNameMapper}
import zio.blocks.schema.{Schema, Modifier}

case class Item(
  @Modifier.rename("item_code") sku: String,
  qty: Int,
  discount: Option[Double]
)
object Item { implicit val schema: Schema[Item] = Schema.derived }

val cols = TableMetadata.columnsFor(Item.schema)
cols.map(_.name)     // IndexedSeq("item_code", "qty", "discount")
cols.map(_.nullable) // IndexedSeq(false, false, true)
```

### `TableNamingPolicy` — Convert a Scala type name to a SQL table name

`TableNamingPolicy` controls the `defaultName` transformation applied to the short Scala type name (without package) when `Table.derived` is called without an explicit table name.

- `Singular` converts the type name to `snake_case` singular — `"UserAccount"` → `"user_account"`.
- `Plural` converts to `snake_case` and then applies English pluralisation rules — `"UserAccount"` → `"user_accounts"`.
- `Custom(f)` applies `f` directly to the type name.

```scala
import zio.blocks.sql.TableNamingPolicy

// Singular — default for Table.derived
TableNamingPolicy.Singular.defaultName("Order")       // "order"
TableNamingPolicy.Singular.defaultName("LineItem")    // "line_item"

// Plural — adds "s" (or irregular endings)
TableNamingPolicy.Plural.defaultName("Order")         // "orders"
TableNamingPolicy.Plural.defaultName("Category")      // "categories"

// Custom — full control
TableNamingPolicy.Custom("t_" + _).defaultName("order") // "t_order"
```

## Integration

`TableMetadata.columnsFor` is called by `Table.derived` and `Table.derived(tableName)` to populate `Table#columnsMeta`. Application code accesses column metadata through `Table#columnsMeta` or `Table#columns` rather than calling `columnsFor` directly.

`ColumnMeta#dbValue` feeds into `SqlDialect#typeName` inside `Table#createTable`: for each `ColumnMeta` in `columnsMeta`, `createTable` calls `dialect.typeName(col.dbValue)` to obtain the DDL type string and wraps it in a `ColumnDef` passed to `Ddl.createTable`.

`TableNamingPolicy` is passed as the first argument to `Table.derived(namingPolicy)`. `Table.derived` (no argument) uses `TableNamingPolicy.Singular` by default. Override it with `TableNamingPolicy.Plural` or a `Custom` policy when the generated table name does not match your schema conventions.

For how `Table.derived` assembles the full `Table[A]` binding, see [Table](./table.md). For how `ColumnDef` is consumed by DDL generation, see [Ddl](./ddl.md). For the `SqlNameMapper` that controls column-level naming, see [SqlNameMapper](./sql-name-mapper.md).
