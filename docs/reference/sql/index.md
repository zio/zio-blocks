---
id: index
title: "SQL Module"
description: "Reference index for the sql module: schema-driven SQL fragments, bidirectional codecs, CRUD repositories, and transaction management."
keywords:
  - "DbCodec schema derivation"
  - "Frag sql interpolator"
  - "Repo CRUD operations"
  - "JdbcTransactor connection management"
  - "DbCon implicit context"
  - "DbTx transaction scope"
  - "SqlDialect PostgreSQL SQLite"
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

The `sql` module provides type-safe, schema-driven SQL query building and execution on relational databases. Its core types — `DbCodec`, `Frag`, `Table`, `Repo`, `Transactor`, `DbCon`, and `DbTx` — form a layered system: codecs map Scala types to database columns, fragments carry parameterized SQL built via string interpolation, repositories expose CRUD operations derived at compile time from a schema, and a transactor manages the connection lifecycle with automatic commit and rollback.

## Motivation

Relational database access in Scala typically involves one of three trade-offs: raw JDBC requires manual row mapping and string concatenation (SQL injection risk, tedious boilerplate); reflection-based ORMs hide SQL entirely (opaque at compile time, difficult to optimize); and lifted-embedding DSLs like Slick require learning a parallel query language on top of SQL. The `sql` module takes a different path: it uses ZIO Blocks' `Schema` system for compile-time column metadata and familiar string interpolation for SQL text, giving you type safety without losing SQL's expressiveness.

Key advantages of the `sql` module are:

- **Schema-driven derivation.** `DbCodec`, `Table`, and `Repo` all derive from the same `Schema[A]`, keeping column names, SQL types, and nullability in sync with your Scala data model automatically.
- **No runtime reflection.** Derivation runs at compile time through the `Deriver[DbCodec]` framework, producing zero-overhead codecs with no reflective calls at runtime.
- **Compile-time SQL parameterization.** The `sql"..."` macro interpolator verifies that every interpolated value has a `DbParam[A]` instance at compile time, converts it to a `DbValue`, and binds it to a `?` placeholder — no string concatenation, no SQL injection.
- **Composable fragments.** `Frag` values compose via `Frag#++`, letting you build reusable WHERE clauses, ORDER BY expressions, and pagination helpers that render correctly for any `SqlDialect`.
- **Explicit transaction boundaries.** `Transactor#transact` disables auto-commit, commits on success, and rolls back on any exception — the connection is always closed whether the block succeeds or throws.

The table below shows how `sql` compares to common alternatives:

| Approach                                    | Key difference from `sql`                                                                                      |
|---------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| Doobie                                      | Cats/typeclasses (do-notation, `IO`); `sql` uses `Deriver` + string interpolation, no Cats dependency.        |
| Slick                                       | Lifted-embedding DSL (Scala objects model tables/queries); `sql` uses plain SQL strings + compile-time codecs. |
| Raw JDBC                                    | Manual row mapping and string concatenation; `sql` automates both via `DbCodec` and the `sql"..."` macro.     |
| `java.sql.ResultSet` / `PreparedStatement`  | Framework-agnostic but untyped; `sql` adds `DbResultReader`, `DbParamWriter`, and typed `DbValue` ADT on top. |

## Installation

The core SQL module and the ZIO integration module publish separately. Add the artifacts you need to your build:

```scala
// Core SQL module (synchronous JDBC, JVM only)
libraryDependencies += "dev.zio" %% "zio-blocks-sql" % "@VERSION@"

// ZIO integration (lifts JDBC into ZIO effects)
libraryDependencies += "dev.zio" %% "zio-blocks-sql-zio" % "@VERSION@"
```

Both modules require a JDBC driver on the classpath (for example, `org.xerial:sqlite-jdbc` for SQLite or `org.postgresql:postgresql` for PostgreSQL). Swap the JDBC URL and `SqlDialect` constant to switch databases.

## Overview

The module is organized into three groups: core types you interact with directly in application code, supporting types that implement the protocol layer, and a ZIO integration layer that lifts synchronous operations into the effect system.

### Core Types

These seven types form the public API for everyday SQL work:

- **[`DbCodec`](./db-codec.md)** — The bidirectional codec between a Scala type `A` and one or more database columns. `DbCodec#readValue` decodes result-set rows by label (so column order can differ from codec order); `DbCodec#writeValue` and `DbCodec#toDbValues` encode values into `DbValue` parameters. Codecs are derived automatically from `Schema[A]` via `DbCodecDeriver`, or composed manually with `DbCodec#transform`. Every other type in the module depends on `DbCodec` to move data between Scala and the database.
- **[`Frag`](./frag.md)** — An immutable SQL fragment holding literal SQL text in `parts` and typed parameters in `params`, with the invariant `parts.length == params.length + 1`. Built with the `sql"..."` interpolator or the `Frag.literal`, `Frag.sequence`, and `Frag.values` factory methods, and executed via extension methods: `Frag#query`, `Frag#queryOne`, `Frag#queryLimit`, `Frag#update`, and `Frag#updateReturningKeys`.
- **[`Table`](./table.md)** — Metadata binding a Scala type `A` to a database table: name, `DbCodec[A]`, and per-column metadata (`IndexedSeq[ColumnMeta]`). `Table.derived` inspects a `Schema[A]` and applies a `TableNamingPolicy` and `SqlNameMapper` to translate Scala names to SQL identifiers. `Table#createTable` and `Table#dropTable` emit dialect-specific DDL `Frag` values.
- **[`Repo`](./repo.md)** — A type-safe repository providing CRUD operations for entities of type `E` with primary key type `ID`: `Repo#findAll`, `Repo#findById`, `Repo#insert`, `Repo#insertBatch`, `Repo#insertAll`, `Repo#update`, `Repo#deleteById`, `Repo#delete`, `Repo#count`, and `Repo#truncate`. All SQL is pre-built at construction time via `Repo.derived`, avoiding per-call string assembly.
- **[`Transactor`](./transactor.md)** — The entry point for executing SQL. `Transactor#connect` acquires a JDBC connection, supplies a `DbCon` context, and closes the connection on return. `Transactor#transact` additionally disables auto-commit, commits on success, and rolls back on any exception. `JdbcTransactor` is the JDBC-backed implementation, with factory methods `JdbcTransactor.fromUrl` and `JdbcTransactor.fromDataSource`.
- **[`DbCon`](./db-con.md)** — The implicit context threaded through every SQL operation. Carries three members: `DbCon#connection` (the active `DbConnection`), `DbCon#dialect` (the `SqlDialect`), and `DbCon#logger` (the `SqlLogger`). All `Frag` extension methods and `Repo` CRUD methods accept a `DbCon ?=>` context, making them available anywhere inside a `Transactor#connect` block.
- **[`DbTx`](./db-tx.md)** — A `DbCon` subtype that marks transactional scope. It carries no additional members; its role is to distinguish code that runs inside `Transactor#transact` from code that runs inside `Transactor#connect`. Because `DbTx extends DbCon`, any method that accepts `DbCon` also accepts `DbTx`, so transactional and non-transactional operations compose freely.

### Supporting Types

These types implement the protocol and derivation layers and are rarely used directly in application code:

