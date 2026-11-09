---
id: insert-and-update-many-rows-in-batches
title: "How to Insert and Update Many Rows in Batches"
description: "Choose between a loop of inserts, insertBatch and insertAll, load large feeds in chunks with one transaction each, and update many rows with one statement or one batch."
keywords:
  - "Batch Insert"
  - "insertBatch"
  - "insertAll"
  - "Chunked Import"
  - "Transactions"
  - "Repo"
---

In this guide, we load a feed of sensor readings into a SQLite table and then update many of them. By the end, you will know which insert method to use for a given number of rows, how to split a large feed into chunks so that a bad row costs one chunk instead of the whole import, and how to update thousands of rows with one statement or one batch.

We build it one recipe at a time:

- Choosing between a loop of `Repo#insert`, `Repo#insertBatch` and `Repo#insertAll`, with timings
- Importing a large feed in chunks, one transaction per chunk
- What one bad row does to a batch, inside and outside `transact`
- Updating many rows: one set-based `UPDATE`, a loop of `Repo#update`, and `Repo#insertOrUpdateBatch`

This guide builds on [Build Your First Database Repository on SQLite](./your-first-sqlite-repository.md). We use `JdbcTransactor`, `Repo` and `sql"..."` the way that tutorial introduces them and don't explain them again.

Saving one row at a time with an upsert, and the `PRIMARY KEY` that upserts need, are covered in [How to Upsert Rows and Paginate with Keyset Cursors](./upsert-and-paginate-with-keyset-cursors.md). We link to that guide instead of repeating it.

## The Problem

A feed of ten thousand sensor readings arrives as a file. Each reading must end up in the database, and a bad row must not leave the table half-loaded without anyone knowing.

With plain JDBC and the default auto-commit mode, the obvious code is one `INSERT` per reading:

```scala mdoc:compile-only
import java.sql.Connection

// One INSERT per reading. With auto-commit on, each executeUpdate is its own transaction.
def loadReadings(conn: Connection, readings: List[(Long, Long, Double)]): Unit = {
  val ps = conn.prepareStatement("INSERT INTO reading (id, sensor_id, celsius, flagged) VALUES (?, ?, ?, 0)")
  readings.foreach { case (id, sensorId, celsius) =>
    ps.setLong(1, id)
    ps.setLong(2, sensorId)
    ps.setDouble(3, celsius)
    ps.executeUpdate()
  }
  ps.close()
}
```

Every row costs a round trip to the database, and with auto-commit every row also costs a commit. The load gets slower as the feed grows, and if the process dies at row 7,000, the first 7,000 rows stay and the rest are lost. Wrapping the loop in one transaction fixes the commits, but the code is still a loop that the database sees one row at a time. Loading the same feed with batches is a matter of choosing the right call and deciding where the transactions begin and end.

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

The `sql` module is published for Scala 3 only. The code in this guide is the same for both artifact names.

The base imports and a few helpers go in one setup block. `Reading` has a generated ID, a sensor ID, a temperature and a flag:

```scala mdoc:silent
import java.nio.file.Files
import scala.util.Try
import zio.blocks.schema.Schema
import zio.blocks.sql.*

case class Reading(id: Long, sensorId: Long, celsius: Double, flagged: Boolean)

object Reading {
  given Schema[Reading] = Schema.derived
}

val repo = Repo.derived[Reading, Long]

// Readings with IDs from `from` to `from + n - 1`, all unflagged
def sample(from: Long, n: Int): List[Reading] =
  (0 until n).toList.map { i =>
    val id = from + i
    Reading(id, id % 4 + 1, 18.0 + (id % 150) / 10.0, flagged = false)
  }

val dbFile     = Files.createTempFile("sensors", ".db")
val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)
```

