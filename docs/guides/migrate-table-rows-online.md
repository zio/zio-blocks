---
id: migrate-table-rows-online
title: "How to Migrate Table Rows to a New Version Online"
description: "Convert every row of a live table to a new row type with SmallMigrator or LargeMigrator while the application keeps reading and writing."
keywords:
  - "Online Migration"
  - "Backfill"
  - "Shadow Table"
  - "LargeMigrator"
  - "SmallMigrator"
  - "Migration"
---

In this guide, we change the row type of a live `customers` table from `CustomerV1`, which stores one `full_name` column, to `CustomerV2`, which stores `first_name` and `last_name`, and we convert every existing row while the application keeps reading and writing. By the end, you will have backfilled a small SQLite table in place with `SmallMigrator`, copied a 20,000-row PostgreSQL table into a shadow table with `LargeMigrator` while another thread writes to it, and swapped the new table in without losing a write.

We describe the row change once, as a typed [`Migration`](../reference/schema/migration.md), and let the `dataMigration` module run it in batches from a queue of row keys. Along the way, we:

- Describe the old and new rows and the conversion between them
- Preview the SQL a migration would run, before running anything
- Backfill a small table in place with `SmallMigrator`
- Migrate a large PostgreSQL table through a shadow table with `LargeMigrator`
- Recover from a failed batch and restart a migration
- Choose an execution model for your own table

This guide assumes you know SQL, have built a repository as in [Your First SQLite Repository](./your-first-sqlite-repository.md), and have written a `Migration` as in [Evolve Your Data](./evolve-your-data.md). We don't explain `Repo`, `JdbcTransactor` or `Migration.newBuilder` again here.

## The Problem

Our `customers` table has a `full_name` column, and the next version of the application needs `first_name` and `last_name` instead. The table is live: customers sign up and change their details all day, and we can't take the application down while we rewrite every row.

The usual first attempt is a backfill loop that reads rows in pages and updates them. This is that loop in plain JDBC:

```scala mdoc:compile-only
import java.sql.Connection

def backfillNames(connection: Connection): Unit = {
  val select = connection.prepareStatement(
    "SELECT id, full_name FROM customers WHERE first_name IS NULL ORDER BY id LIMIT 500"
  )
  val update = connection.prepareStatement("UPDATE customers SET first_name = ?, last_name = ? WHERE id = ?")
  var done   = false
  while (!done) {
    val rows = select.executeQuery()
    done = true
    while (rows.next()) {
      done = false
      val fullName = rows.getString("full_name")
      val space    = fullName.indexOf(' ')
      update.setString(1, fullName.substring(0, space))
      update.setString(2, fullName.substring(space + 1))
      update.setLong(3, rows.getLong("id"))
      update.executeUpdate()
    }
    rows.close()
  }
}
```

This loop has three problems. A customer who changes their name after the loop has passed their row keeps the old `first_name`, because `first_name IS NULL` never selects that row again. A single-word name such as "Cher" makes `String#substring` throw, and every rerun throws at the same row. And the loop only works while the new columns sit beside the old one: when `full_name` has to go and the new columns must be `NOT NULL`, we need a new table and a moment when the application switches to it, and the loop gives us neither.

In this guide, we let the `dataMigration` module do the bookkeeping. It keeps the keys of the rows still to convert in a queue table, converts them in batches with a typed `Migration`, and, for a new table, records every concurrent write in the same queue with database triggers until we swap the tables.

## Prerequisites

Add the `dataMigration` module, which brings in the `sql` and `schema` modules, and the JDBC drivers of the databases you use to your `build.sbt`. [How to Add ZIO Blocks to Your Build](./add-zio-blocks-to-your-build.md) explains the two artifact names:

```scala
// Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-data-migration" % "@VERSION@"

// Scala 3.9 and newer
libraryDependencies += "dev.zio" %% "zio-blocks-next-data-migration" % "@VERSION@"
scalacOptions += "-experimental"

// JDBC drivers, for every Scala version
libraryDependencies += "org.xerial"     % "sqlite-jdbc" % "3.53.4.0"
libraryDependencies += "org.postgresql" % "postgresql"  % "42.7.14"
```

The module is published for Scala 3 only; there is no Scala 2.13 artifact. It runs on the JVM, through [`JdbcTransactor`](../reference/sql/transactor.md); the code in this guide runs on JDK 17 and newer.

The large-table section needs PostgreSQL 14 or newer, because the capture triggers use `CREATE OR REPLACE TRIGGER`. If you have Docker, this command starts PostgreSQL 16 with a `shop` database, user and password:

```bash
docker run --rm -d --name shop-db -p 5432:5432 \
  -e POSTGRES_USER=shop -e POSTGRES_PASSWORD=shop -e POSTGRES_DB=shop \
  postgres:16
```

