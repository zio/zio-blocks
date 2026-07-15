---
id: sql
title: "SQL"
---

`zio-blocks-sql` is a **thin, type-safe JDBC wrapper** for Scala 3. It maps case classes to database tables automatically via `zio-blocks-schema`, provides a composable SQL fragment DSL, and offers a lightweight repository abstraction for common CRUD patterns — all without an ORM runtime or code generation.

Key design goals:

- **Zero magic** — SQL strings are visible, composable, and inspectable.
- **Schema-first** — column names, types, and nullability are derived from the same `Schema[A]` you already use for JSON/Avro codecs.
- **Effect-system agnostic** — the core module (`zio-blocks-sql`) is plain synchronous Scala. A thin `zio-blocks-sql-zio` adapter lifts operations into ZIO effects.
- **Cross-platform core** — the `sql` module cross-compiles to JVM and Scala.js, while the JDBC-backed runtime implementations (`JdbcTransactor`, `sql-zio`) are JVM-only.

## Overview

```
┌─────────────────────────────────────────────────────────┐
│                    Application Code                     │
├─────────────────────────────────────────────────────────┤
│         Repo[E, ID]  ·  sql"..." interpolator           │
├─────────────────────────────────────────────────────────┤
│              Frag  ·  Table  ·  Repo                    │
├─────────────────────────────────────────────────────────┤
│          DbCodec[A]  ·  DbResultReader  ·  DbParamWriter│
├─────────────────────────────────────────────────────────┤
│                  Transactor  (sync)                     │
└─────────────────────────────────────────────────────────┘
                          JDBC / DataSource
```

## Installation

```scala
// Core module (Scala 3, JVM + Scala.js)
libraryDependencies += "dev.zio" %% "zio-blocks-sql" % "@VERSION@"

// Optional ZIO integration (Scala 3, JVM only)
libraryDependencies += "dev.zio" %% "zio-blocks-sql-zio" % "@VERSION@"
```

## Quick Start

```scala
import zio.blocks.schema._
import zio.blocks.sql._

// 1. Define your data type — derives both Schema and DbCodec
case class User(id: Long, name: String, email: String) derives Schema, DbCodec

// 2. Create a Repo (auto-derives table name "user", id column "id")
val userRepo: Repo[User, Long] = Repo.derived[User, Long]

// 3. Create a transactor
val transactor = JdbcTransactor.fromUrl(
  "jdbc:postgresql://localhost/mydb",
  SqlDialect.PostgreSQL
)

// 4. Execute operations
val users: List[User] = transactor.connect:
  userRepo.all

val saved: User = transactor.transact:
  userRepo.insert(User(0L, "Alice", "alice@example.com"))
  userRepo.find(1L).get
```

## Core Concepts

### DbCodec[A]

`DbCodec[A]` is a bidirectional codec between a Scala value `A` and one or more database columns.

```scala
trait DbCodec[A]:
  def columns: IndexedSeq[String]          // column names in order
  def columnCount: Int                     // columns.size
  def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): A
  def writeValue(writer: DbParamWriter, startIndex: Int, value: A): Unit
  def toDbValues(value: A): IndexedSeq[DbValue]
```

Built-in instances are provided for all JDBC-compatible primitive types:

| Scala type         | SQL type         |
|--------------------|------------------|
| `Int`              | INTEGER          |
| `Long`             | BIGINT           |
| `Double`           | DOUBLE PRECISION |
| `Float`            | FLOAT            |
| `String`           | TEXT / VARCHAR   |
| `Boolean`          | BOOLEAN          |
| `Short`            | SMALLINT         |
| `Byte`             | TINYINT          |
| `BigDecimal`       | NUMERIC          |
| `java.time.LocalDate` | DATE          |
| `java.time.LocalDateTime` | TIMESTAMP |
| `java.time.Instant`    | TIMESTAMP     |
| `java.util.UUID`       | UUID / TEXT   |

For case classes, derive a codec automatically using the `derives` clause:

```scala
case class Address(street: String, city: String, zip: String) derives Schema, DbCodec
```

Or derive explicitly when you need more control (e.g. a custom column name mapper):

```scala
val addressCodec: DbCodec[Address] = summon[Schema[Address]].deriving(DbCodecDeriver).derive
```

Derived codecs map each field to a snake_case column name by default
(`street`, `city`, `zip`). Rename a column with `@Modifier.rename`:

```scala
import zio.blocks.schema.Modifier

case class User(
  @Modifier.rename("user_id") id: Long,
  name: String
)
```

> **Tip:** A `DbCodec[A]` is all you need — it automatically works for both query decoding and SQL interpolation. No separate `DbParam[A]` required.

### Frag — SQL Fragments

`Frag` is the primitive for composable SQL. It interleaves literal SQL
strings with typed parameter values so that SQL injection is structurally
impossible.

```scala
final case class Frag(parts: IndexedSeq[String], params: IndexedSeq[DbValue])
```

The `sql"..."` string interpolator creates fragments safely:

```scala
import zio.blocks.sql._

val minAge = 18
val city   = "Berlin"

// Typed parameters are bound as `?` placeholders — never string-interpolated
val frag: Frag = sql"SELECT * FROM users WHERE age > $minAge AND city = $city"
```

