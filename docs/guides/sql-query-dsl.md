---
id: sql-query-dsl
title: SQL Query DSL
---

# Typed Relational Query Layer

The typed query layer builds SQL `SELECT` statements from Scala case classes, relations, and typed expression trees, with compile-time validation of columns, projections, join nullability, and query-bound scoping.

## Tables

`Table.derived[A]` reads column names from the `Schema` metadata at the call site. It validates column names against `SqlIdentifier` rules, so typos in table or column names fail immediately.

```scala mdoc:silent
import zio.blocks.sql.query.*
import zio.blocks.sql.{DbValue, Frag, JdbcTransactor, SqlDialect, Table, sql}
import zio.blocks.schema.{Modifier, Schema}

@Modifier.config("sql.table_name", "users")
case class User(id: Int, name: String)
object User { given Schema[User] = Schema.derived }

@Modifier.config("sql.table_name", "repos")
case class Repo(id: Int, ownerId: Int, name: String)
object Repo { given Schema[Repo] = Schema.derived }

@Modifier.config("sql.table_name", "stars")
case class Star(id: Int, repoId: Int, userId: Int)
object Star { given Schema[Star] = Schema.derived }

val userTable = Table.derived[User]
val repoTable = Table.derived[Repo]
val starTable = Table.derived[Star]
```

## Column References

Column expressions are **query-bound**: they are built from a query value with `q.col[TableType](_.field)`, and they carry that query's scope. A column built from one query value cannot be mixed into another query's `select`/`where`/`groupBy`/`having` — that is rejected at compile time.

```scala mdoc:silent
val q = SqlQuery.from(userTable)
val userId: Expr[Int, q.type]      = q.col[User](_.id)
val userName: Expr[String, q.type] = q.col[User](_.name)
```

The `Expr[A, Scope]` type carries the Scala type of the column (which constrains what comparisons and projections are valid) and the query scope (which prevents cross-query mixing). You can't accidentally compare an `Expr[Int, _]` to a `lit("hello")`, and you can't use `q1.col[User](_.id)` inside `q2.select(...)`.

The field selector is macro-validated: a misspelling like `_.nam` instead of `_.name` produces a compile error naming the invalid field and listing valid alternatives.

## Literals

`lit(value)` wraps a Scala value into a scope-neutral expression. The value becomes a `?` placeholder in the rendered SQL, with the actual value carried in `Frag.params`. `Schema[A]` is required to convert the value to a database-representable form.

```scala mdoc:silent
val intLit    = lit(42)
val stringLit = lit("alice")
val longLit   = lit(100L)
```

Literals have scope `Nothing`, so they can be combined with any query's columns (comparisons, arithmetic, `IN`).

## Relations

`Rel.manyToOne` declares a foreign key relationship between two tables. The inline selector form validates at compile time that both referenced fields exist in their respective schemas.

```scala mdoc:silent
val userRepoRel = Rel.manyToOne(repoTable, _.ownerId, userTable, _.id)
val repoStarRel = Rel.manyToOne(starTable, _.repoId, repoTable, _.id)
```

Here `repoTable.ownerId` references `userTable.id`, and `starTable.repoId` references `repoTable.id`. The macro extracts the field names and maps them to snake_case column names. After construction, the `Rel` validates that those column names exist in each `Table`'s metadata — a runtime backstop against schema drift.

The string-based constructor is available for dynamic or reflective scenarios:

```scala mdoc:silent
val relFromString = Rel.manyToOne(repoTable, "owner_id", userTable, "id")
```

## Building Queries

`SqlQuery.from(table)` starts a query from a single table. You chain operations to add joins, filters, groupings, and projections. Name the base query value first — every column expression in the chain is built from that same value, and the whole lineage of the query shares one scope:

```scala mdoc:silent
val q2 = SqlQuery
  .from(userTable)
  .innerJoin(userRepoRel)

val q3 = SqlQuery
  .from(userTable)
  .innerJoin(userRepoRel)
  .innerJoin(repoStarRel)
```

### Joins

`innerJoin(rel)` and `leftJoin(rel)` add a JOIN clause using a previously declared `Rel`. Aliases are allocated deterministically: `t0` for the source table, `t1`, `t2`, etc. for each successive join.

`join(rel)` is an alias for `innerJoin`, and `joinLeft(rel)` is an alias for `leftJoin`.

The renderer produces SQL like:

```sql
SELECT ... FROM "users" AS t0
  INNER JOIN "repos" AS t1 ON t1."owner_id" = t0."id"
  INNER JOIN "stars" AS t2 ON t2."repo_id" = t1."id"
```