The PostgreSQL code reads the database URL, user and password from `SHOP_DB_URL`, `SHOP_DB_USER` and `SHOP_DB_PASSWORD`, with defaults that match the Docker command, so it runs without any variables set. The SQLite code needs no server.

## Describe the Old and New Rows

A migration needs three things: the row type the table holds today, the row type it should hold tomorrow, and a conversion from one to the other. We describe all three once and use them for every database in this guide:

```scala mdoc:silent
import zio.blocks.schema.*
import zio.blocks.schema.migration.Migration
import zio.blocks.data.migration.*
import zio.blocks.sql.*

final case class CustomerV1(id: Long, fullName: String, email: String)

object CustomerV1 extends CompanionOptics[CustomerV1] {
  implicit val schema: Schema[CustomerV1] = Schema.derived

  val fullName: Lens[CustomerV1, String] = $(_.fullName)
}

final case class CustomerV2(id: Long, firstName: String, lastName: String, email: String)

object CustomerV2 {
  implicit val schema: Schema[CustomerV2] = Schema.derived
}

val customersV1 = Repo.derived[CustomerV1, Long]("customers", "id", _.id)
val customersV2 = Repo.derived[CustomerV2, Long]("customers", "id", _.id)

val fullName   = SchemaExpr.optic[CustomerV1, String](CustomerV1.fullName.toDynamic, CustomerV1.schema)
val firstSpace = SchemaExpr.stringIndexOf(fullName, SchemaExpr.literal(" "))

val splitName: Migration[CustomerV1, CustomerV2] =
  Migration
    .newBuilder[CustomerV1, CustomerV2]
    .addField(_.firstName, SchemaExpr.stringSubstring(fullName, SchemaExpr.literal(0), firstSpace))
    .addField(
      _.lastName,
      SchemaExpr.stringTrim(SchemaExpr.stringSubstring(fullName, firstSpace, SchemaExpr.stringLength(fullName)))
    )
    .dropField(_.fullName, SchemaExpr.literal(""))
    .build
```

We keep `import zio.blocks.sql.*` after the other `zio.blocks` imports: if your project also uses the `sql-zio` module, it brings a package named `zio` into scope, and a `zio.blocks` import after it fails to compile.

Both repositories name the same table, `customers`. `customersV1` reads and writes the old columns (`id`, `full_name`, `email`), and `customersV2` the new ones (`id`, `first_name`, `last_name`, `email`). The migrators read each row through the first and write it through the second, and for a shadow table they derive the shadow table's name from the second.

`splitName` is built from [`SchemaExpr`](../reference/schema/schema-expr.md) expressions instead of a Scala function, and the migration evaluates them on each row. `firstSpace` is the index of the first space in the full name. `MigrationBuilder#addField` computes each new field from the whole `CustomerV1` value: `firstName` is the text before the first space, and `lastName` is the rest, trimmed. `MigrationBuilder#dropField` removes `fullName`, and `MigrationBuilder#build` checks at compile time that every field of `CustomerV2` is accounted for.

Applying the migration to two customers shows the split:

```scala mdoc
println(splitName(CustomerV1(1, "Ada Lovelace", "ada@example.com")))
println(splitName(CustomerV1(2, "Grace Brewster Hopper", "grace@example.com")))
```

`Migration#apply` returns an `Either`, so a row that the conversion can't handle becomes a `Left`, not an exception. We meet such a row in [Recover from a Failed Batch and Restart](#recover-from-a-failed-batch-and-restart).

All the examples in this guide share these definitions, together with two pieces we show in later sections, in one file:

<details>
  <summary>The shared model: <code>Customers.scala</code></summary>

```scala mdoc:embed:sql-examples/src/main/scala/migratetablerowsonline/Customers.scala:show-line-numbers
```

</details>

## Preview the SQL Before Running Anything

Before a migration touches a production database, we want to read the SQL it will run. `SmallMigrator#previewSql` and `LargeMigrator#previewSql` render that SQL for the dialect in scope without opening a connection, so the transactor they ask for doesn't need a running database.

For PostgreSQL, we write the transactor once, as a helper that the large-table section reuses:

```scala mdoc:silent
def shopTransactor(): JdbcTransactor =
  JdbcTransactor.fromUrl(
    sys.env.getOrElse("SHOP_DB_URL", "jdbc:postgresql://localhost:5432/shop"),
    sys.env.getOrElse("SHOP_DB_USER", "shop"),
    sys.env.getOrElse("SHOP_DB_PASSWORD", "shop"),
    SqlDialect.PostgreSQL
  )
```

The first preview is an in-place migration on SQLite, which updates the rows of `customers` where they are:

```scala mdoc:silent
val inPlace = {
  given Transactor = JdbcTransactor.fromUrl("jdbc:sqlite:customers.db", SqlDialect.SQLite)
  given Dialect    = Dialect.SQLite
  SmallMigrator[CustomerV1, CustomerV2, Long, Long](
    repoV1 = customersV1,
    repoV2 = customersV2,
    migration = splitName,
    queueTable = "customers_split_q",
    batchSize = 500,
    target = TargetStrategy.InPlace
  )
}
```

`SmallMigrator` takes the two repositories, the migration, the name of the queue table that holds the keys still to convert, the number of keys per batch and a `TargetStrategy`. The `Dialect` given picks the SQL dialect of the generated statements. Printing its preview:

```scala mdoc
inPlace.previewSql().foreach(println)
```

The preview shows only the queue table and the query that claims a batch of keys: an in-place migration creates no other table.

The second preview is a shadow-table migration on PostgreSQL, which copies the rows into a new table and swaps it in at the end:

```scala mdoc:silent
val shadow = {
  given Transactor = shopTransactor()
  given Dialect    = Dialect.Postgres
  LargeMigrator[CustomerV1, CustomerV2, Long, Long](
    repoV1 = customersV1,
    repoV2 = customersV2,
    migration = splitName,
    queueTable = "customers_split_q",
    batchSize = 500,
    target = TargetStrategy.ShadowTable("v2"),
    captureTriggers = true
  )
}
```

Its preview is longer:

```scala mdoc
shadow.previewSql().foreach(println)
```

This preview tells us three things we have to act on before we run anything:

- **Nobody creates the queue table for us.** The preview lists its `CREATE TABLE`, but `SmallMigrator#init` and `LargeMigrator#init` don't run it. We create it with `QueueTable.create` first.
- **The shadow table copies the old shape.** `CREATE TABLE IF NOT EXISTS customers_v2 (LIKE customers INCLUDING ALL)` makes `customers_v2` a copy of today's `customers`, with `full_name` and without `first_name` or `last_name`, so writing a `CustomerV2` row into it would fail. We create `customers_v2` ourselves with the new columns before `LargeMigrator#init`, and the `IF NOT EXISTS` then leaves our table alone.
- **The triggers record every write.** With `captureTriggers = true`, a trigger on `customers` adds the key of every inserted, updated or deleted row to the queue, inside the writer's own transaction. The swap at the end is the two `ALTER TABLE ... RENAME` statements.

The claim query differs by database: PostgreSQL claims keys with `FOR UPDATE SKIP LOCKED`, so several workers can share a queue, and SQLite claims them with a plain `LIMIT`, so one worker processes a SQLite queue.

<details>
  <summary>Runnable version, with both previews in one program: <code>Step2PreviewSqlExample.scala</code></summary>

```scala mdoc:embed:sql-examples/src/main/scala/migratetablerowsonline/Step2PreviewSqlExample.scala:show-line-numbers
```

</details>

<details>
  <summary>The shared PostgreSQL setup, including helpers from the large-table section: <code>ShopDatabase.scala</code></summary>

```scala mdoc:embed:sql-examples/src/main/scala/migratetablerowsonline/ShopDatabase.scala:show-line-numbers
```

</details>

## Backfill a Small Table in Place

When the new columns can live beside the old one for a while, we convert the rows where they are. This is the classic expand-and-contract change: we add `first_name` and `last_name` next to `full_name`, fill them in while the old code keeps writing `full_name`, and drop `full_name` when the new code starts. `SmallMigrator` does the filling, and we drive it batch by batch. We use SQLite here; the same code runs on PostgreSQL with `Dialect.Postgres`.

The table holds three customers to start with:

```scala mdoc:silent
import java.nio.file.Files

def createCustomersV1()(using DbCon): Unit =
  Frag
    .literal("CREATE TABLE customers (id BIGINT PRIMARY KEY, full_name TEXT NOT NULL, email TEXT NOT NULL)")
    .update

val dbFile     = Files.createTempFile("customers", ".db")
val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)
given Transactor = transactor
given Dialect    = Dialect.SQLite

transactor.transact {
  createCustomersV1()
  customersV1.insertBatch(
    List(
      CustomerV1(1, "Ada Lovelace", "ada@example.com"),
      CustomerV1(2, "Alan Turing", "alan@example.com"),
      CustomerV1(3, "Grace Brewster Hopper", "grace@example.com")
    )
  )
}
```

The migrators find the `Transactor` and `Dialect` as givens, so we declare both once.

### Add the New Columns

An in-place migration writes `first_name` and `last_name` into `customers`, so the table needs those columns before the first batch. A `TinyMigrator` runs schema changes like this one in a single transaction, typically at application startup. We extend it and put the statements in `run()`:

