---
id: stream-large-query-results
title: "How to Stream Large Query Results"
description: "Read a large SQL result set as a stream of row chunks, aggregate it, export it to a file and handle its errors without loading every row."
keywords:
  - "Streaming"
  - "Large Result Sets"
  - "queryStream"
  - "queryChunked"
  - "CSV Export"
  - "Frag"
---

In this guide, we read a large `sales` table as a stream of row chunks with `Frag#queryStream` and `Frag#queryChunked`. By the end, you will have a report of copies sold per title and a CSV export of every sale, both computed while only a small batch of rows is held in memory at a time.

We start from a table of 12,000 sales and add one capability at a time:

- Aggregating the whole table with `runFold`, one chunk at a time
- Choosing the chunk size
- Exporting every row to a CSV file through a `Sink`
- Filtering and reshaping rows on their way to the file
- Keeping the stream inside the block that owns the connection
- Handling a bad query or a row that can't be decoded

This guide builds on [Build Your First Database Repository on SQLite](./your-first-sqlite-repository.md). We use `JdbcTransactor`, `Table`, `Repo` and `sql"..."` the way that tutorial introduces them and don't explain them again. The streaming operators we use (`map`, `filter`, `flatMap`, `grouped`, `runFold`) are the ones the tutorial "Build Your First Streaming Pipeline" teaches.

## The Problem

`Frag#query` returns a `List`: it reads every row of the result into memory before it gives you the first one. That is fine for a page of results, but a report over a whole table holds the entire table on the heap, and an export of a table that doesn't fit in memory fails with an `OutOfMemoryError` halfway through.

Without a streaming API, the usual way out is a hand-written JDBC cursor loop:

```scala mdoc:compile-only
import java.sql.DriverManager
import scala.collection.mutable

val connection = DriverManager.getConnection("jdbc:sqlite:bookstore.db")
try {
  val statement = connection.prepareStatement("SELECT title, quantity FROM sale")
  try {
    statement.setFetchSize(500)
    val rows = statement.executeQuery()
    try {
      val copiesPerTitle = mutable.Map.empty[String, Int]
      while (rows.next()) {
        val title    = rows.getString("title")
        val quantity = rows.getInt("quantity")
        copiesPerTitle(title) = copiesPerTitle.getOrElse(title, 0) + quantity
      }
      println(copiesPerTitle)
    } finally rows.close()
  } finally statement.close()
} finally connection.close()
```

This keeps memory flat, but every report repeats the same three nested `try`/`finally` blocks, decodes columns by hand, and mixes the reading, the decoding and the aggregation in one loop. Writing a second consumer, such as a file export, means copying the whole loop again.

In this guide, we solve this by asking `Frag` for a `Stream` of decoded rows and building each consumer from stream operators.

## Prerequisites

Add the `sql` module, the `streams` module and the SQLite JDBC driver to your `build.sbt`:

```scala
// Scala 3.9 and newer, with scalacOptions += "-experimental"; on Scala 3.3 to 3.8 the artifacts are "zio-blocks-sql" and "zio-blocks-streams"
libraryDependencies += "dev.zio"   %% "zio-blocks-next-sql"     % "@VERSION@"
libraryDependencies += "dev.zio"   %% "zio-blocks-next-streams" % "@VERSION@"
libraryDependencies += "org.xerial" % "sqlite-jdbc"             % "3.53.4.0"
```

The `sql` module already depends on `streams`; listing it makes the dependency explicit, since the code imports `zio.blocks.streams` directly. The `sql` module is published for Scala 3 only, and `JdbcTransactor` runs on the JVM.

We use these imports throughout the guide:

```scala mdoc:silent
import java.nio.file.Files
import java.time.LocalDate
import scala.util.Try

import zio.blocks.chunk.Chunk
import zio.blocks.schema.Schema
import zio.blocks.sql.*
import zio.blocks.streams.*
```

## The Sales Table

Our bookstore records one row per sale: which book, how many copies, and on which day. The sales data is fictional; the titles are real Scala books:

```scala mdoc:silent
case class Sale(id: Long, title: String, quantity: Int, soldOn: LocalDate)

object Sale {
  given Schema[Sale] = Schema.derived
}

val saleTable = Table.derived[Sale]
val sales     = Repo.derived[Sale, Long]

val titles = Vector("Zionomicon", "Programming in Scala", "Functional Programming in Scala")
```

We store the table in a temporary SQLite file. `jdbc:sqlite::memory:` would not work here: SQLite gives every new connection its own empty in-memory database, and each `connect` block opens a new connection. Then we create the table and insert 12,000 sales in one transaction:

```scala mdoc:silent
val dbFile     = Files.createTempFile("bookstore-sales", ".db")
val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

val saleCount = 12000

transactor.transact {
  saleTable.createTable(SqlDialect.SQLite).update
  // In every 7 sales, 3 are of Zionomicon and 2 of each other title
  sales.insertBatch((1 to saleCount).map { i =>
    Sale(i.toLong, titles(i % 7 % 3), 1 + i % 4, LocalDate.of(2026, 1, 1).plusDays((i % 365).toLong))
  })
}
```

We also name the query we stream from in most sections. A `Frag` is only SQL text and parameters, so defining it touches no connection:

```scala mdoc:silent
val allSales = sql"SELECT id, title, quantity, sold_on FROM sale ORDER BY id"
```

## Aggregate a Large Table One Chunk at a Time

`Frag#queryStream[A]` returns a `Stream[Throwable, Chunk[A]]`. Each element of the stream is a `Chunk` of up to 64 decoded rows, and a terminal operation such as `runFold` pulls the chunks one after another and returns an `Either`. We add up the copies sold per title, and also record how many chunks arrived and how large the biggest one was:

```scala mdoc:silent
case class Report(copiesPerTitle: Map[String, Int], rows: Int, chunks: Int, largestChunk: Int)

val emptyReport = Report(Map.empty, 0, 0, 0)

def addChunk(report: Report, chunk: Chunk[Sale]): Report = {
  val copies = chunk.foldLeft(report.copiesPerTitle) { (totals, sale) =>
    totals.updated(sale.title, totals.getOrElse(sale.title, 0) + sale.quantity)
  }
  Report(copies, report.rows + chunk.size, report.chunks + 1, report.largestChunk.max(chunk.size))
}

val report: Either[Throwable, Report] =
  transactor.connect {
    allSales.queryStream[Sale].runFold(emptyReport)(addChunk)
  }
```

Then we print it:

```scala mdoc
report.foreach { r =>
  r.copiesPerTitle.toList.sorted.foreach((title, copies) => println(s"$title: $copies copies"))
  println(s"${r.rows} rows in ${r.chunks} chunks, at most ${r.largestChunk} rows per chunk")
}
```

The fold saw all 12,000 rows, but each step only received one chunk of at most 64 of them. The fold keeps the running totals and drops each chunk once it has added it in, so the rows the stream holds at any moment are bounded by the chunk size, not by the size of the table.

## Choose a Chunk Size

`queryStream` is `queryChunked` with the default chunk size of 64. When you want larger or smaller batches, call `Frag#queryChunked[A](chunkSize)` instead. We run the same fold with chunks of 1,000 rows:

```scala mdoc:silent
val bigChunks: Either[Throwable, Report] =
  transactor.connect {
    allSales.queryChunked[Sale](1000).runFold(emptyReport)(addChunk)
  }
```

The totals don't change, but the batches do:

```scala mdoc
bigChunks.foreach(r => println(s"${r.rows} rows in ${r.chunks} chunks, at most ${r.largestChunk} rows per chunk"))
```

The chunk size trades memory for per-chunk overhead. Larger chunks mean fewer steps through the stream and suit consumers that work best in batches, such as a batch insert into another table or a buffered write. Smaller chunks keep fewer rows in memory and deliver the first rows sooner, which matters when each row is large or when the consumer is slow. The chunk size bounds only the rows the stream holds; how many rows the JDBC driver fetches from the server per round trip is the driver's own setting.

The size must be at least 1. `queryChunked` checks it as soon as it is called, before any stream exists, so a bad size throws instead of returning a `Left`:

```scala mdoc
println(Try(transactor.connect(allSales.queryChunked[Sale](0))))
```

## Export Every Row to a CSV File

To export the table, we write each chunk to a file as it arrives. `Sink.foreach` builds a `Sink` that runs a function for every element, and `Stream#run(sink)` drains the stream into it. We open a buffered writer, write a header line, and turn every sale into one CSV line:

```scala mdoc:silent
def csvLine(sale: Sale): String =
  s"${sale.id},${sale.title},${sale.quantity},${sale.soldOn}"

val exportFile = Files.createTempFile("sales-export", ".csv")

val exported: Either[Throwable, Unit] = {
  val writer = Files.newBufferedWriter(exportFile)
  try {
    writer.write("id,title,quantity,sold_on")
    writer.newLine()
    transactor.connect {
      allSales.queryStream[Sale].run(Sink.foreach[Chunk[Sale]] { chunk =>
        chunk.foreach { sale =>
          writer.write(csvLine(sale))
          writer.newLine()
        }
      })
    }
  } finally writer.close()
}
```

`Sink.foreach` doesn't close the writer, so we close it in `finally`, whether the export succeeds or fails. Then we check the file:

```scala mdoc
println(exported)
println(s"Lines in the file: ${Files.readAllLines(exportFile).size}")
Files.readAllLines(exportFile).subList(0, 4).forEach(line => println(line))
```