We create the table ourselves, with the primary key written out. `Table#createTable` emits no `PRIMARY KEY`, which is a known bug in 0.0.56, and the upsert and batch methods need one. The [upsert guide](./upsert-and-paginate-with-keyset-cursors.md#create-a-table-with-a-real-primary-key) explains the same workaround in more detail.

```scala mdoc:silent
transactor.connect {
  sql"""CREATE TABLE reading (
    id        INTEGER PRIMARY KEY,
    sensor_id INTEGER NOT NULL,
    celsius   REAL NOT NULL,
    flagged   INTEGER NOT NULL
  )""".update
}
```

## Choosing an Insert Method

The library has three ways to write more than one row. `Repo#insertBatch` sends one prepared `INSERT` and all its parameter sets as a JDBC batch, then returns the total row count. `Repo#insertAll` builds one multi-row `INSERT ... VALUES (...), (...)` statement and returns the IDs of the rows. A loop of `Repo#insert` runs one statement per row.

The three give the same rows. We run each on the same five readings, clearing the table between runs:

```scala mdoc
val rows = sample(1L, 5)

transactor.transact {
  val counts = rows.map(repo.insert)
  println(s"loop of insert:  ${counts.sum} rows affected, ${repo.count} in table")
  repo.clear()
}

transactor.transact {
  val affected = repo.insertBatch(rows)
  println(s"insertBatch:     $affected rows affected, ${repo.count} in table")
  repo.clear()
}

transactor.transact {
  val ids = repo.insertAll(rows)
  println(s"insertAll:       returned ids ${ids.mkString("[", ", ", "]")}, ${repo.count} in table")
}
```

The counts agree, so the choice is about speed and about what you need back. `insertBatch` returns a count, not IDs, and it is the one that scales. `insertAll` returns the IDs from `Repo#getId`, in input order, which the batch method doesn't; its SQL grows with the number of rows, so it suits moderate batches. The [Repo reference](../reference/sql/repo.md) compares the two in detail.

We timed the three on a file-backed SQLite database, one transaction per run, with `System.nanoTime`. This is one run on one Linux machine with sqlite-jdbc 3.53.4.0, so read the ratios, not the milliseconds:

```text
rows     loop of insert   insertBatch   insertAll
1,000        113.5 ms        30.1 ms      58.3 ms
5,000        183.9 ms        35.4 ms      53.8 ms
10,000       239.2 ms        62.3 ms      98.1 ms
```

The loop was run in one transaction too, so the gap is the per-statement cost. Running each insert in its own transaction is much worse: 1,000 readings took 19,018.7 ms that way, against 113.5 ms in one transaction. The timing program is `TimingExample` in the companion examples.

:::tip
For a few rows, any of the three is fine. For thousands, use `insertBatch`. If you need the generated IDs of a few thousand rows, `insertAll` with a chunk size (see below) keeps the SQL to a sensible size.
:::

## Importing in Chunks

A feed of a million rows should not sit in one transaction. The transaction holds locks and a large undo log until it commits, and a single bad row rolls back everything before it. Splitting the feed into chunks gives each chunk its own transaction: a failure costs one chunk, and the chunks before it stay written.

The library has no chunking helper, so we split the list ourselves with `grouped` and call `transact` once per chunk:

```scala mdoc:silent
// Inserts rows in chunks of chunkSize, each chunk in its own transaction
def importInChunks(rows: List[Reading], chunkSize: Int): Int = {
  var written = 0
  rows.grouped(chunkSize).foreach { chunk =>
    written += transactor.transact(repo.insertBatch(chunk))
  }
  written
}
```

We run it on a feed of twelve readings in chunks of four. The feed repeats ID 3 in its third chunk, so that chunk fails:

```scala mdoc
val feed = sample(1L, 8) ++ sample(3L, 1) ++ sample(9L, 3)
println(s"feed has ${feed.size} readings, chunks of 4")

Try(importInChunks(feed, 4)) match {
  case scala.util.Success(n) => println(s"imported $n readings")
  case scala.util.Failure(e) => println(s"import stopped: ${e.getMessage}")
}

transactor.connect {
  println(s"readings in table: ${repo.count}")
  println(s"ids in table: ${repo.all.map(_.id).mkString(", ")}")
}
```

The first two chunks, IDs 1 to 8, are committed. The third chunk, which holds the repeated ID 3, rolls back completely, so none of IDs 9 to 11 are written either. The exception stops the import, so the rest of the feed is never tried. If you want to keep going past a bad chunk, catch the exception inside the `foreach`, log the chunk, and continue. Pick the chunk size from how much you can afford to redo: a few thousand rows is a common range, and the time each chunk takes is the measure to check.

:::warning
The chunk boundaries are not a resume point by themselves. If the process dies between chunks, the table holds the chunks that committed. To resume, record the last committed ID and skip the rows before it, or upsert the feed so that running it again is safe; the [upsert guide](./upsert-and-paginate-with-keyset-cursors.md) shows how.
:::

## What One Bad Row Does

`Repo#insertBatch` doesn't open a transaction. It runs the batch on whatever connection it is given, so what survives a failure depends on where the call sits. We feed it five readings, the third of which repeats an ID that already exists, first outside any transaction and then inside `transact`:

```scala mdoc
val batch = sample(1L, 5) // IDs 1 to 5; ID 3 is about to clash

transactor.connect {
  repo.clear()
  repo.insert(sample(3L, 1).head)
}

val outside = Try(transactor.connect(repo.insertBatch(batch)))
println(s"without transact: ${outside.failed.map(_.getMessage).getOrElse("no error")}")
transactor.connect(println(s"  readings in table: ${repo.count}"))

transactor.connect {
  repo.clear()
  repo.insert(sample(3L, 1).head)
}

val inside = Try(transactor.transact(repo.insertBatch(batch)))
println(s"with transact:    ${inside.failed.map(_.getMessage).getOrElse("no error")}")
transactor.connect(println(s"  readings in table: ${repo.count}"))
```

Outside a transaction, the rows before the failing one stay: IDs 1 and 2 were written, plus the existing ID 3. Inside `transact`, the failure rolls the whole batch back, and only the existing row remains. This is SQLite's behaviour with this driver. The source doesn't promise that a driver keeps the rows before a failed row, so the safe rule is to always wrap a batch in `transact`, as the chunked import above does. `transact` commits when the block returns and rolls back when it throws, and the exception is rethrown.

## Updating Many Rows

The library has `Repo#update`, which updates one row by its ID, and `Repo#insertOrUpdateBatch`, which upserts many rows in one JDBC batch. It has no `updateBatch`, and there is no public way to run a custom `UPDATE` as a JDBC batch. The `sql"..."` interpolator returns a `Frag`, and a `Frag` runs one statement. So for "update many rows", you pick one of three approaches.

### One Set-Based Statement

When the change is the same for every row that matches a condition, write one `UPDATE` with a `WHERE` clause. The database does the whole job in one statement:

```scala mdoc
transactor.transact {
  val threshold = 18.5
  val flagged   = sql"UPDATE reading SET flagged = 1 WHERE celsius > $threshold".update
  println(s"set-based UPDATE flagged $flagged readings")
}
```

This is the fastest option when it fits, because the rows never leave the database. It doesn't fit a change that depends on each row's own values, which is where the next two come in.

### A Loop of `Repo#update` in One Transaction

When each row gets a different value, `Repo#update` writes all non-ID columns of an entity by its ID, and returns `0` when no row has that ID. Calling it in a loop inside one `transact` keeps the work atomic:

```scala mdoc
val recalibrated = transactor.connect(repo.all.take(4)).map(r => r.copy(celsius = r.celsius + 0.5))

transactor.transact {
  val counts = recalibrated.map(repo.update)
  println(s"loop of update:          ${counts.sum} rows affected")
}
```

Each `update` is a separate statement, so this costs one round trip per row. It is still one transaction, so a failure rolls back the whole loop.

### `Repo#insertOrUpdateBatch` as a Batch Update

When each row is a full entity and you have every column's new value, `Repo#insertOrUpdateBatch` sends all of them as one JDBC batch, and it updates the rows that exist. It also inserts the rows that don't, so use it when a missing row is acceptable. The upsert guide covers how the `ON CONFLICT` clause picks the row.

```scala mdoc
val recalibratedAgain = recalibrated.map(r => r.copy(celsius = r.celsius + 0.5)) :+
  Reading(99L, 1L, 21.0, flagged = false)

transactor.transact {
  val affected = repo.insertOrUpdateBatch(recalibratedAgain)
  println(s"insertOrUpdateBatch:     $affected rows affected")
}

transactor.connect {
  println(s"readings in table: ${repo.count}")
  repo.all.take(5).foreach(println)
}
```

Four of the five rows existed, so they were updated; ID 99 didn't, so it was inserted. The count is 13 (12 loaded, plus 99), and the statement's count of 5 can't tell the two apart, which is the same caveat the upsert guide gives for single rows.

## Choosing an Update Method

- **One condition, one value:** a single `UPDATE` with `WHERE`. One statement, no rows leave the database.
- **A different value per row, few rows:** `Repo#update` in a loop inside `transact`. Simple, one round trip per row.
- **A different value per row, many rows, full entities:** `Repo#insertOrUpdateBatch` inside `transact`. One JDBC batch, with the insert side effect.

## Running the Examples

The companion examples in `sql-examples/src/main/scala/batchwritesmanyrows/` run the same code as the sections above, with the output printed to the terminal:

- `BatchChoiceExample` runs the loop, `insertBatch` and `insertAll`
- `ChunkedImportExample` runs the chunked import with a bad chunk
- `FailedBatchExample` runs one failing batch with and without `transact`
- `UpdateManyExample` runs the three update approaches
- `TimingExample` times the insert methods; its numbers depend on your machine
- `CompleteExample` imports 10,000 readings in chunks, flags the hot ones, applies a recalibration batch and ends with a late bad chunk

From the `sql-examples` directory, run one with `sbt "runMain batchwritesmanyrows.CompleteExample"`.

## Going Further

- The [Repo reference](../reference/sql/repo.md) documents `insertBatch`, `insertAll`, `insertOrUpdateBatch` and `update`, with their empty-input and failure behaviour.
- [How to Upsert Rows and Paginate with Keyset Cursors](./upsert-and-paginate-with-keyset-cursors.md) covers the `PRIMARY KEY` requirement, `Upsert.insertDoNothing` and `Upsert.insertDoUpdate`, and the batch upsert used for the nightly feed.
- [How to Stream Large Query Results](./stream-large-query-results.md) covers the read side of a large table, with the same care about memory.
- [How to Migrate Table Rows Online](./migrate-table-rows-online.md) shows batches of rows in a migration, where each batch also has to record progress.