```scala mdoc:silent
class AddNameColumns(transactor: Transactor) extends TinyMigrator(transactor) {
  def run()(using DbTx): Unit = {
    Frag.literal("ALTER TABLE customers ADD COLUMN first_name TEXT").update
    Frag.literal("ALTER TABLE customers ADD COLUMN last_name TEXT").update
  }
}

AddNameColumns(transactor).migrate()
```

The new columns are nullable, so adding them rewrites no row, and the old code, which never names them, keeps working.

### Queue Every Row

The migrator converts only the rows whose keys are in the queue table. We create the queue and add the key of every existing customer:

```scala mdoc:silent
QueueTable.create[Long]("customers_split_q", transactor)
transactor.transact(QueueTable.enqueue("customers_split_q", customersV1.all.map(_.id)))
```

`QueueTable.create[Long]` takes the key type, and the queue stores each key as text. `QueueTable.enqueue` ignores keys that are already queued, so queuing the same row twice converts it once.

:::warning
Don't set `captureTriggers = true` for an in-place migration. The triggers sit on the table the migrator writes to, so every converted row queues itself again and the queue never empties. In place, the application queues its own writes, as the next part shows.
:::

### Convert the Rows While the Old Code Writes

We create the migrator with a batch size of two, so three rows take two batches:

```scala mdoc:silent
val migrator = SmallMigrator[CustomerV1, CustomerV2, Long, Long](
  repoV1 = customersV1,
  repoV2 = customersV2,
  migration = splitName,
  queueTable = "customers_split_q",
  batchSize = 2,
  target = TargetStrategy.InPlace
)
```

We call `SmallMigrator#init` once and convert the first batch:

```scala mdoc
migrator.init()
println(s"first batch: ${migrator.processBatch()} rows")
```

`SmallMigrator#processBatch` claims up to two keys, reads their rows through `customersV1`, applies `splitName` and writes the results through `customersV2`, all in one transaction.

Meanwhile, the old version of the application is still running. It signs up Edsger and changes Ada's name, and, because no trigger watches the table, it adds both keys to the queue in the same transaction as the writes:

```scala mdoc:silent
transactor.transact {
  customersV1.insert(CustomerV1(4, "Edsger Dijkstra", "edsger@example.com"))
  customersV1.update(CustomerV1(1, "Ada King", "ada@example.com"))
  QueueTable.enqueue("customers_split_q", List(4L, 1L))
}
```

Ada's row was converted in the first batch, so without the second key her `first_name` and `last_name` would keep the old name. We convert batches until the queue is empty and then call `SmallMigrator#complete`:

```scala mdoc
while (transactor.connect(QueueTable.pending("customers_split_q")) > 0)
  println(s"next batch: ${migrator.processBatch()} rows")

migrator.complete()
```

`SmallMigrator#complete` refuses to run while keys are still queued. For an in-place migration, it changes no table; for a shadow table, it swaps the tables.

:::note
The loop asks `QueueTable.pending` whether keys are left instead of stopping when `SmallMigrator#processBatch` returns `0`. The count is the number of rows written, which is `0` for a batch whose rows were deleted before it ran, even while more keys wait behind it.
:::

Every row now has its new columns filled in, Ada's from her new name:

```scala mdoc
transactor.connect {
  Frag
    .literal("SELECT id, full_name, first_name, last_name FROM customers ORDER BY id")
    .query[(Long, String, String, String)]
    .foreach(println)
}
```

### Drop the Old Column

The contract step removes `full_name`. Once it is gone, old code that writes `full_name` fails, so this step belongs to the start of the new version, after the last old process has stopped. A second `TinyMigrator` runs it, and the new code then reads `CustomerV2` rows:

```scala mdoc
class DropFullName(transactor: Transactor) extends TinyMigrator(transactor) {
  def run()(using DbTx): Unit =
    Frag.literal("ALTER TABLE customers DROP COLUMN full_name").update
}

DropFullName(transactor).migrate()

transactor.connect(customersV2.all.foreach(println))
```

`ALTER TABLE ... DROP COLUMN` needs SQLite 3.35 or newer; the `sqlite-jdbc` driver above bundles a newer one. The example deletes its database file at the end:

```scala mdoc:silent
Files.deleteIfExists(dbFile)
```

<details>
  <summary>Runnable version, with all steps of this section in one program: <code>Step3SmallTableExample.scala</code></summary>

```scala mdoc:embed:sql-examples/src/main/scala/migratetablerowsonline/Step3SmallTableExample.scala:show-line-numbers
```

</details>

## Migrate a Large Table Through a Shadow Table

When the new shape can't live beside the old one, we copy the rows into a new table and swap it in. In `CustomerV2`, `full_name` is gone and both name columns are `NOT NULL`, so the new table is `customers_v2`, built from scratch. `LargeMigrator` copies `customers` into it while the application keeps writing, uses capture triggers to queue every concurrent write, and swaps the tables with a short cutover: `LargeMigrator#fence`, `LargeMigrator#drain` and `LargeMigrator#complete`. Shadow tables need PostgreSQL; SQLite can't create them.