- **[`DbValue`](./db-value.md)** — The sealed ADT of typed database values used internally by `DbCodec` and `Frag` to hold parameters before binding: `DbNull`, `DbInt`, `DbLong`, `DbDouble`, `DbFloat`, `DbBoolean`, `DbString`, `DbBigDecimal`, `DbBytes`, `DbShort`, `DbByte`, `DbChar`, `DbLocalDate`, `DbLocalDateTime`, `DbLocalTime`, `DbInstant`, `DbDuration`, `DbUUID`, and `DbArray`.
- **[`DbParam`](./db-param.md)** — The typeclass that converts a Scala value of type `A` to a `DbValue` for use in the `sql"..."` interpolator. Instances are provided for all primitive types, `Option[A]`, and `Maybe[A]`; a `fromDbCodec` instance bridges single-column codecs into `DbParam` automatically.
- **[`SqlDialect`](./sql-dialect.md)** — Encodes database-specific SQL differences: `SqlDialect#typeName` translates a `DbValue` to its DDL type string, and `SqlDialect#paramPlaceholder` renders the parameter marker. `SqlDialect.PostgreSQL` and `SqlDialect.SQLite` are the built-in instances; the sealed trait is extensible.
- **[`SqlLogger`](./sql-logger.md)** — A hook for observing query execution. `SqlLogger#onSuccess` receives the rendered SQL, parameters, duration, and row count after a successful statement; `SqlLogger#onError` receives the same fields plus the throwable. `SqlLogger.noop` provides a no-op implementation.
- **[`SqlNameMapper`](./sql-name-mapper.md)** — The strategy for mapping Scala field names to SQL column names. `SqlNameMapper.SnakeCase` (the default) converts `camelCase` to `snake_case`; `SqlNameMapper.Identity` passes names through unchanged; `SqlNameMapper.Custom` accepts an arbitrary `String => String` function.
- **[`TableMetadata`](./table-metadata.md)** — Derives column metadata from a `Schema[A]`: each field becomes a `ColumnMeta` holding the column name (after `SqlNameMapper`), the representative `DbValue` (for DDL type resolution), and nullability. Used by `Table.derived` to populate `Table#columnsMeta`.
- **[`Ddl`](./ddl.md)** — Helpers for generating DDL `Frag` values. `Ddl.createTable` emits `CREATE TABLE IF NOT EXISTS` with column definitions; `Ddl.dropTable` emits `DROP TABLE IF EXISTS`. Both are called by `Table#createTable` and `Table#dropTable` rather than used directly.
- **[`DbConnection`](./db-connection.md)** — The abstraction over a JDBC `Connection`. Provides `DbConnection#prepareStatement`, `DbConnection#prepareStatementReturningKeys`, transaction control (`setAutoCommit`, `commit`, `rollback`), and `close`. `JdbcConnection` is the concrete JDBC-backed implementation.
- **[`DbResultReader`](./db-result-reader.md)** — The abstraction for reading column values from a result set, used by `DbCodec#readValue`. Supports both label-based access (e.g., `DbResultReader#getString("name")`) and 1-based positional access (e.g., `DbResultReader#getInt(1)`), plus `DbResultReader#wasNull` for null detection.
- **[`DbParamWriter`](./db-param-writer.md)** — The abstraction for binding parameter values to a prepared statement, used by `DbCodec#writeValue`. Covers all primitive types, temporal types, UUIDs, arrays, and `setNull`. `JdbcParamWriter` delegates to `java.sql.PreparedStatement`.
- **[`DbCodecDeriver`](./db-codec-deriver.md)** — The schema-driven codec derivation engine. Instantiated as `DbCodecDeriver(columnNameMapper)`, it implements `Deriver[DbCodec]` and processes `Schema[A]`'s `Reflect` tree to produce `DbCodec[A]`, handling primitives, records, options, enums, and JSONB-encoded complex types.
- **[`TransactorZIO`](./transactor-zio.md)** — ZIO integration wrapping `JdbcTransactor`. `TransactorZIO#connect` and `TransactorZIO#transact` run synchronous bodies on the blocking thread pool via `ZIO.attemptBlocking`. `TransactorZIO#connectZIO` and `TransactorZIO#transactZIO` accept effect-returning bodies and bracket connections via `ZIO.acquireRelease`, ensuring cleanup even on interruption. A `ZLayer` is available via `TransactorZIO.layer`.

## How They Work Together

Five interconnected flows define how the module's types cooperate: codec derivation, SQL assembly, execution context, entity mapping, and transaction lifecycle. Understanding these flows shows why each type exists and how to compose them.

The typical workflow proceeds in this order:

1. Define a `Schema[A]` for your domain type (usually with `Schema.derived`).
2. Call `Repo.derived` (or `Table.derived` + `Repo.apply`) to produce a `Repo[E, ID]` — all SQL is pre-built here.
3. Create a `JdbcTransactor` from a JDBC URL or `DataSource`.
4. Open a connection with `Transactor#connect` (for reads) or `Transactor#transact` (for writes), which supplies a `DbCon` or `DbTx` context.
5. Inside the context block, call `Repo` methods or execute `sql"..."` fragments directly.

The following diagram shows the data-flow relationships between types:

```
                        Schema[A]
                            │
           ┌────────────────┼──────────────────┐
           │                │                  │
           ▼                ▼                  ▼
    DbCodecDeriver    TableMetadata      deriveTableName
           │           .columnsFor()    (TableNamingPolicy
           ▼                │            + SqlNameMapper)
      DbCodec[A] ◄──────────┘                  │
           │                                   │
           └───────────────────────────────────┘
                            │
                    Table[A](name, codec, columnsMeta)
                            │
                    Repo[E, ID]                   sql"..." → Frag
                    ─ findAll / findById           ─ frag.query[A]
                    ─ insert / update / delete     ─ frag.update
                            │                             │
                    ┌───────┴─────────────────────────────┘
                    │
                    ▼
              Transactor
              ├─ .connect  { given DbCon => … }    (non-transactional)
              └─ .transact { given DbTx =>  … }    (commit / rollback)
                                │
                         DbCon / DbTx
                   ┌────────────┼────────────┐
                   ▼            ▼            ▼
            DbConnection    SqlDialect   SqlLogger
            (JDBC wrapper) (PostgreSQL  (observability)
                            | SQLite)
```

The `sql"..."` interpolator is defined as an extension on `StringContext`. At compile time the macro verifies that every interpolated expression has a `DbParam[A]` instance, converts it to a `DbValue`, and assembles a `Frag` with literal SQL in `parts` and the typed values in `params`. The `Frag` carries no dialect-specific rendering until `Frag#sql(dialect)` is called, so the same fragment works with PostgreSQL and SQLite unchanged.

A complete end-to-end example showing schema definition, table setup, repository construction, and mixed repository/fragment queries follows:

<Tabs groupId="scala-version" defaultValue="scala2">
  <TabItem value="scala2" label="Scala 2">

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User {
  implicit val schema: Schema[User] = Schema.derived
}

// 1. Create transactor and repository
val tx   = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
val repo = Repo.derived[User, Int]("id", _.id)

// 2. Set up schema and run a transactional workflow
tx.transact { implicit tx: DbTx =>
  // Create table using derived DDL
  repo.table.createTable(tx.dialect).update

  // Insert via repository (uses pre-built INSERT fragment)
  repo.insert(User(1, "Alice", "alice@example.com"))
  repo.insert(User(2, "Bob", "bob@example.com"))

  // Read via repository
  val alice: Option[User] = repo.findById(1)

  // Read via raw SQL fragment — composes freely with repo operations
  val aUsers: List[User] =
    sql"SELECT id, name, email FROM user WHERE name LIKE ${"A%"}".query[User]

  // Update and delete
  repo.update(User(1, "Alice Smith", "alice.smith@example.com"))
  repo.deleteById(2)
}
```

  </TabItem>
  <TabItem value="scala3" label="Scala 3">

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User {
  implicit val schema: Schema[User] = Schema.derived
}

// 1. Create transactor and repository
val tx   = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
val repo = Repo.derived[User, Int]("id", _.id)

// 2. Set up schema and run a transactional workflow
tx.transact { given tx: DbTx =>
  // Create table using derived DDL
  repo.table.createTable(tx.dialect).update

  // Insert via repository (uses pre-built INSERT fragment)
  repo.insert(User(1, "Alice", "alice@example.com"))
  repo.insert(User(2, "Bob", "bob@example.com"))

  // Read via repository
  val alice: Option[User] = repo.findById(1)

  // Read via raw SQL fragment — composes freely with repo operations
  val aUsers: List[User] =
    sql"SELECT id, name, email FROM user WHERE name LIKE ${"A%"}".query[User]

  // Update and delete
  repo.update(User(1, "Alice Smith", "alice.smith@example.com"))
  repo.deleteById(2)
}
```

  </TabItem>
</Tabs>

Inside `transact`, auto-commit is disabled. If any call throws, the entire block rolls back and the exception propagates. On normal return, the transaction commits and the connection closes.

## Common Patterns

The module is designed around a small set of patterns that appear throughout most application code. Recognizing these patterns makes it easy to choose the right approach for each situation.

### Schema-Driven Derivation

`DbCodec`, `Table`, and `Repo` all derive from a single `Schema[A]`. Derivation respects `@Modifier` annotations — `@Modifier.rename` overrides a column name, `@Modifier.transient` excludes a field from the codec, and `@Modifier.config("sql.table_name", "my_table")` overrides the table name. This means your Scala type definition is the single source of truth for column names, types, and nullability:

```scala
import zio.blocks.sql._
import zio.blocks.schema.{Schema, Modifier}

case class BlogPost(
  @Modifier.rename("post_id") id: Int,
  title: String,
  @Modifier.transient authorHandle: String  // excluded from SQL
)
object BlogPost {
  implicit val schema: Schema[BlogPost] = Schema.derived
}

val repo = Repo.derived[BlogPost, Int]("post_id", _.id)
// Table name: "blog_post"  (snake_case of "BlogPost")
// Columns: "post_id", "title"  ("author_handle" is transient)
```

### Implicit Context Threading

Every SQL operation — `Frag` execution and `Repo` CRUD — requires an implicit `DbCon` (or `DbTx`) in scope. The context carries the connection, dialect, and logger, but you never pass it explicitly. Calling code inside `Transactor#connect` or `Transactor#transact` automatically has the context available, and helper methods can propagate it with an implicit parameter:

<Tabs groupId="scala-version" defaultValue="scala2">
  <TabItem value="scala2" label="Scala 2">

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class Product(id: Int, name: String, price: BigDecimal)
object Product { implicit val schema: Schema[Product] = Schema.derived }

def cheapProducts(maxPrice: BigDecimal)(implicit con: DbCon): List[Product] =
  sql"SELECT id, name, price FROM product WHERE price < $maxPrice".query[Product]

val tx = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
tx.connect { implicit con: DbCon =>
  // `cheapProducts` picks up `con` automatically
  val items = cheapProducts(BigDecimal("9.99"))
}
```

  </TabItem>
  <TabItem value="scala3" label="Scala 3">

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class Product(id: Int, name: String, price: BigDecimal)
object Product { implicit val schema: Schema[Product] = Schema.derived }

def cheapProducts(maxPrice: BigDecimal)(using DbCon): List[Product] =
  sql"SELECT id, name, price FROM product WHERE price < $maxPrice".query[Product]

val tx = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
tx.connect { given con: DbCon =>
  // `cheapProducts` picks up `con` automatically
  val items = cheapProducts(BigDecimal("9.99"))
}
```

  </TabItem>
</Tabs>

### Multi-Column Codecs

A single `DbCodec[A]` can span multiple database columns. When you use a multi-column type directly in an `sql"..."` expression, the `fromDbCodec` `DbParam` instance throws at runtime because it cannot collapse multiple values into a single `?` placeholder. Instead, use `Frag.values` for multi-row inserts or write the columns explicitly in the fragment:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class Point(x: Double, y: Double)
object Point { implicit val schema: Schema[Point] = Schema.derived }

// Multi-row insert using Frag.values — one (?, ?) tuple per row
val points = List(Point(1.0, 2.0), Point(3.0, 4.0))
val frag   = Frag.literal("INSERT INTO point (x, y) VALUES ") ++ Frag.values(points)
// Renders: INSERT INTO point (x, y) VALUES (?, ?), (?, ?)
```

### Type-Safe SQL Parameterization

The `sql"..."` interpolator accepts any Scala value for which a `DbParam[A]` exists. The macro checks this at compile time and binds the value to a `?` placeholder, preventing SQL injection regardless of the value's content. All standard scalar types have built-in instances, `Option[A]` binds to `NULL` or the inner value, and custom types with a `DbCodec[A]` automatically gain a `DbParam[A]`:

```scala
import zio.blocks.sql._

val userId: Int             = 42
val namePattern: String     = "%alice%"
val active: Option[Boolean] = Some(true)

// All three are compile-time safe — no string concatenation
val frag =
  sql"SELECT * FROM user WHERE id = $userId AND name LIKE $namePattern AND active = $active"
