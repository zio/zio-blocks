---
id: dump
title: "Dump"
description: "Reference for Dump, the sql module's compile-time SQL dumps: dumpQuery and dumpTable write .sql files for review when zib.sql.dumpDir is set."
keywords:
  - "Compile-Time SQL"
  - "SQL Review"
  - "zib.sql.dumpDir"
  - "dumpQuery"
  - "dumpTable"
  - "Dump"
---

`Dump` is a **compile-time SQL dump**: two macros that write the SQL of a [`SqlQuery`](./sql-query.md) or the `CREATE TABLE` statement of a [`Table`](./table.md) to `.sql` files while the code compiles. The files let you review the SQL a program runs without running it, for example in a pull request, where a change to a join shows up as a diff of its SQL, or for a database administrator who doesn't read Scala. Dumping is opt-in: the macros only write when the JVM system property `zib.sql.dumpDir` is set in the JVM that runs the compiler, and otherwise each call expands to `()` and costs nothing at run time.

`Dump` lives in the `zio.blocks.sql` package of the `zio-blocks-sql` module, which is published for Scala 3 only (see the [SQL module](./index.md) page). Its key properties are:

- Each call writes one file per dialect, for PostgreSQL and for SQLite.
- The macros read the source code of their argument, not its value, so the query must be written out where the macro can see it.
- Problems never fail the build: a part the macro can't read produces a compiler warning, and the file is written without that part or not at all.
- While the property is set, every `sql"..."` fragment writes its own file too.

`Dump` is an object with two inline methods:

```scala
object Dump {
  inline def dumpQuery[A](inline query: SqlQuery[A]): Unit
  inline def dumpTable[A](inline table: Table[A])(using schema: Schema[A]): Unit
}
```

## Usage

We put the queries to review in `inline def`s, so the macro can read them, and call `Dump.dumpQuery` and `Dump.dumpTable` in an object that is compiled with the rest of the code:

```scala mdoc:compile-only
import zio.blocks.schema.Schema
import zio.blocks.sql.query._
import zio.blocks.sql._

case class Customer(customerId: Int, name: String, city: String, referrerId: Option[Int])
object Customer { implicit val schema: Schema[Customer] = Schema.derived }

case class Invoice(invoiceId: Int, buyerId: Int, year: Int, totalCents: Long)
object Invoice { implicit val schema: Schema[Invoice] = Schema.derived }

object ShopTables {
  val customers = Table.derived[Customer]
  val invoices  = Table.derived[Invoice]
}

object ShopQueries {
  inline def largeInvoices: SqlQuery[Invoice] =
    SqlQuery
      .from(ShopTables.invoices)
      .join(Rel.manyToOne(ShopTables.invoices, _.buyerId, ShopTables.customers, _.customerId))
      .where(Frag(IndexedSeq("""t0."total_cents" > """, ""), IndexedSeq(DbValue.DbLong(10000L))))
      .orderBy("year", SortOrder.Desc)
      .limit(10)
}

object ShopDumps {
  val largeInvoices = Dump.dumpQuery(ShopQueries.largeInvoices)

  Dump.dumpTable(ShopTables.customers)
  Dump.dumpTable(ShopTables.invoices)
}
```

Compiling with the property set writes the files:

```bash
sbt -Dzib.sql.dumpDir=target/sql-dumps clean compile
```

The query's file, `target/sql-dumps/largeInvoices_query-postgresql.sql`, holds the same SQL that `SqlQuery#sql` returns at run time, on one line:

```sql
SELECT t0."invoice_id", t0."buyer_id", t0."year", t0."total_cents", t1."customer_id", t1."name", t1."city", t1."referrer_id" FROM "invoice" AS t0 INNER JOIN "customer" AS t1 ON t0."buyer_id" = t1."customer_id" WHERE t0."total_cents" > ? ORDER BY t0."year" DESC LIMIT 10
```

Each table's file, such as `target/sql-dumps/invoice-postgresql.sql`, holds its `CREATE TABLE` statement:

```sql
CREATE TABLE IF NOT EXISTS invoice (
  invoice_id INTEGER NOT NULL,
  buyer_id INTEGER NOT NULL,
  year INTEGER NOT NULL,
  total_cents BIGINT NOT NULL
)
```

The `.sql` listings on this page are copied from the files that a compile of this code against `zio-blocks-sql` 0.0.56 wrote.

## Turning Dumps On

The macros look up `zib.sql.dumpDir` when they expand, so the property must be set in the JVM that runs the Scala compiler, and the directory it names is resolved against that JVM's working directory. With sbt, that is the sbt JVM:

```bash
sbt -Dzib.sql.dumpDir=target/sql-dumps clean compile
```

`SBT_OPTS="-Dzib.sql.dumpDir=target/sql-dumps"` works too. A running sbt server (`sbt --client`, or the one an IDE starts) keeps the properties it was started with, so shut it down and start it again with the property. `clean` matters as well: incremental compilation skips unchanged files, and a file that isn't compiled writes no dumps. Passing the property through `scalacOptions` or `javaOptions` does nothing, because those reach the compiler as flags or the forked program at run time, not the compiler's JVM.

With scala-cli, compile without the build server, so the compiler runs in a JVM that you start, and pass the property through `JAVA_TOOL_OPTIONS`:

```bash
JAVA_TOOL_OPTIONS="-Dzib.sql.dumpDir=$PWD/sql-dumps" scala-cli compile --server=false .
```

## Core Operations

