---
id: your-first-sqlite-repository
title: "Build Your First Database Repository on SQLite"
description: "Connect to SQLite, derive a table and a repository from one schema, write your own queries and roll back a transaction."
keywords:
  - "Getting Started"
  - "SQLite Repository"
  - "CRUD"
  - "Repo"
  - "Table"
  - "JdbcTransactor"
---

Welcome! This tutorial is for Scala 3 developers who are new to the ZIO Blocks `sql` module. We assume you have finished [Your First Schema](./your-first-schema.md), so you know how to derive a `Schema` for a case class. You also need to know what a database table and a row are, but you don't need any prior experience with JDBC, SQL libraries or transactions to follow along.

## 1. Introduction

By the end of this tutorial, you will understand:

- How a [JdbcTransactor](../reference/sql/transactor.md) opens connections to a SQLite database and gives your code a `DbCon` to work with
- How one `Schema` gives you a [Table](../reference/sql/table.md) with its `CREATE TABLE` statement and a [Repo](../reference/sql/repo.md) with ready-made CRUD operations
- How to write your own SQL with the `sql"..."` interpolator and read the results as case classes
- Why `Frag#queryOne` and `Repo#find` return a `Maybe`, and how to handle the found and the empty case
- How `JdbcTransactor#transact` commits a group of writes together, or rolls all of them back when something fails

We learn these concepts by building a small bookshelf, one step at a time:

1. Connect to SQLite
2. Map a case class to a table
3. Insert rows with a repository
4. Find, update and delete rows
5. Write your own query
6. Group writes in a transaction

We recommend reading from top to bottom — each section builds on the previous one.

To follow along, add the `sql` module and the SQLite JDBC driver to your `build.sbt`:

```scala
// Scala 3.3 LTS
libraryDependencies += "dev.zio" %% "zio-blocks-sql" % "@VERSION@"

// Scala 3.7 and newer
libraryDependencies += "dev.zio" %% "zio-blocks-next-sql" % "@VERSION@"

// The SQLite JDBC driver, for every Scala version
libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.53.4.0"
```

The `sql` module is published for Scala 3 only, and the `JdbcTransactor` we use to talk to the database runs on the JVM. Scala 3.7 and newer use the `zio-blocks-next-sql` artifact name; the code in this tutorial is the same for both.

## 2. Background: One Schema, One Repository

A repository is an object that stores and loads one kind of value — books, users, orders — so the rest of the program never writes SQL for the everyday cases. In many projects, a repository is written by hand: a `CREATE TABLE` statement, an `INSERT` with one placeholder per field, a `SELECT` that reads each column back into the right field, and the same again for `UPDATE` and `DELETE`. Every one of those statements has to change when the case class gains a field.

The ZIO Blocks `sql` module derives all of that from the `Schema` you already know from the previous tutorial. The schema says which fields a `Book` has and what their types are; from it, the module builds the table definition, the code that turns a row into a `Book` and back, and a repository with the standard operations. For anything the repository doesn't cover, you write the SQL yourself and still get the rows back as `Book` values. Finally, a transactor decides when the work talks to the database and whether a group of writes is kept or undone.

## 3. Connect to SQLite

A `JdbcTransactor` is the entry point to a database. It knows how to open a JDBC connection and which `SqlDialect` the database speaks. We create one with `JdbcTransactor.fromUrl`, giving it a JDBC URL and the dialect.

Our bookshelf lives in a SQLite file. SQLite stores a whole database in one file, so there is no server to install:

```scala mdoc:silent
import zio.blocks.maybe.Maybe
import zio.blocks.schema.Schema
import zio.blocks.sql.*

val dbFile = java.nio.file.Files.createTempFile("bookshelf", ".db")

val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)
```

The code above:

