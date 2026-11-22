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
- **JSON fallback** — non-inline nested records, sequences, maps, and dynamic values are stored as JSON in a single column backed by `DbCodec.jsonb`. The codec binds the JSON as a string (`DbValue.DbString`), which both dialects map to `TEXT`; no dialect emits `JSONB`, so a `JSONB` column on PostgreSQL must be declared by hand. In 0.0.56, `Table.derived` and `Repo.derived` don't follow this fallback, a known bug: they split a non-inline nested record into one column per field and throw for a collection field, so they work only for records whose nested record fields carry `sql.inline` (see the caution under [`Table#createTable`](./table.md#createtable--generate-a-create-table-statement)). This is the trap to watch for with driver-specific types: a `List[String]` field is **not** a SQL `varchar[]`, and a nested value type is **not** decoded by a `given DbCodec` for that type. Use [`DbCodec.arrayOf`](./db-codec.md#dbcodecarrayof--sql-array-columns) for real array columns and an explicit field codec for other custom column types.

The structural shape of `DbCodecDeriver` is:

```scala
class DbCodecDeriver(
  columnNameMapper: SqlNameMapper = SqlNameMapper.SnakeCase
) extends Deriver[DbCodec] {
  def withFieldCodec[P, F](
    parentTypeId: TypeId[P],
    fieldName: String,
    codec: DbCodec[F]
  ): DbCodecDeriver
}

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
```

```scala mdoc:silent
val eventCodec = DbCodec.derived[Event]
```

```scala mdoc
eventCodec.columns

// 3. withColumnNameMapper — use Identity when your DB already uses camelCase
case class Widget(widgetId: Int, widgetName: String)
object Widget { implicit val schema: Schema[Widget] = Schema.derived }
```

```scala mdoc:silent
val identityDeriver = DbCodecDeriver.withColumnNameMapper(SqlNameMapper.Identity)

val widgetCodec     = Widget.schema.deriving(identityDeriver).derive
```

```scala mdoc
widgetCodec.columns
```

## Field codec overrides

A `given DbCodec[FieldType]` in scope is **not** consulted for a record field. Derivation follows the field's `Schema`; non-inline nested records, sequences, and maps use the JSONB fallback regardless of any called codec. This is the usual cause of `JSONB decode error: expected '{'` when a column such as a pgvector embedding stores text like `[1,2,3]`. Forgetting the override now produces an error naming the field, the schema path, and a prefix of the offending value.

Register the field codec explicitly with `withFieldCodec`:

```scala mdoc:reset
import zio.blocks.schema.Schema
import zio.blocks.typeid.TypeId
import zio.blocks.sql._

case class Embedding(values: List[Double])
object Embedding { implicit val schema: Schema[Embedding] = Schema.derived }

case class RowDetail(id: Int, embedding: Embedding)
object RowDetail { implicit val schema: Schema[RowDetail] = Schema.derived }

val embeddingCodec: DbCodec[Embedding] =
  DbCodec[String].transform[Embedding] { text =>
    Embedding(text.stripPrefix("[").stripSuffix("]").split(",").toList.map(_.toDouble))
  }(e => e.values.mkString("[", ",", "]"))

val deriver = DbCodecDeriver.withFieldCodec(TypeId.of[RowDetail], "embedding", embeddingCodec)
val rowCodec = RowDetail.schema.deriving(deriver).derive

rowCodec.columns
```

`DbCodec.derivedWith` accepts the same field overrides through the `DerivationBuilder` API and is the more concise form:

```scala mdoc:compile-only
import _root_.zio
import zio.blocks.typeid.TypeId
import zio.blocks.sql._

val rowCodec: DbCodec[RowDetail] =
  DbCodec.derivedWith[RowDetail](
    _.instance(TypeId.of[RowDetail], "embedding", embeddingCodec)
  )
```

Resolution order for a field is:

1. `DbCodecDeriver.withFieldCodec` / `DbCodec.derivedWith` override for `(parent type, field name)`.
2. `@Modifier.config("sql.inline", "true")` on a nested record (flatten into parent columns).
3. JSONB for non-inline nested records, sequences, and maps.
4. The normal single-column codec.

Use this for driver-specific field types and for real SQL array columns (`DbCodec.arrayOf`); see [DbCodec](./db-codec.md#dbcodecarrayof--sql-array-columns).

## See Also

For the naming strategy that `DbCodecDeriver` applies, see [SqlNameMapper](./sql-name-mapper.md). For the `DbCodec` type that derivation produces, see [DbCodec](./db-codec.md). For `Table.derived` and DDL generation, see [Table](./table.md).