The overall flow looks like this:

```
 old app ──writes──► customers ──trigger──► customers_split_q ◄── INSERT ... SELECT (existing rows)
                         │                         │
                         │ read by key             │ claim a batch (SKIP LOCKED)
                         ▼                         ▼
                    LargeMigrator: splitName ──writes──► customers_v2
                                                           │
 cutover: stop writers → fence() → drain() → complete()    │ rename: customers → customers_old_v2,
                                                           ▼         customers_v2 → customers
 new app ──reads and writes──► customers (CustomerV2 shape)
```

The code in this section needs a running PostgreSQL server, so the page compiles it but doesn't run it. Each step is a method, and the output at the end of the section is from running them against PostgreSQL 16.

### Create a Large Table

The `customers` table gets 20,000 generated customers. `resetShop()` drops everything the migration leaves behind, so the example can run again:

```scala mdoc:silent
def resetShop()(using DbCon): Unit = {
  Frag.literal("DROP TABLE IF EXISTS customers, customers_v2, customers_old_v2, customers_split_q CASCADE").update
  Frag.literal("DROP FUNCTION IF EXISTS customers_split_q_notify() CASCADE").update
}

val firstNames = Vector("Ada", "Alan", "Grace", "Edsger", "Barbara", "Donald", "Margaret", "Ken")
val lastNames  = Vector("Lovelace", "Turing", "Hopper", "Dijkstra", "Liskov", "Knuth", "Hamilton", "Thompson")

def sampleCustomer(id: Long): CustomerV1 =
  CustomerV1(id, s"${firstNames(((id - 1) % 8).toInt)} ${lastNames(((id - 1) / 8 % 8).toInt)}", s"customer$id@example.com")

def createLargeTable()(using transactor: Transactor): Unit =
  transactor.transact {
    resetShop()
    createCustomersV1()
    customersV1.insertBatch((1L to 20000L).map(sampleCustomer))
  }
```

### Prepare the Shadow Table and the Queue

We create `customers_v2` with the new columns and the queue table, then create the migrator and call `LargeMigrator#init`, which installs the capture triggers on `customers`. The triggers queue only the writes that happen from now on, so we then queue the key of every existing row:

```scala mdoc:silent
def prepareShadowCopy()(using
  transactor: Transactor,
  dialect: Dialect
): LargeMigrator[CustomerV1, CustomerV2, Long, Long] = {
  transactor.transact {
    Frag
      .literal(
        "CREATE TABLE customers_v2 (id BIGINT PRIMARY KEY, first_name TEXT NOT NULL, last_name TEXT NOT NULL, email TEXT NOT NULL)"
      )
      .update
  }
  QueueTable.create[Long]("customers_split_q", transactor)

  val migrator = LargeMigrator[CustomerV1, CustomerV2, Long, Long](
    repoV1 = customersV1,
    repoV2 = customersV2,
    migration = splitName,
    queueTable = "customers_split_q",
    batchSize = 500,
    target = TargetStrategy.ShadowTable("v2"),
    captureTriggers = true
  )

  migrator.init()

  val queued = transactor.transact {
    Frag
      .literal("INSERT INTO customers_split_q (id) SELECT CAST(id AS TEXT) FROM customers ON CONFLICT DO NOTHING")
      .update
  }
  println(s"queued $queued existing customers")

  migrator
}
```

The order matters. Installing the triggers before queuing the existing rows means that no write can fall between the two: a row written in between is queued by its trigger, and `ON CONFLICT DO NOTHING` skips its key when the `INSERT ... SELECT` reaches it. We queue the existing rows with one SQL statement, because `QueueTable.enqueue` takes the keys as a Scala collection, and loading the keys of a large table into memory first is what we want to avoid. The queue stores keys as text, hence the `CAST`.

### Copy the Rows While the Application Writes

`LargeMigrator#run` claims batches and converts them until a batch writes no rows, then returns the number of rows it wrote. To show writes arriving during the copy, a second thread plays the old application: it signs up 100 new customers and changes the email address of 100 existing ones while the copy runs:

```scala mdoc:silent
def copyWhileTheAppWrites(migrator: LargeMigrator[CustomerV1, CustomerV2, Long, Long])(using
  transactor: Transactor
): Unit = {
  val writer = new Thread(() =>
    for (i <- 1L to 100L) {
      transactor.transact {
        customersV1.insert(sampleCustomer(20000L + i))
        customersV1.update(sampleCustomer(i).copy(email = s"customer$i@new-domain.example"))
      }
      Thread.sleep(5)
    }
  )
  writer.start()

  val copied = migrator.run()
  println(s"first pass copied $copied rows")

  writer.join()
  println(s"writer stopped with ${migrator.pendingCount} keys still queued")
}
```

