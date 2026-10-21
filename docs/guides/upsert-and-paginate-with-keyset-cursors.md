---
id: upsert-and-paginate-with-keyset-cursors
title: "How to Upsert Rows and Paginate with Keyset Cursors"
description: "Insert or update rows in one statement, choose which columns an upsert changes, and walk large tables page by page with keyset cursors."
keywords:
  - "Upsert"
  - "ON CONFLICT"
  - "Keyset Pagination"
  - "Seek Pagination"
  - "Repo"
  - "Frag"
---

In this guide, we keep a small bookstore inventory up to date and read a large sales table back in pages. By the end, you will have a table that accepts upserts (one statement that inserts a row, or updates it when the row is already there), a nightly supplier import that applies a mix of new and changed books in one batch, and a loop that walks 10,000 rows 50 at a time, each page starting right after the last row of the previous one.

We build it one recipe at a time:

- A table with a real primary key, which every upsert needs
- `Repo#insertOrUpdate` and `Repo#insertOrUpdateBatch` for "insert or replace"
- `Upsert.insertDoNothing` for "insert only if it's new"
- `Upsert.insertDoUpdate` for "insert, or update only these columns"
- The upsert SQL on SQLite and on PostgreSQL, side by side
- `Repo#pageAfter` for keyset pagination by ID, and why it beats `OFFSET`
- `Frag.keysetAfter` for keyset pagination over your own query
- What keyset cursors can't do

This guide builds on [Build Your First Database Repository on SQLite](./your-first-sqlite-repository.md). We use `JdbcTransactor`, `Table`, `Repo` and `sql"..."` the way that tutorial introduces them and don't explain them again.

## The Problem

Two jobs come up in almost every database-backed application. The first is "save this row": insert it if it's new, update it if it isn't. The second is "read this big table in pages": show 50 rows, then the next 50, and so on.

Written with plain JDBC, both look harmless:

```scala mdoc:compile-only
import java.sql.Connection

// "Save this book": look first, then INSERT or UPDATE
def saveBook(conn: Connection, id: Long, title: String, price: BigDecimal, stock: Int): Unit = {
  val check = conn.prepareStatement("SELECT 1 FROM book WHERE id = ?")
  check.setLong(1, id)
  val exists = check.executeQuery().next()
  check.close()

  val write =
    if (exists) conn.prepareStatement("UPDATE book SET title = ?, price = ?, stock = ? WHERE id = ?")
    else conn.prepareStatement("INSERT INTO book (title, price, stock, id) VALUES (?, ?, ?, ?)")
  write.setString(1, title)
  write.setBigDecimal(2, price.bigDecimal)
  write.setInt(3, stock)
  write.setLong(4, id)
  write.executeUpdate()
  write.close()
}

// "Read page n": skip n * size rows, then take size rows
def salesPage(conn: Connection, pageNumber: Int, size: Int): List[Long] = {
  val query = conn.prepareStatement("SELECT id FROM sale ORDER BY id LIMIT ? OFFSET ?")
  query.setInt(1, size)
  query.setInt(2, pageNumber * size)
  val rs  = query.executeQuery()
  val ids = Iterator.continually(rs).takeWhile(_.next()).map(_.getLong(1)).toList
  query.close()
  ids
}
```

Both break down under real load. `saveBook` makes two round trips, lists every column twice, and has a race: two writers can both see "no such book" and both `INSERT`, and the second one fails. `salesPage` asks the database to read and throw away `pageNumber * size` rows before it returns anything, so every page costs more than the one before it, and a row inserted or deleted while the reader is on page 3 shifts every later page by one: a row is shown twice, or never.

In this guide, we let the database do both jobs in one statement each: `INSERT ... ON CONFLICT` for the upsert, and `WHERE id > ?` with the last ID the reader saw for the page.

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

The `sql` module is published for Scala 3 only. `JdbcTransactor` runs on the JVM and needs JDK 21 or newer. The code in this guide is the same for both artifact names.

For the database setup itself, see [How to Test Database Code Against SQLite](./test-database-code-against-sqlite.md) (a fresh database per test) and [How to Connect to PostgreSQL with a Connection Pool](./connect-to-postgresql-with-a-connection-pool.md) (the PostgreSQL side). Everything below runs unchanged on PostgreSQL once the table exists there.