### Filters

`where(expr)` or `filter(expr)` adds a `WHERE` clause. Multiple calls chain with AND.

```scala mdoc:silent
val qBaseF = SqlQuery.from(userTable)
val qFiltered = qBaseF.where(qBaseF.col[User](_.id) === lit(1))
```

Available comparison operators on `Expr[A, Scope]`:

| Operator | Name | Notes |
|----------|------|-------|
| `===` / `equalTo` | Equal | Requires same `A` on both sides |
| `=!=` / `notEqualTo` | Not equal | |
| `>` / `greaterThan` | Greater than | Requires `Ordering[A]`, rejects `Boolean` |
| `<` / `lessThan` | Less than | Requires `Ordering[A]`, rejects `Boolean` |
| `>=` / `greaterThanOrEqualTo` | Greater or equal | Requires `Ordering[A]`, rejects `Boolean` |
| `<=` / `lessThanOrEqualTo` | Less or equal | Requires `Ordering[A]`, rejects `Boolean` |

Boolean combinators:

```scala mdoc:silent
val qAnd = SqlQuery.from(userTable)
val combined    = (qAnd.col[User](_.id) > lit(0)) && (qAnd.col[User](_.name) === lit("alice"))
val alternative = (qAnd.col[User](_.id) === lit(1)) || (qAnd.col[User](_.id) === lit(2))
val negated     = !(qAnd.col[User](_.id) === lit(1))
```

`in(values)` tests membership in a set:

```scala mdoc:silent
val membership = qAnd.col[User](_.id).in(Seq(1, 2, 3))
```

`like(pattern)` matches SQL `LIKE`:

```scala mdoc:silent
val nameMatch = qAnd.col[User](_.name).like("alice%")
```

### Group By and Having

`groupBy(expr, exprs*)` adds a `GROUP BY` clause. `having(expr)` adds a `HAVING` clause.

```scala mdoc:silent
val qRepo = SqlQuery.from(repoTable)
val qGrouped = qRepo.groupBy(qRepo.col[Repo](_.ownerId)).having(qRepo.count(qRepo.col[Repo](_.id)) > lit(1L))
```

### Aggregate Functions

Aggregates are query-bound methods on `SqlQuery` so their results carry the query scope and their result types normalize at compile time:

| Function | Signature | Returns |
|----------|-----------|---------|
| `q.count(expr)` | `Expr[A, q.Scope] => Expr[Long, q.Scope]` | Count of non-null values |
| `countStar` | `Expr[Long, Nothing]` | `COUNT(*)` |
| `q.sum(expr)` | `Expr[A, q.Scope] => Expr[Option[SumOut[A]], q.Scope]` | Widened sum |
| `q.avg(expr)` | `Expr[A, q.Scope] => Expr[Option[AvgOut[A]], q.Scope]` | Widened average |
| `q.min(expr)` | `Expr[A, q.Scope] => Expr[Option[A], q.Scope]` | Minimum, nullable |
| `q.max(expr)` | `Expr[A, q.Scope] => Expr[Option[A], q.Scope]` | Maximum, nullable |

Result types are PostgreSQL/SQLite truthful and reduce to concrete types at compile time:

- `SumOut`: `Byte`/`Short`/`Int` → `Long`; `Long`/`BigInt`/`BigDecimal` → `BigDecimal`; `Float`/`Double` → `Double`.
- `AvgOut`: integral/`BigInt`/`BigDecimal` → `BigDecimal`; `Float`/`Double` → `Double`.

```scala mdoc:silent
val qStar = SqlQuery.from(starTable)
val total: Expr[Option[Long], qStar.type]        = qStar.sum(qStar.col[Star](_.id))
val average: Expr[Option[BigDecimal], qStar.type] = qStar.avg(qStar.col[Star](_.id))
```

`sum`/`avg` over unsupported types (e.g. `String`) are rejected at compile time.

### Ordering and Limits

`orderBy(expr, dir)` adds a query-scoped `ORDER BY` term; repeated calls accumulate with independent directions. The expression must belong to the same query lineage (or be scope-neutral like a literal), so ordering by a joined table qualifies its alias and cross-query ordering is rejected at compile time.

```scala mdoc:silent
val qOrdered = qBaseF.where(qBaseF.col[User](_.id) > lit(0)).orderBy(qBaseF.col[User](_.id)).limit(10).offset(20)
```

`SortOrder.Asc` is the default. Pass `SortOrder.Desc` as a second argument to `orderBy`.

## Typed Projections

`select[T](exprs*)` defines which columns appear in the output and how they decode. The macro validates that:

