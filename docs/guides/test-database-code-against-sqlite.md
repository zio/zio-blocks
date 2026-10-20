---
id: test-database-code-against-sqlite
title: "How to Test Database Code Against SQLite"
description: "Give every test its own SQLite database, create the schema from code, seed fixtures and roll back writes in ZIO Test specs."
keywords:
  - "Testing"
  - "SQLite"
  - "Test Isolation"
  - "Fixtures"
  - "JdbcTransactor"
  - "ZIO Test"
---

In this guide, we test a small bookshop repository against a real SQLite database with ZIO Test. By the end, you will have a reusable `withTestDb` helper that gives every test its own seeded database and deletes it afterwards, a `rollingBack` helper for tests that share one database, and a spec whose tests can't see each other's rows.

We start with the trap that makes the obvious in-memory database fail, then build the helpers one at a time:

- A fresh SQLite file for every test
- A schema created from code, with the primary key the derived table leaves out
- Fixture books that every test starts from
- A transaction that always rolls back, for tests that share a database
- A complete ZIO Test spec that uses all of them

This guide builds on [Build Your First Database Repository on SQLite](./your-first-sqlite-repository.md). We use `JdbcTransactor`, `Table`, `Repo` and `sql"..."` the way that tutorial introduces them and don't explain them again.

## The Problem

Database tests are only trustworthy when each test starts from a known state. A test suite that shares one database lets every test see the rows the previous tests left behind, so a test can pass or fail depending on the order the suite runs in.

A common first attempt keeps one database file for the whole suite and cleans it by hand at the start of each test:

```scala mdoc:compile-only
import java.sql.DriverManager

// One database file shared by every test in the suite
val connection = DriverManager.getConnection("jdbc:sqlite:bookshop-test.db")
val statement  = connection.createStatement()

// Each test first deletes what the previous tests wrote, then inserts its own rows
statement.executeUpdate("DELETE FROM book")
statement.executeUpdate("INSERT INTO book VALUES (1, 'Zionomicon', 'John A. De Goes', 2024, NULL, 5)")
statement.executeUpdate("INSERT INTO book VALUES (2, 'Programming in Scala', 'Martin Odersky', 2008, '9780981531601', 3)")
```

This approach breaks down as the suite grows. Every new table needs another `DELETE`, a test that crashes before its cleanup leaks rows into the next one, and two tests running at the same time overwrite each other's data. The schema itself is created somewhere else, so nothing checks that it still matches the case classes the code reads.

In this guide, we solve this by giving every test a database of its own, built from code, and throwing it away when the test ends.

## Prerequisites

Add the `sql` module, the SQLite JDBC driver and ZIO Test to your `build.sbt`:

```scala
// Scala 3.7 and newer; on Scala 3.3 LTS the artifact is "zio-blocks-sql"
libraryDependencies += "dev.zio"   %% "zio-blocks-next-sql" % "@VERSION@"
libraryDependencies += "org.xerial" % "sqlite-jdbc"         % "3.53.4.0"

libraryDependencies += "dev.zio" %% "zio-test"     % "2.1.27" % Test
libraryDependencies += "dev.zio" %% "zio-test-sbt" % "2.1.27" % Test
```

The `sql` module is published for Scala 3 only, and `JdbcTransactor` runs on the JVM. The code under test lives in `src/main`; the helpers and the spec we write in this guide live in `src/test`, so they never ship with the application.

## The Code Under Test

We test a bookshop. Its `Book` is the one from the SQLite tutorial with one more field, `stock`, which counts the copies on the shelf. As in the tutorial, `author` holds each book's first author:

```scala mdoc:silent
import zio.blocks.maybe.Maybe
import zio.blocks.schema.Schema
import zio.blocks.sql.*

case class Book(id: Long, title: String, author: String, year: Int, isbn: Option[String], stock: Int)

object Book {
  given Schema[Book] = Schema.derived
}

object Bookshop {

  val books: Repo[Book, Long] = Repo.derived[Book, Long]

  def findByTitle(title: String)(using DbCon): Maybe[Book] =
    sql"SELECT id, title, author, year, isbn, stock FROM book WHERE title = $title".queryOne[Book]

  def restock(id: Long, copies: Int)(using DbCon): Int =
    sql"UPDATE book SET stock = stock + $copies WHERE id = $id".update

  def addAll(newBooks: Seq[Book])(using DbCon): Int =
    newBooks.map(books.insert).sum
}
```