Interpolated values must correspond to a **single SQL column**. Primitive
types, `Option[A]`, `Maybe[A]`, and wrappers around a single-column codec work
directly. Composite record values do not expand automatically inside one
placeholder — write one placeholder per column instead.

Fragments compose with `++`:

```scala
val base    = sql"SELECT * FROM users WHERE active = ${true}"
val ordered = base ++ sql" ORDER BY name"
val paged   = ordered ++ sql" LIMIT ${10} OFFSET ${0}"
```

Use `Frag.literal` for fully trusted strings (e.g. identifiers you control):

```scala
val tableName = "users"
val frag = Frag.literal(s"SELECT count(*) FROM $tableName")
```

Do **not** pass user input or unchecked identifiers to `Frag.literal`. Prefer
derived `Table` / `Repo` metadata, which validates table and column names.

Execute a fragment directly via extension methods:

```scala
// Given an implicit DbCon and DbCodec[User] in scope:
val users: List[User]   = frag.query[User]
val user:  Maybe[User] = frag.queryOne[User]
val count: Int          = sql"DELETE FROM users WHERE active = ${false}".update

// Limit result materialization
val firstTen: List[User] = frag.queryLimit[User](10)

// Return generated keys when supported by the driver
val ids: List[Long] = sql"INSERT INTO users(name) VALUES (${"Alice"})".updateReturningKeys[Long]
```

### Table[A]

`Table[A]` binds a Scala type to a database table name and carries the
`DbCodec[A]` plus column metadata for DDL generation.

```scala
// Auto-derive from Schema — table name is "user" (singular snake_case)
val userTable: Table[User] = Table.derived[User]

// Explicit table name
val legacyTable: Table[User] = Table.derived[User]("tbl_users")

// Generate CREATE TABLE DDL
val createDdl: Frag = userTable.createTable(SqlDialect.PostgreSQL)
// → CREATE TABLE IF NOT EXISTS user (
//     id BIGINT NOT NULL,
//     name TEXT NOT NULL,
//     email TEXT NOT NULL
//   )
```

Override the table name from the schema definition using a config annotation:

```scala
@Modifier.config("sql.table_name", "accounts")
case class User(id: Long, name: String, email: String)
```

### Repo[E, ID]

`Repo[E, ID]` provides a complete set of typed CRUD operations for an entity
type `E` with primary key type `ID`.

`Repo` can also be extended directly when the schema and ID codec are available:

```scala
final class UserRepo extends Repo[User, Long]
```

The inherited CRUD operations use the same metadata as `Repo.derived`.

#### Constructing a Repo

**Fully auto-derived** (zero-argument) — resolves the ID field in this order:

1. a field marked with `@Modifier.id`
2. the unique field whose type is `ID`
3. a field named `id`
4. a field named `<entity>Id` (for example, `userId`)

It fails if none of these rules identifies exactly one field.

```scala
case class User(id: Long, name: String)
object User:
  given Schema[User] = Schema.derived

// Finds the `Long` field automatically → idColumn = "id"
val repo = Repo.derived[User, Long]
```

**Named ID column** — when you want to be explicit or there are multiple `Long` fields:

```scala
val repo = Repo.derived[User, Long]("id", _.id)
```

**Explicit table name**:

```scala
val repo = Repo.derived[User, Long]("tbl_users", "id", _.id)
```

**Fully explicit** — when you have a pre-built `Table`:

```scala
val repo = Repo(userTable, "id", DbCodec.longCodec, _.id)
```

#### Read Operations

```scala
// All rows
val all: List[User] = repo.all

// By primary key
val user: Maybe[User] = repo.find(42L)

// Existence check
val exists: Boolean = repo.exists(42L)

// Row count
val n: Long = repo.count
```

#### Write Operations

```scala
// Insert a single row — returns affected row count
val inserted: Int = repo.insert(User(0L, "Alice", "alice@example.com"))

// Insert and re-fetch via generated key
val saved: User = repo.insertReturning(User(0L, "Bob", "bob@example.com"))

// Batch insert — uses JDBC batch for efficiency
val total: Int = repo.insertAll(List(user1, user2, user3))

// Update all non-ID columns for a matching row
val updated: Int = repo.update(user.copy(name = "Updated Name"))

// Delete by ID
val deleted: Int = repo.delete(42L)

// Delete entity (extracts its ID)
val deleted: Int = repo.delete(someUser)

// Delete all rows (equivalent to DELETE FROM table)
val cleared: Int = repo.clear()
```

### Transactor

`Transactor` is the entry point for executing SQL against a database. It
manages connection lifecycle — acquiring, using, and closing connections.

```scala
trait Transactor:
  def connect[A](f: DbCon ?=> A): A
  def transact[A](f: DbTx ?=> A): A
```

- `connect` — opens a connection, runs `f`, closes it on return.
- `transact` — opens a connection, disables auto-commit, runs `f`, commits on
  success or rolls back on exception, then closes the connection.

