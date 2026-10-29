---
id: migrate-from-magnum-or-doobie
title: "Coming from Magnum or doobie"
description: "Move a Magnum or doobie data-access layer to the ZIO Blocks sql module: concept map, side-by-side rewrites and a checklist."
keywords:
  - "Migration Guide"
  - "Magnum Migration"
  - "doobie Migration"
  - "Frag"
  - "Repo"
  - "JdbcTransactor"
---

This guide moves a data-access layer written with [Magnum](https://github.com/AugustNagro/magnum) or [doobie](https://typelevel.org/doobie/) to the ZIO Blocks `sql` module. By the end, you will have a bookshelf repository whose entities, queries, transactions and streams run on ZIO Blocks, and a checklist for porting the rest of your code.

Magnum users will find most names unchanged: `DbCodec`, `Repo`, `DbCon`, `DbTx`, `Frag` and `SqlLiteral` all exist in ZIO Blocks with the same roles, so the [Magnum section](#coming-from-magnum) concentrates on the few real differences. doobie users change their effect model as well as their names, so the [doobie section](#coming-from-doobie) is longer and starts with that shift.

The guide covers:

- A concept map from Magnum and doobie names to ZIO Blocks names
- Coming from Magnum: entities, repositories, transactors, the `sql"..."` interpolator, `Spec` queries and custom column types
- Coming from doobie: the effect model, transactors, `Read`/`Write`/`Meta`, fragments, query results, streaming and query checking
- Running the same code from ZIO or cats-effect
- What you gain, what's different, and a migration checklist

## The Problem

A data-access layer is tied to its library in many small places: how a case class becomes a row, how a connection is opened, how a query returns zero, one or many rows, and how errors surface. Porting it means finding the new name for every one of those places. When the two libraries differ in their effect model, as doobie and ZIO Blocks do, the shape of every data-access method changes too.

For example, a doobie repository describes every query as a `ConnectionIO` program and only runs it when an `IO` is executed:

```scala
// Before: doobie 1.0.0-RC13 (not compiled here)
import cats.effect.IO
import org.typelevel.doobie.*
import org.typelevel.doobie.implicits.*

case class Book(id: Long, title: String, author: String, year: Int, isbn: Option[String])

def booksBefore(year: Int): ConnectionIO[List[Book]] =
  sql"SELECT id, title, author, year, isbn FROM book WHERE year < $year".query[Book].to[List]

val xa = Transactor.fromDriverManager[IO](
  driver = "org.sqlite.JDBC",
  url = "jdbc:sqlite:bookshelf.db",
  logHandler = None
)

val classics: IO[List[Book]] = booksBefore(2010).transact(xa)
```

Every method returns a `ConnectionIO`, every caller composes them in a for-comprehension, and the result only exists inside `IO`. Porting that code is mostly mechanical once you know the target shape, and this guide gives you that shape for each construct.

## Prerequisites

Replace the Magnum or doobie dependencies with the ZIO Blocks `sql` module and your JDBC driver. The `sql` module is published for Scala 3 only:

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

This guide assumes you know the basics from [Build Your First Database Repository on SQLite](./your-first-sqlite-repository.md): a `Schema` for each entity, a `JdbcTransactor`, `Repo` and `sql"..."`. This guide does not re-teach them; it maps what you already write onto them. The "before" snippets follow Magnum 2.0.0-M4 and doobie 1.0.0-RC13. They are plain Scala blocks, because neither library is on this site's build. Every ZIO Blocks snippet is compiled, and most of them run against a SQLite file.

## Concept Map

The table below lists the constructs you use every day, with the name each library gives them. The sections that follow show each row in code:

| Concept                 | Magnum                                       | doobie                                             | ZIO Blocks                                                |
| ----------------------- | -------------------------------------------- | -------------------------------------------------- | --------------------------------------------------------- |
| Row mapping             | `derives DbCodec`                            | `Read` / `Write`, derived automatically            | `Schema.derived`; `DbCodec` is derived from it            |
| Custom column type      | `DbCodec[A].biMap`                           | `Meta[A].timap`, `Get` / `Put`                     | `DbCodec[A].transform`                                    |
| Table metadata          | `@Table(dbType, nameMapper)`, `TableInfo`    | none, SQL is written by hand                       | `Table.derived[A]`                                        |
| Column naming           | `SqlNameMapper.CamelToSnakeCase`, `@SqlName` | none, SQL is written by hand                       | snake_case by default, `@Modifier.rename`                 |
| Repository              | `Repo[EC, E, ID]`, `ImmutableRepo[E, ID]`    | none                                               | `Repo.derived[E, ID]`                                     |
| Dynamic criteria        | `Spec[E]`                                    | `Fragments.whereAndOpt`                            | `SqlQuery.from(table)`, or `Frag` composition             |
| SQL fragment            | `Frag`, `sql"..."`                           | `Fragment`, `sql"..."`, `fr"..."`, `fr0"..."`      | `Frag`, `sql"..."`                                        |
| Raw SQL splice          | `SqlLiteral`                                 | `Fragment.const`                                   | `SqlLiteral`                                              |
| Database program        | a block with `using DbCon` / `DbTx`          | `ConnectionIO[A]`                                  | a block with `using DbCon` / `DbTx`                       |
| Transactor              | `Transactor(dataSource)`                     | `Transactor.fromDriverManager`, `HikariTransactor` | `JdbcTransactor.fromUrl`, `JdbcTransactor.fromDataSource` |
| Run on a connection     | `connect(xa) { ... }`                        | none, `transact` always opens a transaction        | `transactor.connect { ... }`                              |
| Run in a transaction    | `transact(xa) { ... }`                       | `program.transact(xa)`                             | `transactor.transact { ... }`                             |
| All rows                | `.query[A].run(): Vector[A]`                 | `.query[A].to[List]`                               | `Frag#query[A]: List[A]`                                  |
| At most one row         | `repo.findById(id): Option[E]`               | `.query[A].option`                                 | `Frag#queryOne[A]: Maybe[A]`, `Repo#find`                 |
| Streaming               | `.query[A].iterator()`                       | `.query[A].stream` (fs2)                           | `Frag#queryStream`, `Frag#queryChunked`                   |
| Insert, update, delete  | `.update.run(): Int`                         | `.update.run`                                      | `Frag#update: Int`                                        |
| Generated keys          | `.returning[K]`, `.returningKeys`            | `.update.withUniqueGeneratedKeys[K]`               | `Frag#updateReturningKeys[K]: List[K]`                    |
| Query checking          | none                                         | `.check` against a live database                   | `StringContext(...).sql(table)(...)` at compile time      |
| ZIO integration         | `magnumzio`: `TransactorZIO`                 | none                                               | `zio-blocks-sql-zio`: `TransactorZIO`                     |
| cats-effect integration | `magnum-cats-effect`: `TransactorCats`       | built in                                           | none; wrap calls in `IO.blocking`                         |

## The Shared Bookshelf

Both migration paths end at the same ZIO Blocks code, so the setup comes first. It reuses the bookshelf from [the SQLite tutorial](./your-first-sqlite-repository.md): a `Book` with a derived `Schema`, a transactor for a temporary SQLite file, the table and the repository:

```scala mdoc:silent
import zio.blocks.maybe.Maybe
import zio.blocks.schema.{Modifier, Schema}
import zio.blocks.sql.query.SqlQuery
import zio.blocks.sql.*

case class Book(id: Long, title: String, author: String, year: Int, isbn: Option[String])

object Book {
  given Schema[Book] = Schema.derived
}

val dbFile     = java.nio.file.Files.createTempFile("bookshelf", ".db")
val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

val bookTable = Table.derived[Book]
val books     = Repo.derived[Book, Long]
```

The `author` field holds each book's first author. Each run of this page creates a new temporary file, so the database always starts empty.

:::caution
Every `JdbcTransactor#connect` and `JdbcTransactor#transact` call opens a new connection. With `jdbc:sqlite::memory:`, each new connection gets its own empty database, so use a file, or a `DataSource` that hands out the same connection, when you move SQLite tests over. [How to Test Database Code Against SQLite](./test-database-code-against-sqlite.md) shows both.
:::

## Coming from Magnum

Magnum and the ZIO Blocks `sql` module share their core design: entities are plain case classes, database work runs in a context function that receives a `DbCon` or `DbTx`, and queries are written with a `sql"..."` interpolator that returns a `Frag`. Most of a Magnum code base therefore ports by changing imports and a handful of method names. The sections below cover the places where the two libraries really differ.

### Entities and Tables

A Magnum entity carries its database type and naming policy in a `@Table` annotation, marks its ID with `@Id` and derives a `DbCodec`:

```scala
// Before: Magnum 2.0.0-M4 (not compiled here)
import com.augustnagro.magnum.*

@Table(SqliteDbType, SqlNameMapper.CamelToSnakeCase)
case class Book(
  @Id id: Long,
  title: String,
  author: String,
  year: Int,
  isbn: Option[String]
) derives DbCodec
```

In ZIO Blocks, the entity only derives a `Schema`, as in the shared bookshelf above. Three things move elsewhere. The database type becomes a `SqlDialect` on the transactor, so the same entity works with PostgreSQL and SQLite. Column names are snake_case by default, which matches `SqlNameMapper.CamelToSnakeCase`. And the ID needs no annotation when the field is named `id`, is the only field of the ID type, or is named `<entity>Id`.

ZIO Blocks also generates the `CREATE TABLE` statement from the same schema, which Magnum leaves to you. The next block prints the statement and runs it:

```scala mdoc
println(bookTable.createTable(SqlDialect.SQLite).sql(SqlDialect.SQLite))

transactor.connect {
  bookTable.createTable(SqlDialect.SQLite).update
  println(s"Rows in ${bookTable.name}: ${books.count}")
}
```

The generated statement declares no `PRIMARY KEY`, not even on the ID column. If you rely on a primary key, for example for `Repo#insertOrUpdate`, keep writing that table's DDL yourself.

When the defaults don't fit, annotations from `zio.blocks.schema.Modifier` take over the job of Magnum's `@SqlName`, `@Table` name mapper and `@Id`:

```scala mdoc:silent
@Modifier.config("sql.table_name", "library_books")
case class LibraryBook(
  @Modifier.id bookNumber: Long,
  @Modifier.rename("book_title") title: String,
  firstAuthor: String
)

object LibraryBook {
  given Schema[LibraryBook] = Schema.derived
}
```

The code above sets the table name with `@Modifier.config("sql.table_name", ...)`, picks the ID field with `@Modifier.id` and renames one column with `@Modifier.rename`. The other field still follows the snake_case rule:

```scala mdoc
println(Table.derived[LibraryBook].createTable(SqlDialect.SQLite).sql(SqlDialect.SQLite))
println(Table.derived[Book](TableNamingPolicy.Plural).name)
```

The second line shows `TableNamingPolicy.Plural`, which derives plural table names such as `books`. `Table.derived` and `Repo.derived` always use snake_case column names; if your columns use another convention, such as Magnum's default `SqlNameMapper.SameCase`, either rename the fields with `@Modifier.rename` or build the codec with a custom mapper, as [SqlNameMapper](../reference/sql/sql-name-mapper.md) shows.

### Repositories

Magnum's `Repo[EC, E, ID]` takes a separate creator type `EC` for inserts, so the database can generate columns such as the ID. ZIO Blocks has no creator type: `Repo.derived[E, ID]` inserts whole entities, so a Magnum `Repo[Book, Book, Long]` maps directly, and a repository with a creator type needs a hand-written insert, shown in [Generated Keys](#generated-keys) below:

```scala
// Before: Magnum 2.0.0-M4 (not compiled here)
val bookRepo = Repo[Book, Book, Long]
```

The ZIO Blocks repository is the `books` value from the shared bookshelf. Most methods keep their meaning but change their name or return type:

| Magnum `Repo` / `ImmutableRepo`          | ZIO Blocks `Repo`                                           |
| ---------------------------------------- | ----------------------------------------------------------- |
| `findById(id): Option[E]`                | `find(id): Maybe[E]`                                        |
| `findAll: Vector[E]`                     | `all: List[E]`                                              |
| `findAll(spec): Vector[E]`               | `SqlQuery` or `sql"..."`, see [Spec Queries](#spec-queries) |
| `findAllById(ids): Vector[E]`            | `findAll(ids): List[E]`                                     |
| `existsById(id): Boolean`                | `exists(id): Boolean`                                       |
| `count: Long`                            | `count: Long`                                               |
| `insert(creator): Unit`                  | `insert(entity): Int`                                       |
| `insertAll(creators): Unit`              | `insertBatch(entities): Int`                                |
| `insertReturning(creator): E`            | `insertReturning(entity): E`                                |
| `update(entity): Unit`                   | `update(entity): Int`                                       |
| `updateAll(entities): BatchUpdateResult` | `Repo#update` in a loop inside `JdbcTransactor#transact`    |
| `deleteById(id): Unit`                   | `delete(id): Int`                                           |
| `delete(entity): Unit`                   | `delete(entity.id): Int`                                    |
| `deleteAllById(ids): BatchUpdateResult`  | `deleteAll(ids): Int`                                       |
| `truncate(): Unit`                       | `clear(): Int`                                              |

:::warning
`findAll` means different things in the two libraries. Magnum's `ImmutableRepo#findAll` returns every row; ZIO Blocks' `Repo#findAll(ids)` returns the rows with the given IDs, and `Repo#all` returns every row.
:::

ZIO Blocks has no `ImmutableRepo`. To hand out a read-only view, expose a trait of your own with only the read methods. In return, `Repo` adds `Repo#insertOrUpdate` and `Repo#insertOrUpdateBatch` for upserts, and `Repo#pageAfter` for keyset pagination, described in [How to Upsert Rows and Paginate with Keyset Cursors](./upsert-and-paginate-with-keyset-cursors.md).

### Connections and Transactions

Magnum builds a `Transactor` from a `DataSource` and runs blocks with `connect` and `transact`:

```scala
// Before: Magnum 2.0.0-M4 (not compiled here)
val xa = Transactor(dataSource)

transact(xa) {
  bookRepo.insert(Book(1L, "Zionomicon", "John A. De Goes", 2024, None))
}

val total: Long = connect(xa) {
  bookRepo.count
}
```

ZIO Blocks has the same two blocks as methods on the transactor. `JdbcTransactor.fromDataSource(dataSource, dialect)` replaces `Transactor(dataSource)`, and `JdbcTransactor.fromUrl` skips the `DataSource` entirely. We put three books on the shelf:

```scala mdoc:silent
val zionomicon         = Book(1L, "Zionomicon", "John A. De Goes", 2024, None)
val programmingInScala = Book(2L, "Programming in Scala", "Martin Odersky", 2008, Some("9780981531601"))
val fpInScala          = Book(3L, "Functional Programming in Scala", "Paul Chiusano", 2014, Some("9781617290657"))
```

Then we insert them in one transaction and count the rows on a plain connection:

```scala mdoc
transactor.transact {
  val inserted = books.insertBatch(List(zionomicon, programmingInScala, fpInScala))
  println(s"Rows inserted: $inserted")
}

transactor.connect {
  println(s"Books on the shelf: ${books.count}")
}
```

Methods that take `(using DbCon)` or `(using DbTx)` port unchanged, because `DbTx` extends `DbCon` in both libraries. Two Magnum settings move. A `SqlLogger` becomes the third constructor argument of `JdbcTransactor`, as [SqlLogger](../reference/sql/sql-logger.md) shows. A `connectionConfig` that sets the isolation level becomes an argument of the transaction itself, `transactor.transact(TransactionIsolation.RepeatableRead, readOnly = false) { ... }`, described in [SQL Transactions](./sql-transactions.md).

### The sql Interpolator

The `sql"..."` interpolator works the same way in both libraries: every `$value` becomes a bound parameter, and a `Frag` interpolated into another `Frag` is spliced in. The difference is in how the result runs. Magnum turns a `Frag` into a `Query` or `Update` and calls `run()`; ZIO Blocks runs the statement as soon as you call `Frag#query` or `Frag#update`:

```scala
// Before: Magnum 2.0.0-M4 (not compiled here)
connect(xa) {
  val classics: Vector[Book] =
    sql"SELECT * FROM book WHERE year < 2010".query[Book].run()
  val changed: Int =
    sql"UPDATE book SET author = 'Martin Odersky et al.' WHERE id = 2".update.run()
}
```

The ZIO Blocks version drops `run()` and returns a `List` instead of a `Vector`:

```scala mdoc
val publishedBefore = 2010

transactor.connect {
  val classics: List[Book] =
    sql"SELECT id, title, author, year, isbn FROM book WHERE year < $publishedBefore".query[Book]
  classics.foreach(println)
}
```

Magnum's `TableInfo` interpolates column lists and table names, as in `sql"SELECT ${u.all} FROM $u"`. In ZIO Blocks, `Table#columns` and `Table#name` are plain strings, so splice them with `SqlLiteral`; a plain `String` would become a bound parameter:

```scala mdoc
val bookColumns = SqlLiteral(bookTable.columns.mkString(", "))
val bookName    = SqlLiteral(bookTable.name)

transactor.connect {
  val newest = sql"SELECT $bookColumns FROM $bookName ORDER BY year DESC".queryOne[Book]
  println(newest.fold("The shelf is empty")(book => s"Newest: ${book.title}"))
}
```

`SqlLiteral` splices raw text, exactly as it does in Magnum, so only use it for SQL your program builds itself, never for user input.

### Spec Queries

Magnum's `Spec[E]` adds `WHERE`, `ORDER BY` and `LIMIT` clauses to a repository's `SELECT`:

```scala
// Before: Magnum 2.0.0-M4 (not compiled here)
val spec = Spec[Book]
  .where(sql"year < 2010")
  .orderBy("year")
  .limit(10)

val classics: Vector[Book] = connect(xa)(bookRepo.findAll(spec))
```

The closest ZIO Blocks construct is `SqlQuery`, a query builder over a `Table`. Like `Spec`, it takes its conditions as `Frag`s and its column names as strings. `SqlQuery#toFrag` renders it for a dialect, and the result runs like any other `Frag`:

```scala mdoc:silent
val classicsQuery =
  SqlQuery
    .from(bookTable)
    .where(sql"year < $publishedBefore")
    .orderBy("year")
    .limit(10)
```

The next block prints the SQL it renders for SQLite and runs it:

```scala mdoc
println(classicsQuery.sql(SqlDialect.SQLite))

transactor.connect {
  classicsQuery.toFrag(SqlDialect.SQLite).query[Book].foreach(println)
}
```

The [SqlQuery](../reference/sql/sql-query.md) reference lists every builder method. For Magnum's `Spec#seek`, which pages by key, use `Repo#pageAfter(cursorId, limit)`. For joins between tables, see [How to Write Joins and Inspect the Generated SQL](./write-joins-and-inspect-generated-sql.md). To build `WHERE` clauses from typed Scala expressions instead of SQL strings, see [Query DSL with Reified Optics — Part 2: SQL Generation](./query-dsl-sql.md).

### Custom Column Types

Magnum maps a wrapper type onto an existing codec with `DbCodec#biMap`. ZIO Blocks has the same idea under the name `DbCodec#transform`, with the two functions in separate parameter lists:

```scala
// Before: Magnum 2.0.0-M4 (not compiled here)
case class Isbn(value: String)
given DbCodec[Isbn] = DbCodec[String].biMap(Isbn(_), _.value)
```

The ZIO Blocks codec is used to read the column and, through the same `given`, to bind an `Isbn` as a parameter:

```scala mdoc
case class Isbn(value: String)

given DbCodec[Isbn] = DbCodec[String].transform(Isbn(_))(_.value)

transactor.connect {
  val isbns = sql"SELECT isbn FROM book WHERE isbn IS NOT NULL ORDER BY id".query[Isbn]
  println(isbns)

  val wanted = Isbn("9781617290657")
  val title  = sql"SELECT title FROM book WHERE isbn = $wanted".queryOne[String]
  println(title.getOrElse("No book with that ISBN"))
}
```

Scala 3 opaque types usually need no codec at all, because ZIO Blocks derives one from the underlying type; see [How to Map Custom Column Types](./map-custom-column-types.md) for opaque types, enums and JSON columns, and [DbCodec](../reference/sql/db-codec.md) for the rules.

## Coming from doobie

doobie and ZIO Blocks both let you write plain SQL with a `sql"..."` interpolator and read rows into case classes. The big change is the effect model, so we start there; after that, most of the work is renaming.

### From ConnectionIO to Direct Style

In doobie, a database operation is a value of type `ConnectionIO[A]`: a description of JDBC work that does nothing until you run it. You compose these descriptions with `flatMap` or a for-comprehension, then `.transact(xa)` turns the whole program into an `IO[A]` that opens a connection, runs the program in a transaction and commits or rolls back:

```scala
// Before: doobie 1.0.0-RC13 (not compiled here)
def addBook(book: Book): ConnectionIO[Int] =
  sql"INSERT INTO book (id, title, author, year, isbn) VALUES (${book.id}, ${book.title}, ${book.author}, ${book.year}, ${book.isbn})".update.run

def countBooks: ConnectionIO[Long] =
  sql"SELECT COUNT(*) FROM book".query[Long].unique

val program: ConnectionIO[Long] =
  for {
    _     <- addBook(Book(4L, "Hands-on Scala Programming", "Li Haoyi", 2020, None))
    total <- countBooks
  } yield total

val total: IO[Long] = program.transact(xa)
```

ZIO Blocks is direct style: a database method is an ordinary method that needs a `DbCon` and returns its result. It runs as soon as it is called, on the calling thread, and blocks until the database answers. The `ConnectionIO[A]` return type becomes `A` plus a `(using DbCon)` parameter:

```scala mdoc:silent
val handsOnScala = Book(4L, "Hands-on Scala Programming", "Li Haoyi", 2020, None)

def addBook(book: Book)(using DbCon): Int =
  sql"INSERT INTO book (id, title, author, year, isbn) VALUES (${book.id}, ${book.title}, ${book.author}, ${book.year}, ${book.isbn})".update

def countBooks(using DbCon): Long =
  sql"SELECT COUNT(*) FROM book".queryOne[Long].getOrElse(0L)
```

Calling them inside one `JdbcTransactor#transact` block replaces the for-comprehension and `.transact(xa)`:

```scala mdoc
val total: Long =
  transactor.transact {
    addBook(handsOnScala)
    countBooks
  }

println(s"Books on the shelf: $total")
```

The compiler still tracks database access for you: `addBook` can only be called where a `DbCon` is in scope, which means inside `JdbcTransactor#connect` or `JdbcTransactor#transact`. A method that needs a transaction, such as one that uses savepoints, asks for `(using DbTx)` instead, and then only compiles inside `JdbcTransactor#transact`.

Two more consequences follow. Errors are exceptions: a failed statement throws the JDBC driver's exception, usually a `java.sql.SQLException`, instead of failing an `IO`, and `JdbcTransactor#transact` rolls back and rethrows it, so use `try`/`catch`, `scala.util.Try`, or the effect wrappers in [Effect Integration](#effect-integration) to handle it. You also choose between `JdbcTransactor#connect` and `JdbcTransactor#transact`: doobie's `.transact(xa)` always runs in a transaction, so `JdbcTransactor#transact` is its direct equivalent, while `JdbcTransactor#connect` runs in auto-commit mode, where each statement is saved as soon as it runs, which suits single reads.

### Transactors

doobie builds a transactor for an effect type, either from the `DriverManager` or from a Hikari pool in a `Resource`:

```scala
// Before: doobie 1.0.0-RC13 (not compiled here)
val xa = Transactor.fromDriverManager[IO](
  driver = "org.postgresql.Driver",
  url = "jdbc:postgresql:bookshelf",
  user = "postgres",
  password = "secret",
  logHandler = None
)

val pooled: Resource[IO, HikariTransactor[IO]] =
  HikariTransactor.fromHikariConfig[IO](hikariConfig)
```

A `JdbcTransactor` has no effect type and no driver class name, and takes a `SqlDialect` instead. Each doobie constructor maps as follows:

| doobie                                                                      | ZIO Blocks                                                             |
| --------------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| `Transactor.fromDriverManager[IO](driver, url, logHandler)`                 | `JdbcTransactor.fromUrl(url, dialect)`                                 |
| `Transactor.fromDriverManager[IO](driver, url, user, password, logHandler)` | `JdbcTransactor.fromUrl(url, user, password, dialect)`                 |
| `Transactor.fromDataSource[IO](dataSource, connectEC)`                      | `JdbcTransactor.fromDataSource(dataSource, dialect)`                   |
| `HikariTransactor.fromHikariConfig[IO](config)`                             | `JdbcTransactor.fromDataSource(new HikariDataSource(config), dialect)` |
| `logHandler = Some(handler)`                                                | `new JdbcTransactor(connectionFactory, dialect, sqlLogger)`            |

With Hikari, you now own the pool: create the `HikariDataSource` at startup and close it at shutdown, which the `Resource` used to do for you. [How to Connect to PostgreSQL with a Connection Pool](./connect-to-postgresql-with-a-connection-pool.md) shows the whole setup.

### Read, Write and Meta

doobie derives `Read` and `Write` instances for case classes, and uses `Meta`, `Get` and `Put` for single columns. ZIO Blocks replaces all five with `DbCodec`, which both reads and writes, and derives it from the entity's `Schema`. The `given Schema[Book]` in the shared bookshelf is all that `Frag#query[Book]` needs, and the same schema serves the other ZIO Blocks modules, such as the JSON codecs.

A custom single-column mapping moves from `Meta#timap` to `DbCodec#transform`:

```scala
// Before: doobie 1.0.0-RC13 (not compiled here)
case class Isbn(value: String)
given Meta[Isbn] = Meta[String].timap(Isbn(_))(_.value)
```

The ZIO Blocks version is the `given DbCodec[Isbn]` shown in [Custom Column Types](#custom-column-types), and it also lets you bind an `Isbn` as a query parameter. Unlike doobie's `Read`, which reads columns by position, a derived `DbCodec` matches result columns by name, so the order of the columns in a `SELECT` doesn't matter as long as their names match the snake_case field names.

### Fragments

doobie has three interpolators: `fr"..."` adds a trailing space, while `fr0"..."` and `sql"..."` don't. ZIO Blocks has only `sql"..."`, and `Frag#++` joins two fragments exactly as written, without adding a space, so write the spaces yourself. doobie's `Fragments.whereAndOpt` becomes a few lines of ordinary Scala:

```scala
// Before: doobie 1.0.0-RC13 (not compiled here)
def search(author: Option[String], before: Option[Int]): Fragment =
  fr"SELECT id, title, author, year, isbn FROM book" ++
    Fragments.whereAndOpt(author.map(a => fr"author = $a"), before.map(y => fr"year < $y")) ++
    fr"ORDER BY year"
```

The ZIO Blocks version collects the conditions and joins them with `AND`:

```scala mdoc
def search(author: Option[String], before: Option[Int]): Frag = {
  val conditions = author.map(a => sql"author = $a").toList ++ before.map(y => sql"year < $y").toList
  val where =
    if (conditions.isEmpty) Frag.empty
    else sql" WHERE " ++ conditions.reduce((left, right) => left ++ sql" AND " ++ right)
  sql"SELECT id, title, author, year, isbn FROM book" ++ where ++ sql" ORDER BY year"
}

val byOdersky = search(Some("Martin Odersky"), None)
println(byOdersky.sql(SqlDialect.SQLite))

transactor.connect {
  byOdersky.query[Book].foreach(println)
}
```

`Fragment.const` becomes `SqlLiteral`, shown in [The sql Interpolator](#the-sql-interpolator). There is no counterpart to `Fragments.in`: for ID lists, use `Repo#findAll(ids)` and `Repo#deleteAll(ids)`, which render the `IN (...)` list for you. For a whole search form's `WHERE` clause, with an `IN (...)` list of strings, a `LIKE` pattern and paging, see [How to Build a Query from Optional Search Filters](./build-a-query-from-optional-search-filters.md).

### Query Results

A doobie `Query0` picks its result shape with a method such as `.to[List]` or `.option`. In ZIO Blocks, the method on `Frag` picks the shape and runs the query:

| doobie                                     | ZIO Blocks                                               |
| ------------------------------------------ | -------------------------------------------------------- |
| `.query[A].to[List]`                       | `Frag#query[A]: List[A]`                                 |
| `.query[A].to[Vector]`                     | `Frag#query[A].toVector`                                 |
| `.query[A].option`                         | `Frag#queryOne[A]: Maybe[A]`                             |
| `.query[A].unique`                         | `Frag#queryOne[A]` plus `getOrElse(throw ...)`           |
| `.query[A].stream`                         | `Frag#queryStream[A]`, `Frag#queryChunked[A](chunkSize)` |
| `.update.run`                              | `Frag#update: Int`                                       |
| `.update.withUniqueGeneratedKeys[K]("id")` | `Frag#updateReturningKeys[K]: List[K]`                   |
| `Update[A](sql).updateMany(rows)`          | `Repo#insertBatch(rows)`                                 |

`Frag#queryOne` returns a `Maybe`, the ZIO Blocks counterpart of `Option`. Unlike doobie's `.option`, it doesn't fail when the query returns several rows; it returns the first one:

```scala mdoc
transactor.connect {
  val first   = sql"SELECT id, title, author, year, isbn FROM book ORDER BY id".queryOne[Book]
  val missing = sql"SELECT id, title, author, year, isbn FROM book WHERE id = 42".queryOne[Book]
  println(first.fold("No books")(book => s"First: ${book.title}"))
  println(missing.fold("No book with id 42")(book => s"Found: ${book.title}"))
}
```

#### Generated Keys

doobie's `.withUniqueGeneratedKeys` and Magnum's `.returningKeys` read the keys the database generates during an insert. In ZIO Blocks, `Frag#updateReturningKeys[K]` runs the insert and returns every generated key. The bookshelf table has no generated keys, so we create a reading log whose `INTEGER PRIMARY KEY` SQLite fills in:

```scala mdoc
transactor.connect {
  sql"CREATE TABLE IF NOT EXISTS reading_log (id INTEGER PRIMARY KEY, book_id INTEGER NOT NULL)".update

  val keys = sql"INSERT INTO reading_log (book_id) VALUES (${zionomicon.id})".updateReturningKeys[Long]
  println(s"Generated keys: $keys")
}
```

This is also the pattern for a Magnum repository with a creator type: write the `INSERT` without the generated columns and read the keys back.

#### Streaming

doobie streams rows as an `fs2.Stream[ConnectionIO, A]` and runs it with `.transact(xa)`. ZIO Blocks returns a ZIO Blocks `Stream[Throwable, Chunk[A]]` from `Frag#queryStream` or `Frag#queryChunked`; each element is a chunk of rows. The statement opens on the first pull, so consume the stream inside the `JdbcTransactor#connect` or `JdbcTransactor#transact` block that created it. Terminal operations such as `runFold` return an `Either` instead of throwing:

```scala mdoc
transactor.connect {
  val rowCount =
    sql"SELECT id, title, author, year, isbn FROM book ORDER BY id"
      .queryChunked[Book](2)
      .runFold(0)((count, chunk) => count + chunk.size)
  println(s"Streamed rows: $rowCount")
}
```

The code above reads the books in chunks of two and counts them; `Right(4)` means the stream finished without an error and saw four rows. [How to Stream Large Query Results](./stream-large-query-results.md) covers aggregating, exporting and handling errors in such streams.

### Checking Queries

doobie's `.check` and its `IOChecker` test helpers prepare each query against a live database and compare the SQL, the parameter types, the column types and their nullability with your Scala types. That catches type mismatches, but only in a test run that can reach a database.

ZIO Blocks checks at compile time instead, without a database. Pass one or more `Table` values to the interpolator, written as `StringContext(...).sql(table)(args)`, and the compiler checks every table and column name in the SQL against them, with a did-you-mean suggestion for typos. A misspelled column fails the build:

```scala mdoc:fail
StringContext("SELECT title, auther FROM book WHERE year < ", "").sql(bookTable)(publishedBefore)
```

The check is lint-grade: it catches misspelled tables and columns, but it doesn't check SQL types or nullability, so doobie-style analysis tests still have a place for complex queries. [sql(table) Checked Interpolation](./sql-checked-interpolation.md) lists exactly what is checked and how to opt out for one fragment.

## Effect Integration

ZIO Blocks runs database work as blocking calls, so in an effect system you run them on a thread pool meant for blocking work.

ZIO users add the `zio-blocks-sql-zio` module, which provides `TransactorZIO`. Its `TransactorZIO#connect` and `TransactorZIO#transact` take the same blocks as `JdbcTransactor` and return a `Task`, running the block with `ZIO.attemptBlocking`. `TransactorZIO#connectZIO` and `TransactorZIO#transactZIO` take a block that returns a `ZIO`, and close the connection even when the fiber is interrupted. Magnum's `magnumzio` users will recognize the class name:

```scala mdoc:reset:silent
import zio.*
import zio.blocks.schema.Schema
import zio.blocks.sql.zio.TransactorZIO
import zio.blocks.sql.*

case class Book(id: Long, title: String, author: String, year: Int, isbn: Option[String])

object Book {
  given Schema[Book] = Schema.derived
}

val books = Repo.derived[Book, Long]
val xa    = TransactorZIO.fromUrl("jdbc:sqlite:bookshelf.db", SqlDialect.SQLite)

val countBooks: Task[Long] = xa.connect(books.count)
val addBook: Task[Int]     = xa.transact(books.insert(Book(5L, "Zionomicon", "John A. De Goes", 2024, None)))
```

Keep `import zio.blocks.sql.*` after every other `zio` import, as above: once `sql-zio` is on the classpath, it brings the subpackage `zio.blocks.sql.zio` into scope under the name `zio`, and a later `import zio.blocks...` fails to compile. `TransactorZIO.layer(url, dialect)` provides the transactor as a `ZLayer`. [How to Use the sql Module from a ZIO Application](./sql-from-zio.md) covers layers and mixing database writes with other effects, and [TransactorZIO](../reference/sql/transactor-zio.md) lists every method.

cats-effect users don't get an integration module: ZIO Blocks has no counterpart to doobie's `Transactor[IO]` or Magnum's `TransactorCats`. Wrap each `JdbcTransactor#connect` or `JdbcTransactor#transact` call in `IO.blocking` yourself:

```scala
// cats-effect wrapper (not compiled here: cats-effect is not on this site's build)
import cats.effect.IO

def countBooks: IO[Long] =
  IO.blocking(transactor.connect(books.count))

def addBook(book: Book): IO[Int] =
  IO.blocking(transactor.transact(books.insert(book)))
```

`IO.blocking` runs the call on cats-effect's blocking pool and turns a thrown exception into a failed `IO`. The JDBC call itself is not interruptible, so cancelling the fiber waits for the statement to finish.

## What You Gain and What's Different

Moving to ZIO Blocks gives you:

- **One schema for everything.** The `Schema` that maps a row also drives JSON and the other ZIO Blocks formats, so an entity is described once.
- **Generated DDL and repositories.** `Table#createTable` writes the `CREATE TABLE` statement, and `Repo.derived` adds upserts and keyset pagination to the usual CRUD operations.
- **Compile-time query checking.** Misspelled tables and columns fail the build, without a database.
- **No effect system required.** The core module depends on no effect library; ZIO users add `zio-blocks-sql-zio`, and everyone else calls it directly.
- **Chunked streaming.** `Frag#queryChunked` streams rows in chunks of a size you choose.

Things that work differently, or don't exist yet:

- **Scala 3 only.** doobie users on Scala 2.13 have to move to Scala 3 first; Magnum is already Scala 3 only.
- **Two dialects.** `SqlDialect` supports PostgreSQL and SQLite. Magnum also supports MySQL, H2, Oracle, ClickHouse and SQL Server, and doobie works with any JDBC driver.
- **Collections and optional results.** Queries return `List` instead of Magnum's `Vector`, and single results are `Maybe` instead of `Option`.
- **No creator entities and no generated primary keys in DDL.** Inserts write every column; use `Frag#updateReturningKeys` and your own DDL for database-generated IDs.
- **No type analysis of SQL.** The compile-time check covers names only, not types or nullability.

## Migration Checklist

Work through these steps in order; each one leaves the project compiling:

1. Upgrade to Scala 3 if you are coming from doobie on Scala 2.
2. Replace the Magnum or doobie dependencies with `zio-blocks-sql` (or `zio-blocks-next-sql` with `-experimental` on Scala 3.9 and newer), and add `zio-blocks-sql-zio` if you use ZIO.
3. Give every entity a `given Schema[E] = Schema.derived`, and remove `@Table`, `@Id`, `derives DbCodec`, `Read` and `Write`.
4. Replace `@SqlName` with `@Modifier.rename`, `@Id` on a field that isn't named `id` with `@Modifier.id`, and custom table names with `@Modifier.config("sql.table_name", ...)`.
5. Port custom column types from `biMap` or `Meta#timap` to `DbCodec#transform`.
6. Build one `JdbcTransactor` with `JdbcTransactor.fromUrl` or `JdbcTransactor.fromDataSource`, passing the right `SqlDialect`.
7. Replace Magnum repositories with `Repo.derived[E, ID]` and rename their methods using the table in [Repositories](#repositories), watching out for `Repo#findAll`.
8. Turn every doobie `ConnectionIO[A]` method into a method returning `A` with `(using DbCon)`, and every `.transact(xa)` into `transactor.transact { ... }`.
9. Drop `run()` after Magnum queries; replace doobie's `.to[List]`, `.option`, `.unique` and `.update.run` with `Frag#query`, `Frag#queryOne` and `Frag#update`.
10. Rewrite `Spec` queries with `SqlQuery`, and `Fragments` helpers with `Frag#++` and explicit spaces.
11. Move fs2 or iterator streaming to `Frag#queryChunked`, consumed inside the `JdbcTransactor#connect` or `JdbcTransactor#transact` block.
12. Wrap transactor calls in `TransactorZIO` for ZIO, or in `IO.blocking` for cats-effect.
13. Pass `Table` values to `sql"..."` for the queries you want checked at compile time.

## Putting It Together

The complete example below is the bookshelf after the migration: entities, a repository, a hand-written query, a dynamic search, a stream and a transaction that rolls back, all on SQLite:

```scala mdoc:embed:sql-examples/src/main/scala/migratefrommagnumordoobie/CompleteExample.scala
```

## Running the Examples

The complete example has a runnable Scala file in the `sql-examples` project. To get it, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/sql-examples
```

The example creates its own temporary database file, so you can run it as often as you like.

<details>
  <summary>sql-examples/src/main/scala/migratefrommagnumordoobie/CompleteExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/migratefrommagnumordoobie/CompleteExample.scala:show-line-numbers
```

</details>

Run it from the `sql-examples` directory:

```bash
sbt "runMain migratefrommagnumordoobie.CompleteExample"
```

To compile the example without running it:

```bash
sbt compile
```

## Going Further

- [Build Your First Database Repository on SQLite](./your-first-sqlite-repository.md) teaches the basics this guide builds on.
- [SQL Transactions](./sql-transactions.md) covers isolation levels, read-only transactions and nested transactions with savepoints.
- [sql(table) Checked Interpolation](./sql-checked-interpolation.md) explains the compile-time query check in detail.
- [Repo](../reference/sql/repo.md) and [Frag](../reference/sql/frag.md) list every repository and fragment operation.
- [SQL Module](../reference/sql/index.md) links to every `sql` reference page.
