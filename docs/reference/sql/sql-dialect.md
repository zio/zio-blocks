---
id: sql-dialect
title: "SqlDialect"
description: "Reference for SqlDialect, the sealed trait that encodes database-specific SQL type names and parameter placeholders for PostgreSQL and SQLite in the sql module."
keywords:
  - "SqlDialect sealed trait"
  - "PostgreSQL dialect"
  - "SQLite dialect"
  - "typeName DDL type mapping"
  - "paramPlaceholder SQL parameters"
  - "DbValue type names"
  - "DbCon dialect selection"
---

`SqlDialect` is a sealed trait that encodes the differences between database engines: `typeName` maps any `DbValue` to the DDL type string that the engine expects in `CREATE TABLE` statements, and `paramPlaceholder` returns the parameter placeholder string for a given zero-based parameter index. Two built-in `case object` instances are provided — `SqlDialect.PostgreSQL` and `SqlDialect.SQLite` — and both expose a `name: String` identifying them.

Key properties:
- **Sealed** — all implementations are defined inside the companion object; the Scala compiler warns on incomplete pattern matches over `SqlDialect`.
- **Stateless** — both built-in instances are `case object`s; they carry no mutable state and are safe to share across threads.
- **Dialect-driven DDL** — `typeName` is the single point of truth for how a Scala type maps to a DDL column type; `Table#createTable` calls it once per column and never constructs type strings elsewhere.
- **Uniform placeholder** — both dialects return `"?"` from `paramPlaceholder` for all indices; the method exists to accommodate dialects such as PostgreSQL's extended protocol that use positional placeholders like `$1`, `$2`.

The structural shape of `SqlDialect` is:

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

## Quick Showcase

The following example shows the two most common uses of `SqlDialect`: calling `typeName` to discover how a Scala type maps to a DDL string in each dialect, and calling `paramPlaceholder` to obtain the correct parameter token for a SQL statement.

```scala
import zio.blocks.sql.{SqlDialect, DbValue}

// typeName — convert a representative DbValue to its DDL type string
SqlDialect.PostgreSQL.typeName(DbValue.DbInt(0))         // "INTEGER"
SqlDialect.PostgreSQL.typeName(DbValue.DbLong(0L))       // "BIGINT"
SqlDialect.PostgreSQL.typeName(DbValue.DbBoolean(false)) // "BOOLEAN"
SqlDialect.PostgreSQL.typeName(DbValue.DbUUID(new java.util.UUID(0L, 0L))) // "UUID"

SqlDialect.SQLite.typeName(DbValue.DbLong(0L))       // "INTEGER"  (SQLite has no BIGINT)
SqlDialect.SQLite.typeName(DbValue.DbBoolean(false)) // "INTEGER"  (stored as 0/1)
SqlDialect.SQLite.typeName(DbValue.DbUUID(new java.util.UUID(0L, 0L))) // "TEXT"

// paramPlaceholder — obtain the placeholder token for each positional parameter
SqlDialect.PostgreSQL.paramPlaceholder(0) // "?"
SqlDialect.SQLite.paramPlaceholder(0)     // "?"
```

## Construction / Creating Instances

No direct construction is needed. `SqlDialect.PostgreSQL` and `SqlDialect.SQLite` are `case object` singletons — reference them by name. Application code receives a `SqlDialect` through `DbCon#dialect`, which is set when the `Transactor` opens a connection.

```scala
import zio.blocks.sql.SqlDialect

val pg: SqlDialect = SqlDialect.PostgreSQL
val sl: SqlDialect = SqlDialect.SQLite

pg.name // "PostgreSQL"
sl.name // "SQLite"
```

To implement a custom dialect, extend `SqlDialect` and override all three abstract members. Because `SqlDialect` is `sealed`, any custom subclass must be defined in the same compilation unit as the sealed trait — in practice, this means adding it to the `sql` module itself rather than application code. For most purposes, the two built-in instances are sufficient.

## Core Operations

### `typeName` — Map a `DbValue` to its DDL column type

`typeName(dbValue: DbValue): String` inspects the runtime variant of `dbValue` and returns the SQL type string that this dialect uses in a `CREATE TABLE` statement for that Scala type. The `dbValue` argument is a representative sample value (the actual scalar it wraps does not matter, only its variant). `TableMetadata.columnsFor` supplies representative `DbValue` instances for each schema field; `Table#createTable` calls `typeName` once per column to assemble the `ColumnDef` list passed to `Ddl.createTable`.

The mapping differs between the two dialects. PostgreSQL preserves Scala's numeric width (`DbLong` → `BIGINT`, `DbBoolean` → `BOOLEAN`) and uses native database types for temporal and UUID values. SQLite collapses all integers to `INTEGER`, stores booleans as `INTEGER`, encodes all temporal types as ISO-8601 `TEXT`, and stores UUIDs and decimals as `TEXT`.

