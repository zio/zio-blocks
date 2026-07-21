---
id: sql-name-mapper
title: "SqlNameMapper"
description: "Reference for SqlNameMapper, the interface for mapping Scala field names to SQL column names."
keywords:
  - "SqlNameMapper Column Naming"
  - "SnakeCase Conversion"
  - "Identity Passthrough"
  - "Custom Column Naming"
---

`SqlNameMapper` converts Scala field names to SQL column names. Three implementations are built in: `SnakeCase` (default, converts `firstName` to `first_name`), `Identity` (no change), and `Custom` (arbitrary function).

## Core API

The simplified structural shape of `SqlNameMapper` is:

```scala
sealed trait SqlNameMapper extends (String => String)

object SqlNameMapper {
  case object SnakeCase                        extends SqlNameMapper
  case object Identity                         extends SqlNameMapper
  final case class Custom(f: String => String) extends SqlNameMapper
}
```

## Usage

Call a mapper like a function:

```scala mdoc:reset
import zio.blocks.sql.SqlNameMapper

SqlNameMapper.SnakeCase("firstName")
SqlNameMapper.SnakeCase("userID")
SqlNameMapper.Identity("firstName")

val upper = SqlNameMapper.Custom(_.toUpperCase)
upper("firstName")
```

Use a custom mapper when deriving codecs:

```scala mdoc:reset
import zio.blocks.sql.{DbCodec, DbCodecDeriver, SqlNameMapper}
import zio.blocks.schema.Schema

case class Order(orderId: Int, totalAmount: BigDecimal)
object Order { given schema: Schema[Order] = Schema.derived }

// Use UPPERCASE column names instead of snake_case
val upperDeriver = DbCodecDeriver.withColumnNameMapper(SqlNameMapper.Custom(_.toUpperCase))
val codec: DbCodec[Order] = Order.schema.deriving(upperDeriver).derive
codec.columns
```

## Key Points

For full control over individual field names, use `@Modifier.rename("column_name")` on specific fields without changing the global mapper. It takes precedence over any `SqlNameMapper` in use.