Every operation takes a `(using DbCon)` instead of a transactor. The caller decides where the connection comes from, which is what lets a test run the same code against a database it controls.

## Give Each Test Its Own Database

`jdbc:sqlite::memory:` looks like the natural choice for tests: nothing touches the disk and nothing needs deleting. It doesn't work with `JdbcTransactor`, though. Every call to `JdbcTransactor#connect` or `JdbcTransactor#transact` opens a new connection and closes it at the end of the block, and SQLite gives each new connection to `:memory:` its own empty database. We create the `book` table in one block:

```scala mdoc:silent
import scala.util.Try

val inMemory = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

inMemory.connect(Table.derived[Book].createTable(SqlDialect.SQLite).update)
```

Now we count the books in the next block:

```scala mdoc
println(Try(inMemory.connect(Bookshop.books.count)))
```

The table is gone by the time the second block runs. Instead, we give each test a temporary SQLite file. `Files.createTempFile` picks a name no other test uses, and a `finally` block deletes the file when the test ends, whether it passes or throws:

```scala mdoc:silent
import java.nio.file.{Files, Paths}

def withTempDb[A](f: JdbcTransactor => A): A = {
  val file = Files.createTempFile("bookshop-test", ".db")
  try f(JdbcTransactor.fromUrl(s"jdbc:sqlite:$file", SqlDialect.SQLite))
  finally Files.deleteIfExists(file)
}
```

A file behaves exactly like the database the application uses: tables outlive a single block, transactions commit and roll back for real, and SQLite's locking is the same. We run two "tests" and ask SQLite which file each one uses:

```scala mdoc:silent
val usedFiles = List("first test", "second test").map { _ =>
  withTempDb { transactor =>
    transactor.connect(sql"SELECT file FROM pragma_database_list WHERE name = 'main'".queryOne[String].getOrElse(""))
  }
}
```

Then we compare the two paths and look for the files on disk:

```scala mdoc
println(s"Each test used its own file: ${usedFiles.distinct.size == usedFiles.size}")
println(s"Files left behind: ${usedFiles.count(file => Files.exists(Paths.get(file)))}")
```

`pragma_database_list` is SQLite's list of open database files, so each test reports the path of its own file. The two paths differ, and after `withTempDb` returns, neither file exists any more. Because the names are unique, tests can also run concurrently without seeing each other's rows.

## Create the Schema from Code

A test database starts empty, so each test needs the tables before it can run. The SQLite tutorial creates them with `Table#createTable`, which derives a `CREATE TABLE` statement from the schema. We derive the table for `Book`:

```scala mdoc:silent
val bookTable = Table.derived[Book]
```

Then we print its `CREATE TABLE` statement for SQLite:

```scala mdoc
println(bookTable.createTable(SqlDialect.SQLite).sql(SqlDialect.SQLite))
```

The statement has a column for every field, but no `PRIMARY KEY`. That matters in tests: a database with no primary key accepts two books with the same ID, so a test that checks that duplicates are rejected can never pass. `Repo#insertOrUpdate` doesn't work on such a table either, because its `ON CONFLICT` clause needs a primary key to detect the conflict. We try both on a table created from the derived statement:

```scala mdoc
withTempDb { transactor =>
  transactor.connect {
    bookTable.createTable(SqlDialect.SQLite).update
    Bookshop.books.insert(Book(1L, "Zionomicon", "John A. De Goes", 2024, None, 5))
    Bookshop.books.insert(Book(1L, "Zionomicon", "John A. De Goes", 2024, None, 5))
    println(s"Rows with id 1 in the derived table: ${Bookshop.books.count}")
    println(Try(Bookshop.books.insertOrUpdate(Book(1L, "Zionomicon", "John A. De Goes", 2024, None, 7))))
  }
}
```

