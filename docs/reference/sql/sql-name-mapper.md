---
id: sql-name-mapper
title: "SqlNameMapper"
description: "Reference for SqlNameMapper, the sealed functional trait that maps Scala field names to SQL column names in the sql module."
keywords:
  - "SqlNameMapper column naming"
  - "SnakeCase camelCase to snake_case"
  - "Identity column name passthrough"
  - "Custom column name mapper"
  - "DbCodecDeriver naming strategy"
  - "TableMetadata column names"
  - "SQL column name derivation"
---

`SqlNameMapper` is a sealed functional trait that determines how a Scala field name becomes a SQL column name. It extends `String => String`, so every instance is an ordinary function that accepts a field name and returns a column name. Three implementations are built in: `SnakeCase` (the default) converts camelCase identifiers to `snake_case`; `Identity` returns the name unchanged; `Custom` wraps an arbitrary `String => String` function. `DbCodecDeriver` applies the mapper to each non-transient, non-renamed field when building a `DbCodec`; `TableMetadata.columnsFor` applies it the same way when enumerating `ColumnMeta` values for DDL.

Key properties:
- **Sealed** — all implementations are defined in the companion object; exhaustive pattern matching is guaranteed at compile time.
- **Functional** — each instance is a `String => String`; call it directly with `mapper(fieldName)` or compose it with standard `Function1` methods.
- **Schema annotations take precedence** — when a field carries `@Modifier.rename("custom_name")`, `DbCodecDeriver` uses that name instead of invoking the mapper; the mapper only applies to fields without a rename annotation.
- **Composable** — `Custom` accepts any `String => String`, so mappers can be composed with `andThen` or `compose`.

The structural shape of `SqlNameMapper` is:

```scala
sealed trait SqlNameMapper extends (String => String)

object SqlNameMapper {
  case object SnakeCase                        extends SqlNameMapper
  case object Identity                         extends SqlNameMapper
  final case class Custom(f: String => String) extends SqlNameMapper
}
```

## Quick Showcase

The following example applies each built-in mapper to the same field name to show the output each produces:

```scala
import zio.blocks.sql.SqlNameMapper

// SnakeCase — converts camelCase transitions to underscores, lowercases
SqlNameMapper.SnakeCase("firstName")  // "first_name"
SqlNameMapper.SnakeCase("userID")     // "user_id"
SqlNameMapper.SnakeCase("createdAt")  // "created_at"
SqlNameMapper.SnakeCase("id")         // "id"  (no change for single words)

// Identity — no transformation
SqlNameMapper.Identity("firstName")   // "firstName"
SqlNameMapper.Identity("userID")      // "userID"

// Custom — arbitrary transformation
val upper = SqlNameMapper.Custom(_.toUpperCase)
upper("firstName")                    // "FIRSTNAME"

val prefixed = SqlNameMapper.Custom("tbl_" + _)
prefixed("id")                        // "tbl_id"
```

## Construction / Creating Instances

`SnakeCase` and `Identity` are `case object` singletons — reference them directly. `Custom` takes a single `f: String => String` argument.

```scala
import zio.blocks.sql.SqlNameMapper

val snake: SqlNameMapper  = SqlNameMapper.SnakeCase
val ident: SqlNameMapper  = SqlNameMapper.Identity
val upper: SqlNameMapper  = SqlNameMapper.Custom(_.toUpperCase)
val prefix: SqlNameMapper = SqlNameMapper.Custom(name => s"col_$name")
```

Because `SqlNameMapper` extends `String => String`, instances can be passed anywhere a plain function is expected:

```scala
import zio.blocks.sql.SqlNameMapper

val fieldNames = List("userId", "createdAt", "emailAddress")

// Apply via the Function1 interface
fieldNames.map(SqlNameMapper.SnakeCase)
// List("user_id", "created_at", "email_address")
```

## Core Operations

### `apply` — Transform a field name to a column name

Calling a `SqlNameMapper` instance with a field name string returns the corresponding column name. Because the trait extends `String => String`, the call syntax is `mapper(fieldName)`.

```scala
import zio.blocks.sql.SqlNameMapper

val mapper: SqlNameMapper = SqlNameMapper.SnakeCase

mapper("orderId")      // "order_id"
mapper("totalAmount")  // "total_amount"
mapper("SKU")          // "s_k_u"  — each uppercase transition inserts an underscore
```

:::note
`SnakeCase` inserts an underscore before any uppercase character that is either preceded by a non-uppercase character or followed by a non-uppercase character. Consecutive uppercase sequences like `"SKU"` produce `"s_k_u"`. Use `@Modifier.rename` on individual fields to override specific names without changing the global mapper.
:::

### Pattern matching — Dispatch on the naming strategy

Because `SqlNameMapper` is sealed, you can pattern-match to branch on which strategy is active. This is most useful when implementing custom DDL tooling that needs to describe the naming strategy in output:

```scala
import zio.blocks.sql.SqlNameMapper

def describe(mapper: SqlNameMapper): String = mapper match {
  case SqlNameMapper.SnakeCase    => "snake_case conversion"
  case SqlNameMapper.Identity     => "no transformation"
  case SqlNameMapper.Custom(_)    => "custom function"
}

describe(SqlNameMapper.SnakeCase)       // "snake_case conversion"
describe(SqlNameMapper.Identity)        // "no transformation"
describe(SqlNameMapper.Custom(_ => "")) // "custom function"
```

## Integration

`SqlNameMapper` is the naming strategy parameter for two module participants: `DbCodecDeriver` and `TableMetadata`.

`DbCodecDeriver` applies the mapper to each non-transient field that does not carry `@Modifier.rename`. The default singleton `DbCodecDeriver` (which extends `DbCodecDeriver(SqlNameMapper.SnakeCase)`) uses `SnakeCase`. To use a different strategy, call `DbCodecDeriver.withColumnNameMapper(mapper)` to get a new deriver instance, then pass that deriver explicitly to `Schema#deriving`:

```scala
import zio.blocks.sql.{DbCodec, DbCodecDeriver, SqlNameMapper}
import zio.blocks.schema.Schema

case class Order(orderId: Int, totalAmount: BigDecimal)
object Order { implicit val schema: Schema[Order] = Schema.derived }

// Use UPPERCASE column names instead of snake_case
val upperDeriver = DbCodecDeriver.withColumnNameMapper(SqlNameMapper.Custom(_.toUpperCase))
val codec: DbCodec[Order] = Order.schema.deriving(upperDeriver).derive
codec.columns // IndexedSeq("ORDERID", "TOTALAMOUNT")
```

`TableMetadata.columnsFor` accepts a `columnNameMapper` parameter that defaults to `SqlNameMapper.SnakeCase`. Pass a custom instance to enumerate `ColumnMeta` values with non-default names, which is useful when constructing a `Table` manually rather than through `Table.derived`:

```scala
import zio.blocks.sql.{TableMetadata, SqlNameMapper}
import zio.blocks.schema.Schema

case class Product(productId: Int, listPrice: BigDecimal)
object Product { implicit val schema: Schema[Product] = Schema.derived }

val cols = TableMetadata.columnsFor(Product.schema, SqlNameMapper.Identity)
cols.map(_.name) // IndexedSeq("productId", "listPrice")
```

`@Modifier.rename` on a field always takes priority over the mapper for that field: `DbCodecDeriver` checks for a rename annotation first and, if found, uses its name without calling the mapper. For related types, see [DbCodecDeriver](./db-codec-deriver.md) and [TableMetadata](./table-metadata.md).