```scala
import zio.blocks.sql.{SqlDialect, DbValue}
import java.time.{Instant, LocalDate}

// PostgreSQL — native types for most variants
SqlDialect.PostgreSQL.typeName(DbValue.DbInstant(Instant.EPOCH)) // "TIMESTAMPTZ"
SqlDialect.PostgreSQL.typeName(DbValue.DbLocalDate(LocalDate.ofEpochDay(0))) // "DATE"
SqlDialect.PostgreSQL.typeName(DbValue.DbBigDecimal(BigDecimal(0))) // "NUMERIC"
SqlDialect.PostgreSQL.typeName(DbValue.DbBytes(Array.emptyByteArray)) // "BYTEA"

// SQLite — TEXT for all temporal and UUID types
SqlDialect.SQLite.typeName(DbValue.DbInstant(Instant.EPOCH)) // "TEXT"
SqlDialect.SQLite.typeName(DbValue.DbLocalDate(LocalDate.ofEpochDay(0))) // "TEXT"
SqlDialect.SQLite.typeName(DbValue.DbBigDecimal(BigDecimal(0))) // "TEXT"
SqlDialect.SQLite.typeName(DbValue.DbBytes(Array.emptyByteArray)) // "BLOB"
```

The full mapping for both dialects is:

| `DbValue` variant   | PostgreSQL DDL type  | SQLite DDL type |
|---------------------|----------------------|-----------------|
| `DbNull`            | `NULL`               | `NULL`          |
| `DbInt`             | `INTEGER`            | `INTEGER`       |
| `DbLong`            | `BIGINT`             | `INTEGER`       |
| `DbDouble`          | `DOUBLE PRECISION`   | `REAL`          |
| `DbFloat`           | `REAL`               | `REAL`          |
| `DbBoolean`         | `BOOLEAN`            | `INTEGER`       |
| `DbString`          | `TEXT`               | `TEXT`          |
| `DbBigDecimal`      | `NUMERIC`            | `TEXT`          |
| `DbBytes`           | `BYTEA`              | `BLOB`          |
| `DbShort`           | `SMALLINT`           | `INTEGER`       |
| `DbByte`            | `SMALLINT`           | `INTEGER`       |
| `DbChar`            | `CHAR(1)`            | `TEXT`          |
| `DbLocalDate`       | `DATE`               | `TEXT`          |
| `DbLocalDateTime`   | `TIMESTAMP`          | `TEXT`          |
| `DbLocalTime`       | `TIME`               | `TEXT`          |
| `DbInstant`         | `TIMESTAMPTZ`        | `TEXT`          |
| `DbDuration`        | `INTERVAL`           | `TEXT`          |
| `DbUUID`            | `UUID`               | `TEXT`          |

### `paramPlaceholder` — Obtain the parameter token for a SQL statement

`paramPlaceholder(index: Int): String` returns the SQL token that represents a bound parameter at position `index` (zero-based). Both built-in dialects return `"?"` unconditionally — JDBC's standard positional placeholder. The method exists as an extension point for dialects that use a different convention (for example, `$1`, `$2`, … in PostgreSQL's extended wire protocol).

```scala
import zio.blocks.sql.SqlDialect

// Both built-in dialects return "?" for any index
SqlDialect.PostgreSQL.paramPlaceholder(0) // "?"
SqlDialect.PostgreSQL.paramPlaceholder(4) // "?"
SqlDialect.SQLite.paramPlaceholder(0)     // "?"
```

`Frag#sql(dialect)` calls this method once per parameter when rendering the SQL string for execution, replacing each parameter slot with the appropriate placeholder.

## Integration

`SqlDialect` is a passive data type that other module participants read but never mutate. The three primary touch-points are `DbCon`, `Frag`, and `Table`.

`DbCon#dialect` carries the active `SqlDialect` for the current connection scope. When `Transactor#connect` or `Transactor#transact` opens a connection it supplies a concrete `SqlDialect` to the `DbCon` it creates; all SQL operations running inside that block automatically use the same dialect.

`Frag#sql(dialect: SqlDialect)` uses `dialect.paramPlaceholder` to render each parameter slot into a complete SQL string ready for JDBC. Calling `sql` with `SqlDialect.PostgreSQL` and `SqlDialect.SQLite` on the same `Frag` produces identical strings for both dialects because both use `"?"`.

`Table#createTable(dialect: SqlDialect)` calls `dialect.typeName` once for every entry in `Table#columnsMeta` to produce the `ColumnDef` list passed to `Ddl.createTable`. The same `Table[A]` can therefore emit dialect-correct DDL for either engine without rebuilding the table metadata.

For the full list of `DbValue` variants, see [DbValue](./db-value.md). For how `Table#createTable` assembles DDL, see [Table](./table.md) and [Ddl](./ddl.md). For how `DbCon` carries the dialect through a connection scope, see [DbCon](./db-con.md).
