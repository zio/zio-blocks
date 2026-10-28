---
id: connect-to-postgresql-with-a-connection-pool
title: "How to Connect to PostgreSQL with a Connection Pool"
description: "Set up a HikariCP pool for PostgreSQL, build a JdbcTransactor on it, size it for virtual threads and close it with a Scope."
keywords:
  - "PostgreSQL"
  - "Connection Pool"
  - "HikariCP"
  - "JdbcTransactor"
  - "Virtual Threads"
---

In this guide, we connect the ZIO Blocks `sql` module to a PostgreSQL server through a HikariCP connection pool. By the end, you will have a [`JdbcTransactor`](../reference/sql/transactor.md) that borrows connections from a pool configured from environment variables, a health check, a bookstore repository running on PostgreSQL, and a pool that closes when your program is done with it.

We start from an empty build and add one piece at a time:

- Add the PostgreSQL driver and HikariCP to the build, and start a local server
- Configure the pool from the environment
- Build a `JdbcTransactor` on the pool and check that the database answers
- Run a bookstore repository on PostgreSQL
- Size the pool for many concurrent callers, including virtual threads
- Pick an isolation level and read-only mode for a report
- Close the pool with a `Scope`
- Recognise the common pool and connection errors

This guide assumes you know the basics of `Table`, `Repo` and the `sql"..."` interpolator from [Build Your First Database Repository on SQLite](./your-first-sqlite-repository.md); we don't explain them again here.

## The Problem

Opening a PostgreSQL connection is expensive: the driver opens a TCP connection, negotiates TLS if it is enabled, and authenticates, and the server starts a new backend process for it. A program that opens a fresh connection for every unit of work pays that cost every time, and under load it can open more connections than the server accepts — PostgreSQL refuses new connections once it reaches its `max_connections` setting.

For example, this is plain JDBC code that counts the books in a table:

```scala mdoc:compile-only
import java.sql.DriverManager

def countBooks(url: String, user: String, password: String): Long = {
  val connection = DriverManager.getConnection(url, user, password)
  try {
    val statement = connection.createStatement()
    try {
      val rows = statement.executeQuery("SELECT count(*) FROM book")
      rows.next()
      rows.getLong(1)
    } finally statement.close()
  } finally connection.close()
}
```

Every call to `countBooks` opens and closes a physical connection. A web server that calls it from 200 requests at once asks PostgreSQL for 200 connections. `JdbcTransactor.fromUrl` behaves the same way: it calls `DriverManager.getConnection` for every `JdbcTransactor#connect` or `JdbcTransactor#transact` and does no pooling.

A connection pool solves this. It opens a fixed number of connections up front and lends them out: "closing" a pooled connection returns it to the pool instead of disconnecting. In this guide, we let HikariCP own the connections and give ZIO Blocks a `JdbcTransactor` that borrows from it.

## Prerequisites

Add the `sql` module, the PostgreSQL JDBC driver and HikariCP to your `build.sbt`:

```scala
// Scala 3.3 LTS
libraryDependencies += "dev.zio" %% "zio-blocks-sql" % "@VERSION@"

// Scala 3.9 and newer (also needs scalacOptions += "-experimental")
libraryDependencies += "dev.zio" %% "zio-blocks-next-sql" % "@VERSION@"

// The PostgreSQL JDBC driver and the HikariCP pool, for every Scala version
libraryDependencies += "org.postgresql" % "postgresql" % "42.7.14"
libraryDependencies += "com.zaxxer"     % "HikariCP"   % "7.1.0"
```

The `sql` module is published for Scala 3 only, and `JdbcTransactor` runs on the JVM. HikariCP 7 needs Java 11 or newer. HikariCP logs through SLF4J, so add an SLF4J provider such as `slf4j-simple` or Logback if you want to see its pool messages; without one, SLF4J prints a warning at startup and drops the log lines.

To follow along, you need a PostgreSQL server. If you have Docker, this command starts PostgreSQL 16 with a `bookstore` database, user and password:

```bash
docker run --rm -d --name bookstore-db -p 5432:5432 \
  -e POSTGRES_USER=bookstore -e POSTGRES_PASSWORD=bookstore -e POSTGRES_DB=bookstore \
  postgres:16
```