1. The number of expressions matches the arity of `T` (tuple or case class).
2. Each expression's type matches the corresponding position in `T`.
3. For case classes, `@Modifier.config("sql.inline_fields", "true")` causes fields to be flattened (a case class with `name: String` becomes a column named `"name"`, not a nested structure).

```scala mdoc:silent
// Two-column tuple projection
val qJoined = SqlQuery.from(userTable).innerJoin(userRepoRel)
val qProj = qJoined.select[(Int, String)](qJoined.col[User](_.id), qJoined.col[Repo](_.name))
```

Swapping the types fails at compile time:

```scala
// This does NOT compile:
// .select[(String, Int)](q.col[User](_.id), q.col[Repo](_.name))
// ^ type mismatch: position 0 expects String, got Int
```

Tuple projections support arity 2 through 8. Case class projections flatten inline fields:

```scala mdoc:silent
@Modifier.config("sql.inline_fields", "true")
case class UserRepoRow(userName: String, repoName: String)
object UserRepoRow { given Schema[UserRepoRow] = Schema.derived }
```

```scala mdoc:silent
val qRecord = qJoined.select[UserRepoRow](qJoined.col[User](_.name), qJoined.col[Repo](_.name))
```

This produces:

```sql
SELECT t0."name" AS "user_name", t1."name" AS "repo_name"
FROM "users" AS t0 INNER JOIN "repos" AS t1 ON t1."owner_id" = t0."id"
```

### LEFT JOIN Nullable Projections

When using `leftJoin`, columns from the right side can be `NULL`. Query-bound `q.col` derives nullability from the join slot: a LEFT JOIN slot automatically yields `Expr[Option[B], q.Scope]`, so the nullable column decodes as `Option` without any manual wrapper:

```scala mdoc:silent
val qLeftQ = SqlQuery.from(userTable).leftJoin(userRepoRel)
val qLeft = qLeftQ.select[(Int, Option[String])](qLeftQ.col[User](_.id), qLeftQ.col[Repo](_.name))
```

Source and inner-join slots stay non-optional; selecting a LEFT-joined column as a non-`Option` type fails at compile time.

### Self-Join Disambiguation

When a table joins to itself (e.g., employee manager lookup), both sides share the same table type. An unaliased `q.col[Employee](_.name)` is rejected at compile time because the table occupies more than one slot. Use `q.colAt(alias, _.field)` to specify which alias you mean:

```scala mdoc:silent
case class Employee(id: Int, name: String, managerId: Option[Int])
object Employee { given Schema[Employee] = Schema.derived }

val employeeTable = Table.derived[Employee]
val empSelfRel = Rel.manyToOne(employeeTable, _.managerId, employeeTable, _.id)

val qSelfQ = SqlQuery.from(employeeTable).innerJoin(empSelfRel)
val qSelf = qSelfQ.select[(String, String)](
  qSelfQ.colAt[Employee]("t0", _.name),
  qSelfQ.colAt[Employee]("t1", _.name)
)
```

Here `"t0"` is the employee and `"t1"` is their manager. The alias is resolved positionally against the query's slot list at compile time: the slot must exist and its table must match the selector, and nullability is derived from the slot (LEFT JOIN slots yield `Option`).

## Nested Fields

Case classes with nested structures can project individual nested fields using `_.outer.inner` selector syntax. When the inner type is annotated with `@Modifier.config("sql.inline", "true")`, the macro flattens the path into snake_case column names:

```scala mdoc:silent
case class Inner(street: String, city: String)
object Inner { given Schema[Inner] = Schema.derived }

@Modifier.config("sql.table_name", "outers")
case class Outer(@Modifier.config("sql.inline", "true") inner: Inner, label: String)
object Outer { given Schema[Outer] = Schema.derived }

val outerTable = Table.derived[Outer]
```

```scala mdoc:silent
val qOuter = SqlQuery.from(outerTable)
val qNested = qOuter.select[Outer](
  qOuter.col[Outer](_.inner.street),
  qOuter.col[Outer](_.inner.city),
  qOuter.col[Outer](_.label)
)
```

The `_.inner.street` selector maps to column `"inner_street"` (snake-joined), and `_.label` maps to `"label"`. The result decodes as `Outer(Inner("Main St", "NYC"), "office")`.

## Rendering SQL Without Execution

You can render the SQL text and inspect parameters without a database connection:

```scala mdoc:silent
val qRender = qJoined.select[(Int, String)](qJoined.col[User](_.id), qJoined.col[Repo](_.name))
```

The rendered SQL:

```
SELECT t0."id" AS "_1", t1."name" AS "_2"
FROM "users" AS t0
  INNER JOIN "repos" AS t1 ON t1."owner_id" = t0."id"
```

`qRender.sql(SqlDialect.SQLite)` returns the SQL string. `qRender.toFrag(SqlDialect.SQLite).params` returns any bound parameters.

## Execution

`TypedQuery` wraps the query IR plus its projection. Once built, call an execution method. All methods require an ambient `DbCon` or `DbTx` in scope, provided by `Transactor.connect` or `Transactor.transact`.

```scala mdoc:silent
Class.forName("org.sqlite.JDBC")
val transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
```

### List all rows

`run` and `query` are synonyms — both return `List[T]`:

```scala mdoc:compile-only
val rows: List[(Int, String)] = transactor.connect {
  userTable.createTable(SqlDialect.SQLite).update
  repoTable.createTable(SqlDialect.SQLite).update
  sql"INSERT INTO users (id, name) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("alice")})".update
  sql"INSERT INTO repos (id, owner_id, name) VALUES (${DbValue.DbInt(10)}, ${DbValue.DbInt(1)}, ${DbValue.DbString("r1")})".update
  qRender.run
}
```

### Single row

`queryOne` returns `Maybe(value)` if exactly one row matched, or `Maybe.absent` otherwise.

### Limited rows

`queryLimit(n)` applies a `LIMIT` without mutating the query builder.

### Streaming

`queryStream` streams all rows. `queryChunked(n)` fetches in batches of `n` rows. Both return ZIO `Stream` values that can be composed with other stream operations.

## Complete SQLite Example

This example uses SQLite's in-memory database. It creates tables, inserts rows, runs a typed query, and verifies the results. The same code works against PostgreSQL by swapping the dialect and transactor.

```scala mdoc:silent:reset
import zio.blocks.sql.query.*
import zio.blocks.sql.{DbValue, JdbcTransactor, SqlDialect, Table, sql}
import zio.blocks.schema.{Modifier, Schema}

// Domain
@Modifier.config("sql.table_name", "users")
case class U(id: Int, name: String)
object U { given Schema[U] = Schema.derived }

@Modifier.config("sql.table_name", "repos")
case class R(id: Int, ownerId: Int, name: String)
object R { given Schema[R] = Schema.derived }

// Tables
val uTable = Table.derived[U]
val rTable = Table.derived[R]

// Relation
val userRepoRel2 = Rel.manyToOne(rTable, _.ownerId, uTable, _.id)

// Create transactor
Class.forName("org.sqlite.JDBC")
val tx2 = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

// Build query — name the base query value, then project with its columns
val q2Base = SqlQuery.from(uTable).innerJoin(userRepoRel2)
val q2 = q2Base.select[(Int, String)](q2Base.col[U](_.id), q2Base.col[R](_.name))
```

Execute inside a `connect` block and verify the rows:

```scala mdoc
val rows2 = tx2.connect {
  uTable.createTable(SqlDialect.SQLite).update
  rTable.createTable(SqlDialect.SQLite).update
  sql"INSERT INTO users (id, name) VALUES (${DbValue.DbInt(1)}, ${DbValue.DbString("alice")})".update
  sql"INSERT INTO users (id, name) VALUES (${DbValue.DbInt(2)}, ${DbValue.DbString("bob")})".update
  sql"INSERT INTO repos (id, owner_id, name) VALUES (${DbValue.DbInt(10)}, ${DbValue.DbInt(1)}, ${DbValue.DbString("r1")})".update
  sql"INSERT INTO repos (id, owner_id, name) VALUES (${DbValue.DbInt(20)}, ${DbValue.DbInt(2)}, ${DbValue.DbString("r2")})".update
  q2.run.sortBy(_._1)
}

assert(rows2 == List((1, "r1"), (2, "r2")))
```

The rendered SQL:

```
SELECT t0."id" AS "_1", t1."name" AS "_2"
FROM "users" AS t0
  INNER JOIN "repos" AS t1 ON t1."owner_id" = t0."id"
```

## Arithmetic Expressions

Numeric columns support `+`, `-`, and `*` when the type has an `IsNumeric` evidence:

```scala mdoc:silent:reset
import zio.blocks.sql.query.*
import zio.blocks.sql.{JdbcTransactor, SqlDialect, Table}
import zio.blocks.schema.{Modifier, Schema}

@Modifier.config("sql.table_name", "users")
case class U2(id: Int, name: String)
object U2 { given Schema[U2] = Schema.derived }
val uTable2 = Table.derived[U2]

val qArith = SqlQuery.from(uTable2)
val doubled = qArith.col[U2](_.id) + qArith.col[U2](_.id)
val offset  = qArith.col[U2](_.id) - lit(1)
```