Each write fires the trigger, which queues the row's key in the writer's transaction. An update to a row that was already copied queues it again, and the migrator copies it again over the earlier copy, so the first pass writes more rows than the table holds. A write that lands after `LargeMigrator#run` returns waits in the queue for the cutover. `LargeMigrator#pendingCount` counts the keys still queued.

`LargeMigrator#run` returning doesn't prove the queue is empty: in 0.0.56 it also returns when a batch holds only keys of rows that were deleted in the meantime (a known bug). The cutover below doesn't rely on it, because `LargeMigrator#drain` checks the queue with a count.

### Cut Over to the New Table

Cutover is the one moment the application has to cooperate. We stop every writer to `customers` (in a real application: put it into a read-only mode, or stop the old instances), then fence, drain and complete:

```scala mdoc:silent
def cutOver(migrator: LargeMigrator[CustomerV1, CustomerV2, Long, Long])(using transactor: Transactor): Unit = {
  migrator.fence()
  transactor.transact(customersV1.insert(CustomerV1(30001L, "Frances Allen", "frances@example.com")))

  val drained = migrator.drain()
  println(s"drain() applied $drained more rows")

  require(migrator.pendingCount == 0, "a write reached the queue after drain(); stop all writers first")
  migrator.complete()
}
```

`LargeMigrator#fence` only moves the migrator into its cutover state; it doesn't block writes. The insert of Frances Allen after it shows that: the trigger still queues her key, and `LargeMigrator#drain`, which converts batches until a count finds the queue empty, copies her row. `LargeMigrator#complete` then renames `customers` to `customers_old_v2` and `customers_v2` to `customers` in one transaction.

:::danger
A write that lands after `LargeMigrator#drain` returns and before `LargeMigrator#complete` runs is lost from the new table. `LargeMigrator#complete` doesn't check the queue in 0.0.56 (a known bug; `SmallMigrator#complete` does), so it swaps the tables anyway, and the row exists only in `customers_old_v2`. Stop every writer before `LargeMigrator#fence`, and check `LargeMigrator#pendingCount` right before `LargeMigrator#complete`, as `cutOver` does.
:::

### Check the Result and Clean Up

After the swap, `customers` has the new shape, and the old code can't write to it anymore. We count the rows in both tables, look up a row from each kind of write, and try an old-style insert:

```scala mdoc:silent
def checkTheSwap()(using transactor: Transactor): Unit = {
  transactor.connect {
    val oldCount = Frag.literal("SELECT COUNT(*) FROM customers_old_v2").queryOne[Long].getOrElse(0L)
    println(s"customers: ${customersV2.count} rows, customers_old_v2: $oldCount rows")
    println(s"updated during the copy: ${customersV2.find(1L)}")
    println(s"inserted during the copy: ${customersV2.find(20001L)}")
    println(s"inserted after fence(): ${customersV2.find(30001L)}")
  }

  try transactor.transact(customersV1.insert(CustomerV1(30002L, "John Backus", "john@example.com")))
  catch { case e: java.sql.SQLException => println(s"old code after the swap: ${e.getMessage.linesIterator.next()}") }
}
```

The migrator leaves the old table, the queue table and the trigger function behind, so that we can compare the tables before we drop anything. Renaming `customers` moved its trigger to `customers_old_v2`, so dropping that table drops the trigger too:

```scala mdoc:silent
def dropMigrationLeftovers()(using transactor: Transactor): Unit =
  transactor.transact {
    Frag.literal("DROP TABLE customers_old_v2").update
    Frag.literal("DROP TABLE customers_split_q").update
    Frag.literal("DROP FUNCTION customers_split_q_notify()").update
  }
```

One method runs the steps in order against the `shop` database:

```scala mdoc:silent
def migrateLargeTable(): Unit = {
  given Transactor = shopTransactor()
  given Dialect    = Dialect.Postgres

  createLargeTable()
  val migrator = prepareShadowCopy()
  copyWhileTheAppWrites(migrator)
  cutOver(migrator)
  checkTheSwap()
  dropMigrationLeftovers()
}
```

Running `migrateLargeTable()` against PostgreSQL 16 prints the following. The second to fourth lines vary from run to run, because they depend on how the writer thread and the copy interleave:

```text
queued 20000 existing customers
first pass copied 20102 rows
writer stopped with 0 keys still queued
drain() applied 1 more rows
customers: 20101 rows, customers_old_v2: 20101 rows
updated during the copy: CustomerV2(1,Ada,Lovelace,customer1@new-domain.example)
inserted during the copy: CustomerV2(20001,Ada,Liskov,customer20001@example.com)
inserted after fence(): CustomerV2(30001,Frances,Allen,frances@example.com)
old code after the swap: ERROR: column "full_name" of relation "customers" does not exist
```