// Renders: SELECT * FROM user WHERE id = ? AND name LIKE ? AND active = ?
// Params:  DbInt(42), DbString("%alice%"), DbBoolean(true)
```

### JSONB Serialization for Complex Types

When a field's type is not directly representable as a single column — such as `List[A]`, `Map[K, V]`, or a sealed trait with multiple variants — `DbCodecDeriver` automatically uses `DbCodec.jsonb[A]` to store and retrieve the value as a JSON-encoded `TEXT` or `JSONB` column. This keeps complex nested data in a single column without requiring a separate table:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class Order(id: Int, tags: List[String], metadata: Map[String, String])
object Order { implicit val schema: Schema[Order] = Schema.derived }

// `tags` and `metadata` are stored as JSON-encoded TEXT columns
val table = Table.derived[Order]
// DDL columns: "id" INTEGER NOT NULL, "tags" TEXT NOT NULL, "metadata" TEXT NOT NULL
```

### Optional and Nullable Handling

`Option[A]` and `Maybe[A]` map to a single nullable column. Reading a `NULL` from the database produces `None` or `Maybe.absent`; writing `None` or `Maybe.absent` binds `NULL` to the parameter. For non-optional types, encountering an unexpected `NULL` throws `IllegalStateException` at read time, surfacing schema mismatches immediately rather than silently coercing `NULL` to a default:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class Profile(userId: Int, bio: Option[String], avatarUrl: Option[String])
object Profile { implicit val schema: Schema[Profile] = Schema.derived }

// bio and avatar_url become nullable TEXT columns in the generated DDL
val repo = Repo.derived[Profile, Int]("user_id", _.userId)
// repo.insert(Profile(1, None, None)) binds NULL for both optional columns
```

## Integration Points

The `sql` module sits at the intersection of several other ZIO Blocks modules and integrates with the JVM JDBC ecosystem.

The primary dependency is **zio.blocks.schema**. `Schema[A]` (specifically its `Reflect` tree) is the source of all compile-time type metadata: field names, types, nullability, and annotations. `DbCodecDeriver` extends `Deriver[DbCodec]`, the same framework used by JSON, Avro, and other codec modules in the schema module's built-in codec suite. The `@Modifier.rename`, `@Modifier.transient`, and `@Modifier.config` annotations attach SQL-specific configuration to individual fields and types without coupling the domain model to the `sql` module.

The module also depends on **zio.blocks.maybe** for `Maybe[A]` support. `Maybe[A]` has distinct absent and present semantics (unlike `Option` which cannot distinguish `Some(null)` from a genuinely absent value), and both `DbCodec` and `DbParam` provide `Maybe[A]` instances alongside `Option[A]`.

For transparent opaque-type support the module integrates with **`As[A, B]`** from `zio.blocks.schema`. If an opaque type has a `DbCodec` for its underlying type and an `As` conversion, `DbCodec` derives an instance for the opaque type automatically via `dbCodecFromAs` without requiring a hand-written codec.

The JDBC layer is isolated behind three abstractions — `DbConnection`, `DbResultReader`, and `DbParamWriter` — so the `JdbcTransactor` can be replaced by an alternate backend (for example, a WebSQL or node-postgres adapter on Scala.js) without changing any application code that depends only on the shared `sql` module.

The **`sql-zio` module** (`zio-blocks-sql-zio`) provides `TransactorZIO`, a ZIO-aware wrapper around `JdbcTransactor`. It exposes two execution models: blocking wrappers (`TransactorZIO#connect`, `TransactorZIO#transact`) that run synchronous bodies with `ZIO.attemptBlocking`, and effect-aware methods (`TransactorZIO#connectZIO`, `TransactorZIO#transactZIO`) that bracket connections via `ZIO.acquireRelease` so connections are always released even on fiber interruption. A `ZLayer` constructor (`TransactorZIO.layer`) integrates with ZIO's dependency injection system.

## See Also

- **[Query DSL Part 2: SQL Generation](../../guides/query-dsl-sql.md)** — Tutorial showing how to translate `SchemaExpr` query trees into parameterized SQL using `Frag` and `DbParam`.
- **[Query DSL Part 4: A Fluent SQL Builder](../../guides/query-dsl-fluent-builder.md)** — Tutorial building type-safe SELECT, UPDATE, INSERT, and DELETE statements on top of `Frag` and `DbCodec`.
- **[Schema Reference](../schema/index.md)** — The `Schema[A]` and `Deriver[F]` types that `DbCodec`, `Table`, and `Repo` derive from.
- **[SQL-ZIO Integration Reference](../sql-zio.md)** — Reference page for `TransactorZIO` and the ZIO `ZLayer` integration.