- `import zio.blocks.sql.*` brings in `JdbcTransactor`, `SqlDialect` and every other type we use in this tutorial except `Schema` and `Maybe`, which we import here for later sections. We keep `import zio.blocks.sql.*` after the other `zio.blocks` imports: if your project also uses the `sql-zio` module, it brings a package named `zio` into scope, and a `zio.blocks` import after it fails to compile.
- `Files.createTempFile("bookshelf", ".db")` creates a new, empty file in the system's temporary directory, so each run of this tutorial starts with an empty database.
- `JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)` creates a transactor for that file. It doesn't connect yet; it only remembers how to.

:::caution
Every call to `JdbcTransactor#connect` or `JdbcTransactor#transact` opens a new connection. With the URL `jdbc:sqlite::memory:`, each new connection gets its own empty in-memory database, so a table created in one block is gone in the next. That's why we use a file.
:::

To do some work, we call `JdbcTransactor#connect` and pass it a block of code. The transactor opens a connection, runs the block and closes the connection again. Let's ask SQLite for its version:

```scala mdoc
transactor.connect {
  val version = sql"SELECT sqlite_version()".queryOne[String]
  println(s"Connected to SQLite ${version.getOrElse("unknown")}")
}
```

The code above:

- `transactor.connect { ... }` runs the block with an open connection. Inside the block, a `DbCon` value — the connection plus its dialect — is available as a context parameter, so we never pass it around by hand.
- `sql"SELECT sqlite_version()"` is a piece of SQL; we look at the `sql"..."` interpolator properly in section 7.
- `Frag#queryOne[String]` runs the SQL with the `DbCon` from `JdbcTransactor#connect` and reads the first row as a `String`.
- `version.getOrElse("unknown")` takes the value out of the result, which may be empty; we meet this result type again in section 6.

The printed version comes from the SQLite engine bundled with the JDBC driver. We have a working connection.

## 4. Map a Case Class to a Table

Now let's describe a book. We use the same pattern as in [Your First Schema](./your-first-schema.md): a case class with a derived `Schema` in its companion object. Since the `sql` module is Scala 3 only, we write the schema as a `given`, which is the Scala 3 spelling of the `implicit val` from that tutorial:

```scala mdoc:silent
case class Book(id: Long, title: String, author: String, year: Int, isbn: Option[String])

object Book {
  given Schema[Book] = Schema.derived
}
```

The code above:

- `id: Long` is the number we use to find a book again later.
- `author` holds each book's first author, which keeps our sample table small.
- `isbn: Option[String]` is optional, because we don't know every book's ISBN.
- `given Schema[Book] = Schema.derived` derives the schema at compile time, exactly as before.

A `Table[A]` describes how values of type `A` are stored: the table's name and one column per field. `Table.derived` builds it from the schema:

```scala mdoc:silent
val bookTable = Table.derived[Book]
```

Let's look at the table name and at the `CREATE TABLE` statement the table produces for SQLite:

```scala mdoc
println(bookTable.name)
println(bookTable.createTable(SqlDialect.SQLite).sql(SqlDialect.SQLite))
```

The code above:

- `bookTable.name` is the table name, derived from the type name `Book` in snake case.
- `bookTable.createTable(SqlDialect.SQLite)` builds the `CREATE TABLE` statement as a `Frag`, a piece of SQL that we can run.
- `Frag#sql(SqlDialect.SQLite)` renders that `Frag` as text, so we can read it before we run it.

Notice how each field became a column. `Long` and `Int` map to SQLite's `INTEGER` and `String` maps to `TEXT`. The required fields are `NOT NULL`, and `isbn`, the `Option` field, is the only column that may hold `NULL`. The `IF NOT EXISTS` clause makes the statement safe to run again: when the table is already there, SQLite leaves it alone.

Reading the statement doesn't create the table. Let's run it, then ask SQLite which tables the database now has:

```scala mdoc
transactor.connect {
  bookTable.createTable(SqlDialect.SQLite).update
  val tables = sql"SELECT name FROM sqlite_master WHERE type = 'table'".query[String]
  println(tables)
}
```