Both tables hold the 20,000 original rows, the 100 sign-ups and Frances Allen. Customer 1 has the email address the writer gave them during the copy, and the old code's insert fails with a clear error instead of writing a row in the old shape. Deploy the code that reads and writes `CustomerV2` as part of the cutover.

<details>
  <summary>Runnable version, with the methods and a <code>main</code> that calls <code>migrateLargeTable()</code>: <code>Step4LargeTableExample.scala</code></summary>

```scala mdoc:embed:sql-examples/src/main/scala/migratetablerowsonline/Step4LargeTableExample.scala:show-line-numbers
```

</details>

## Recover from a Failed Batch and Restart

Production data has rows that the migration didn't expect. When the migration returns a `Left` for a row, the migrator throws, its transaction rolls back, and the keys of the whole batch stay in the queue. There is no automatic retry and no dead-letter table: we fix the cause and run again. A customer named "Cher" shows how that looks. We start a fresh SQLite database with her between two other customers:

```scala mdoc:silent:nest
val dbFile     = Files.createTempFile("customers", ".db")
val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)
given Transactor = transactor
given Dialect    = Dialect.SQLite

transactor.transact {
  createCustomersV1()
  customersV1.insertBatch(
    List(
      CustomerV1(1, "Ada Lovelace", "ada@example.com"),
      CustomerV1(2, "Cher", "cher@example.com"),
      CustomerV1(3, "Alan Turing", "alan@example.com")
    )
  )
}
AddNameColumns(transactor).migrate()
QueueTable.create[Long]("customers_split_q", transactor)
transactor.transact(QueueTable.enqueue("customers_split_q", customersV1.all.map(_.id)))
```

We migrate with `splitName` again, two keys per batch:

```scala mdoc:silent
val firstTry = SmallMigrator[CustomerV1, CustomerV2, Long, Long](
  repoV1 = customersV1,
  repoV2 = customersV2,
  migration = splitName,
  queueTable = "customers_split_q",
  batchSize = 2,
  target = TargetStrategy.InPlace
)
```

The first batch holds Ada and Cher. "Cher" has no space, so `firstSpace` is `-1` and the substring fails:

```scala mdoc
firstTry.init()
try println(s"batch: ${firstTry.processBatch()} rows")
catch { case e: RuntimeException => println(s"batch failed: ${e.getMessage}") }

transactor.connect {
  val migrated =
    Frag.literal("SELECT COUNT(*) FROM customers WHERE first_name IS NOT NULL").queryOne[Long].getOrElse(0L)
  println(s"rows migrated: $migrated, keys still queued: ${QueueTable.pending("customers_split_q")}")
}
```

Ada's row, which converted fine, rolled back with Cher's, and all three keys are still queued. Calling `SmallMigrator#processBatch` again would claim the same two keys and fail the same way, because the queue hands out keys in key order.

The fix is a migration that handles single-word names. Appending a space to the name before searching for it means there is always a space to find; a single-word name then gets an empty `lastName`:

```scala mdoc:silent
val paddedName  = SchemaExpr.stringConcat(fullName, SchemaExpr.literal(" "))
val paddedSpace = SchemaExpr.stringIndexOf(paddedName, SchemaExpr.literal(" "))

val splitNameSafely: Migration[CustomerV1, CustomerV2] =
  Migration
    .newBuilder[CustomerV1, CustomerV2]
    .addField(_.firstName, SchemaExpr.stringSubstring(paddedName, SchemaExpr.literal(0), paddedSpace))
    .addField(
      _.lastName,
      SchemaExpr.stringTrim(SchemaExpr.stringSubstring(paddedName, paddedSpace, SchemaExpr.stringLength(paddedName)))
    )
    .dropField(_.fullName, SchemaExpr.literal(""))
    .build
```

A new migrator with the fixed migration picks up the same queue:

```scala mdoc:silent
val secondTry = SmallMigrator[CustomerV1, CustomerV2, Long, Long](
  repoV1 = customersV1,
  repoV2 = customersV2,
  migration = splitNameSafely,
  queueTable = "customers_split_q",
  batchSize = 2,
  target = TargetStrategy.InPlace
)
```

It converts the three queued rows and finishes the job:

```scala mdoc
secondTry.init()
while (transactor.connect(QueueTable.pending("customers_split_q")) > 0)
  println(s"batch: ${secondTry.processBatch()} rows")
secondTry.complete()

transactor.connect(customersV2.all.foreach(println))
```

As before, the example deletes its database file at the end:

```scala mdoc:silent
Files.deleteIfExists(dbFile)
```

