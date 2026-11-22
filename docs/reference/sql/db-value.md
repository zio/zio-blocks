---
id: db-value
title: "DbValue"
description: "Reference for DbValue, the sealed ADT of typed SQL column values shared by Frag, DbCodec, and SqlDialect in the sql module."
keywords:
  - "DbValue sealed ADT"
  - "SQL column value types"
  - "DbNull DbInt DbString"
  - "typed database parameters"
  - "SqlDialect typeName"
  - "DbCodec toDbValues"
  - "Frag params representation"
---

`DbValue` is a sealed ADT representing every typed SQL column value the `sql` module handles, one `case class` per Scala type. `Frag` stores its bound parameters as `IndexedSeq[DbValue]`, `DbCodec#toDbValues` produces them, and `SqlDialect#typeName` maps a `DbValue` to its DDL type string — it is the common typed currency all three share, with no JDBC imports or dependencies of its own.

| Variant           | Scala field type          | PostgreSQL DDL     | SQLite DDL |
|-------------------|----------------------------|---------------------|------------|
| `DbNull`          | *(none)*                   | `NULL`               | `NULL`     |
| `DbInt`           | `Int`                       | `INTEGER`             | `INTEGER`  |
| `DbLong`          | `Long`                      | `BIGINT`              | `INTEGER`  |
| `DbDouble`        | `Double`                    | `DOUBLE PRECISION`    | `REAL`     |
| `DbFloat`         | `Float`                     | `REAL`                | `REAL`     |
| `DbBoolean`       | `Boolean`                   | `BOOLEAN`             | `INTEGER`  |
| `DbString`        | `String`                    | `TEXT`                | `TEXT`     |
| `DbBigDecimal`    | `scala.BigDecimal`          | `NUMERIC`             | `TEXT`     |
| `DbBytes`         | `Array[Byte]`               | `BYTEA`               | `BLOB`     |
| `DbShort`         | `Short`                     | `SMALLINT`            | `INTEGER`  |
| `DbByte`          | `Byte`                      | `SMALLINT`            | `INTEGER`  |
| `DbChar`          | `Char`                      | `CHAR(1)`             | `TEXT`     |
| `DbLocalDate`     | `java.time.LocalDate`       | `DATE`                | `TEXT`     |
| `DbLocalDateTime` | `java.time.LocalDateTime`   | `TIMESTAMP`           | `TEXT`     |
| `DbLocalTime`     | `java.time.LocalTime`       | `TIME`                | `TEXT`     |
| `DbInstant`       | `java.time.Instant`         | `TIMESTAMPTZ`         | `TEXT`     |
| `DbDuration`      | `java.time.Duration`        | `INTERVAL`            | `TEXT`     |
| `DbUUID`          | `java.util.UUID`            | `UUID`                | `TEXT`     |
| `DbArray`         | `(String, IndexedSeq[Any])` | `TEXT[]`              | `TEXT`     |
| `Raw`             | `(String, Any)`             | *(`sqlType` verbatim)* | *(`sqlType` verbatim)* |

`DbNull` is what `DbParam[Option[A]]` produces for `None` and `DbParam[Maybe[A]]` produces for `Maybe.absent`; `DbNull` appears only on the write side: decoding never sees a `DbValue`, because codecs read from a result reader and detect `NULL` themselves. A non-optional codec does not fail on `NULL` either, except for `BigDecimal` and a derived `Char`: `Int` reads `0` and `String` reads `null`. `DbArray` is the one variant with two fields (`elementType`, `elements`) instead of `value`, used for collection-typed columns and mapped by `SqlDialect#typeName` to `TEXT[]` on PostgreSQL and `TEXT` on SQLite.

### `Raw` — driver-specific values

`Raw(sqlType, value)` is the escape hatch for values that cannot be expressed as a standard `DbValue`, such as a PostgreSQL `PGobject` or a pgvector value. It remains a **bound parameter**: `Frag` writes it through `DbParamWriter#setRaw`, and the JDBC writer binds it with `PreparedStatement.setObject(index, value, Types.OTHER)`. The value is never inlined into the SQL text, so it keeps prepared-statement reuse and does not create an injection surface the way `SqlLiteral` does.

```scala
import zio.blocks.sql._

val vec = new org.postgresql.util.PGobject()
vec.setType("vector")
vec.setValue("[1.0,2.0,3.0]")

val frag = sql"INSERT INTO items (embedding) VALUES (${DbValue.Raw("vector", vec)})"
```

`sqlType` is advisory metadata: `SqlDialect#typeName` returns it verbatim and a future dialect module can use it to choose a driver mapping. JDBC drivers that understand `Types.OTHER` infer the concrete type from `value` itself.

:::caution
`Raw` is a trust boundary: the value is handed to the JDBC driver as-is. Pass a driver-understood object (or a `String` in the driver's text format), never raw SQL. Use the standard `DbValue` variants and normal `DbParam` binding everywhere else; do not reach for `Raw` or `SqlLiteral` just to avoid writing a `DbCodec`.
:::

For a dialect-wide type mapping (`vector`, `tsvector`, ...), see
[`DbTypeRegistry`](./db-codec-deriver.md#dialect-type-registry) and
[ADR-002](../../adr/2026-11-22-sql-postgres-type-extension.md).

See [Frag](./frag.md), [DbCodec](./db-codec.md), and the [SQL module index](./index.md).