`Dump` has two operations, one per kind of SQL it writes, plus the files that the `sql"..."` interpolator writes on its own while the property is set. All of them write through the same file rules, described in [File Handling](#file-handling).

### Dumping a Query

`Dump.dumpQuery` writes a query's `SELECT` statement to `<name>_query-<dialect>.sql`:

```scala
object Dump {
  inline def dumpQuery[A](inline query: SqlQuery[A]): Unit
}
```

#### What the Macro Can Read

The macro rebuilds the SQL from the source code of the query, so each part of the query must be visible at the call site:

- The query is an `inline def`, or the `SqlQuery.from(...)` chain itself written in the call. A query stored in a plain `val` or built at run time writes no file, and the compiler warns `Dump requires inline query value; preconstructed value will emit no file; ...`.
- Each `Rel` is built inside the chain. A `Rel` stored in a `val` gives the warning `Dump.dumpQuery: could not decode Rel for join`, and the file is written without that join.
- `orderBy` columns are string literals, and `limit` and `offset` take integer literals. Anything else gives a warning such as `Dump.dumpQuery: limit expects int literal`, and the file is written without that clause.
- An `IN` condition built from a `DbArray` whose size isn't known at compile time writes no file, with a warning that starts `IN placeholder: compile-time DbArray cardinality is indeterminate`.

A query that passes these rules is written with the same SQL as `SqlQuery#sql`, plain `?` placeholders included. Unlike `SqlQuery#explain`, the file has no numbered placeholders and no `-- params:` line.

:::warning
A `where(sql"...")` condition loses its parameter in the dump, a known bug in 0.0.56. A query written as `SqlQuery.from(ShopTables.invoices).where(sql"""t0."year" = ${2026}""")` is dumped as `... WHERE t0."year" = ` with no `?`, while `SqlQuery#explain` shows `WHERE t0."year" = ?1`. A condition built with the `Frag` constructor, as in [Usage](#usage), keeps its `?`. Use `SqlQuery#explain` to check queries with `sql"..."` conditions.
:::

#### How Files Are Named

The `<name>` part of the file name comes from where the call is. For a call assigned to a `val` or made inside a `def`, it is that `val` or `def`'s name: `val largeInvoices = Dump.dumpQuery(...)` writes `largeInvoices_query-postgresql.sql` and `largeInvoices_query-sqlite.sql`. A call inside `def inDef(): Unit` writes `inDef_query-<dialect>.sql`.

:::warning
A `Dump.dumpQuery` call that isn't assigned to a `val`, written directly in an object body, is named after the object as `_local_<Object>___query-<dialect>.sql` (three underscores before `query`), and every other such call in the same object writes the same file, so only the last one survives. This is a known bug in 0.0.56: assign every call to its own `val`.
:::

### Dumping a Table

`Dump.dumpTable` writes a table's `CREATE TABLE IF NOT EXISTS` statement to `<table>-<dialect>.sql`, with each dialect's column types: an `Int` column is `INTEGER` in both files, and a `Long` column is `BIGINT` for PostgreSQL and `INTEGER` for SQLite. It needs the row type's `Schema` in scope:

```scala
object Dump {
  inline def dumpTable[A](inline table: Table[A])(using schema: Schema[A]): Unit
}
```

Like the statement from `Table#createTable`, the dumped statement declares no `PRIMARY KEY`. The SQLite file for the `customer` table from [Usage](#usage) is:

```sql
CREATE TABLE IF NOT EXISTS customer (
  customer_id INTEGER NOT NULL,
  name TEXT NOT NULL,
  city TEXT NOT NULL,
  referrer_id INTEGER
)
```

:::warning
`Dump.dumpTable` doesn't read the `Table` value; it rebuilds the statement from the row type, a known bug in 0.0.56. Every column is the field name in snake case, even when `@Modifier.rename` gives the column another name, and the table name is the first string literal in the argument, such as `Table.derived[Invoice]("invoices")`, or else the type name in snake case. A table created with `TableNamingPolicy.Plural`, or named by a string stored elsewhere, is dumped under the singular type name. `Dump.dumpQuery` takes its table and column names the same way. Compare the dumps of such tables with `Table#createTable` before you rely on them.
:::

### Fragment Files

While the property is set, every `sql"..."` fragment also writes a file, `<owner>-<dialect>.sql`, with the fragment's SQL text and a `?` for each interpolated value. The owner is the enclosing `def` or `val`. A fragment in this method:

```scala mdoc:compile-only
import zio.blocks.sql._

def byCity(city: String): Frag =
  sql"""SELECT name FROM customer WHERE city = $city"""
```

writes `byCity-postgresql.sql` and `byCity-sqlite.sql`, each containing:

```sql
SELECT name FROM customer WHERE city = ?
```

A fragment inside an `inline def` writes one file per place the `inline def` is used, named after the owner at that place. The table-checked form of the interpolator, described in [sql(table) Checked Interpolation](../../guides/sql-checked-interpolation.md), writes the same files. Two fragments with the same owner write the same file, and the last one compiled wins.

## File Handling

All dump files follow the same rules:

- The directory and any missing parent directories are created on the first write.
- Files are written as UTF-8 with a trailing newline.
- A file whose content is already the same isn't rewritten, so committed dump files only change when the SQL does.
- A name that isn't a plain SQL identifier has every other character replaced with `_`, which is why `largeInvoices-query` becomes `largeInvoices_query`.
- A write that fails gives a compiler warning that starts `[zib.sql.dumpDir] Failed to dump` and doesn't fail the build.
- Nothing is ever deleted, so the files of a removed or renamed query stay until you clear the directory.

To keep committed dumps in step with the code, clear the directory and run `sbt -Dzib.sql.dumpDir=<dir> clean compile` before committing.

## Integration

`Dump` reviews the SQL that the module's other types generate:

- [`SqlQuery`](./sql-query.md) is the query type `Dump.dumpQuery` reads; `SqlQuery#explain` prints the same SQL at run time, with parameter types.
- [`Rel`](./rel.md) describes the joins, and must be built inside a dumped query.
- [`Table`](./table.md) and [`Ddl`](./ddl.md) produce the `CREATE TABLE` statements that `Dump.dumpTable` writes at compile time.
- [How to Write Joins and Inspect the Generated SQL](../../guides/write-joins-and-inspect-generated-sql.md) has a runnable dump example in the `sql-examples` project.