## The Bookstore Inventory

Our inventory is one `Book` per title. `isbn` is optional because Zionomicon has none in our records, so the inventory is keyed by `id` instead. The prices and stock counts are made up:

```scala mdoc:silent
import zio.blocks.schema.Schema
import zio.blocks.sql.*
import scala.util.Try

case class Book(id: Long, title: String, isbn: Option[String], price: BigDecimal, stock: Int)

object Book {
  given Schema[Book] = Schema.derived
}

val books = Repo.derived[Book, Long]

// Prices and stock are fictional
val zionomicon         = Book(1L, "Zionomicon", None, BigDecimal("39.99"), 5)
val programmingInScala = Book(2L, "Programming in Scala", Some("9780981531601"), BigDecimal("49.95"), 3)
val fpInScala          = Book(3L, "Functional Programming in Scala", Some("9781617290657"), BigDecimal("44.99"), 8)
```

Each recipe prints the inventory, so we add a helper that lists every book in `id` order. The database is a temporary file, because SQLite's `jdbc:sqlite::memory:` URL gives every connection its own empty database:

```scala mdoc:silent
val dbFile     = java.nio.file.Files.createTempFile("bookstore", ".db")
val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

def printBooks()(using DbCon): Unit =
  sql"SELECT id, title, isbn, price, stock FROM book ORDER BY id".query[Book].foreach { b =>
    println(f"${b.id}%2d  ${b.title}%-32s ${b.isbn.getOrElse("-")}%-14s ${b.price}%6s  stock ${b.stock}%2d")
  }
```

## Create a Table with a Real Primary Key

An upsert works by letting the `INSERT` collide with an existing row, so the table needs a `PRIMARY KEY` or `UNIQUE` constraint on the column it collides on. `Table#createTable` doesn't declare one. Here is what it generates for `Book`, and what happens when we upsert into that table:

```scala mdoc
println(books.table.createTable(SqlDialect.SQLite).sql(SqlDialect.SQLite))

transactor.connect {
  books.table.createTable(SqlDialect.SQLite).update
  println(Try(books.insertOrUpdate(zionomicon)))
}
```

SQLite refuses the statement because nothing on `id` can conflict. The fix is to write the `CREATE TABLE` statement ourselves. The easiest way is to copy what `createTable` printed and add `PRIMARY KEY` to the `id` column. We drop the table `createTable` made and create the keyed one in its place:

```scala mdoc
transactor.connect {
  sql"DROP TABLE book".update
  sql"""CREATE TABLE book (
    id    INTEGER PRIMARY KEY,
    title TEXT NOT NULL,
    isbn  TEXT,
    price TEXT NOT NULL,
    stock INTEGER NOT NULL
  )""".update
  println(Try(books.insertOrUpdate(zionomicon)))
  printBooks()
}
```