The `DbCon` and `DbTx` context types are passed implicitly using Scala 3
context parameters (`using`). All `Repo` methods and `Frag` extension methods
require one of these in scope.

```scala
val transactor: Transactor = JdbcTransactor.fromUrl(
  "jdbc:postgresql://localhost/mydb",
  SqlDialect.PostgreSQL
)

// Read — no transaction needed
val users: List[User] = transactor.connect:
  userRepo.all

// Write — use a transaction for atomicity
val result: User = transactor.transact:
  userRepo.insert(newUser)
  userRepo.find(newUser.id).get
```

For production use, prefer `JdbcTransactor.fromDataSource(...)` with a pooled
`DataSource` so connection pooling is handled outside the library.

### DDL Generation

Generate schema DDL from a `Table` using the dialect's type mappings:

```scala
val table = Table.derived[User]

// CREATE TABLE
val createSql: Frag = table.createTable(SqlDialect.PostgreSQL)
createSql.sql(SqlDialect.PostgreSQL)
// → "CREATE TABLE IF NOT EXISTS user (\n  id BIGINT NOT NULL,\n  name TEXT NOT NULL\n)"

// DROP TABLE
val dropSql: Frag = table.dropTable
dropSql.sql(SqlDialect.PostgreSQL)
// → "DROP TABLE IF EXISTS user"
```

For advanced manual DDL construction, lower-level helpers such as `Ddl` and
`ColumnDef` are available, but most application code should prefer
`Table#createTable` / `Table#dropTable`:

```scala
import zio.blocks.sql._

val frag = Ddl.createTable("events", IndexedSeq(
  ColumnDef("id",         "BIGINT",    nullable = false),
  ColumnDef("payload",    "JSONB",     nullable = false),
  ColumnDef("created_at", "TIMESTAMP", nullable = false)
))
```

### SQL Dialects

`SqlDialect` controls dialect-specific rendering: placeholder syntax (`?` vs `$1`) and type names.

```scala
object SqlDialect:
  val PostgreSQL: SqlDialect  // Uses $1, $2, ... placeholders
  val SQLite:     SqlDialect  // Uses ? placeholders
```

Pass the dialect when constructing a transactor; it is threaded through
automatically to all `Frag.sql(dialect)` calls at execution time.

### Logging

Every SQL execution fires lifecycle events on a `SqlLogger`. The default is `SqlLogger.noop`. Plug in your own for observability:

```scala
val logger: SqlLogger = new SqlLogger:
  def onSuccess(event: SqlLogger.SuccessEvent): Unit =
    println(s"[SQL] ${event.sql} — ${event.duration.toMillis}ms, ${event.rowCount} rows")

  def onError(event: SqlLogger.ErrorEvent): Unit =
    println(s"[SQL ERROR] ${event.sql} — ${event.error.getMessage}")

val transactor = JdbcTransactor(connectionFactory, SqlDialect.PostgreSQL, logger)
```

## Column Naming

By default, Scala field names are mapped to SQL column names using the
`SnakeCase` policy: `firstName` → `first_name`.

Override per-field with `@Modifier.rename`:

```scala
case class Product(
  @Modifier.rename("product_id") id: Long,
  @Modifier.rename("product_name") name: String,
  price: Double
)
```

Table names follow a similar convention. The default `TableNamingPolicy.Singular` converts `CamelCase` to `snake_case` singular (`UserProfile` → `user_profile`). Override with `@Modifier.config("sql.table_name", ...)` or pass the name explicitly to `Table.derived` / `Repo.derived`.

## Custom Queries

For queries that go beyond the standard CRUD operations, use the `sql"..."`
interpolator directly:

```scala
transactor.connect:
  // JOIN query
  val frag = sql"""
    SELECT u.id, u.name, o.total
    FROM users u
    JOIN orders o ON o.user_id = u.id
    WHERE u.active = ${true}
    ORDER BY o.total DESC
    LIMIT ${10}
  """
  case class OrderSummary(id: Long, name: String, total: BigDecimal) derives Schema, DbCodec

  frag.query[OrderSummary]

  // Aggregate
  val avgFrag = sql"SELECT AVG(price) FROM products WHERE category = ${"electronics"}"
  avgFrag.queryOne[Double]
```

For custom result shapes, define a small record and derive a codec from its
schema:

```scala
case class ProductAverage(value: Double) derives Schema, DbCodec
```

## Error Handling

All SQL errors surface as `java.sql.SQLException` (or a subclass) wrapped in whatever the calling effect system provides. SQL execution paths do not swallow exceptions — they log via `SqlLogger.onError` and re-throw.

`Transactor.transact` guarantees rollback on any `Throwable`.

## Thread Safety

- `Repo`, `Table`, `DbCodec`, and `Frag` instances are **immutable** and safe to share across threads.
- `DbCon` / `DbTx` context values wrap a single JDBC `Connection` and must **not** be used concurrently. Each call to `connect` / `transact` creates a fresh context.
- `Transactor` and `JdbcTransactor` are safe to share; they create a new connection per `connect` / `transact` invocation.

## ZIO Integration

ZIO users should continue with [SQL — ZIO Integration](./sql-zio.md).