The code in this guide reads the password from the `BOOKSTORE_DB_PASSWORD` environment variable, so set it in the shell that runs your program:

```bash
export BOOKSTORE_DB_PASSWORD=bookstore
```

## Configure the Pool from the Environment

A `HikariConfig` holds the pool's settings: where the database is, who we log in as, and how the pool behaves. We fill it from environment variables, so the password never appears in the source code or in version control:

```scala mdoc:silent
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

def bookstorePoolConfig(): HikariConfig = {
  val config = new HikariConfig()
  config.setJdbcUrl(sys.env.getOrElse("BOOKSTORE_DB_URL", "jdbc:postgresql://localhost:5432/bookstore"))
  config.setUsername(sys.env.getOrElse("BOOKSTORE_DB_USER", "bookstore"))
  config.setPassword(sys.env("BOOKSTORE_DB_PASSWORD"))
  config.setPoolName("bookstore")
  config.setMaximumPoolSize(10)
  config.setConnectionTimeout(5000)
  config
}
```

The code above sets the JDBC URL and user name from `BOOKSTORE_DB_URL` and `BOOKSTORE_DB_USER`, with defaults that match the Docker command. The password has no default: `sys.env("BOOKSTORE_DB_PASSWORD")` throws a `NoSuchElementException` naming the variable when it is missing, so a misconfigured deployment fails at startup. `HikariConfig#setPoolName` names the pool in log lines and error messages. `HikariConfig#setMaximumPoolSize` caps the pool at 10 connections, which is also HikariCP's default; we size it properly in [Size the Pool for Many Concurrent Callers](#size-the-pool-for-many-concurrent-callers). `HikariConfig#setConnectionTimeout` makes a caller wait at most 5 seconds for a free connection instead of the default 30 seconds.