Both inserts succeed, and the upsert fails. So we write the `CREATE TABLE` statement by hand with `sql"..."`, keeping the derived column types and adding `PRIMARY KEY` to `id`:

```scala mdoc:silent
def createSchema()(using DbCon): Unit =
  sql"""CREATE TABLE book (
    id INTEGER PRIMARY KEY,
    title TEXT NOT NULL,
    author TEXT NOT NULL,
    year INTEGER NOT NULL,
    isbn TEXT,
    stock INTEGER NOT NULL
  )""".update
```

A hand-written schema can drift from the case class, so we check it against the derived table too. `pragma_table_info('book')` lists the columns of the table SQLite holds, and `Table#columns` lists the columns the repository reads and writes. With the hand-written schema, the duplicate insert now fails and the columns still match:

```scala mdoc
withTempDb { transactor =>
  transactor.connect {
    createSchema()
    Bookshop.books.insert(Book(1L, "Zionomicon", "John A. De Goes", 2024, None, 5))
    println(Try(Bookshop.books.insert(Book(1L, "Zionomicon", "John A. De Goes", 2024, None, 5))))

    val columns = sql"SELECT name FROM pragma_table_info('book')".query[String]
    println(s"Columns match the derived table: ${columns == bookTable.columns.toList}")
  }
}
```

The `SQLiteException` that SQLite raises is the same one the application would see in production, so a test can rely on it. The spec at the end of this guide keeps the column check as a test of its own, so adding a field to `Book` without updating `createSchema` breaks the build.

## Seed Fixtures for Every Test

Most tests need some data to work on. We define the fixture books once and share them across the whole suite. Since they are immutable values, a test can compare the database against them directly:

```scala mdoc:silent
val zionomicon         = Book(1L, "Zionomicon", "John A. De Goes", 2024, None, 5)
val programmingInScala = Book(2L, "Programming in Scala", "Martin Odersky", 2008, Some("9780981531601"), 3)
val fpInScala          = Book(3L, "Functional Programming in Scala", "Paul Chiusano", 2014, Some("9781617290657"), 0)

val fixtures: List[Book] = List(zionomicon, programmingInScala, fpInScala)
```

`resetFixtures` empties the table with `Repo#clear` and inserts the fixtures in one JDBC batch with `Repo#insertBatch`. `withTestDb` builds on `withTempDb`: it creates the schema and the fixtures in one transaction, then hands the transactor to the test:

```scala mdoc:silent
def resetFixtures()(using DbCon): Unit = {
  Bookshop.books.clear()
  Bookshop.books.insertBatch(fixtures)
}

def withTestDb[A](f: JdbcTransactor => A): A =
  withTempDb { transactor =>
    transactor.transact {
      createSchema()
      resetFixtures()
    }
    f(transactor)
  }
```

Each test that calls `withTestDb` starts from exactly these three books, whatever the tests before it did. `resetFixtures` is useful on its own when several steps share one database and each must start from the fixtures again:

```scala mdoc
withTestDb { transactor =>
  transactor.connect {
    Bookshop.books.all.foreach(println)

    Bookshop.books.delete(zionomicon.id)
    println(s"After a test deletes a book: ${Bookshop.books.count} books")

    resetFixtures()
    println(s"After resetFixtures: ${Bookshop.books.count} books")
  }
}
```

## Roll Back Instead of Cleaning Up

Sometimes a fresh database per test is too slow, for example when the fixtures are large and many tests only read them. Then the tests share one seeded database, and each test must leave it exactly as it found it.

The usual answer is to run each test inside a transaction and roll it back at the end. `JdbcTransactor` has no rollback-only flag: `JdbcTransactor#transact` commits when its block returns and rolls back only when the block throws. So we throw on purpose. `rollingBack` runs the test body inside `JdbcTransactor#transact` and wraps its result in a private exception; `JdbcTransactor#transact` rolls back and rethrows it, and `rollingBack` catches it and returns the result:

```scala mdoc:silent
private final class Rollback(val result: Any) extends RuntimeException(null, null, false, false)

def rollingBack[A](transactor: JdbcTransactor)(f: DbTx ?=> A): A =
  try transactor.transact(throw new Rollback(f))
  catch { case rollback: Rollback => rollback.result.asInstanceOf[A] }
```

The `Rollback` class is private, so no code under test can catch it by accident, and its `RuntimeException` constructor skips the stack trace, which a signal like this never needs. When the test body itself throws, `JdbcTransactor#transact` rolls back too and the original exception reaches the test. We add a book and restock another inside `rollingBack`, then look at the database afterwards:

```scala mdoc
withTestDb { transactor =>
  val (booksInside, stockInside) = rollingBack(transactor) {
    Bookshop.books.insert(Book(4L, "Hands-on Scala Programming", "Li Haoyi", 2020, None, 2))
    Bookshop.restock(fpInScala.id, 10)
    (Bookshop.books.count, Bookshop.books.find(fpInScala.id).map(_.stock).getOrElse(-1))
  }
  println(s"Inside the test: $booksInside books, $stockInside copies of FP in Scala")

  transactor.connect {
    val stock = Bookshop.books.find(fpInScala.id).map(_.stock).getOrElse(-1)
    println(s"After the test: ${Bookshop.books.count} books, $stock copies of FP in Scala")
  }
}
```

Inside the block, the test sees its own writes. Afterwards, the shelf is back to three books and _Functional Programming in Scala_ has no copies again.

:::warning
`rollingBack` only works when the code under test uses the connection it is given, as every `(using DbCon)` method in `Bookshop` does. Code that calls `JdbcTransactor#transact` itself opens a second connection, which can't see the uncommitted rows and waits for the test's write lock until SQLite gives up with `SQLITE_BUSY` after five seconds. When such code needs its own all-or-nothing block, give it a `(using DbTx)` and use the nested `DbTx#transact`, which runs on the same connection with a savepoint; [SQL Transactions](./sql-transactions.md) explains savepoints.
:::

A fresh database per test is still the simpler default. It needs no discipline from the code under test, and a test that fails halfway can't affect any other.

## Putting It Together

The complete spec runs four tests, each on its own database from `withTestDb`. `dbTest` wraps the helper in `ZIO.attemptBlocking`, because JDBC calls block the thread they run on; an exception from the database becomes a test failure instead of a defect:

```scala mdoc:embed:sql-examples/src/test/scala/testdatabasecode/BookshopSpec.scala
```

Each of the four tests checks one behaviour:

- `finds a book by its title` reads a fixture book and checks that an unknown title finds nothing.
- `restock adds copies to one book only` compares the whole shelf, so it also catches an `UPDATE` without a `WHERE` clause.
- `a failing insert rolls back the books added before it` inserts _Hands-on Scala Programming_ and then a duplicate _Zionomicon_; the primary key rejects the duplicate, `JdbcTransactor#transact` rolls back both inserts, and the shelf still holds exactly the fixtures.
- `the hand-written table has the columns Book needs` guards `createSchema` against drift.

Assertions compare whole values with `==` — the full `Maybe[Book]`, the full shelf as a `List[Book]` — so a wrong field anywhere fails the test.

## What SQLite Can't Test

SQLite is a good stand-in for code that sticks to portable SQL, but not for features only PostgreSQL has. These stay untested on SQLite:

- Array and `jsonb` columns read through `PgCodec`, which exists only for PostgreSQL.
- Shadow tables: `Dialect.Postgres#createShadowTableDDL` copies a table with `CREATE TABLE ... LIKE`, which SQLite doesn't support.
- Queue claims with `FOR UPDATE SKIP LOCKED`, which SQLite doesn't have.
- Isolation levels: SQLite runs every transaction as `SERIALIZABLE`, so code that depends on PostgreSQL's `READ COMMITTED` behaviour can't be checked here.

SQLite's own dialect says so when we ask it for a shadow table:

```scala mdoc
println(Try(Dialect.SQLite.createShadowTableDDL("book_shadow", "book")))
```