## LIKE Pattern Matching

`like` translates to SQL `LIKE` with `%` and `_` wildcards:

```scala mdoc:silent
val namePrefix = qArith.col[U2](_.name).like("a%")
```

Note that `like` uses SQL-style patterns (`%` wildcards), not regex patterns.

## Compile-Time Safety Boundary

The typed query API validates several things at compile time:

**Checked at compile time:**
- `q.col[A](_.field)`: field name exists in schema `A`, column type matches, the table is part of the query (a table not in the query is rejected), and the query-bound scope is carried into every expression.
- `q.colAt[A](alias, _.field)`: field selector is compile-checked (same as `col`); the alias is resolved positionally against the query's slot list — the slot must exist and its table must match the selector.
- Cross-query mixing: expressions built from one query value cannot be used in another query's `select`/`where`/`groupBy`/`having`.
- `select[T](exprs*)`: expression count matches `T` arity, each expression type matches the corresponding position in `T`.
- `Rel.manyToOne(from, fkSelector, to, pkSelector)`: field selectors resolve to column names via macro.
- `in(values)`: schema constraint on element type.
- Aggregates: `sum`/`avg` require supported numeric types (result types normalized via `SumOut`/`AvgOut`), `min`/`max` require `Ordering`, ordering comparisons reject `Boolean`.

**Checked at runtime (construction/render time):**
- `Rel` validates FK and PK column names exist in their respective `Table.columns` after construction.
- `SqlQuery` methods validate string column names against `SqlIdentifier` rules.
- `Table.derived[A]` reads metadata from `Schema[A]`; a malformed or missing `Schema` fails here.
- `select[T]` verifies column count matches projection size (macro check) and codec column count matches (runtime backstop).

**Not checked:**
- There is no full-static query shape validation (no compile-time check that a joined table's columns are actually present in the SQL schema). That would require a database connection at compile time.
- Table names and schema structure come from `Schema` metadata, not from the live database. If your database schema drifts from your case class definitions, errors surface at query execution, not at compile time.

## v1 Limitations

This is v1 of the typed query layer. The following are not supported yet:

- **ORM-style automatic relation inference**. You declare each relation explicitly with `Rel.manyToOne`.
- **Subqueries and CTEs**. Only flat queries with joins, filters, and aggregations.
- **`FOR UPDATE`** and other locking clauses.
- **Raw predicate `Frag` escape hatch in public API**. The `where(Frag)` overload exists but is `private[sql]`.
- **Async adapters**. Execution is synchronous JDBC, delegated to `Frag` extensions.
- **Composite key cursors**. Keyset pagination uses single-column cursors only (see `Frag.keysetAfter`).

## What the Production Renderer Does

The query IR renders to SQL through `QueryRenderer` — the sole query renderer — which builds every clause via `Frag.literal` and `Frag.++` composition. Each column is quoted as `alias."column_name"` and validated through `SqlIdentifier`. Parameters appear as `?` placeholders in the rendered SQL, with actual values carried in `Frag.params`. The former `zio.blocks.sql.SqlQuery` string-based builder and its duplicate renderer have been removed; all inspection (`explain`, `statement`, `Dump`) now derives from this typed IR without a second builder state machine.

If you need to build SQL from `SchemaExpr` expression trees instead of the typed `Expr` API, see [Query DSL with Reified Optics, Part 2: SQL Generation](./query-dsl-sql.md) for the `SchemaExpr`-based approach.

## Going Further

- **[Query DSL with Reified Optics, Part 2: SQL Generation](./query-dsl-sql.md)** -- Translating `SchemaExpr` expression trees to SQL
- **[sql(table) Checked Interpolation](./sql-checked-interpolation.md)** -- Compile-time identifier checking in raw SQL strings
- **[Query DSL with Reified Optics, Part 3: Extending the Expression Language](./query-dsl-extending.md)** -- Custom operators beyond `SchemaExpr`
- **[Query DSL with Reified Optics, Part 4: A Fluent SQL Builder](./query-dsl-fluent-builder.md)** -- Type-safe SELECT, UPDATE, INSERT, DELETE
- **[Table Reference](../reference/sql/table.md)** -- Table metadata and DDL
- **[Frag Reference](../reference/sql/frag.md)** -- Fragment composition and execution
- **[DbCodec Reference](../reference/sql/db-codec.md)** -- Type-safe database codecs