:::tip
If you load your settings with [ZIO Blocks Config](../reference/config/index.md), as in [Build a Typed Configuration Loader](./typed-config-loader.md), read the password as a [`Secret`](../reference/config/config-source.md#secrets). `Secret#toString` is always `<secret>`, so the password can't leak into logs, and `Secret.unwrap` gives you the `String` to pass to `HikariConfig#setPassword`.
:::

Creating a `HikariDataSource` from the config starts the pool:

```scala mdoc:silent
def openBookstorePool(): HikariDataSource =
  new HikariDataSource(bookstorePoolConfig())
```

HikariCP checks the database right away: by default, the constructor opens one connection and throws if it can't, so a wrong URL or password stops your program at startup instead of at the first query. The `HikariDataSource` is a `javax.sql.DataSource`, which is all ZIO Blocks needs from it.

## Build the Transactor on the Pool

`JdbcTransactor.postgres` creates a transactor that takes its connections from a `DataSource` and speaks the PostgreSQL dialect:

```scala mdoc:silent
import javax.sql.DataSource
import zio.blocks.schema.Schema
import zio.blocks.scope.*
import zio.blocks.sql.*

def bookstoreTransactor(pool: DataSource): JdbcTransactor =
  JdbcTransactor.postgres(pool)
```

:::note
The `Schema` and `scope` imports, used later on this page, come before `import zio.blocks.sql.*` on purpose. When the `sql-zio` module is also on the classpath, `zio.blocks.sql.*` brings its subpackage `zio.blocks.sql.zio` into scope under the name `zio`, and every `import zio.blocks...` after it fails to compile. Keeping `import zio.blocks.sql.*` last avoids this.
:::

`JdbcTransactor.postgres(pool)` is shorthand for `JdbcTransactor.fromDataSource(pool, SqlDialect.PostgreSQL)` and does nothing else: it runs no setup statements on the connections and changes none of their settings. Each `JdbcTransactor#connect` or `JdbcTransactor#transact` call borrows one connection with `DataSource#getConnection` and closes it at the end of the block, which returns it to the pool.

The `JdbcTransactor` factories differ in where the connections come from:

| Factory                                                  | Connections come from                      | Use it when                                                        |
|----------------------------------------------------------|--------------------------------------------|--------------------------------------------------------------------|
| `JdbcTransactor.postgres(dataSource)`                    | The `DataSource`, so the pool              | You run on PostgreSQL with a pool; the usual production choice     |
| `JdbcTransactor.fromDataSource(dataSource, dialect)`     | The `DataSource`, so the pool              | You choose the `SqlDialect` at runtime                             |
| `JdbcTransactor.fromUrl(url, dialect)`                   | `DriverManager`, a new connection per call | Scripts, tests and one-off tools, where the URL carries everything |
| `JdbcTransactor.fromUrl(url, user, password, dialect)`   | `DriverManager`, a new connection per call | The same, with the credentials passed separately                   |

:::note
A `JdbcTransactor` doesn't own its `DataSource` and isn't `AutoCloseable`, so there is nothing to close on it. The pool stays open until you close it yourself, as we do in [Close the Pool with a Scope](#close-the-pool-with-a-scope).
:::

## Check That the Database Answers

A health check runs the cheapest possible query and reports whether it worked. `SELECT 1` needs no table, and `Frag#queryOne[Int]` returns the single value as a `Maybe[Int]`:

```scala mdoc:silent
import java.sql.SQLException

def isHealthy(transactor: JdbcTransactor): Boolean =
  try transactor.connect(sql"SELECT 1".queryOne[Int].contains(1))
  catch { case _: SQLException => false }

def serverVersion()(using DbCon): String =
  sql"SELECT version()".queryOne[String].getOrElse("unknown")
```

The code above defines two functions. `isHealthy` borrows a connection, runs `SELECT 1` and checks the answer with `Maybe#contains`. When the database is down, or the pool can't hand out a connection within the connection timeout, the JDBC call throws an `SQLException` and `isHealthy` returns `false`. `serverVersion` asks PostgreSQL for its version string, which is a quick way to confirm which server the URL points to. It takes a `DbCon` from its caller instead of a transactor, like `findByTitle` in the SQLite tutorial, so we run it with `transactor.connect(serverVersion())`.

From here on, we write the database work as functions with a `using DbCon` parameter and leave the choice of `JdbcTransactor#connect` or `JdbcTransactor#transact`, and of the transaction settings, to the caller. This shape also lets us use the work with a transactor that a `Scope` manages, as we see in [Close the Pool with a Scope](#close-the-pool-with-a-scope).

## Run the Bookstore Repository on PostgreSQL

The repository code from the SQLite tutorial works unchanged on PostgreSQL. We use the same `Book` shape:

```scala mdoc:silent
case class Book(id: Long, title: String, author: String, year: Int, isbn: Option[String])

object Book {
  given Schema[Book] = Schema.derived
}

val bookTable = Table.derived[Book]
val books     = Repo.derived[Book, Long]
```

The only thing that changes is the dialect we pass to `Table#createTable`. With `SqlDialect.PostgreSQL`, the column types are PostgreSQL types:

```scala mdoc
println(bookTable.createTable(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL))
```

`Long` becomes `BIGINT`, `Int` becomes `INTEGER` and `String` becomes `TEXT`; on SQLite, both `Long` and `Int` map to `INTEGER`. To create the table and put three books on the shelf:

```scala mdoc:silent
def stockShelf()(using DbCon): List[Book] = {
  bookTable.createTable(SqlDialect.PostgreSQL).update
  books.clear()
  books.insert(Book(1L, "Zionomicon", "John A. De Goes, Adam Fraser and Milad Khajavi", 2024, None))
  books.insert(Book(2L, "Programming in Scala", "Martin Odersky", 2008, Some("9780981531601")))
  books.insert(Book(3L, "Functional Programming in Scala", "Paul Chiusano", 2014, Some("9781617290657")))
  books.all
}

def restockShelf(transactor: JdbcTransactor): List[Book] =
  transactor.transact(stockShelf())
```

The code above defines two functions. `stockShelf` runs the `CREATE TABLE IF NOT EXISTS` statement, empties the table with `Repo#clear` so it gives the same result every time we run it, inserts three books and reads them back with `Repo#all`. `restockShelf` runs `stockShelf` in one transaction. PostgreSQL can run DDL inside a transaction, so if any insert fails, the whole block is rolled back, the `CREATE TABLE` included.

:::caution
The generated `CREATE TABLE` declares no primary key, so PostgreSQL won't stop two rows from sharing an `id`. That is fine for a demo; in production, create your tables with a migration tool and declare the keys there. See [Table](../reference/sql/table.md) for what `Table#createTable` generates.
:::

## Size the Pool for Many Concurrent Callers

`JdbcTransactor` has no thread pool of its own. Every JDBC call blocks the thread that makes it, until PostgreSQL answers. Which threads call it is up to you, and the connection pool decides how many of them can use the database at once: with `maximumPoolSize` set to 10, the eleventh concurrent caller waits for a connection to come back, for at most the connection timeout.

To see this, we run many lookups at once, each holding its connection for 0.2 seconds. The function takes any `ExecutorService`, so it works with platform threads and virtual threads alike:

```scala mdoc:silent
import java.util.concurrent.ExecutorService

def runLookups(transactor: JdbcTransactor, executor: ExecutorService, callers: Int): Long = {
  val started = System.nanoTime()
  val lookups = (1 to callers).map { _ =>
    executor.submit[Int](() => transactor.connect(sql"SELECT 1 FROM pg_sleep(0.2)".queryOne[Int].getOrElse(0)))
  }
  lookups.foreach(_.get())
  (System.nanoTime() - started) / 1000000
}
```

The code above submits one task per caller, waits for all of them and returns the elapsed milliseconds. With 50 callers and 10 connections, the lookups run in five waves of ten, so the call takes a little over one second however many threads the executor has.

Virtual threads, final since JDK 21, make this style cheap: a virtual thread that blocks on JDBC gives its carrier thread back to the JVM, so thousands of waiting callers cost little memory. On JDK 21 or newer, give each caller its own virtual thread:

```scala
val executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()
val elapsed  = runLookups(transactor, executor, callers = 50)
```

Before JDK 24, a virtual thread that blocked inside a `synchronized` block stayed pinned to its carrier thread. [JEP 491](https://openjdk.org/jeps/491) removed that limitation in JDK 24, and the PostgreSQL driver replaced its `synchronized` blocks with `ReentrantLock` in version 42.6.0, so blocking JDBC calls don't pin on any JDK that has virtual threads.

With virtual threads, threads are no longer the scarce resource; connections are. Size the pool with these rules:

- **Stay under the server limit.** The `maximumPoolSize` of every pool, across every instance of every application that uses the database, must add up to less than PostgreSQL's `max_connections` (100 by default), minus the connections it reserves for superusers. Three instances with a pool of 30 already use 90.
- **Start small.** A database server can only do as much work in parallel as its CPU cores and disks allow, so a bigger pool doesn't make it faster. HikariCP's [About Pool Sizing](https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing) page suggests `(core_count * 2) + effective_spindle_count` of the database server as a starting point, and recommends "a small pool, saturated with threads waiting for connections". Callers that wait briefly in the pool are cheaper than queries that compete for the same CPUs.
- **Keep connections short.** Every `JdbcTransactor#connect` or `JdbcTransactor#transact` block holds its connection until the block ends, so don't make HTTP calls or wait for user input inside one.

## Pick an Isolation Level and Read-Only Mode

The one-argument `JdbcTransactor#transact` keeps the driver's defaults, which on PostgreSQL is the `READ COMMITTED` isolation level with writes allowed. For a report that runs several queries, we want them all to see the same data, and we want the database to refuse accidental writes. The two-argument overload sets both:

```scala mdoc:silent
def shelfReport()(using DbCon): (Long, List[Book]) =
  (books.count, books.all)

def readShelfReport(transactor: JdbcTransactor): (Long, List[Book]) =
  transactor.transact(TransactionIsolation.RepeatableRead, readOnly = true)(shelfReport())
```

The code above:

- `TransactionIsolation.RepeatableRead` gives the transaction one snapshot of the database, so `Repo#count` and `Repo#all` agree even when other transactions insert books in between.
- `readOnly = true` makes the PostgreSQL driver start the transaction with `BEGIN READ ONLY`, so an `INSERT`, `UPDATE` or `DELETE` in the block throws an `SQLException` such as `ERROR: cannot execute INSERT in a read-only transaction` instead of changing data.
- `JdbcTransactor#transact` restores the connection's previous isolation level and read-only flag before returning it to the pool, so the next borrower gets a clean connection.

Choose `TransactionIsolation.Serializable` when a transaction must behave as if it ran alone. PostgreSQL then aborts one of two conflicting transactions with a serialization failure (SQLSTATE `40001`), which your code must retry. [SQL Transactions](./sql-transactions.md) covers every isolation level on PostgreSQL and SQLite, and nested transactions with savepoints.

## Close the Pool with a Scope

A `HikariDataSource` holds open connections and a background housekeeping thread until we call `HikariDataSource#close`. A [`Scope`](../reference/resource-management/scope.md) ties that call to a block of code: `Resource.fromAutoCloseable` turns the pool into a resource that the scope closes when the block ends, whether it ends normally or with an exception:

```scala mdoc:silent
def stockShelfWithScopedPool(): List[String] =
  Scope.global.scoped { scope =>
    import scope.*
    val transactor: $[JdbcTransactor] =
      Resource.fromAutoCloseable(openBookstorePool())
        .map(JdbcTransactor.postgres)
        .allocate
    $(transactor)(_.transact(stockShelf()).map(_.title))
  }
```

The code above:

- `Resource.fromAutoCloseable(openBookstorePool())` describes how to open the pool and registers `HikariDataSource#close` as its finalizer.
- `Resource#map(JdbcTransactor.postgres)` builds the transactor on the pool, so the scope hands us a transactor whose pool it closes for us.
- `Resource#allocate` opens the pool in this scope and returns a scoped `$[JdbcTransactor]`, which can't escape the block.
- `$(transactor)(_.transact(stockShelf()).map(_.title))` runs `stockShelf` in a transaction and returns the titles as a plain `List[String]`. When `Scope#scoped` returns, the scope closes the pool.

Inside `$(transactor)(...)`, the transactor may only be the receiver of a method call. Passing it to a function, as in `$(transactor)(t => restockShelf(t))`, doesn't compile: the compiler reports that the parameter "cannot be passed as an argument to a function or method". That is why `stockShelf` takes a `DbCon` instead of a transactor: we call `JdbcTransactor#transact` on the scoped transactor and pass the work as the block.

In a long-running service, wrap the whole application in one `Scope#scoped` block, so the pool lives exactly as long as the service. [Compile-Time Resource Safety with Scope](./compile-time-resource-safety-with-scope.md) explains how scopes nest and why a scoped value can't leak out of its block.

## Troubleshooting

Most problems show up as an exception from `HikariDataSource`'s constructor or from the first `JdbcTransactor#connect`:

- **The pool doesn't start.** The `HikariDataSource` constructor throws `HikariPool.PoolInitializationException` when it can't open its first connection, and the message names the real problem. `Failed to initialize pool: Connection to localhost:5432 refused` means nothing listens at the host and port in the URL; `Failed to initialize pool: FATAL: password authentication failed for user "bookstore"` means the credentials are wrong.
- **`Connection is not available, request timed out`.** Every connection stays in use for the whole connection timeout, and HikariCP throws an `SQLTransientConnectionException` whose message ends with the pool's state, such as `(total=1, active=1, idle=0, waiting=0)`. Either the pool is too small for the load, or connections are held too long. A common cause is calling `JdbcTransactor#connect` or `JdbcTransactor#transact` inside another such block: the inner call borrows a second connection, and with a small pool, every thread can end up holding one connection while it waits for another. Inside a transaction, use the `DbTx` you already have, as [SQL Transactions](./sql-transactions.md) shows. HikariCP's `leakDetectionThreshold` setting logs a warning with a stack trace for any connection that stays out of the pool longer than the threshold.
- **The connection must be encrypted.** The PostgreSQL driver's default `sslmode` is `prefer`, which encrypts only when the server offers TLS and never checks the server's certificate. To require TLS and verify both the certificate and the host name, add the parameters to the URL, for example `jdbc:postgresql://db.example.com:5432/bookstore?sslmode=verify-full&sslrootcert=/etc/ssl/certs/bookstore-root.crt`. `sslmode=require` encrypts without verifying the certificate. The [PostgreSQL JDBC SSL documentation](https://jdbc.postgresql.org/documentation/ssl/) lists every mode.

To make HikariCP log a warning for any connection held for more than 10 seconds, set the leak detection threshold on the config:

```scala mdoc:silent
def leakDetectingPoolConfig(): HikariConfig = {
  val config = bookstorePoolConfig()
  config.setLeakDetectionThreshold(10000)
  config
}
```

## Putting It Together

The complete example opens the pool in a scope, prints the server version, stocks the shelf and prints a read-only report:

```scala mdoc:embed:sql-examples/src/main/scala/postgrespool/CompleteExample.scala
```

## Running the Examples

All examples in this guide have runnable Scala files in the `sql-examples` project. They need a running PostgreSQL server, so start one with the Docker command from [Prerequisites](#prerequisites) and set `BOOKSTORE_DB_PASSWORD` first. Then clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/sql-examples
```

The examples share the pool settings and the `Book` repository, which live in one file:

<details>
  <summary>sql-examples/src/main/scala/postgrespool/Bookstore.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/postgrespool/Bookstore.scala:show-line-numbers
```

</details>

### Connect and Check the Database

This example opens the pool, builds the transactor, runs the health check and prints the server version.

<details>
  <summary>sql-examples/src/main/scala/postgrespool/ConnectExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/postgrespool/ConnectExample.scala:show-line-numbers
```

</details>

Run it from the `sql-examples` directory:

```bash
sbt "runMain postgrespool.ConnectExample"
```

### Run the Repository

This example creates the `book` table on PostgreSQL, stocks the shelf and lists the books.

<details>
  <summary>sql-examples/src/main/scala/postgrespool/RepositoryExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/postgrespool/RepositoryExample.scala:show-line-numbers
```

</details>

Run it from the `sql-examples` directory:

```bash
sbt "runMain postgrespool.RepositoryExample"
```

### Many Concurrent Callers

This example runs 50 lookups at once on a pool of 10 connections and prints how long they took.

<details>
  <summary>sql-examples/src/main/scala/postgrespool/ConcurrentCallersExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/postgrespool/ConcurrentCallersExample.scala:show-line-numbers
```

</details>

Run it from the `sql-examples` directory:

```bash
sbt "runMain postgrespool.ConcurrentCallersExample"
```

### Read-Only Report

This example prints a report from a `RepeatableRead`, read-only transaction, then shows the error PostgreSQL raises for a write inside one.

<details>
  <summary>sql-examples/src/main/scala/postgrespool/ReadOnlyReportExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/postgrespool/ReadOnlyReportExample.scala:show-line-numbers
```

</details>

Run it from the `sql-examples` directory:

```bash
sbt "runMain postgrespool.ReadOnlyReportExample"
```

### Pool Exhaustion

This example uses a pool of one connection and borrows a second connection inside a transaction, so it prints the timeout error from [Troubleshooting](#troubleshooting).

<details>
  <summary>sql-examples/src/main/scala/postgrespool/PoolExhaustionExample.scala</summary>

```scala mdoc:embed:sql-examples/src/main/scala/postgrespool/PoolExhaustionExample.scala:show-line-numbers
```

</details>

Run it from the `sql-examples` directory:

```bash
sbt "runMain postgrespool.PoolExhaustionExample"
```

### The Complete Example

The complete example from "Putting It Together" runs everything from this guide in one program:

```bash
sbt "runMain postgrespool.CompleteExample"
```

To compile every example without running them, which needs no database:

```bash
sbt compile
```

When you are done, stop the database container:

```bash
docker stop bookstore-db
```

## Going Further

- [Transactor](../reference/sql/transactor.md) lists every `JdbcTransactor` factory and `JdbcTransactor#transact` overload.
- [SQL Transactions](./sql-transactions.md) covers isolation levels, read-only transactions and nested transactions with savepoints.
- [SqlDialect](../reference/sql/sql-dialect.md) shows how the PostgreSQL dialect maps each column type.
- [TransactorZIO](../reference/sql/transactor-zio.md) runs the same work as ZIO effects, if your application uses ZIO.
- The [SQL Module](../reference/sql/index.md) overview links to every `sql` reference page.