The file has the header and one line per sale. None of our titles contains a comma or a quote, so `csvLine` doesn't quote its fields; for real data, quote any field that may contain them.

## Filter and Reshape Rows on the Way Out

Stream operators work between the query and the sink, so the file receives only the rows and the columns we want. To export only Zionomicon sales as `date,copies` lines, we first flatten the chunks into single rows with `flatMap(Stream.fromChunk(_))`, then filter, map each sale to its line, and batch the lines again with `grouped` so the writer gets 500 lines per call:

```scala mdoc:silent
val zionomiconFile = Files.createTempFile("zionomicon-sales", ".csv")

val zionomiconBatches: Either[Throwable, Long] = {
  val writer = Files.newBufferedWriter(zionomiconFile)
  try
    transactor.connect {
      allSales
        .queryChunked[Sale](1000)
        .flatMap(chunk => Stream.fromChunk(chunk))
        .filter(_.title == "Zionomicon")
        .map(sale => s"${sale.soldOn},${sale.quantity}")
        .grouped(500)
        .tapEach(lines => lines.foreach { line => writer.write(line); writer.newLine() })
        .count
    }
  finally writer.close()
}
```

`tapEach` writes each batch and passes it on, and `count` returns how many batches went through:

```scala mdoc
println(s"Batches written: $zionomiconBatches")
println(s"Lines in the file: ${Files.readAllLines(zionomiconFile).size}")
Files.readAllLines(zionomiconFile).subList(0, 3).forEach(line => println(line))
```

The file has one line per Zionomicon sale. `grouped(500)` emits full batches of 500 lines and a smaller last batch for the rest.

:::warning
A known bug: after a `flatMap` over objects such as rows, a `map` to a primitive (`Int`, `Long`, `Double`, `Boolean`, `Char`) followed by `grouped`, `sliding`, `take`, `takeWhile` or `scan` throws a `ClassCastException` from blocking terminals such as `runCollect` or `count`. For example, `.flatMap(chunk => Stream.fromChunk(chunk)).map(_.quantity).grouped(500)` fails. Keep the elements as objects or strings until after the stateful operator, as above, or map to the primitive inside the batches (`.grouped(500).map(_.map(_.quantity))`).
:::

## Keep the Stream Inside the Connection Block

A query stream is lazy. `queryStream` only prepares a description of the work; the statement runs and the result set opens on the first pull, and both close when the stream is fully drained or closed early. The connection itself belongs to the `connect` (or `transact`) block that provided the `DbCon`, and that block closes it when it returns.

So a stream that leaves its block can't be read any more. Here we return the stream itself from `connect` and run it afterwards:

```scala mdoc:silent
val escaped: Stream[Throwable, Chunk[Sale]] =
  transactor.connect(allSales.queryStream[Sale])
```

The first pull happens after the connection is closed:

```scala mdoc
println(escaped.runFold(0)((rows, chunk) => rows + chunk.size))
```

The failure is a `Left` from the terminal operation, not an exception. The fix is to run the terminal operation inside the block and return its result instead of the stream:

```scala mdoc
println(transactor.connect(allSales.queryStream[Sale].runFold(0)((rows, chunk) => rows + chunk.size)))
```

Because the stream is a description, every terminal operation runs the query again. If you need two results from the same rows, compute both in one fold, as the report above does, rather than running the stream twice.

:::caution
Consume the stream on the connection that opened it. Calling `connect` or `transact` again inside an open block, for example to write each chunk to another table, opens a second connection while the first one still holds the result set open. On SQLite the write then waits for the read's lock and fails with `SQLITE_BUSY`; with a connection pool on PostgreSQL, enough nested calls exhaust the pool and time out. Do the writes through the same `DbCon`, inside the same block.
:::

## Handle Query and Decoding Errors

A query that fails surfaces as a `Left` from the terminal operation, carrying the driver's exception. A misspelled column fails when the statement runs on the first pull:

```scala mdoc:silent
val badColumn = transactor.connect {
  sql"SELECT id, titel, quantity, sold_on FROM sale".queryStream[Sale].runFold(0)((rows, chunk) => rows + chunk.size)
}
```

We match on the result:

```scala mdoc
badColumn match {
  case Left(error)  => println(s"Query failed: ${error.getMessage}")
  case Right(rows)  => println(s"Read $rows rows")
}
```

A row that can't be decoded fails the same way, at the row where decoding fails. Here the query hands the `title` column to the `soldOn` field, and a book title isn't a date:

```scala mdoc:silent
val badRow = transactor.connect {
  sql"SELECT id, title, quantity, title AS sold_on FROM sale".queryStream[Sale].runFold(0)((rows, chunk) => rows + chunk.size)
}
```