The same holds when the process itself dies mid-migration. The queue is a table, and a batch claims its keys and writes its rows in one transaction, so a crash leaves the keys of the unfinished batch queued and the finished batches written. After the restart, we create a new migrator with the same arguments and call `init()` again: it creates nothing twice, because the shadow table's DDL uses `CREATE TABLE IF NOT EXISTS` and the triggers `CREATE OR REPLACE TRIGGER`. A shadow table makes reruns safe, too: a row copied twice is updated, not inserted again. A `LargeMigrator` keeps its cutover state in memory, so a new one starts before `LargeMigrator#fence` and needs its own `fence()`, `drain()` and `complete()`. To stop a `LargeMigrator` on purpose, call `LargeMigrator#pause` from another thread: `LargeMigrator#run` finishes its current batch and returns, and `LargeMigrator#resume` lets the next `run()` continue.

<details>
  <summary>Runnable version, with the failing run and the fixed run in one program: <code>Step5FailureAndRestartExample.scala</code></summary>

```scala mdoc:embed:sql-examples/src/main/scala/migratetablerowsonline/Step5FailureAndRestartExample.scala:show-line-numbers
```

</details>

## Choose an Execution Model

The module has three migrators. The size of the table matters less than two other questions: does the change rewrite rows at all, and can the new columns sit beside the old ones until the old code is gone?

| Your change                                     | Use                                                 | Database              |
| ----------------------------------------------- | --------------------------------------------------- | --------------------- |
| Schema only, such as a nullable column or index | `TinyMigrator`                                      | SQLite and PostgreSQL |
| New columns beside the old ones                 | `SmallMigrator` with `TargetStrategy.InPlace`       | SQLite and PostgreSQL |
| A new shape that replaces the old one           | `LargeMigrator` with `TargetStrategy.ShadowTable`   | PostgreSQL only       |

`TinyMigrator` runs your DDL in one transaction, usually at startup, and converts no rows. `SmallMigrator` converts rows in batches that you drive with `processBatch()`, and its `complete()` refuses to run while keys are queued; in place, the application queues its own writes. `LargeMigrator` adds trigger-captured writes (`captureTriggers = true`), `run()` with `pause()` and `resume()`, the `fence()`, `drain()` and `complete()` cutover, and several workers on one PostgreSQL queue.

A few combinations fall between the rows. `SmallMigrator` also takes `TargetStrategy.ShadowTable` on PostgreSQL, when you want a shadow table but drive the batches yourself. `LargeMigrator` also takes `TargetStrategy.InPlace`, when a big in-place backfill should run with `run()`, `pause()` and `resume()`; without triggers, the application then queues its own writes, as in the small-table section. On SQLite, only in-place migrations work, with one worker.

## Putting It Together

The complete example is the large-table migration from [Migrate a Large Table Through a Shadow Table](#migrate-a-large-table-through-a-shadow-table) as one program, with one change: it uses `splitNameSafely` from [Recover from a Failed Batch and Restart](#recover-from-a-failed-batch-and-restart), so a single-word name can't stop it:

```scala mdoc:embed:sql-examples/src/main/scala/migratetablerowsonline/CompleteExample.scala
```

Running it against PostgreSQL 16 prints the same lines as the large-table section. The second to fourth lines vary from run to run:

```text
queued 20000 existing customers
first pass copied 20102 rows
writer stopped with 0 keys still queued
drain() applied 1 more rows
customers: 20101 rows, customers_old_v2: 20101 rows
updated during the copy: CustomerV2(1,Ada,Lovelace,customer1@new-domain.example)
inserted during the copy: CustomerV2(20001,Ada,Liskov,customer20001@example.com)
inserted after fence(): CustomerV2(30001,Frances,Allen,frances@example.com)
old code after the swap: ERROR: column "full_name" of relation "customers" does not exist
```

## Running the Examples

All runnable code from this guide is in [`sql-examples/src/main/scala/migratetablerowsonline/`](https://github.com/zio/zio-blocks/tree/main/sql-examples/src/main/scala/migratetablerowsonline). Run any file from that folder with `scala-cli run <File>.scala`, or from `sql-examples` with `sbt "runMain migratetablerowsonline.<ObjectName>"`. `Step4LargeTableExample` and `CompleteExample` need the PostgreSQL server from [Prerequisites](#prerequisites); stop it afterwards with `docker stop shop-db`.

## Where to Go Next

- [Data Migration](../reference/data-migration.md) is the reference for the three migrators, the target strategies and the queue primitives.
- [Migration](../reference/schema/migration.md) lists every builder operation, and [SchemaExpr](../reference/schema/schema-expr.md) every expression you can compute a new field with.
- [How to Connect to PostgreSQL with a Connection Pool](./connect-to-postgresql-with-a-connection-pool.md) builds the transactor on a HikariCP pool, which a migration running for hours next to the application should use.
- [How to Test Database Code Against SQLite](./test-database-code-against-sqlite.md) shows how to test code like the in-place backfill in a unit test.
