---
id: write-joins-and-inspect-generated-sql
title: "How to Write Joins and Inspect the Generated SQL"
description: "Join related tables with SqlQuery and Rel, read the rows as tuples, and print or dump the SQL each join generates."
keywords:
  - "SQL Joins"
  - "Left Join"
  - "Generated SQL"
  - "SqlQuery"
  - "Rel"
  - "Dump"
---

In this guide, we query two related tables, `author` and `book`, with the `sql` module's `SqlQuery` builder. By the end, you will have inner and left joins that return each book with its first author and each author with their books, filtered and sorted, plus the exact SQL each join sends to the database, printed at run time and written to `.sql` files at compile time.

We describe the link between the tables once, as a `Rel`, and reuse it in every query:

- Define two tables that reference each other
- Join each book to its first author (many-to-one)
- List each author's books (one-to-many)
- Keep authors who have no books yet (left join)
- Filter and sort a join
- Print the generated SQL for SQLite and PostgreSQL
- Write the SQL to files at compile time for review

## The Problem

A join returns columns from two tables in one row, and your code has to split that row back into two values. Without a query builder, you write the column list, the `ON` condition and the row mapping by hand, and keep all three in sync with the case classes:

```scala mdoc:compile-only
import java.sql.DriverManager

case class Author(authorId: Int, name: String, born: Option[Int])
case class Book(bookId: Int, title: String, year: Int, firstAuthorId: Int)

val connection = DriverManager.getConnection("jdbc:sqlite:library.db")
val statement = connection.prepareStatement(
  """SELECT b.book_id, b.title, b.year, b.first_author_id, a.author_id, a.name, a.born
    |FROM book b JOIN author a ON b.first_author_id = a.author_id""".stripMargin
)
val rs = statement.executeQuery()
val rows = Iterator
  .continually(rs.next())
  .takeWhile(identity)
  .map { _ =>
    val book   = Book(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getInt(4))
    val born   = rs.getInt(7)
    val author = Author(rs.getInt(5), rs.getString(6), if (rs.wasNull()) None else Some(born))
    (book, author)
  }
  .toList
connection.close()
```

Every field added to `Book` or `Author` means editing the `SELECT` list and shifting every column index after it. A wrong index still compiles and silently puts the wrong value in the wrong field. And the SQL is a string, so the only way to review the joins a program runs is to read every string in the code base.

In this guide, we let `SqlQuery` write the column list and the `ON` condition from the tables' schemas, decode each row into a tuple of case classes, and print the SQL it generates so you can check it.

## Prerequisites

Add the `sql` module and the SQLite JDBC driver to your `build.sbt`:

```scala
// Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-sql" % "@VERSION@"

// Scala 3.9 and newer
libraryDependencies += "dev.zio" %% "zio-blocks-next-sql" % "@VERSION@"
scalacOptions += "-experimental"

// The SQLite JDBC driver, for every Scala version
libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.53.4.0"
```

The `sql` module is published for Scala 3 only. Running queries over JDBC needs the JVM and JDK 21 or newer.

We assume you have connected to a database and run a query with the `sql"..."` interpolator before, as in [Build Your First Database Repository on SQLite](./your-first-sqlite-repository.md). The same queries run on PostgreSQL; [How to Connect to PostgreSQL with a Connection Pool](./connect-to-postgresql-with-a-connection-pool.md) shows how to build the `JdbcTransactor` for it.

:::note
`SqlQuery` in `zio.blocks.sql.query` is not the query DSL from the [Query DSL with Reified Optics](./query-dsl-reified-optics.md) guides. Those guides build their own SQL generator from `SchemaExpr` optics; this guide uses the join builder that ships with the `sql` module.
:::

Everything in this guide comes from three imports:

```scala mdoc:silent
import zio.blocks.schema.Schema
import zio.blocks.sql.*
import zio.blocks.sql.query.*
```

## The Library Tables

Our library has authors and books. Each book stores the ID of its first author, so many books can point at one author:

```scala mdoc:silent
case class Author(authorId: Int, name: String, born: Option[Int])
object Author {
  given Schema[Author] = Schema.derived
}

case class Book(bookId: Int, title: String, year: Int, firstAuthorId: Int)
object Book {
  given Schema[Book] = Schema.derived
}

val authors = Table.derived[Author]
val books   = Table.derived[Book]
```

`Table.derived` maps each case class to a table named after the type, `author` and `book`, with one snake-case column per field:

```scala mdoc
println(s"${authors.name}: ${authors.columns.mkString(", ")}")
println(s"${books.name}: ${books.columns.mkString(", ")}")
```

:::warning
Give every column a name that no other table in the join uses, as `author_id` and `book_id` do here. Rows decode into tuples by column label, and when two joined tables both have an `id` column, every `id` in the tuple is read from the first one. With `Author(id, ...)` and `Book(id, ...)`, each book comes back paired with an `Author` that carries the book's ID. The SQL itself is correct; only the decoding is wrong.
:::

We create both tables in a temporary SQLite file, so each run starts empty. A file-backed database is needed because every `connect` opens a new connection, and an in-memory SQLite database (`jdbc:sqlite::memory:`) is empty again on every new connection. We write the `CREATE TABLE` statements by hand, because `Table#createTable` doesn't declare a `PRIMARY KEY`:

```scala mdoc:silent
val dbFile     = java.nio.file.Files.createTempFile("library", ".db")
val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

transactor.connect {
  sql"""CREATE TABLE author (
    author_id INTEGER PRIMARY KEY,
    name      TEXT NOT NULL,
    born      INTEGER
  )""".update
  sql"""CREATE TABLE book (
    book_id         INTEGER PRIMARY KEY,
    title           TEXT NOT NULL,
    year            INTEGER NOT NULL,
    first_author_id INTEGER NOT NULL REFERENCES author (author_id)
  )""".update
}
```

Then we add four authors and four books. Li Haoyi has no book in our library yet, which matters for the left join later:

```scala mdoc:silent
val authorRepo = Repo.derived[Author, Int]
val bookRepo   = Repo.derived[Book, Int]

transactor.connect {
  authorRepo.insert(Author(1, "Martin Odersky", Some(1958)))
  authorRepo.insert(Author(2, "John A. De Goes", None))
  authorRepo.insert(Author(3, "Paul Chiusano", None))
  authorRepo.insert(Author(4, "Li Haoyi", None))

  bookRepo.insert(Book(1, "Zionomicon", 2024, 2))
  bookRepo.insert(Book(2, "Programming in Scala", 2008, 1))
  bookRepo.insert(Book(3, "Functional Programming in Scala", 2014, 3))
  bookRepo.insert(Book(4, "Programming in Scala, Fifth Edition", 2021, 1))
}
```

## Join Each Book to Its First Author

A `Rel` describes how two tables are linked: a foreign-key column in one table equals a key column in the other. `Rel.manyToOne` takes the table holding the foreign key, a selector for that field, the referenced table and a selector for its key:

```scala mdoc:silent
val bookAuthor = Rel.manyToOne(books, _.firstAuthorId, authors, _.authorId)
```

The selectors are checked at compile time, so `_.firstAuthorID` doesn't compile, and each field name is turned into its snake-case column name, `first_author_id` and `author_id`.

`SqlQuery.from(books)` starts a query on the `book` table, and `join(bookAuthor)` adds an inner join to `author`. `toFrag` renders the query to a `Frag`, which runs like any `sql"..."` fragment. A join selects every column of both tables, books first, so we decode each row as a `(Book, Author)` tuple:

```scala mdoc:silent
val booksWithAuthors = SqlQuery.from(books).join(bookAuthor)
```

```scala mdoc
transactor.connect {
  booksWithAuthors.toFrag(SqlDialect.SQLite).query[(Book, Author)].foreach { case (book, author) =>
    println(s"${book.title} (${book.year}) by ${author.name}")
  }
}
```

The tuple follows the join order: the table passed to `SqlQuery.from` comes first, then one element per `join`. A tuple whose types don't match that order reads the wrong columns, so keep the two in step.

## List Each Author's Books

The same link, read from the other side, is one-to-many: one author, many books. `Rel.oneToMany` takes the "one" table first:

```scala mdoc:silent
val authorBooks = Rel.oneToMany(authors, _.authorId, books, _.firstAuthorId)
```

`Rel.manyToOne` and `Rel.oneToMany` build the same `Rel`: the `ON` condition is "the first table's column equals the second table's column", and each selector must name a field of the table next to it. The constructor's name only documents which way the link reads. In fact `SqlQuery.from(authors).join(bookAuthor)` produces the same rows as the query below.

A join still returns one row per matching pair, so an author with two books comes back twice. We start from `authors` and group the rows by author in Scala:

```scala mdoc:silent
val authorsAndBooks = SqlQuery.from(authors).join(authorBooks)
```

```scala mdoc
transactor.connect {
  val rows = authorsAndBooks.toFrag(SqlDialect.SQLite).query[(Author, Book)]
  rows.groupMap(_._1)(_._2).toList.sortBy(_._1.name).foreach { case (author, written) =>
    println(s"${author.name}: ${written.sortBy(_.year).map(_.title).mkString(", ")}")
  }
}
```

Li Haoyi is missing: an inner join only keeps authors that have at least one matching book.

## Keep Authors Who Have No Books

To keep every author, use a left join. `leftJoin` and `joinLeft` are the same method under two names, and so are `join` and `innerJoin`. `join(rel, JoinKind.Left)` picks the kind from a value:

```scala mdoc
println(SqlQuery.from(authors).leftJoin(authorBooks) == SqlQuery.from(authors).joinLeft(authorBooks))
println(SqlQuery.from(authors).leftJoin(authorBooks) == SqlQuery.from(authors).join(authorBooks, JoinKind.Left))
```

For an author with no books, the database returns SQL `NULL` in every `book` column. Decoding those `NULL`s as a plain `Book` doesn't fail: SQL `NULL` decodes to `null` for a `String` and to `0` for an `Int`, so Li Haoyi gets a book that doesn't exist:

```scala mdoc:silent
val everyAuthor = SqlQuery.from(authors).leftJoin(authorBooks)
```

```scala mdoc
transactor.connect {
  everyAuthor.toFrag(SqlDialect.SQLite).query[(Author, Book)].foreach(println)
}
```

`Option[Book]` isn't an option either, because `Option` is only supported for single-column types. Instead, give the optional side a row type whose fields are all `Option`s, and turn it into an `Option[Book]` in Scala:

```scala mdoc:silent
case class BookColumns(bookId: Option[Int], title: Option[String], year: Option[Int], firstAuthorId: Option[Int]) {
  def toBook: Option[Book] =
    for {
      id     <- bookId
      t      <- title
      y      <- year
      author <- firstAuthorId
    } yield Book(id, t, y, author)
}
object BookColumns {
  given Schema[BookColumns] = Schema.derived
}
```

`BookColumns` has the same fields in the same order as `Book`, so it reads the same columns. Now the missing book is `None`:

```scala mdoc
transactor.connect {
  val rows = everyAuthor.toFrag(SqlDialect.SQLite).query[(Author, BookColumns)]
  rows.groupMap(_._1)(_._2.toBook).toList.sortBy(_._1.name).foreach { case (author, written) =>
    val titles = written.flatten.map(_.title)
    println(s"${author.name}: ${if (titles.isEmpty) "no books yet" else titles.mkString(", ")}")
  }
}
```

## Filter and Sort a Join

`where` adds a condition to the query, written as a `sql"..."` fragment; `filter` is the same method. Values you interpolate become bound parameters, as in any `sql"..."` fragment. Inside the fragment, refer to columns through the table aliases the query uses: `t0` is the table passed to `SqlQuery.from`, and `t1`, `t2` and so on are the joined tables in the order you added them. Quote column names with double quotes, as the generated SQL does.

`orderBy` sorts by a column of the `t0` table, ascending unless you pass `SortOrder.Desc`. Here are the books published after 2010, newest first, with their first authors:

```scala mdoc:silent
val publishedAfter = 2010

val recentBooks = SqlQuery
  .from(books)
  .join(bookAuthor)
  .where(sql"""t0."year" > $publishedAfter""")
  .orderBy("year", SortOrder.Desc)
```

```scala mdoc
transactor.connect {
  recentBooks.toFrag(SqlDialect.SQLite).query[(Book, Author)].foreach { case (book, author) =>
    println(s"${book.year}: ${book.title} by ${author.name}")
  }
}
```

A condition on the joined table uses its alias. Each `where` call adds a condition, and the conditions are combined with `AND`:

```scala mdoc:silent
val author = "Martin Odersky"

val booksByOdersky = SqlQuery
  .from(books)
  .join(bookAuthor)
  .where(sql"""t1."name" = $author""")
  .where(sql"""t0."year" < ${2020}""")
```

```scala mdoc
transactor.connect {
  booksByOdersky.toFrag(SqlDialect.SQLite).query[(Book, Author)].foreach { case (book, _) =>
    println(book.title)
  }
}
```