The first row already fails:

```scala mdoc
badRow.left.foreach(error => println(s"${error.getClass.getSimpleName}: ${error.getMessage}"))
```

Both errors arrive as `Throwable` values, so you decide in one place whether to log them, retry, or turn them into a domain error with `Either#left.map`.

:::warning
A known bug: a query that returns fewer columns than the row type has (for example `SELECT id, title FROM sale` decoded as `Sale`) throws a `java.sql.SQLException` out of the terminal operation instead of returning a `Left`. Wrap such calls in `Try` if the column list comes from outside your code.
:::

## Putting It Together

The complete example creates the table, prints the per-title report, writes the CSV export, and deletes its temporary files:

```scala mdoc:embed:sql-examples/src/main/scala/streamlargequeryresults/CompleteExample.scala
```

```scala mdoc:invisible
Files.deleteIfExists(exportFile)
Files.deleteIfExists(zionomiconFile)
Files.deleteIfExists(dbFile)
```

## Running the Examples

All the code in this guide has runnable companions in the `sql-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/sql-examples
```

Each example creates its own temporary database and files and deletes them when it finishes, so you can run them in any order. To compile all the examples without running them:

```bash
sbt compile
```

### The Shared Setup

The `Sale` row type, the 12,000 seeded sales and the temporary database every example uses are in this file:

<details>
  <summary>sql-examples/src/main/scala/streamlargequeryresults/Sales.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/streamlargequeryresults/Sales.scala:show-line-numbers
```

</details>

### Aggregate a Large Table One Chunk at a Time

<details>
  <summary>sql-examples/src/main/scala/streamlargequeryresults/Step1AggregateExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/streamlargequeryresults/Step1AggregateExample.scala:show-line-numbers
```

</details>

It prints the copies sold per title and the chunk statistics:

```bash
sbt "runMain streamlargequeryresults.Step1AggregateExample"
```

### Choose a Chunk Size

<details>
  <summary>sql-examples/src/main/scala/streamlargequeryresults/Step2ChunkSizeExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/streamlargequeryresults/Step2ChunkSizeExample.scala:show-line-numbers
```

</details>

It reads the table with three chunk sizes and shows the rejected size 0:

```bash
sbt "runMain streamlargequeryresults.Step2ChunkSizeExample"
```

### Export Every Row to a CSV File

<details>
  <summary>sql-examples/src/main/scala/streamlargequeryresults/Step3ExportExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/streamlargequeryresults/Step3ExportExample.scala:show-line-numbers
```

</details>

It writes `sales.csv` and prints its first lines:

```bash
sbt "runMain streamlargequeryresults.Step3ExportExample"
```

### Filter and Reshape Rows on the Way Out

<details>
  <summary>sql-examples/src/main/scala/streamlargequeryresults/Step4TransformExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/streamlargequeryresults/Step4TransformExample.scala:show-line-numbers
```

</details>

It exports only the Zionomicon sales, in batches of 500 lines:

```bash
sbt "runMain streamlargequeryresults.Step4TransformExample"
```

### Keep the Stream Inside the Connection Block

<details>
  <summary>sql-examples/src/main/scala/streamlargequeryresults/Step5ScopeExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/streamlargequeryresults/Step5ScopeExample.scala:show-line-numbers
```

</details>

It runs a stream outside its `connect` block, then inside it:

```bash
sbt "runMain streamlargequeryresults.Step5ScopeExample"
```

### Handle Query and Decoding Errors

<details>
  <summary>sql-examples/src/main/scala/streamlargequeryresults/Step6ErrorsExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/streamlargequeryresults/Step6ErrorsExample.scala:show-line-numbers
```

</details>

It prints the `Left` for a misspelled column and for a row that can't be decoded:

```bash
sbt "runMain streamlargequeryresults.Step6ErrorsExample"
```

### The Complete Example

```bash
sbt "runMain streamlargequeryresults.CompleteExample"
```

## Going Further

- [`Frag`](../reference/sql/frag.md) documents `queryStream`, `queryChunked` and the other execution methods.
- [`Transactor`](../reference/sql/transactor.md) explains how `connect` and `transact` open and close connections.
- [How to Connect to PostgreSQL with a Connection Pool](./connect-to-postgresql-with-a-connection-pool.md) moves the same code to PostgreSQL, where pool size limits how many streams can be open at once.
- [How to Test Database Code Against SQLite](./test-database-code-against-sqlite.md) shows how to give each test its own database file.
- The `sql-zio` module has no bridge from a query stream to a ZIO `ZStream` yet. To use a query stream from ZIO, run the whole stream, terminal operation included, inside [`TransactorZIO#connect`](../reference/sql/transactor-zio.md), which runs its block on ZIO's blocking thread pool.