The code above runs the `CREATE TABLE` statement with `Frag#update`, which executes a `Frag` that changes the database, such as a `CREATE TABLE`, `INSERT` or `DELETE`, and returns the number of affected rows. The query then reads `sqlite_master`, SQLite's own catalog of everything in the database, and `Frag#query[String]` returns all of its matching rows as a `List[String]`.

The list holds our `book` table. The database is ready for data.

## 5. Insert Rows with a Repository

A `Repo[E, ID]` is a repository for entities of type `E` whose ID has type `ID`. `Repo.derived` builds one from the schema, so it already knows the table, the columns and the SQL for each operation:

```scala mdoc:silent
val books = Repo.derived[Book, Long]
```

The code above asks for a repository of `Book` values with `Long` IDs. `Repo.derived` finds the ID column on its own: our `Book` has exactly one `Long` field, and it is named `id`.

Let's put some books on the shelf. We create four books, using `None` for the ISBNs we don't know:

```scala mdoc:silent
val zionomicon = Book(1L, "Zionomicon", "John A. De Goes", 2024, None)
val programmingInScala = Book(2L, "Programming in Scala", "Martin Odersky", 2008, Some("9780981531601"))
val fpInScala = Book(3L, "Functional Programming in Scala", "Paul Chiusano", 2014, Some("9781617290657"))
val handsOnScala = Book(4L, "Hands-on Scala Programming", "Li Haoyi", 2020, None)
```

Now we insert them, count the rows and list everything on the shelf:

```scala mdoc
transactor.connect {
  val inserted = books.insert(zionomicon)
  println(s"Rows inserted: $inserted")

  books.insert(programmingInScala)
  books.insert(fpInScala)
  books.insert(handsOnScala)

  println(s"Books on the shelf: ${books.count}")
  books.all.foreach(println)
}
```

The code above:

- `books.insert(zionomicon)` writes one book and returns the number of rows it inserted.
- `books.count` returns the number of rows in the `book` table as a `Long`.
- `books.all` reads every row back as a `List[Book]`, and `foreach(println)` prints one book per line.

Notice that we never wrote an `INSERT` or a `SELECT`. The repository built them from the schema, and the books came back from the database as ordinary `Book` values, with `None` where the `isbn` column is `NULL`.

## 6. Find, Update and Delete Rows

To read one book, we call `Repo#find` with its ID. A book with that ID may not exist, so `Repo#find` returns a `Maybe[Book]`: either a book or nothing. `Maybe` is the ZIO Blocks version of `Option`, and its `Maybe#fold` method handles both cases — the first argument is used when the value is absent, and the function is used when it is present:

```scala mdoc
transactor.connect {
  println(books.find(2L).fold("No book with id 2")(book => s"Found: $book"))
  println(books.find(42L).fold("No book with id 42")(book => s"Found: $book"))
}
```

The code above finds _Programming in Scala_ with `books.find(2L)`, so `Maybe#fold` calls the function and prints the book. With `books.find(42L)` it finds nothing, so `Maybe#fold` returns its first argument, the "No book" message.

Next, let's change a book. `Repo#update` takes a whole `Book` and overwrites the row with the same ID. We use it to record both authors of _Functional Programming in Scala_:

```scala mdoc
transactor.connect {
  val updated = books.update(fpInScala.copy(author = "Paul Chiusano and Rúnar Bjarnason"))
  println(s"Rows updated: $updated")
  println(books.find(3L).fold("No book with id 3")(book => s"Authors: ${book.author}"))
}
```

The code above:

- `fpInScala.copy(author = ...)` makes a new `Book` with the same ID and a new `author`; the case class itself never changes.
- `books.update(...)` writes it to the row with ID `3` and returns the number of rows it changed.
- `books.find(3L)` reads the row again, so we see the new value as the database stores it.

Finally, `Repo#delete` removes the row with a given ID. Let's take _Hands-on Scala Programming_ off the shelf:

```scala mdoc
transactor.connect {
  val deleted = books.delete(4L)
  println(s"Rows deleted: $deleted")
  println(s"Books on the shelf: ${books.count}")
}
```