:::tip
The `price TEXT` column is what `createTable` picks for a `BigDecimal` on SQLite: it keeps the exact decimal digits. On PostgreSQL the same column is `NUMERIC`; see [Compare the Upsert SQL for SQLite and PostgreSQL](#compare-the-upsert-sql-for-sqlite-and-postgresql).
:::

## Insert or Update One Book

`Repo#insertOrUpdate` saves an entity in one statement: it inserts the row, or, when a row with the same ID exists, overwrites every other column with the entity's values. It returns the affected row count:

```scala mdoc
transactor.connect {
  println(books.insertOrUpdate(programmingInScala))
  println(books.insertOrUpdate(fpInScala))
  println(books.insertOrUpdate(zionomicon.copy(stock = 4)))
  printBooks()
}
```

The first two calls insert new books; the third finds Zionomicon already there and updates its stock from 5 to 4. All three return `1`, so the count can't tell an insert from an update. If your code needs to know which one happened, check with `books.exists(id)` first, inside the same transaction.

## Apply a Nightly Supplier Batch

A supplier sends a nightly feed: some books changed price or stock, and some are new to the store. `Repo#insertOrUpdateBatch` sends the whole feed as one JDBC batch of upserts. We run it inside `transact`, so a failure halfway through leaves the inventory as it was:

```scala mdoc:silent
val supplierFeed = List(
  programmingInScala.copy(price = BigDecimal("44.95")),             // price drop
  fpInScala.copy(stock = 12),                                       // restock
  Book(4L, "Hands-on Scala Programming", None, BigDecimal("39.00"), 6) // new title
)
```

```scala mdoc
transactor.transact {
  println("Before:")
  printBooks()
  println(s"Rows affected: ${books.insertOrUpdateBatch(supplierFeed)}")
  println("After:")
  printBooks()
}
```

The count is the sum over the batch, `1` per row whether it was inserted or updated. An empty feed returns `0` without touching the database.

## Keep Existing Rows Untouched

Sometimes the row already in the database must win. A catalog import that runs every time the application starts should add the books it doesn't know yet, but never reset the price or stock the shop has changed since. `Upsert.insertDoNothing(table, entity, conflictColumn)` builds an `INSERT ... ON CONFLICT (...) DO NOTHING` statement for that. It returns a `Frag`, which we run with `update`:

```scala mdoc:silent
val catalogDefaults = List(
  zionomicon,                                                   // stock 5 in the catalog, 4 in the shop
  Book(5L, "Programming Scala", None, BigDecimal("59.99"), 2)   // not in the shop yet
)
```

```scala mdoc
transactor.connect {
  catalogDefaults.foreach { book =>
    val inserted = Upsert.insertDoNothing(books.table, book, "id").update
    println(s"${book.title}: $inserted")
  }
  printBooks()
}
```

Here the count does tell the two cases apart: `0` means the row already existed and nothing changed, `1` means the row was inserted. Zionomicon keeps the stock of 4 that the shop set earlier.

## Update Only the Columns You Choose

A supplier is the source of truth for price and stock, but not for titles: its feed spells Zionomicon in capitals with an edition note. The four-argument `Upsert.insertDoUpdate(table, entity, conflictColumn, updateColumns)` inserts the full row when the book is new, and updates only `updateColumns` when it exists:

```scala mdoc:silent
val supplierRow   = zionomicon.copy(title = "ZIONOMICON (PAPERBACK)", price = BigDecimal("34.99"), stock = 20)
val priceAndStock = Upsert.insertDoUpdate(books.table, supplierRow, "id", Seq("price", "stock"))
```

```scala mdoc
println(priceAndStock.sql(SqlDialect.SQLite))

transactor.connect {
  println(priceAndStock.update)
  printBooks()
}
```

The title stays "Zionomicon"; price and stock come from the feed. The three-argument `Upsert.insertDoUpdate(table, entity, conflictColumn)` updates every column except the conflict column, which is what `Repo#insertOrUpdate` uses.

The column names are checked when the `Frag` is built, not when it runs. A column the table doesn't have, the conflict column itself, a duplicate, or an empty list throws `IllegalArgumentException`:

```scala mdoc
println(Try(Upsert.insertDoUpdate(books.table, supplierRow, "id", Seq("cost"))))
println(Try(Upsert.insertDoUpdate(books.table, supplierRow, "id", Seq("id", "price"))))
```

:::note
The conflict column doesn't have to be the ID. Any column with a `UNIQUE` constraint works, for example `Upsert.insertDoNothing(books.table, book, "isbn")` on a table that declares `isbn TEXT UNIQUE`. Without the constraint, the database rejects the statement just as it did for the table `createTable` made.
:::

## Compare the Upsert SQL for SQLite and PostgreSQL

SQLite (3.24 and newer) and PostgreSQL (9.5 and newer) accept the same `INSERT ... ON CONFLICT (column) DO UPDATE SET ...` syntax, and both take `?` placeholders through JDBC. So the upsert statement is the same text for both dialects:

```scala mdoc:silent
val upsert = Upsert.insertDoUpdate(books.table, zionomicon, "id")
```

```scala mdoc
println(upsert.sql(SqlDialect.SQLite))
println(upsert.sql(SqlDialect.PostgreSQL))
println(upsert.sql(SqlDialect.SQLite) == upsert.sql(SqlDialect.PostgreSQL))
println(upsert.queryParams)
```

The parameters show how the update works: the entity's values are bound once for `VALUES (...)` and once more for the `SET` columns, instead of reading them back through PostgreSQL's `EXCLUDED` row. That keeps one statement valid in both databases.

What does differ is the table you create. The column types come from the dialect:

```scala mdoc
println(books.table.createTable(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL))
```

On PostgreSQL, write the same statement with the primary key added, `id BIGINT PRIMARY KEY`, and every upsert in this guide runs there unchanged.

## Walk a Large Table with pageAfter

Now for the second job. The store logs every sale, and a nightly report reads the whole log. We generate 10,000 fictional sales and insert them in one batch:

```scala mdoc:silent
case class Sale(id: Long, bookId: Long, quantity: Int)

object Sale {
  given Schema[Sale] = Schema.derived
}

val sales = Repo.derived[Sale, Long]
```

```scala mdoc
transactor.transact {
  sql"CREATE TABLE sale (id INTEGER PRIMARY KEY, book_id INTEGER NOT NULL, quantity INTEGER NOT NULL)".update
  val generated = (1L to 10000L).map(i => Sale(i, i % 3 + 1, (i % 5 + 1).toInt))
  println(sales.insertBatch(generated))
}
```

`Repo#pageAfter(cursorId, limit)` returns the next `limit` rows whose ID is strictly greater than `cursorId`, in ascending ID order. The cursor is the ID of the last row on the previous page; for the first page, pass any value smaller than every ID, here `0L`:

```scala mdoc
transactor.connect {
  val first  = sales.pageAfter(0L, 50)
  val second = sales.pageAfter(first.last.id, 50)
  println(s"First page:  ${first.size} rows, ids ${first.head.id} to ${first.last.id}")
  println(s"Second page: ${second.size} rows, ids ${second.head.id} to ${second.last.id}")
  println(s"After the last id: ${sales.pageAfter(10000L, 50)}")
}
```

To walk the whole table, keep passing the last ID back in until a page comes back empty:

```scala mdoc:silent
def walkSales(cursor: Long, pages: Int, rows: Int, sold: Long)(using DbCon): (Int, Int, Long) = {
  val page = sales.pageAfter(cursor, 50)
  if (page.isEmpty) (pages, rows, sold)
  else walkSales(page.last.id, pages + 1, rows + page.size, sold + page.map(_.quantity).sum)
}
```

```scala mdoc
transactor.connect {
  val (pages, rows, sold) = walkSales(0L, 0, 0, 0L)
  println(s"$pages pages, $rows rows, $sold copies sold")
}
```

Each call sends `SELECT ... FROM "sale" WHERE "id" > ? ORDER BY "id" ASC LIMIT 50`. Because the row with the cursor's ID is excluded (`>`, not `>=`), consecutive pages never repeat the boundary row. A limit of zero or less throws `IllegalArgumentException`.

## Why Keyset Pagination Beats OFFSET

`LIMIT 50 OFFSET 9950` and `WHERE id > 9950 LIMIT 50` return the same 50 rows, but the database reaches them differently. SQLite's `EXPLAIN QUERY PLAN` shows how:

```scala mdoc
transactor.connect {
  val plan = (q: Frag) => q.query[(Int, Int, Int, String)].map(_._4)
  println(plan(sql"EXPLAIN QUERY PLAN SELECT * FROM sale WHERE id > 9950 ORDER BY id ASC LIMIT 50"))
  println(plan(sql"EXPLAIN QUERY PLAN SELECT * FROM sale ORDER BY id ASC LIMIT 50 OFFSET 9950"))
}
```

The keyset query is a `SEARCH`: SQLite jumps straight to the first ID after the cursor through the primary key and reads 50 rows. The `OFFSET` query is a `SCAN`: SQLite walks the table from the start and discards the first 9,950 rows before it returns any. So the cost of an `OFFSET` page grows with its page number, while a keyset page reads only the rows it returns. PostgreSQL behaves the same way: its `OFFSET` also has to produce and skip every earlier row.

Keyset pages are also stable while the table changes. A row inserted before the cursor doesn't push later rows onto the next page, and a deleted row doesn't pull one back, because each page starts from a value, not from a position.

The trade-off: a keyset reader can only go to the next page, not jump to page 137. That fits infinite scrolling, exports and batch jobs; a numbered page picker still needs `OFFSET`.

## Page Through Your Own Query with keysetAfter

`pageAfter` always reads the whole table. To page through a filtered query, such as the sales of one book, build the query yourself and append `Frag.keysetAfter(table, orderCol, lastValue, limit)`. It produces `WHERE <orderCol> > ? ORDER BY <orderCol> ASC LIMIT <limit>` with `lastValue` as the one bound parameter:

```scala mdoc
println(Frag.keysetAfter(sales.table, "id", DbValue.DbLong(0L), 5).sql(SqlDialect.SQLite))
```

Because the fragment starts with its own `WHERE`, our filter goes into a subquery, and `keysetAfter` pages through its result. The alias `AS s` is optional on SQLite but required by PostgreSQL before version 16:

```scala mdoc:silent
def salesOfBookAfter(bookId: Long, cursor: Long, limit: Int): Frag =
  sql"SELECT * FROM (SELECT id, book_id, quantity FROM sale WHERE book_id = $bookId) AS s" ++
    Frag.keysetAfter(sales.table, "id", DbValue.DbLong(cursor), limit)
```

```scala mdoc
println(salesOfBookAfter(2L, 0L, 5).sql(SqlDialect.SQLite))

transactor.connect {
  val first = salesOfBookAfter(2L, 0L, 5).query[Sale]
  val next  = salesOfBookAfter(2L, first.last.id, 5).query[Sale]
  println(first)
  println(next)
}
```

The cursor is a `DbValue`, so it works for any column type: `DbValue.DbLong` here, `DbValue.DbString` for a text key, `DbValue.DbInstant` for a timestamp. `keysetAfter` checks `orderCol` when the fragment is built: it must be a plain identifier (letters, digits and underscores) and, with the table argument, one of the table's columns. The overload without a table, `Frag.keysetAfter(orderCol, lastValue, limit)`, checks only the identifier, for queries that don't map to one `Table`:

```scala mdoc
println(Try(Frag.keysetAfter(sales.table, "sold_at", DbValue.DbLong(0L), 5)))
println(Try(Frag.keysetAfter(sales.table, "id", DbValue.DbLong(0L), 0)))
```

## What Keyset Cursors Can't Do

Keep these limits in mind when you choose a cursor column:

- **One column only.** Both `pageAfter` and `keysetAfter` take a single cursor column. Cursors over several columns, such as `(price, id)`, are not supported.
- **The column must be ordered and unique.** Rows that share a cursor value can be split across a page boundary, and the ones after the boundary are then skipped, because the next page starts strictly after that value. Page by the ID, or by another unique column.
- **Ascending, forward only.** Both helpers emit `ORDER BY ... ASC` and `>`. There is no descending order and no "previous page".
- **`keysetAfter` brings its own `WHERE`.** Put your filters in a subquery, as shown above.

The fragment below is the SQL `keysetAfter` produces: always `>`, always `ASC`, with one bound parameter, the cursor value:

```scala mdoc
println(Frag.keysetAfter("id", DbValue.DbLong(50L), 5).sql(SqlDialect.SQLite))
```

:::caution
`Frag.keysetAfter` writes the column name without quotes, unlike `Repo` and `Upsert`, which double-quote every identifier. A column whose name is an SQL keyword, such as `order`, passes validation but produces invalid SQL (`near "order": syntax error` on SQLite). `Repo#pageAfter` quotes its ID column and isn't affected; for `keysetAfter`, use a column name that isn't a keyword.
:::

## Putting It Together

The complete example creates the keyed inventory table, applies a supplier batch, imports catalog defaults with `DO NOTHING`, applies a price-and-stock feed, and then walks the 10,000-row sales log both with `pageAfter` and through one book's sales with `keysetAfter`:

```scala mdoc:embed:sql-examples/src/main/scala/upsertandpaginatewithkeysetcursors/CompleteExample.scala
```

## Running the Examples

All examples in this guide have corresponding runnable Scala files in the `sql-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/sql-examples
```

Each example creates its own temporary database file, so you can run them in any order and as often as you like.

### Create a Table with a Real Primary Key

This example shows the upsert failing on the table `createTable` makes, then succeeding on a table with a primary key.

<details>
  <summary>sql-examples/src/main/scala/upsertandpaginatewithkeysetcursors/Step1KeyedTableExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/upsertandpaginatewithkeysetcursors/Step1KeyedTableExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain upsertandpaginatewithkeysetcursors.Step1KeyedTableExample"
```

### Insert or Update Books

This example saves single books with `insertOrUpdate` and then applies a nightly supplier batch with `insertOrUpdateBatch`.

<details>
  <summary>sql-examples/src/main/scala/upsertandpaginatewithkeysetcursors/Step2InsertOrUpdateExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/upsertandpaginatewithkeysetcursors/Step2InsertOrUpdateExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain upsertandpaginatewithkeysetcursors.Step2InsertOrUpdateExample"
```

### Keep Existing Rows Untouched

This example imports catalog defaults with `Upsert.insertDoNothing` and prints which rows were inserted.

<details>
  <summary>sql-examples/src/main/scala/upsertandpaginatewithkeysetcursors/Step3DoNothingExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/upsertandpaginatewithkeysetcursors/Step3DoNothingExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain upsertandpaginatewithkeysetcursors.Step3DoNothingExample"
```

### Update Only the Columns You Choose

This example applies a supplier row that updates price and stock but keeps the title.

<details>
  <summary>sql-examples/src/main/scala/upsertandpaginatewithkeysetcursors/Step4ChosenColumnsExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/upsertandpaginatewithkeysetcursors/Step4ChosenColumnsExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain upsertandpaginatewithkeysetcursors.Step4ChosenColumnsExample"
```

### Compare the Upsert SQL for SQLite and PostgreSQL

This example prints the upsert statement and the `CREATE TABLE` statement for both dialects. It needs no database.

<details>
  <summary>sql-examples/src/main/scala/upsertandpaginatewithkeysetcursors/Step5DialectSqlExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/upsertandpaginatewithkeysetcursors/Step5DialectSqlExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain upsertandpaginatewithkeysetcursors.Step5DialectSqlExample"
```

### Walk a Large Table with pageAfter

This example fills a 10,000-row sales table, walks it in pages of 50, and prints SQLite's query plans for a keyset page and an `OFFSET` page.

<details>
  <summary>sql-examples/src/main/scala/upsertandpaginatewithkeysetcursors/Step6PageAfterExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/upsertandpaginatewithkeysetcursors/Step6PageAfterExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain upsertandpaginatewithkeysetcursors.Step6PageAfterExample"
```

### Page Through Your Own Query with keysetAfter

This example pages through the sales of one book with `Frag.keysetAfter`.

<details>
  <summary>sql-examples/src/main/scala/upsertandpaginatewithkeysetcursors/Step7KeysetAfterExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/upsertandpaginatewithkeysetcursors/Step7KeysetAfterExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain upsertandpaginatewithkeysetcursors.Step7KeysetAfterExample"
```

### The Complete Example

```bash
sbt "runMain upsertandpaginatewithkeysetcursors.CompleteExample"
```

To compile every example at once without running them:

```bash
sbt compile
```

## Going Further

- [Repo](../reference/sql/repo.md) documents `insertOrUpdate`, `insertOrUpdateBatch` and `pageAfter` alongside the rest of the repository API.
- [Frag](../reference/sql/frag.md) covers building, combining and running SQL fragments.
- [Table](../reference/sql/table.md) and [Ddl](../reference/sql/ddl.md) explain what `createTable` generates, and why it has no primary key.
- [SQL Transactions: Isolation, Read-Only and Savepoints](./sql-transactions.md) shows how to group upserts with other writes in one transaction.
- [Query DSL with Reified Optics — Part 2: SQL Generation](./query-dsl-sql.md) builds `SELECT` statements from optics instead of hand-written SQL.