For these features, test against a real PostgreSQL server. Testcontainers is the usual way to start a throwaway PostgreSQL in Docker from a test suite. A separate guide covers connecting to PostgreSQL through a connection pool.

## Running the Examples

All the code in this guide has runnable companions in the `sql-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/sql-examples
```

The code under test is in `src/main` and everything else is in `src/test`, so the examples run with `Test/runMain`. Each one creates and deletes its own temporary database files, so you can run them in any order. To compile all the examples without running them:

```bash
sbt "Test/compile"
```

### The Code Under Test

`Book` and the `Bookshop` operations the tests exercise are in this file:

<details>
  <summary>sql-examples/src/main/scala/testdatabasecode/Bookshop.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/testdatabasecode/Bookshop.scala:show-line-numbers
```

</details>

### The Test Helpers

`withTempDb`, `createSchema`, the fixtures, `resetFixtures`, `withTestDb` and `rollingBack` are in one object:

<details>
  <summary>sql-examples/src/test/scala/testdatabasecode/TestDb.scala</summary>

```scala mdoc:embed:sql-examples/src/test/scala/testdatabasecode/TestDb.scala:show-line-numbers
```

</details>

### Give Each Test Its Own Database

This example shows the `:memory:` table disappearing between blocks and two tests using two temporary files that are gone afterwards:

<details>
  <summary>sql-examples/src/test/scala/testdatabasecode/FreshDatabaseExample.scala</summary>

```scala mdoc:embed:sql-examples/src/test/scala/testdatabasecode/FreshDatabaseExample.scala:show-line-numbers
```

</details>

Run it from the `sql-examples` directory:

```bash
sbt "Test/runMain testdatabasecode.FreshDatabaseExample"
```

### Create the Schema from Code

This example prints the derived `CREATE TABLE` statement, shows the derived table accepting a duplicate ID and rejecting an upsert, and shows the hand-written schema rejecting the duplicate:

<details>
  <summary>sql-examples/src/test/scala/testdatabasecode/SchemaExample.scala</summary>

```scala mdoc:embed:sql-examples/src/test/scala/testdatabasecode/SchemaExample.scala:show-line-numbers
```

</details>

Run it from the `sql-examples` directory:

```bash
sbt "Test/runMain testdatabasecode.SchemaExample"
```

### Seed Fixtures for Every Test

This example lists the fixture books, deletes one and restores them with `resetFixtures`:

<details>
  <summary>sql-examples/src/test/scala/testdatabasecode/FixturesExample.scala</summary>

```scala mdoc:embed:sql-examples/src/test/scala/testdatabasecode/FixturesExample.scala:show-line-numbers
```

</details>

Run it from the `sql-examples` directory:

```bash
sbt "Test/runMain testdatabasecode.FixturesExample"
```

### Roll Back Instead of Cleaning Up

This example writes inside `rollingBack` and shows that the writes are gone afterwards:

<details>
  <summary>sql-examples/src/test/scala/testdatabasecode/RollbackExample.scala</summary>

```scala mdoc:embed:sql-examples/src/test/scala/testdatabasecode/RollbackExample.scala:show-line-numbers
```

</details>

Run it from the `sql-examples` directory:

```bash
sbt "Test/runMain testdatabasecode.RollbackExample"
```

### The Complete Spec

The spec from "Putting It Together" runs with sbt's test runner:

```bash
sbt "testOnly testdatabasecode.BookshopSpec"
```

## Going Further

- [SQL Transactions](./sql-transactions.md) covers isolation levels, read-only transactions and nested transactions with savepoints.
- [Transactor](../reference/sql/transactor.md) documents `JdbcTransactor#connect`, `JdbcTransactor#transact` and the SQLite connection settings.
- [Repo](../reference/sql/repo.md) lists every repository operation, including `Repo#insertOrUpdate` and its primary-key requirement.
- [Ddl](../reference/sql/ddl.md) and [Table](../reference/sql/table.md) describe the generated `CREATE TABLE` statements.
- [sql(table) Checked Interpolation](./sql-checked-interpolation.md) lets the compiler check table and column names in queries like `Bookshop.findByTitle`.
