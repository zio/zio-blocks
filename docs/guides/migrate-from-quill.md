---
id: migrate-from-quill
title: "Coming from Quill"
description: "Move a Quill data-access layer to the ZIO Blocks sql module: concept map, side-by-side rewrites, what has no equivalent and a checklist."
keywords:
  - "Migration Guide"
  - "Quill Migration"
  - "Quoted Queries"
  - "Frag"
  - "Repo"
  - "JdbcTransactor"
---

This guide moves a data-access layer written with [Quill](https://zio.dev/zio-quill/) to the ZIO Blocks `sql` module. By the end, you will have a clinic's patients and appointments stored, queried, joined and updated through ZIO Blocks, and a checklist for porting the rest of your code.

The two libraries differ in one big way. Quill turns quoted Scala into SQL at compile time, and ZIO Blocks builds SQL at run time from SQL text. Most of this guide renames things, but that difference decides what you can and can't carry over, so the [last section before the checklist](#what-does-not-carry-over) spells it out.

The guide covers:

- A concept map from Quill names to ZIO Blocks names
- Contexts, naming strategies and custom table and column names
- Quoted queries and `run`: filters, sorting, paging and joins
- Inserts, updates, deletes, generated keys and batch actions
- Raw SQL, custom column types and transactions
- Running the same code from ZIO
- What has no equivalent, what you gain, and a migration checklist

## The Problem

A Quill data-access layer is tied to the library in many places: the context that owns the connection, the `quote` blocks that describe queries, the `lift` calls that bring Scala values into them, and the `run` calls that execute them. Porting it means finding a new home for each of those, and for the compile-time machinery behind them.

For example, a Quill repository for a clinic writes every query as a quoted Scala expression, and the compiler turns each one into SQL:

```scala
// Before: Quill 4.8.6 (not compiled here)
import io.getquill.*

case class Patient(patientId: Long, name: String, birthYear: Int, phone: Option[String])

val ctx = new SqliteJdbcContext(SnakeCase, "ctx")
import ctx.*

inline def bornBefore(year: Int) =
  quote {
    query[Patient].filter(p => p.birthYear < lift(year))
  }

val veterans: List[Patient] = ctx.run(bornBefore(1990))
```

Nothing in `bornBefore` mentions SQL, and the compiler checks it as Scala. Porting it means writing the SQL, or a query builder call, yourself, and getting back the checks the compiler used to do. This guide shows how, construct by construct.

## Prerequisites

Replace the Quill dependencies with the ZIO Blocks `sql` module and your JDBC driver. The `sql` module is published for Scala 3 only, as is Quill's Scala 3 build:

```scala
// Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-sql" % "@VERSION@"

// Scala 3.9 and newer, which also needs -experimental
libraryDependencies += "dev.zio" %% "zio-blocks-next-sql" % "@VERSION@"
scalacOptions += "-experimental"

// Your JDBC driver, for example SQLite
libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.53.4.0"
```

The module is cross-built for the JVM and Scala.js, but `JdbcTransactor`, the part that talks to a database, runs on the JVM only. The built-in dialects are `SqlDialect.PostgreSQL` and `SqlDialect.SQLite`.

This guide assumes you know the basics from [Build Your First Database Repository on SQLite](./your-first-sqlite-repository.md): a `Schema` for each entity, a `JdbcTransactor`, `Repo` and `sql"..."`. It maps what you already write onto them. The "before" snippets follow Quill 4.8.6 with `quill-jdbc` on Scala 3. They are plain Scala blocks, because Quill is not on this site's build. Every ZIO Blocks snippet is compiled, and most of them run against a SQLite file.

## Concept Map

The table below lists the constructs you use every day, with the name each library gives them. The sections that follow show each row in code:

| Concept                  | Quill                                                      | ZIO Blocks                                                           |
| ------------------------ | ---------------------------------------------------------- | -------------------------------------------------------------------- |
| Database handle          | `SqliteJdbcContext`, `PostgresJdbcContext`                 | `JdbcTransactor.fromUrl`, `JdbcTransactor.fromDataSource`            |
| SQL flavor               | the context class                                          | `SqlDialect.SQLite`, `SqlDialect.PostgreSQL`                         |
| Column naming            | naming strategy such as `SnakeCase`                        | snake_case by default, `@Modifier.rename`, `SqlNameMapper`           |
| Entity and table         | `query[A]`                                                 | `Table.derived[A]`, `Repo.derived[A, ID]`, with a `Schema[A]`        |
| Custom table and columns | `querySchema`, `schemaMeta`                                | `@Modifier.config("sql.table_name", ...)`, `@Modifier.rename`        |
| Query                    | `quote { query[A].filter(...) }`                           | `sql"..."`, or `SqlQuery.from(table)`                                |
| Run a query              | `ctx.run(quoted): List[A]`                                 | `Frag#query[A]: List[A]`                                             |
| Bind a value             | `lift(x)`                                                  | `$x` inside `sql"..."`                                               |
| Sort and page            | `sortBy`, `drop`, `take`                                   | `SqlQuery#orderBy`, `SqlQuery#offset`, `SqlQuery#limit`              |
| Join                     | `join(...).on(...)`, `leftJoin`, for-comprehension         | `Rel` with `SqlQuery#join`, or SQL text                              |
| Insert, update, delete   | `insertValue`, `updateValue`, `delete`                     | `Repo#insert`, `Repo#update`, `Repo#delete`, or `Frag#update`        |
| Generated keys           | `returningGenerated`                                       | `Frag#updateReturningKeys[K]: List[K]`                               |
| Batch                    | `liftQuery(rows).foreach(...)`                             | `Repo#insertBatch`, `Repo#insertAll`                                 |
| Raw SQL                  | `sql"...".as[Query[A]]`, `sql"...".as[Action[Long]]`       | `sql"..."` then `Frag#query[A]`, `Frag#update`                       |
| Custom column type       | `MappedEncoding`                                           | `DbCodec[A].transform`                                               |
| Transaction              | `ctx.transaction { ... }`                                  | `transactor.transact { ... }`                                        |
| Dynamic query            | `dynamicQuery`, `filterIf`                                 | ordinary Scala building a `Frag`                                     |
| ZIO integration          | `quill-jdbc-zio`: `Quill.Sqlite`, `Quill.DataSource`       | `zio-blocks-sql-zio`: `TransactorZIO`, `TransactorZIO.layer`         |
| SQL built at compile time | every static `quote`                                      | none; `sql(table)` checks names at compile time                      |
| Query probing            | `QueryProbing` (Scala 2 build only, not in Scala 3)        | none                                                                 |

## The Shared Clinic

Every section ends at the same ZIO Blocks code, so the setup comes first: a `Patient`, an `Appointment`, a transactor for a temporary SQLite file, the tables and the repositories. Quill entities can keep their `id` fields; here the fields are named `patientId` and `appointmentId`, and the appointment's link to a patient is `forPatient`, for a reason the [joins](#joins) section explains.

```scala mdoc:silent
import zio.blocks.schema.{Modifier, Schema}
import zio.blocks.sql.query.*
import zio.blocks.sql.*

case class Patient(patientId: Long, name: String, birthYear: Int, phone: Option[String])

object Patient {
  given Schema[Patient] = Schema.derived
}

case class Appointment(appointmentId: Long, forPatient: Long, day: String, reason: String, cancelled: Boolean)

object Appointment {
  given Schema[Appointment] = Schema.derived
}

val dbFile     = java.nio.file.Files.createTempFile("clinic", ".db")
val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

val patientTable     = Table.derived[Patient]
val appointmentTable = Table.derived[Appointment]
val patients         = Repo.derived[Patient, Long]
val appointments     = Repo.derived[Appointment, Long]
```

Each run of this page creates a new temporary file, so the database always starts empty.

:::caution
Every `JdbcTransactor#connect` and `JdbcTransactor#transact` call opens a new connection. With `jdbc:sqlite::memory:`, each new connection gets its own empty database, so use a file, or a `DataSource` that hands out the same connection, when you move SQLite tests over. [How to Test Database Code Against SQLite](./test-database-code-against-sqlite.md) shows both.
:::

## Contexts and Naming Strategies

A Quill context bundles three things: the connection settings, the SQL flavor and the naming strategy that turns `birthYear` into `birth_year`. You import its members to get `query`, `quote`, `lift` and `run`:

```scala
// Before: Quill 4.8.6 (not compiled here)
import io.getquill.*

val ctx = new SqliteJdbcContext(SnakeCase, "ctx") // settings read from the `ctx` config prefix
import ctx.*

val pgCtx = new PostgresJdbcContext(SnakeCase, "ctx")
```

ZIO Blocks splits these three apart. The connection is a `JdbcTransactor`, the flavor is the `SqlDialect` you pass to it, and the naming strategy is a property of the schema. Nothing is imported into scope except the `sql` package itself. We created the transactor in the shared clinic with `JdbcTransactor.fromUrl`; `JdbcTransactor.fromDataSource(dataSource, dialect)` takes a pooled `DataSource` instead, as [How to Connect to PostgreSQL with a Connection Pool](./connect-to-postgresql-with-a-connection-pool.md) shows.

Quill's `SnakeCase` is the default in ZIO Blocks, so `birthYear` is already `birth_year`:

```scala mdoc
println(patientTable.name + ": " + patientTable.columns.mkString(", "))
println(appointmentTable.name + ": " + appointmentTable.columns.mkString(", "))
```

`SqlNameMapper` also offers `Identity`, which leaves names alone, and `Custom` for any function, as [SqlNameMapper](../reference/sql/sql-name-mapper.md) shows. Table names are the type's name in snake_case; `TableNamingPolicy.Plural` derives `patients` instead.

ZIO Blocks also generates the `CREATE TABLE` statement from the schema, which Quill leaves to you. The generated statement declares no `PRIMARY KEY`, a known bug in 0.0.56, so we write the DDL by hand, as Quill users usually do. We create both tables:

```scala mdoc:silent
transactor.connect {
  sql"""CREATE TABLE patient (
    patient_id INTEGER PRIMARY KEY,
    name       TEXT NOT NULL,
    birth_year INTEGER NOT NULL,
    phone      TEXT
  )""".update
  sql"""CREATE TABLE appointment (
    appointment_id INTEGER PRIMARY KEY,
    for_patient    INTEGER NOT NULL REFERENCES patient (patient_id),
    day            TEXT NOT NULL,
    reason         TEXT NOT NULL,
    cancelled      INTEGER NOT NULL
  )""".update
}
```

## Quoted Queries and run

In Quill, a query is a Scala expression inside `quote`. Wrapping it in an `inline def` lets the compiler see the whole expression and generate the SQL during compilation; `lift` brings a runtime value in as a bound parameter:

```scala
// Before: Quill 4.8.6 (not compiled here)
inline def bornBefore(year: Int) =
  quote {
    query[Patient].filter(p => p.birthYear < lift(year))
  }

val veterans: List[Patient] = ctx.run(bornBefore(1990))
// Compile time: SELECT p.patient_id AS patientId, p.name, p.birth_year AS birthYear, p.phone FROM patient p WHERE p.birth_year < ?
```

In ZIO Blocks, you write the SQL, and every interpolated `$value` becomes a bound parameter, as `lift` did. The query runs as soon as you call `Frag#query`, on the calling thread, inside a `JdbcTransactor#connect` or `JdbcTransactor#transact` block. First we put three patients in the clinic with `Repo#insertBatch`, which is the counterpart of a Quill batch insert, covered [below](#batch-actions):

```scala mdoc:silent
transactor.transact {
  patients.insertBatch(
    List(
      Patient(1L, "Ann", 1980, Some("555-0100")),
      Patient(2L, "Ben", 1995, None),
      Patient(3L, "Cara", 1970, None)
    )
  )
}
```

Then we port `bornBefore`. A method that needs a database takes a `(using DbCon)` parameter, which the compiler fills in inside `connect` and `transact`:

```scala mdoc
def bornBefore(year: Int)(using DbCon): List[Patient] =
  sql"SELECT patient_id, name, birth_year, phone FROM patient WHERE birth_year < $year".query[Patient]

transactor.connect {
  bornBefore(1990).foreach(println)
}
```

A derived `DbCodec` matches result columns to fields by name, so the order of the columns in the `SELECT` doesn't matter.

Quill's `filter`, `sortBy`, `drop` and `take` have a counterpart in `SqlQuery`, a query builder over a `Table`. Its conditions are `Frag`s and its column names are strings, so it takes SQL, not Scala predicates:

```scala
// Before: Quill 4.8.6 (not compiled here)
val page = quote {
  query[Appointment].sortBy(a => a.day)(Ord.desc).drop(1).take(2)
}
```

The ZIO Blocks version is `SqlQuery#orderBy`, `SqlQuery#offset` and `SqlQuery#limit`. The next block prints the SQL it renders for SQLite and runs it:

```scala mdoc:silent
transactor.transact {
  appointments.insertBatch(
    List(
      Appointment(1L, 1L, "2026-01-05", "checkup", false),
      Appointment(2L, 1L, "2026-02-10", "flu", false),
      Appointment(3L, 2L, "2026-01-07", "back pain", true),
      Appointment(4L, 3L, "2026-03-01", "vaccine", false)
    )
  )
}

val latest = SqlQuery.from(appointmentTable).orderBy("day", SortOrder.Desc).offset(1).limit(2)
```

```scala mdoc
println(latest.sql(SqlDialect.SQLite))

transactor.connect {
  latest.toFrag(SqlDialect.SQLite).query[Appointment].foreach(println)
}
```

The [SqlQuery](../reference/sql/sql-query.md) reference lists every builder method. For keyset paging instead of `drop`, use `Repo#pageAfter`, described in [How to Upsert Rows and Paginate with Keyset Cursors](./upsert-and-paginate-with-keyset-cursors.md).

### Dynamic Queries

Quill's `dynamicQuery` builds a query at run time, for example with `filterIf`, and Quill then generates its SQL at run time too. In ZIO Blocks every query is already built at run time, so a dynamic query is ordinary Scala that combines `Frag`s with `Frag#++`. [How to Build a Query from Optional Search Filters](./build-a-query-from-optional-search-filters.md) shows a whole search form.

## Custom Table and Column Names

When the table or a column doesn't follow the naming strategy, Quill's `querySchema` names them one by one:

```scala
// Before: Quill 4.8.6 (not compiled here)
inline def clinicPatients = querySchema[Patient](
  "clinic_patient",
  _.name      -> "full_name",
  _.birthYear -> "birth_year_col"
)
```

In ZIO Blocks the names live on the entity, as annotations from `zio.blocks.schema.Modifier`. `@Modifier.config("sql.table_name", ...)` sets the table name, `@Modifier.rename` renames a column, and `@Modifier.id` picks the ID field when it isn't named `id` or `<entity>Id`:

```scala mdoc:silent
@Modifier.config("sql.table_name", "clinic_patient")
case class ClinicPatient(
  @Modifier.id patientNo: Long,
  @Modifier.rename("full_name") name: String,
  @Modifier.rename("birth_year_col") birthYear: Int
)

object ClinicPatient {
  given Schema[ClinicPatient] = Schema.derived
}
```

```scala mdoc
println(Table.derived[ClinicPatient].createTable(SqlDialect.SQLite).sql(SqlDialect.SQLite))
```

Quill's `schemaMeta`, which makes a mapping a `given` instance for every query, has the same replacement: the annotations apply wherever the entity is used. The checked `sql(table)` form reads names from the case class fields, so it ignores `@Modifier.rename`; see [sql(table) Checked Interpolation](./sql-checked-interpolation.md) before you rely on both together.

## Inserts, Updates and Deletes

Quill's write operations are quoted actions. `insertValue` writes a whole entity, `insert` with assignments writes some columns, and `update` and `delete` act on a filtered query:

```scala
// Before: Quill 4.8.6 (not compiled here)
ctx.run(query[Patient].insertValue(lift(Patient(4L, "Dee", 1985, None))))
ctx.run(query[Patient].filter(p => p.patientId == lift(2L)).updateValue(lift(Patient(2L, "Ben", 1995, Some("555-0199")))))
ctx.run(query[Patient].filter(p => p.patientId == lift(2L)).update(_.phone -> lift(Option("555-0150"))))
ctx.run(query[Patient].filter(p => p.patientId == lift(3L)).delete)
```

`Repo` covers the whole-entity versions. Each method runs at once and returns the number of affected rows, as Quill's `run` does for an action. Partial updates are plain `sql"..."` statements run with `Frag#update`:

```scala mdoc
transactor.transact {
  println(patients.insert(Patient(4L, "Dee", 1985, None)))
  println(patients.update(Patient(2L, "Ben", 1995, Some("555-0199"))))
  println(sql"UPDATE patient SET phone = ${Option("555-0150")} WHERE patient_id = ${2L}".update)
  println(patients.delete(3L))
}
```

Both `Repo#update` and the `sql` update return `1`. The `Repo` table in [Repo](../reference/sql/repo.md) lists the other methods, such as `Repo#find`, `Repo#all` and `Repo#count`.

### Generated Keys

Quill's `returningGenerated` runs an insert and gives back the column the database generated:

```scala
// Before: Quill 4.8.6 (not compiled here)
val newId: Long =
  ctx.run(query[Patient].insert(_.name -> lift("Eli"), _.birthYear -> lift(1990)).returningGenerated(_.patientId))
```

In ZIO Blocks, `Frag#updateReturningKeys[K]` runs the insert and returns every generated key as a `List`. Write the `INSERT` without the generated column, and use hand-written DDL with an `INTEGER PRIMARY KEY` so SQLite generates the ID, as we did above:

```scala mdoc
transactor.transact {
  val keys = sql"INSERT INTO patient (name, birth_year) VALUES (${"Eli"}, ${1990})".updateReturningKeys[Long]
  println(s"Generated keys: $keys")
}
```

:::warning
`Repo#insertReturning` also reads generated keys, but it can read the wrong column, a known bug in 0.0.56. On SQLite it reads the `rowid`, which is the ID only when the ID column is declared `INTEGER PRIMARY KEY`, and on PostgreSQL it reads the table's first column, which is the ID only when the ID is the first column. Prefer `Frag#updateReturningKeys` for generated IDs.
:::

### Batch Actions

Quill runs one action per element of a lifted list with `liftQuery(...).foreach` and returns the counts as a `List`:

```scala
// Before: Quill 4.8.6 (not compiled here)
val counts: List[Long] = ctx.run(
  liftQuery(List(Patient(10L, "Fay", 1992, None), Patient(11L, "Gus", 1988, None)))
    .foreach(p => query[Patient].insertValue(p))
)
```

`Repo#insertBatch` is the counterpart, one prepared statement run as a JDBC batch. It returns the total number of rows, not a list. `Repo#insertAll` writes one multi-row `INSERT` and returns the IDs it took from the rows:

```scala mdoc
transactor.transact {
  println(patients.insertBatch(List(Patient(10L, "Fay", 1992, None), Patient(11L, "Gus", 1988, None))))
  println(patients.insertAll(List(Patient(12L, "Hal", 1975, None), Patient(13L, "Ida", 2001, None))))
}
```

There is no batch counterpart for `updateValue` or `delete`: loop over `Repo#update` or `Repo#deleteAll(ids)` inside one `transact` block.

## Joins

Quill writes a join as a for-comprehension, or with `join(...).on(...)`, and the compiler produces a typed tuple:

```scala
// Before: Quill 4.8.6 (not compiled here)
val booked = quote {
  for {
    a <- query[Appointment] if !a.cancelled
    p <- query[Patient] if p.patientId == a.forPatient
  } yield (p.name, a.day, a.reason)
}
```

ZIO Blocks describes how two tables are linked with a `Rel`, and `SqlQuery#join` adds it to a query. A join selects every column of both tables, so the rows decode into a tuple of the two entities, in join order:

```scala mdoc:silent
val bookedQuery =
  SqlQuery
    .from(appointmentTable)
    .join(Rel.manyToOne(appointmentTable, _.forPatient, patientTable, _.patientId))
    .where(sql"cancelled = 0")
```

```scala mdoc
transactor.connect {
  bookedQuery.toFrag(SqlDialect.SQLite).query[(Appointment, Patient)].foreach { case (appointment, patient) =>
    println(s"${patient.name}: ${appointment.day} ${appointment.reason}")
  }
}
```

The selectors in `Rel.manyToOne` are checked at compile time. The `where` condition is SQL text, though: `cancelled = 0` isn't checked against the `Appointment` class. If you need a projection such as Quill's `yield (p.name, a.day)`, select into your own case class with a `sql"..."` statement.

:::warning
Give every column a name that no other joined table uses, as `patient_id`, `appointment_id` and `for_patient` do here. Rows decode into tuples by column label, and when two joined tables both have an `id` column, every `id` is read from the first table, so each appointment comes back paired with a patient that carries the appointment's ID. The SQL is correct; only the decoding is wrong. This is why the clinic entities don't use a plain `id` field, as the Quill ones can.
:::

A left join, Quill's `leftJoin`, is `SqlQuery#leftJoin`; [How to Write Joins and Inspect the Generated SQL](./write-joins-and-inspect-generated-sql.md) shows it, with the `Option` pairing it needs.

## Raw SQL

Quill's `sql"..."` interpolator, which replaces `infix` (deprecated in Quill's changelog), splices SQL into a quote and gives it a type with `.as`:

```scala
// Before: Quill 4.8.6 (not compiled here)
val byName = quote { (n: String) =>
  sql"SELECT patient_id, name, birth_year, phone FROM patient WHERE name = $n".as[Query[Patient]]
}
val changed = ctx.run(sql"UPDATE patient SET phone = '555-0199' WHERE patient_id = 2".as[Action[Long]])
```

This is the construct ZIO Blocks users write every day, so it ports by dropping the wrapper: `sql"..."` makes a `Frag`, `Frag#query[A]` replaces `.as[Query[A]]` plus `run`, and `Frag#update` replaces `.as[Action[Long]]` plus `run`. A `Frag` interpolated into another `Frag` is spliced in; any other value becomes a bound parameter. To splice raw text, such as a column list, use `SqlLiteral`:

```scala mdoc:silent
val columns   = SqlLiteral(patientTable.columns.mkString(", "))
val tableName = SqlLiteral(patientTable.name)
```

```scala mdoc
transactor.connect {
  val ann = sql"SELECT $columns FROM $tableName WHERE name = ${"Ann"}".queryOne[Patient]
  println(ann.fold("No such patient")(patient => s"Found: ${patient.name}"))
}
```

`SqlLiteral` splices its text unescaped, so only use it for SQL your program builds itself, never for user input.

## Custom Column Types

Quill maps a wrapper type with a pair of `MappedEncoding` instances, one for each direction:

```scala
// Before: Quill 4.8.6 (not compiled here)
case class Phone(value: String)

given MappedEncoding[Phone, String] = MappedEncoding(_.value)
given MappedEncoding[String, Phone] = MappedEncoding(Phone(_))
```

ZIO Blocks has one `DbCodec#transform` with the two functions in separate parameter lists. The codec reads the column and, through the same `given`, binds a `Phone` as a parameter:

```scala mdoc
case class Phone(value: String)

given DbCodec[Phone] = DbCodec[String].transform(Phone(_))(_.value)

transactor.connect {
  val phones = sql"SELECT phone FROM patient WHERE phone IS NOT NULL ORDER BY patient_id".query[Phone]
  println(phones)

  val wanted = Phone("555-0100")
  val owner  = sql"SELECT name FROM patient WHERE phone = $wanted".queryOne[String]
  println(owner.getOrElse("No patient with that phone"))
}
```

Scala 3 opaque types usually need no codec at all, because ZIO Blocks derives one from the underlying type; see [How to Map Custom Column Types](./map-custom-column-types.md) for opaque types, enums and JSON columns, and [DbCodec](../reference/sql/db-codec.md) for the rules.

## Transactions

Quill's `ctx.transaction` runs a block in one transaction, and an exception thrown inside it rolls everything back:

```scala
// Before: Quill 4.8.6 (not compiled here)
ctx.transaction {
  ctx.run(query[Patient].insertValue(lift(Patient(9L, "Jo", 1990, None))))
  throw new RuntimeException("Clinic is closed")
}
```

ZIO Blocks has the same block as a method on the transactor. `JdbcTransactor#transact` commits when the block returns, and rolls back and rethrows the exception when it throws. `JdbcTransactor#connect` runs without a transaction, in auto-commit mode:

```scala mdoc:silent
import scala.util.Try

val outcome = Try {
  transactor.transact {
    patients.insert(Patient(9L, "Jo", 1990, None))
    throw new IllegalStateException("Clinic is closed")
  }
}
```

```scala mdoc
println(outcome.failed.map(_.getMessage).getOrElse("Committed"))

transactor.connect {
  println(s"Patients named Jo: ${sql"SELECT name FROM patient WHERE name = ${"Jo"}".query[String].size}")
}
```

Methods that take `(using DbCon)` run in either block, and a method that needs a transaction asks for `(using DbTx)` instead. Isolation levels, read-only transactions and nested transactions with savepoints are covered in [SQL Transactions](./sql-transactions.md).

## ZIO Integration

`quill-jdbc-zio` gives you contexts such as `Quill.Sqlite`. You build a `DataSource` layer, then a context layer on top of it that captures the `DataSource`; the context's `run` returns a `ZIO[Any, SQLException, T]`:

```scala
// Before: Quill 4.8.6 with quill-jdbc-zio (not compiled here)
import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.*

val dataSourceLayer: ZLayer[Any, Throwable, javax.sql.DataSource] =
  Quill.DataSource.fromPrefix("ctx")

val program = ZIO.serviceWithZIO[Quill.Sqlite[SnakeCase.type]] { ctx =>
  import ctx.*
  ctx.run(query[Patient].filter(p => p.birthYear < lift(1990)))
}

program.provide(dataSourceLayer, Quill.Sqlite.fromNamingStrategy(SnakeCase))
```

ZIO Blocks has the `zio-blocks-sql-zio` module, which provides `TransactorZIO`. Its `TransactorZIO#connect` and `TransactorZIO#transact` take the same blocks as `JdbcTransactor` and return a `Task`, running the block with `ZIO.attemptBlocking`. `TransactorZIO.layer(url, dialect)` provides the transactor as a `ZLayer`, and `TransactorZIO.fromDataSource` builds it on a pool:

```scala mdoc:reset:silent
import zio.*
import zio.blocks.schema.Schema
import zio.blocks.sql.zio.TransactorZIO
import zio.blocks.sql.*

case class Patient(patientId: Long, name: String, birthYear: Int, phone: Option[String])

object Patient {
  given Schema[Patient] = Schema.derived
}

val patients = Repo.derived[Patient, Long]

val transactorLayer: ZLayer[Any, Nothing, TransactorZIO] =
  TransactorZIO.layer("jdbc:sqlite:clinic.db", SqlDialect.SQLite)

val countPatients: ZIO[TransactorZIO, Throwable, Long] =
  ZIO.serviceWithZIO[TransactorZIO](_.connect(patients.count))

val bornBefore1990: ZIO[TransactorZIO, Throwable, List[Patient]] =
  ZIO.serviceWithZIO[TransactorZIO] { transactor =>
    transactor.connect {
      sql"SELECT patient_id, name, birth_year, phone FROM patient WHERE birth_year < ${1990}".query[Patient]
    }
  }

val program = (countPatients zip bornBefore1990).provide(transactorLayer)
```

Keep `import zio.blocks.sql.*` after every other `zio` import, as above: once `sql-zio` is on the classpath, it brings the subpackage `zio.blocks.sql.zio` into scope under the name `zio`, and a later `import zio.blocks...` fails to compile.

Quill's `ctx.transaction` inside a ZIO program becomes `TransactorZIO#transact`. `TransactorZIO#transact` opens and closes the connection for you, and `TransactorZIO#transactZIO` takes a block that returns a `ZIO` and closes the connection even when the fiber is interrupted. [How to Use the sql Module from a ZIO Application](./sql-from-zio.md) covers layers, services, retries and mixing database writes with other effects, and [TransactorZIO](../reference/sql/transactor-zio.md) lists every method.

## What Does Not Carry Over

Quill's defining feature has no counterpart in ZIO Blocks. Quill reads the Scala inside a `quote` at compile time, rewrites it into SQL and checks it, so a static query costs almost nothing to build at run time and a wrong query is a compile error. ZIO Blocks has no quoted queries: its `sql"..."` interpolator and `SqlQuery` take SQL text and `Frag`s, and build the SQL string when the program runs. Three consequences follow.

- **The compiler no longer type-checks your predicates.** In Quill, `p.birthYear < lift(year)` fails to compile when `birthYear` isn't an `Int`. In ZIO Blocks, `"birth_year < " + year` is SQL, and the database tells you at run time if it's wrong.
- **Names can still be checked, but only names.** The `sql(table)` form, written `StringContext(...).sql(table)(args)`, checks every table and column name in the SQL against the `Table` values you pass, with a did-you-mean suggestion. It doesn't type-check expressions, and it doesn't see through aliases or subqueries. [sql(table) Checked Interpolation](./sql-checked-interpolation.md) lists its limits.
- **No compile-time database check.** Quill's documentation describes query probing, an opt-in `QueryProbing` mix-in that validates static queries against a live database while compiling, but only the Scala 2 build implements it; Quill on Scala 3 has no query probing. ZIO Blocks never connects to a database at compile time. The nearest feature is `Dump`: it writes the SQL of your `SqlQuery` and `Table` values to `.sql` files at compile time when the `zib.sql.dumpDir` system property is set, so you can review the SQL in a pull request. It shows the SQL; it doesn't check it. See [Dump](../reference/sql/dump.md).

Compile-time errors in Quill that were really type errors become test failures in ZIO Blocks, so port your queries together with tests that run them against SQLite. [How to Test Database Code Against SQLite](./test-database-code-against-sqlite.md) shows how.

## What You Gain and What's Different

Moving to ZIO Blocks gives you:

- **SQL you can read in the source.** What the program sends is what the code says, and `Frag#sql(dialect)` prints it. There is no macro expansion to reason about.
- **One schema for everything.** The `Schema` that maps a row also drives JSON and the other ZIO Blocks formats, so an entity is described once.
- **Generated repositories.** `Repo.derived` adds CRUD operations, upserts and keyset pagination for free.
- **No effect system required.** The core module depends on no effect library; ZIO users add `zio-blocks-sql-zio`, and everyone else calls it directly.

Things that work differently, or don't exist yet:

- **Scala 3 only**, as Quill on Scala 3 is, but there is no Scala 2.13 build to fall back on.
- **Two dialects.** `SqlDialect` supports PostgreSQL and SQLite. Quill also supports MySQL, H2, SQL Server and Oracle contexts.
- **No query language in Scala.** Filters, sorting and joins are SQL text or `SqlQuery` builder calls with string column names.
- **No compile-time SQL generation.** See the section above.
- **Direct style.** Database methods run on the calling thread and throw exceptions, where `quill-jdbc-zio` returns `ZIO` values; ZIO code wraps the calls with `TransactorZIO`.
- **Results.** Queries return `List`, and a single result is a `Maybe` rather than an `Option`.
- **No generated primary keys in DDL.** `Table#createTable` declares no `PRIMARY KEY`; write the DDL for tables with generated IDs yourself.

## Migration Checklist

Work through these steps in order; each one leaves the project compiling:

1. Make sure the project is on Scala 3.
2. Replace the Quill dependencies with `zio-blocks-sql` (or `zio-blocks-next-sql` with `-experimental` on Scala 3.9 and newer), and add `zio-blocks-sql-zio` if you use ZIO.
3. Give every entity a `given Schema[E] = Schema.derived`, and rename `id` fields if two joined tables share the name.
4. Replace `querySchema` and `schemaMeta` with `@Modifier.config("sql.table_name", ...)`, `@Modifier.rename` and `@Modifier.id` on the entity.
5. Port each `MappedEncoding` pair to one `DbCodec#transform`.
6. Replace each context with one `JdbcTransactor` from `JdbcTransactor.fromUrl` or `JdbcTransactor.fromDataSource`, passing the right `SqlDialect`, and drop `import ctx.*`.
7. Turn each `inline def ... quote { ... }` into a method with a `(using DbCon)` parameter, written as a `sql"..."` statement or a `SqlQuery`, and replace `lift(x)` with `$x`.
8. Replace `insertValue`, `updateValue` and `delete` with `Repo#insert`, `Repo#update` and `Repo#delete`, and partial inserts and updates with `sql"..."` statements run with `Frag#update`.
9. Replace `returningGenerated` with `Frag#updateReturningKeys`, and `liftQuery(...).foreach` with `Repo#insertBatch` or `Repo#insertAll`.
10. Replace for-comprehension joins with `Rel` and `SqlQuery#join`, checking that no two joined tables share a column name.
11. Replace `ctx.transaction` with `transactor.transact`, and `ctx.run` calls outside a transaction with `transactor.connect`.
12. Replace the `Quill.Sqlite` and `Quill.DataSource` layers with `TransactorZIO.layer` or `TransactorZIO.fromDataSource`.
13. Pass `Table` values to `sql"..."` for the queries whose names you want checked at compile time, and add tests that run your queries against SQLite.

## Putting It Together

The complete example below is the clinic after the migration: entities, repositories, a hand-written query, a `SqlQuery` page, a join, a batch insert, generated keys, a custom column type and a transaction that rolls back, all on SQLite:

```scala mdoc:embed:sql-examples/src/main/scala/migratefromquill/CompleteExample.scala
```

## Running the Examples

The complete example has a runnable Scala file in the `sql-examples` project. To get it, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/sql-examples
```

The example creates its own temporary database file, so you can run it as often as you like.

<details>
  <summary>sql-examples/src/main/scala/migratefromquill/CompleteExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/migratefromquill/CompleteExample.scala:show-line-numbers
```

</details>

Run it from the `sql-examples` directory:

```bash
sbt "runMain migratefromquill.CompleteExample"
```

To compile the example without running it:

```bash
sbt compile
```

## Going Further

- [Build Your First Database Repository on SQLite](./your-first-sqlite-repository.md) teaches the basics this guide builds on.
- [Coming from Magnum or doobie](./migrate-from-magnum-or-doobie.md) maps two other Scala data-access libraries onto the same module.
- [SQL Transactions](./sql-transactions.md) covers isolation levels, read-only transactions and nested transactions with savepoints.
- [sql(table) Checked Interpolation](./sql-checked-interpolation.md) explains the compile-time name check in detail.
- [Repo](../reference/sql/repo.md) and [Frag](../reference/sql/frag.md) list every repository and fragment operation.
- [SQL Module](../reference/sql/index.md) links to every `sql` reference page.