The code above:

- `books.delete(4L)` removes the row with ID `4` and returns the number of deleted rows.
- `books.count` returns the number of rows that remain.
- The two `println` calls show the deleted-row count and the new total.

That's the full set of CRUD operations — create, read, update and delete — without a single line of SQL.

## 7. Write Your Own Query

A repository covers the everyday operations, but sooner or later you need a query of your own, such as "every book published before 2010". For that, we write SQL with the `sql"..."` interpolator. Each `$value` inside it becomes a query parameter: the value is sent to the database separately from the SQL text, so it can never change the meaning of the query.

Let's find the books published before a given year:

```scala mdoc
val publishedBefore = 2010

transactor.connect {
  val classics = sql"SELECT id, title, author, year, isbn FROM book WHERE year < $publishedBefore".query[Book]
  classics.foreach(println)
}
```

The code above builds a `Frag` with `sql"... WHERE year < $publishedBefore"`, whose SQL text is `... WHERE year < ?` and whose parameter is the `Int` value `2010`. Then `Frag#query[Book]` runs it and reads every row into a `Book`, using the same schema as the repository, so the result is a `List[Book]`.

When a query returns at most one row, we use `Frag#queryOne` instead. It returns a `Maybe`, just like `Repo#find`. Let's wrap a title lookup in a method of its own:

```scala mdoc:silent
def findByTitle(title: String)(using DbCon): Maybe[Book] =
  sql"SELECT id, title, author, year, isbn FROM book WHERE title = $title".queryOne[Book]
```

The code above:

- `(using DbCon)` says that the method needs a connection context from its caller. We can only call `findByTitle` inside `JdbcTransactor#connect` or `JdbcTransactor#transact`, and the compiler checks that for us.
- `$title` becomes a `String` parameter, just as `$publishedBefore` became an `Int` parameter.
- `import zio.blocks.maybe.Maybe` lets us write the result type in the method signature; we didn't need it before because we never wrote the type down.

Now let's look up one title that is on the shelf and one that isn't:

```scala mdoc
transactor.connect {
  println(findByTitle("Zionomicon").fold("Not on the shelf")(book => s"Found: $book"))
  println(findByTitle("Scala Puzzlers").fold("Not on the shelf")(book => s"Found: $book"))
}
```

The code above:

- `findByTitle("Zionomicon")` finds the book, so `Maybe#fold` calls the function and prints it.
- `findByTitle("Scala Puzzlers")` finds nothing, so `Maybe#fold` returns its first argument, "Not on the shelf".
- Both calls run inside one `JdbcTransactor#connect` block, which supplies the `DbCon` that `findByTitle` requires.

Notice that a query of our own and `Repo#find` give us the same kind of result, so the code that handles them looks the same.

## 8. Group Writes in a Transaction

So far, every statement inside `JdbcTransactor#connect` was saved as soon as it ran. Sometimes several writes belong together: either all of them should happen, or none of them. That's what a transaction is for, and `JdbcTransactor#transact` runs a block inside one. When the block finishes normally, `JdbcTransactor#transact` commits the transaction and every write is saved. When the block throws an exception, `JdbcTransactor#transact` rolls the transaction back, undoing every write in the block, and then rethrows the exception to the caller.

Let's make our shelf hold at most three books, and try to put _Hands-on Scala Programming_ back on it:

```scala mdoc
import scala.util.{Failure, Success, Try}

Try {
  transactor.transact {
    books.insert(handsOnScala)
    println(s"Inside the transaction: ${books.count} books")
    if (books.count > 3) throw new IllegalStateException("The shelf holds at most 3 books")
  }
} match {
  case Failure(error) => println(s"Transaction failed: ${error.getMessage}")
  case Success(_)     => println("Transaction committed")
}

transactor.connect {
  println(s"After the rollback: ${books.count} books")
}
```

The code above:

- `transactor.transact { ... }` opens a connection, starts a transaction and runs the block. The repository methods work here too, because the transaction context is also a `DbCon`.
- `books.insert(handsOnScala)` succeeds, and inside the transaction the count is four.
- `throw new IllegalStateException(...)` stops the block. `JdbcTransactor#transact` rolls the transaction back, so the insert is undone, and then rethrows the exception.
- `Try { ... }` catches the rethrown exception as a `Failure`, and the `match` prints its message.
- The `JdbcTransactor#connect` block afterwards counts the rows in a new connection and finds three books again.

Notice the order of the output: the transaction saw four books, but the shelf ended with three. The insert really happened inside the transaction, and the rollback undid it.

Now let's write a block that succeeds. We swap one book for another — take _Programming in Scala_ off the shelf and put _Hands-on Scala Programming_ on it — so the shelf never holds more than three books:

```scala mdoc
transactor.transact {
  books.delete(2L)
  books.insert(handsOnScala)
  println("Swapped Programming in Scala for Hands-on Scala Programming")
}

transactor.connect {
  books.all.foreach(println)
}
```

The code above:

- `books.delete(2L)` removes _Programming in Scala_ and `books.insert(handsOnScala)` puts _Hands-on Scala Programming_ on the shelf.
- The block finishes without an exception, so `JdbcTransactor#transact` commits it and both writes are saved together.
- The `JdbcTransactor#connect` block afterwards lists the shelf, and the swap shows in the output.

If the insert had failed, the delete would have been rolled back too, and _Programming in Scala_ would still be on the shelf.

:::note
The one-argument `JdbcTransactor#transact` keeps the JDBC driver's default isolation level instead of choosing one for you. SQLite supports only the `SERIALIZABLE` level, so on SQLite every transaction behaves as serializable. To ask for a specific level on other databases, see [SQL Transactions](./sql-transactions.md).
:::

## 9. Putting It Together

This example combines all the concepts we've learned — connecting to a SQLite file, creating a table from a schema, CRUD with a derived repository, a hand-written query and a transaction that rolls back:

```scala mdoc:embed:sql-examples/src/main/scala/yourfirstsqliterepository/CompleteExample.scala
```

## 10. Running the Examples

All examples in this tutorial have corresponding runnable Scala files in the `sql-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/sql-examples
```

Each example creates its own temporary database file, so you can run them in any order and as often as you like. We recommend running them in order to follow the tutorial step by step.

### Connect to SQLite

This example creates a transactor for a temporary SQLite file and prints the SQLite version.

<details>
  <summary>sql-examples/src/main/scala/yourfirstsqliterepository/Concept1ConnectExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/yourfirstsqliterepository/Concept1ConnectExample.scala:show-line-numbers
```

</details>

Run it from the `sql-examples` directory:

```bash
sbt "runMain yourfirstsqliterepository.Concept1ConnectExample"
```

### Map a Case Class to a Table

This example derives a `Table` for `Book`, prints its `CREATE TABLE` statement and creates the table.

<details>
  <summary>sql-examples/src/main/scala/yourfirstsqliterepository/Concept2TableExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/yourfirstsqliterepository/Concept2TableExample.scala:show-line-numbers
```

</details>

Run it from the `sql-examples` directory:

```bash
sbt "runMain yourfirstsqliterepository.Concept2TableExample"
```

### Insert Rows with a Repository

This example derives a `Repo` for `Book`, inserts four books and lists them.

<details>
  <summary>sql-examples/src/main/scala/yourfirstsqliterepository/Concept3InsertExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/yourfirstsqliterepository/Concept3InsertExample.scala:show-line-numbers
```

</details>

Run it from the `sql-examples` directory:

```bash
sbt "runMain yourfirstsqliterepository.Concept3InsertExample"
```

### Find, Update and Delete Rows

This example finds a book that exists and one that doesn't, updates a book's authors and deletes a book.

<details>
  <summary>sql-examples/src/main/scala/yourfirstsqliterepository/Concept4FindUpdateDeleteExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/yourfirstsqliterepository/Concept4FindUpdateDeleteExample.scala:show-line-numbers
```