:::caution
`orderBy` and `groupBy` only reach the `t0` table: they always render `t0."<column>"`, and a name like `t1.name` is rejected with an `IllegalArgumentException` ("Invalid SQL column identifier"). To sort by a column of a joined table, start the query from that table, or sort the decoded rows in Scala. The alias names `t0`, `t1` in a `where` fragment are not checked at all, so a typo there only fails when the database runs the query.
:::

## See the SQL Before It Runs

`SqlQuery#explain(dialect)` returns the SQL on one line, with numbered `?1`, `?2` placeholders, followed by a `-- params:` comment that lists each parameter's position and type, never its value. We render the filtered query for both dialects:

```scala mdoc
println(recentBooks.explain(SqlDialect.SQLite))
println(recentBooks.explain(SqlDialect.PostgreSQL))
```

The two are identical: both dialects use `?` placeholders and the same quoting, so a `SqlQuery` renders the same SQL for SQLite and PostgreSQL today. What differs between the dialects is the column types in `CREATE TABLE` statements, not the queries.

`explain` is meant for logs and code review. `SqlQuery#sql(dialect)` returns the exact string that `toFrag` sends to the JDBC driver, with plain `?` placeholders:

```scala mdoc
println(recentBooks.sql(SqlDialect.SQLite))
println(everyAuthor.sql(SqlDialect.SQLite))
```

The output shows the rules every join follows: the columns of each table in schema order, every identifier double-quoted, the `FROM` table aliased as `t0`, each `JOIN` kind spelled out, and each `ON` condition built from the `Rel`.

## Dump the SQL to Files at Compile Time

`explain` needs the program to run. To review every join a code base contains, for example in a pull request or by a DBA who doesn't read Scala, `Dump.dumpQuery` writes a query's SQL to a `.sql` file while the code compiles. The macro only writes when the JVM system property `zib.sql.dumpDir` is set in the JVM that runs the compiler; otherwise each call compiles to nothing.

Dumping happens at compile time, so the code in this section lives in the companion example `JoinsDumpExample.scala` rather than on this page. Two rules decide what the macro can see:

- The query must be written out where `Dump.dumpQuery` can read it: an `inline def`, or the `SqlQuery.from(...)` chain itself. A query stored in a plain `val` or built at run time can't be dumped; the macro warns and writes nothing.
- Likewise, the `Rel` must be built inside the chain, not referenced from a `val`.

```scala
object LibraryQueries {
  inline def booksWithFirstAuthors: SqlQuery[Book] =
    SqlQuery.from(Library.books).join(Rel.manyToOne(Library.books, _.firstAuthorId, Library.authors, _.authorId))
}

object JoinsDumpExample {
  // The val's name becomes the file name
  val booksWithFirstAuthors = Dump.dumpQuery(LibraryQueries.booksWithFirstAuthors)
}
```

Set the property when you start sbt. A running sbt server (`sbt --client`, or the one an IDE starts) keeps the property it was started with, so shut it down first. `clean` makes sure every file is compiled, and so every dump is written:

```bash
sbt -Dzib.sql.dumpDir=target/sql-dumps clean compile
```

`SBT_OPTS="-Dzib.sql.dumpDir=target/sql-dumps"` works too. Passing the property through `scalacOptions` or `javaOptions` does nothing, because those reach the compiler as flags or the forked program at run time, not as properties of the compiler's JVM.

Each `Dump.dumpQuery` call writes one file per dialect, named `<name>_query-<dialect>.sql`, where `<name>` is the `val` the call is assigned to. For the query above, `target/sql-dumps/booksWithFirstAuthors_query-sqlite.sql` contains:

```sql
SELECT t0."book_id", t0."title", t0."year", t0."first_author_id", t1."author_id", t1."name", t1."born" FROM "book" AS t0 INNER JOIN "author" AS t1 ON t0."first_author_id" = t1."author_id"
```

A file is only rewritten when its content changes, so the files can be committed and a pull request that changes a join shows the SQL diff next to the Scala diff. `Dump.dumpTable(table)` does the same for a table's `CREATE TABLE` statement, written to `<table>-<dialect>.sql`.

:::warning
Check the dump files before you rely on them:

- A `where(sql"...")` condition loses its parameter in the dump: `t0."year" > $publishedAfter` is written as `WHERE t0."year" >` with no `?`. Use `explain` for filtered queries.
- A `Dump.dumpQuery` call that isn't assigned to a `val` is named after the enclosing object, as `_local_<Object>__query-<dialect>.sql`, and a second such call in the same object overwrites the first one's file.
- Every `sql"..."` fragment also writes its own file, named after its enclosing method or object, while the property is set.
:::

## Putting It Together

This program builds the library, runs the many-to-one join, the one-to-many and left joins, the filtered query, and prints the generated SQL:

```scala mdoc:embed:sql-examples/src/main/scala/writejoinsandinspectgeneratedsql/CompleteExample.scala
```

## Running the Examples

All the code in this guide has runnable companions in the `sql-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/sql-examples
```

Each example creates its own temporary SQLite file, so you can run them in any order. To compile all the examples without running them:

```bash
sbt compile
```

### The Library Tables

The domain, the tables, the `Rel` and the code that creates and fills the database are shared by every example:

<details>
  <summary>sql-examples/src/main/scala/writejoinsandinspectgeneratedsql/Library.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/writejoinsandinspectgeneratedsql/Library.scala:show-line-numbers
```

</details>

### Join Each Book to Its First Author

<details>
  <summary>sql-examples/src/main/scala/writejoinsandinspectgeneratedsql/Step1ManyToOneExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/writejoinsandinspectgeneratedsql/Step1ManyToOneExample.scala:show-line-numbers
```

</details>

Run it with:

```bash
sbt "runMain writejoinsandinspectgeneratedsql.Step1ManyToOneExample"
```

### List Each Author's Books

<details>
  <summary>sql-examples/src/main/scala/writejoinsandinspectgeneratedsql/Step2OneToManyExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/writejoinsandinspectgeneratedsql/Step2OneToManyExample.scala:show-line-numbers
```

</details>

Run it with:

```bash
sbt "runMain writejoinsandinspectgeneratedsql.Step2OneToManyExample"
```

### Keep Authors Who Have No Books

<details>
  <summary>sql-examples/src/main/scala/writejoinsandinspectgeneratedsql/Step3LeftJoinExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/writejoinsandinspectgeneratedsql/Step3LeftJoinExample.scala:show-line-numbers
```

</details>

Run it with:

```bash
sbt "runMain writejoinsandinspectgeneratedsql.Step3LeftJoinExample"
```

### Filter and Sort a Join

<details>
  <summary>sql-examples/src/main/scala/writejoinsandinspectgeneratedsql/Step4FilterSortExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/writejoinsandinspectgeneratedsql/Step4FilterSortExample.scala:show-line-numbers
```

</details>

Run it with:

```bash
sbt "runMain writejoinsandinspectgeneratedsql.Step4FilterSortExample"
```

### See the SQL Before It Runs

<details>
  <summary>sql-examples/src/main/scala/writejoinsandinspectgeneratedsql/Step5ExplainExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/writejoinsandinspectgeneratedsql/Step5ExplainExample.scala:show-line-numbers
```

</details>

Run it with:

```bash
sbt "runMain writejoinsandinspectgeneratedsql.Step5ExplainExample"
```

### Dump the SQL to Files at Compile Time

<details>
  <summary>sql-examples/src/main/scala/writejoinsandinspectgeneratedsql/JoinsDumpExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/writejoinsandinspectgeneratedsql/JoinsDumpExample.scala:show-line-numbers
```

</details>

Compile with the dump directory set, then run the example to print the files it produced:

```bash
sbt -Dzib.sql.dumpDir=target/sql-dumps clean compile "runMain writejoinsandinspectgeneratedsql.JoinsDumpExample"
```

### The Complete Example

```bash
sbt "runMain writejoinsandinspectgeneratedsql.CompleteExample"
```

## Going Further

- [Frag](../reference/sql/frag.md) documents `Frag#query`, `Frag#queryOne` and the other ways to run the fragment `SqlQuery#toFrag` returns.
- [DbCodec](../reference/sql/db-codec.md) explains how rows decode into case classes and tuples, and how SQL `NULL` is handled.
- [sql(table) Checked Interpolation](./sql-checked-interpolation.md) checks table and column names in hand-written SQL at compile time, for joins that `SqlQuery` can't express.
- [How to Test Database Code Against SQLite](./test-database-code-against-sqlite.md) gives every test its own database, which suits tests for join queries.
- [SqlLogger](../reference/sql/sql-logger.md) records the SQL of every query a transactor runs, including joins, with its duration and row count.
