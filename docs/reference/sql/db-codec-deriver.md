---
id: db-codec-deriver
title: "DbCodecDeriver"
description: "Reference for DbCodecDeriver, the schema-driven derivation engine that converts a Schema[A] into a DbCodec[A] in the sql module."
keywords:
  - "DbCodecDeriver schema derivation"
  - "DbCodec automatic derivation"
  - "Deriver DbCodec"
  - "SqlNameMapper column naming"
  - "withColumnNameMapper"
  - "deriveRecord deriveVariant"
  - "JSON fallback codec"
---

`DbCodecDeriver` is a schema-driven derivation engine that converts a `Schema[A]` into a `DbCodec[A]`. 

The companion object `DbCodecDeriver` is itself an instance of the class constructed with the default `SqlNameMapper.SnakeCase`. `DbCodec.derived`, `DbCodec.builder`, `DbCodec.derivedWith`, and `Table.derived` all delegate to this deriver for the row codec; application code rarely constructs or calls it directly.

Key properties:
- **Schema-driven** — derivation is entirely data-driven from the `Schema`'s `Reflect` tree; no separate macro or annotation processor is involved beyond `Schema.derived`.
- **Configurable naming** — the `columnNameMapper: SqlNameMapper` constructor parameter controls how Scala field names become SQL column names; the default is `SnakeCase`.
- **Annotation-aware** — `@Modifier.transient` fields are skipped; `@Modifier.rename` overrides the mapper for individual fields; `@Modifier.config("sql.inline","true")` flattens a nested record into the parent's column list.
- **JSON fallback** — non-inline nested records, sequences, maps, and dynamic values are stored as JSON in a single column backed by `DbCodec.jsonb`. The codec binds the JSON as a string (`DbValue.DbString`), which both dialects map to `TEXT`; no dialect emits `JSONB`, so a `JSONB` column on PostgreSQL must be declared by hand. In 0.0.56, `Table.derived` and `Repo.derived` don't follow this fallback, a known bug: they split a non-inline nested record into one column per field and throw for a collection field, so they work only for records whose nested record fields carry `sql.inline` (see the caution under [`Table#createTable`](./table.md#createtable--generate-a-create-table-statement)).

The structural shape of `DbCodecDeriver` is:

```scala
class DbCodecDeriver(columnNameMapper: SqlNameMapper = SqlNameMapper.SnakeCase)
    extends Deriver[DbCodec]

object DbCodecDeriver extends DbCodecDeriver(SqlNameMapper.SnakeCase) {
  def withColumnNameMapper(mapper: SqlNameMapper): DbCodecDeriver
}
```

## Usage

The following example shows the three most common ways to reach `DbCodecDeriver`: through the `derives` clause on a case class, through `DbCodec.derived` (equivalent), and through `DbCodecDeriver.withColumnNameMapper` when a non-default naming strategy is needed.

```scala mdoc
import zio.blocks.schema.Schema
import zio.blocks.sql.{DbCodec, DbCodecDeriver, SqlNameMapper}

// 1. Derives clause — most concise
case class User(userId: Int, fullName: String) derives DbCodec
DbCodec[User].columns

// 2. DbCodec.derived — equivalent, explicit
case class Event(eventId: Long, eventType: String)
object Event { implicit val schema: Schema[Event] = Schema.derived }
val eventCodec = DbCodec.derived[Event]
eventCodec.columns

// 3. withColumnNameMapper — use Identity when your DB already uses camelCase
case class Widget(widgetId: Int, widgetName: String)
object Widget { implicit val schema: Schema[Widget] = Schema.derived }

val identityDeriver = DbCodecDeriver.withColumnNameMapper(SqlNameMapper.Identity)
val widgetCodec     = Widget.schema.deriving(identityDeriver).derive
widgetCodec.columns
```

## See Also

For the naming strategy that `DbCodecDeriver` applies, see [SqlNameMapper](./sql-name-mapper.md). For the `DbCodec` type that derivation produces, see [DbCodec](./db-codec.md). For `Table.derived` and DDL generation, see [Table](./table.md).