</details>

Run it from the `sql-examples` directory:

```bash
sbt "runMain yourfirstsqliterepository.Concept4FindUpdateDeleteExample"
```

### Write Your Own Query

This example runs a hand-written query with an `Int` parameter and a `Frag#queryOne` lookup by title, for a title that is on the shelf and one that isn't.

<details>
  <summary>sql-examples/src/main/scala/yourfirstsqliterepository/Concept5QueryExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/yourfirstsqliterepository/Concept5QueryExample.scala:show-line-numbers
```

</details>

Run it from the `sql-examples` directory:

```bash
sbt "runMain yourfirstsqliterepository.Concept5QueryExample"
```

### Group Writes in a Transaction

This example shows a transaction that rolls back after an exception and one that commits two writes together.

<details>
  <summary>sql-examples/src/main/scala/yourfirstsqliterepository/Concept6TransactionExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/yourfirstsqliterepository/Concept6TransactionExample.scala:show-line-numbers
```

</details>

Run it from the `sql-examples` directory:

```bash
sbt "runMain yourfirstsqliterepository.Concept6TransactionExample"
```

### The Complete Example

The complete example from "Putting It Together" runs everything from the tutorial in one program:

```bash
sbt "runMain yourfirstsqliterepository.CompleteExample"
```

To compile every example at once without running them:

```bash
sbt compile
```

## 11. What You've Learned

In this tutorial, you learned:

- How a `JdbcTransactor` opens a connection for each `JdbcTransactor#connect` or `JdbcTransactor#transact` block, and why a SQLite file keeps your tables between blocks
- How one `Schema` gives you a `Table`, with its `CREATE TABLE` statement, and a `Repo` with `Repo#insert`, `Repo#all`, `Repo#find`, `Repo#update`, `Repo#delete` and `Repo#count`
- How to write your own SQL with `sql"..."`, where every `$value` becomes a query parameter, and read the rows as case classes with `Frag#query` and `Frag#queryOne`
- How `Repo#find` and `Frag#queryOne` return a `Maybe`, and how `Maybe#fold` handles the found and the empty case
- How `JdbcTransactor#transact` commits a group of writes together, or rolls all of them back and rethrows when the block throws

You now have a solid foundation in the ZIO Blocks `sql` module. The next step is to see how transactions and checked queries work in a real application.

## 12. Where to Go Next

- **Ready to control transactions?** The how-to guide [SQL Transactions](./sql-transactions.md) covers isolation levels, read-only transactions and nested transactions with savepoints.
- **Want the compiler to check your SQL?** [sql(table) Checked Interpolation](./sql-checked-interpolation.md) shows how to let the compiler check table and column names in `sql"..."` queries.
- **Want every repository operation?** Read the [Repo](../reference/sql/repo.md) reference, which also covers batch inserts, `insertReturning` and deleting many rows at once.
- **Curious about tables and DDL?** Read [Table](../reference/sql/table.md) for custom table and column names, and [Ddl](../reference/sql/ddl.md) for the statements it generates.
- **Need to stream large results?** The [Frag](../reference/sql/frag.md) reference covers `queryStream` and `queryChunked`.
- **Prefer building queries from Scala expressions?** [Query DSL with Reified Optics — Part 2: SQL Generation](./query-dsl-sql.md) turns typed query expressions into SQL.
- **Moving to PostgreSQL?** [How to Connect to PostgreSQL with a Connection Pool](./connect-to-postgresql-with-a-connection-pool.md) sets up a HikariCP pool and builds the same `Repo` on PostgreSQL.
- **Using ZIO?** [TransactorZIO](../reference/sql/transactor-zio.md) runs the same work as ZIO effects.
- **Testing your database code?** [How to Test Database Code Against SQLite](./test-database-code-against-sqlite.md) gives every test its own SQLite database and rolls back writes.
- **Want the big picture?** The [SQL Module](../reference/sql/index.md) overview links to every `sql` reference page.
