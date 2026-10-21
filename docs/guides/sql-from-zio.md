---
id: sql-from-zio
title: "How to Use the sql Module from a ZIO Application"
description: "Provide a ZIO Blocks transactor as a ZLayer, run queries off ZIO's main threads, and mix database writes with other effects in one transaction."
keywords:
  - "ZIO"
  - "ZLayer"
  - "Database Access"
  - "Transactions"
  - "TransactorZIO"
  - "sql-zio"
---

In this guide, we connect the ZIO Blocks [sql module](../reference/sql/index.md) to a ZIO application. By the end, you will have a bookstore `BookRepo` service that ZIO wires with `ZLayer`, a sale that writes to the database and calls a payment service inside one transaction, error handling with retries and timeouts, and a ZIO Test spec that gives every test its own database.

We start from a database provided as a layer, then add one capability at a time:

- A `TransactorZIO` provided as a `ZLayer`, for SQLite and for a pooled PostgreSQL database
- A ZIO service that runs its queries on ZIO's blocking thread pool
- A transaction whose body is a ZIO effect, and the rollback when that effect fails
- Typed errors, retries and timeouts with ZIO's own operators
- A ZIO Test layer that gives each test a fresh database
- Reading a large result set without a `ZStream`

This guide builds on [Build Your First Database Repository on SQLite](./your-first-sqlite-repository.md). We use `Table`, `Repo`, `DbCon` and `sql"..."` the way that tutorial introduces them, and assume you know `ZIO`, `ZLayer` and `ZIO.serviceWithZIO`.

## The Problem

The `sql` module is plain blocking Scala: `JdbcTransactor#connect` and `JdbcTransactor#transact` hold a thread until the database answers. A ZIO application runs its fibers on a small pool of threads, one per CPU core, and expects blocking work to move to a separate blocking pool. A JDBC call made on the main pool stalls every fiber scheduled behind it.

A first attempt usually wraps the transactor by hand:

```scala mdoc:compile-only
import zio.*
import zio.blocks.schema.Schema
import zio.blocks.sql.*

case class Book(id: Long, title: String, year: Int, stock: Int)
object Book { given Schema[Book] = Schema.derived }

val books      = Repo.derived[Book, Long]
val transactor = JdbcTransactor.fromUrl("jdbc:sqlite:bookstore.db", SqlDialect.SQLite)

// Runs JDBC on ZIO's main thread pool
val allBooks: Task[List[Book]] =
  ZIO.attempt(transactor.connect(books.all))

// Correct pool, but every call site has to remember it
val count: Task[Long] =
  ZIO.attemptBlocking(transactor.connect(books.count))

// No way to call another ZIO effect between two statements of one transaction
def sell(id: Long): Task[Int] =
  ZIO.attemptBlocking(transactor.transact(sql"UPDATE book SET stock = stock - 1 WHERE id = $id".update))
```

Every query needs the right wrapper, and one forgotten `ZIO.attemptBlocking` slows down the whole application without failing a test. The transaction body is synchronous, so a sale that must also charge a customer through a ZIO service can't do both in one transaction.

In this guide, we solve this with the `sql-zio` module. Its `TransactorZIO` returns ZIO effects that already run on the blocking pool, comes as a `ZLayer`, and has `TransactorZIO#transactZIO` for transactions whose body is a ZIO effect.

## Prerequisites

Add `sql-zio` and the SQLite JDBC driver to your `build.sbt`. `sql-zio` brings in the `sql` module and ZIO 2.1.27:

```scala
// Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-sql-zio" % "@VERSION@"

// Scala 3.9 and newer, which also needs -experimental
libraryDependencies += "dev.zio" %% "zio-blocks-next-sql-zio" % "@VERSION@"
scalacOptions += "-experimental"

// The SQLite JDBC driver, for every Scala version
libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.53.4.0"
```

`sql` and `sql-zio` are published for Scala 3 only and run on the JVM. Run your application on JDK 21 or newer.

We import ZIO, the ZIO integration and the `sql` module:

```scala mdoc:silent
import zio.*
import zio.blocks.schema.Schema
import zio.blocks.sql.zio.*
import zio.blocks.sql.*
```

:::warning[Put `import zio.blocks.sql.*` last]
Once `sql-zio` is on the classpath, `import zio.blocks.sql.*` brings the subpackage `zio.blocks.sql.zio` into scope under the name `zio`. After it, `zio` no longer means the root `zio` package, so every later import that starts with `zio`, such as `import zio.blocks.sql.zio.*` or `import zio.blocks.schema.Schema`, fails with `value blocks is not a member of zio.blocks.sql.zio`, and so does a fully qualified name such as `zio.Duration`. This also breaks existing files that compiled before you added `sql-zio`. Put `import zio.blocks.sql.*` after every other `zio` import, as above, or write `_root_.zio` where you need the root package after it.
:::

This page runs its effects inside mdoc, so we use a small helper that runs an effect and returns its result. In an application, `ZIOAppDefault#run` does this for you:

```scala mdoc:silent
def runDemo[A](effect: ZIO[Any, Any, A]): A =
  Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
  }
```

## The Bookstore Model

We keep a bookstore's stock in a `book` table. Each `Book` has an ID, a title, the year it was published and the copies on the shelf. The stock numbers are made up:

```scala mdoc:silent
case class Book(id: Long, title: String, year: Int, stock: Int)

object Book {
  given Schema[Book] = Schema.derived
}

val books: Repo[Book, Long] = Repo.derived[Book, Long]

val shelf = List(
  Book(1L, "Zionomicon", 2024, 5),
  Book(2L, "Programming in Scala", 2008, 3),
  Book(3L, "Functional Programming in Scala", 2014, 4)
)

def createBookTable()(using DbCon): Unit =
  sql"""CREATE TABLE book (
    id INTEGER PRIMARY KEY,
    title TEXT NOT NULL,
    year INTEGER NOT NULL,
    stock INTEGER NOT NULL
  )""".update
```

We write the `CREATE TABLE` statement by hand instead of using `Table#createTable`, because the derived statement has no `PRIMARY KEY`, as [How to Test Database Code Against SQLite](./test-database-code-against-sqlite.md#create-the-schema-from-code) explains. `createBookTable` takes a `(using DbCon)`, so it runs inside any `TransactorZIO#connect` or `TransactorZIO#transact` block.

## Provide the Database as a ZLayer

ZIO applications get their dependencies from layers, so the database is a layer too. `TransactorZIO.layer` takes a JDBC URL and a dialect and returns a `ZLayer[Any, Nothing, TransactorZIO]`. We point it at a temporary SQLite file:

```scala mdoc:silent
val dbFile = java.nio.file.Files.createTempFile("bookstore", ".db")
dbFile.toFile.deleteOnExit()

val transactorLayer: ZLayer[Any, Nothing, TransactorZIO] =
  TransactorZIO.layer(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)
```

The layer can't fail because it opens nothing: `TransactorZIO` opens a new JDBC connection for each `TransactorZIO#connect` or `TransactorZIO#transact` call and closes it afterwards. A wrong URL shows up at the first query, not at startup.

:::warning
Don't use `jdbc:sqlite::memory:` here. Each new connection to `:memory:` gets its own empty database, so the table we create in one call is gone in the next. Use a file, as above.
:::

We create the table and put the books on the shelf. `TransactorZIO#connect` runs its synchronous body on ZIO's blocking thread pool and returns a `Task`:

```scala mdoc
runDemo {
  ZIO.serviceWithZIO[TransactorZIO] { transactor =>
    transactor.connect {
      createBookTable()
      shelf.foreach(books.insert)
      books.count
    }
  }.provide(transactorLayer)
}
```

In production, a PostgreSQL database is usually reached through a connection pool. A pool is a `javax.sql.DataSource` that ZIO opens at startup and closes at shutdown, which is what `ZLayer.scoped` with `ZIO.acquireRelease` expresses. `TransactorZIO.fromDataSource` then builds the transactor on the pool. The pool's settings are the ones from [How to Connect to PostgreSQL with a Connection Pool](./connect-to-postgresql-with-a-connection-pool.md):

```scala mdoc:compile-only
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import javax.sql.DataSource

def poolConfig(): HikariConfig = {
  val config = new HikariConfig()
  config.setJdbcUrl(sys.env.getOrElse("BOOKSTORE_DB_URL", "jdbc:postgresql://localhost:5432/bookstore"))
  config.setUsername(sys.env.getOrElse("BOOKSTORE_DB_USER", "bookstore"))
  config.setPassword(sys.env("BOOKSTORE_DB_PASSWORD"))
  config.setMaximumPoolSize(10)
  config
}

val poolLayer: ZLayer[Any, Throwable, DataSource] =
  ZLayer.scoped {
    ZIO.acquireRelease(ZIO.attemptBlocking(new HikariDataSource(poolConfig())))(pool =>
      ZIO.attemptBlocking(pool.close()).orDie
    )
  }

val postgresTransactorLayer: ZLayer[DataSource, Nothing, TransactorZIO] =
  ZLayer.fromFunction((pool: DataSource) => TransactorZIO.fromDataSource(pool, SqlDialect.PostgreSQL))

val databaseLayer: ZLayer[Any, Throwable, TransactorZIO] =
  poolLayer >>> postgresTransactorLayer
```

`HikariDataSource` connects in its constructor, so `databaseLayer` fails at startup when the database is unreachable, and the pool closes when the application stops. The rest of this guide only depends on `TransactorZIO`, so swapping `transactorLayer` for `databaseLayer` is the only change it needs to run on PostgreSQL.

`sql-zio` also adds `JdbcTransactor.postgresLayer` and `JdbcTransactor.sqliteLayer`, which turn a `DataSource` layer into a layer of the synchronous `Transactor`. They are extension methods defined in `zio.blocks.sql.zio`, so they need `import zio.blocks.sql.zio.*`. A `Transactor` runs its body on the calling thread, so you would have to wrap each call in `ZIO.attemptBlocking` yourself; prefer `TransactorZIO` in ZIO code.

## Write a ZIO Service on the Transactor

The rest of the application shouldn't see SQL. We hide it behind a `BookRepo` service whose methods return ZIO effects:

```scala mdoc:silent
trait BookRepo {
  def all: Task[List[Book]]
  def find(id: Long): Task[Option[Book]]
  def add(book: Book): Task[Unit]
}

final case class BookRepoLive(transactor: TransactorZIO) extends BookRepo {
  def all: Task[List[Book]] =
    transactor.connect(books.all)

  def find(id: Long): Task[Option[Book]] =
    transactor.connect(books.find(id).toOption)

  def add(book: Book): Task[Unit] =
    transactor.transact(books.insert(book)).unit
}

object BookRepoLive {
  val layer: ZLayer[TransactorZIO, Nothing, BookRepo] =
    ZLayer.fromFunction(BookRepoLive(_))
}
```

`BookRepoLive` takes the transactor as a constructor parameter, and `ZLayer.fromFunction` turns that constructor into a layer that needs a `TransactorZIO`. Each method is one `TransactorZIO#connect` or `TransactorZIO#transact` call, so each one runs on the blocking pool and fails with the JDBC exception as a `Task` error. `TransactorZIO#transact` turns off auto-commit, commits when its body returns and rolls back when it throws.

Callers ask ZIO for the service and provide both layers:

```scala mdoc
runDemo {
  ZIO.serviceWithZIO[BookRepo] { repo =>
    repo.add(Book(4L, "Scala with Cats", 2020, 2)) *> repo.all
  }.provide(BookRepoLive.layer, transactorLayer)
}.foreach(println)
```

## Mix Database Writes with Other Effects in One Transaction

`TransactorZIO#transact` is enough while the whole transaction is SQL. A sale is not: it lowers the stock and charges the customer, and the charge is a ZIO service. If the charge fails, the stock change must be undone. That calls for `TransactorZIO#transactZIO`, whose body is a ZIO effect: it opens a connection, turns off auto-commit, runs the effect, then commits if the effect succeeds and rolls back if it fails.

We model the payment provider as a ZIO service that declines one customer:

```scala mdoc:silent
final case class PaymentDeclined(customer: String) extends Exception(s"Payment declined for $customer")
final case class OutOfStock(bookId: Long)          extends Exception(s"Book $bookId is out of stock")

trait Payments {
  def charge(customer: String, copies: Int): IO[PaymentDeclined, Unit]
}

val paymentsLayer: ZLayer[Any, Nothing, Payments] =
  ZLayer.succeed(new Payments {
    def charge(customer: String, copies: Int): IO[PaymentDeclined, Unit] =
      if (customer == "mallory") ZIO.fail(PaymentDeclined(customer)) else ZIO.unit
  })
```

The sale lowers the stock, fails with `OutOfStock` if there weren't enough copies, then charges the customer, all inside one `TransactorZIO#transactZIO`:

```scala mdoc:silent
def sell(bookId: Long, copies: Int, customer: String): ZIO[TransactorZIO & Payments, Throwable, Unit] =
  ZIO.serviceWithZIO[TransactorZIO] { transactor =>
    transactor.transactZIO {
      for {
        updated <- ZIO.attemptBlocking(
                     sql"UPDATE book SET stock = stock - $copies WHERE id = $bookId AND stock >= $copies".update
                   )
        _       <- ZIO.fail(OutOfStock(bookId)).when(updated == 0)
        _       <- ZIO.serviceWithZIO[Payments](_.charge(customer, copies))
      } yield ()
    }
  }

def stockOf(bookId: Long): ZIO[TransactorZIO, Throwable, Int] =
  ZIO.serviceWithZIO[TransactorZIO](
    _.connect(sql"SELECT stock FROM book WHERE id = $bookId".queryOne[Int].getOrElse(0))
  )
```

Alice buys two copies of Zionomicon and Mallory tries to buy one:

```scala mdoc
runDemo {
  (for {
    before  <- stockOf(1L)
    alice   <- sell(1L, 2, "alice").either
    mallory <- sell(1L, 1, "mallory").either
    after   <- stockOf(1L)
  } yield {
    println(s"Stock before: $before")
    println(s"Alice: $alice")
    println(s"Mallory: ${mallory.left.map(_.getMessage)}")
    println(s"Stock after: $after")
  }).provide(transactorLayer, paymentsLayer)
}
```

Alice's sale committed and took two copies. Mallory's `UPDATE` ran, but the declined charge failed the body, so `TransactorZIO#transactZIO` rolled the `UPDATE` back and the stock only dropped by Alice's two copies. The error type is `Throwable` here because both of our errors extend `Exception`; in general `TransactorZIO#transactZIO` returns `ZIO[R, E | Throwable, A]`, adding `Throwable` for the JDBC errors to whatever error type the body has.

Inside `TransactorZIO#transactZIO`, wrap every statement in `ZIO.attemptBlocking`. `TransactorZIO#transactZIO` runs only the connection setup, the commit and the rollback on the blocking pool, and the body's effect runs on whatever thread the fiber is on. `ZIO.attemptBlocking` moves the statement to the blocking pool and turns its exception into a typed failure that triggers the rollback.

:::danger[Two ways `TransactorZIO#transactZIO` 0.0.56 commits instead of rolling back]
A statement written directly in the body, such as `transactZIO { sql"...".update; ZIO.unit }`, runs while the body's effect is being built, before `TransactorZIO#transactZIO` watches it for failures. If such a statement throws, `TransactorZIO#transactZIO` does not roll back, and the statements before it in the same body are committed. Wrapping each statement in `ZIO.attemptBlocking`, as above, avoids this.

Interrupting a running `TransactorZIO#transactZIO` from outside, for example with `ZIO#timeout`, `ZIO#race` or by interrupting its fiber, also commits the statements that already ran. Don't put `ZIO#timeout` around a `TransactorZIO#transactZIO`; put a time limit on the slow effect inside the body with `ZIO#timeoutFail`, which fails the body and so rolls the transaction back, as shown in [Limit How Long an Effect Waits](#limit-how-long-an-effect-waits).
:::

`TransactorZIO#transactZIO` keeps the driver's default isolation level. When a transaction needs a specific one, pass it with the two-argument overload, `transactor.transactZIO(TransactionIsolation.Serializable, readOnly = false) { ... }`. [SQL Transactions: Isolation, Read-Only and Savepoints](./sql-transactions.md) explains the levels.

Operations that belong to one transaction must also share its connection: every `TransactorZIO#connect`, `TransactorZIO#transact`, `TransactorZIO#connectZIO` and `TransactorZIO#transactZIO` call takes its own connection. Calling one from inside another, for example a service method that calls `BookRepo#add` while it is inside its own `TransactorZIO#transactZIO`, opens a second connection while the first one still holds its locks. On SQLite, the inner write waits for the outer transaction and fails with `SQLITE_BUSY` after the busy timeout; on PostgreSQL, enough concurrent callers doing this use up the pool and wait for each other.

Instead, write the operations that should share a transaction as functions with a `(using DbCon)` parameter, like `createBookTable` above, and call them inside one `TransactorZIO#transactZIO`:

```scala mdoc:silent
def restock(bookId: Long, copies: Int)(using DbCon): Int =
  sql"UPDATE book SET stock = stock + $copies WHERE id = $bookId".update

def restockAll(copies: Int): ZIO[TransactorZIO, Throwable, Int] =
  ZIO.serviceWithZIO[TransactorZIO](_.transactZIO {
    ZIO.foreach(List(1L, 2L, 3L))(bookId => ZIO.attemptBlocking(restock(bookId, copies))).map(_.sum)
  })
```

Both `restock` calls use the `DbTx` that `TransactorZIO#transactZIO` passes to its body, so they run on one connection and commit together.

## Handle Errors, Retries and Timeouts the ZIO Way

Every `TransactorZIO` method returns an ordinary ZIO effect, so ZIO's error operators work on database calls like on any other effect. This section shows the three you need most.

### Turn Database Errors into Domain Errors

A JDBC failure arrives as a `java.sql.SQLException` in the error channel. Callers usually care about a few specific cases, such as a duplicate key, and should see them as their own error type. With SQLite, `SQLiteException#getResultCode` tells the cases apart; on PostgreSQL, check `SQLException#getSQLState`, which is `"23505"` for a unique violation:

```scala mdoc:silent
import org.sqlite.{SQLiteErrorCode, SQLiteException}

final case class DuplicateBook(id: Long) extends Exception(s"Book $id already exists")

def addNew(book: Book): ZIO[BookRepo, Throwable, Unit] =
  ZIO.serviceWithZIO[BookRepo](_.add(book)).catchSome {
    case e: SQLiteException if e.getResultCode == SQLiteErrorCode.SQLITE_CONSTRAINT_PRIMARYKEY =>
      ZIO.fail(DuplicateBook(book.id))
  }
```

Adding Zionomicon a second time fails with the domain error, not the driver's exception:

```scala mdoc
runDemo {
  addNew(Book(1L, "Zionomicon", 2024, 1)).either.provide(BookRepoLive.layer, transactorLayer)
}
```

### Retry Transient Failures

Some failures go away on their own: the database is restarting, the network dropped a connection, or another writer held a lock. `ZIO#retry` reruns the effect with a `Schedule`. Retrying a whole `TransactorZIO#connect` or `TransactorZIO#transact` is safe, because each attempt takes a new connection and a failed `TransactorZIO#transact` has already rolled back.

To see a retry without a real outage, we build a `TransactorZIO` whose connection factory fails twice before it connects, the way a database that is still starting up would:

```scala mdoc:silent
val attempts = new java.util.concurrent.atomic.AtomicInteger(0)

val startingUp = new TransactorZIO(
  () =>
    if (attempts.incrementAndGet() <= 2) throw new java.sql.SQLException("Connection refused")
    else java.sql.DriverManager.getConnection(s"jdbc:sqlite:$dbFile"),
  SqlDialect.SQLite
)
```

We retry up to five times, waiting 10 milliseconds before the first retry and twice as long before each next one:

```scala mdoc
runDemo {
  startingUp
    .connect(books.count)
    .retry(Schedule.exponential(10.millis) && Schedule.recurs(5))
    .map(count => println(s"Counted $count books after ${attempts.get} attempts"))
}
```

Retry only what is safe to repeat. A `TransactorZIO#transactZIO` body that calls a payment service would charge the customer again on every retry; retry the database part inside the body instead, or make the charge idempotent.

### Limit How Long an Effect Waits

`ZIO#timeout` interrupts an effect that takes too long, but interruption can't stop a JDBC call that is already running. `TransactorZIO` runs statements with `ZIO.attemptBlocking`, which doesn't interrupt its thread, so the fiber waits until the statement finishes and only then reports the timeout. We time a query that counts to three million inside SQLite, with a timeout of 10 milliseconds:

```scala mdoc:silent
val slowCount =
  sql"WITH RECURSIVE n(x) AS (SELECT 1 UNION ALL SELECT x + 1 FROM n WHERE x < 3000000) SELECT count(*) FROM n"

def timed[A](effect: Task[A]): Task[(A, Duration)] =
  effect.timed.map(_.swap)
```

The timeout reports `None`, but only after the whole query ran:

```scala mdoc
runDemo {
  ZIO.serviceWithZIO[TransactorZIO] { transactor =>
    timed(transactor.connect(slowCount.queryOne[Long]).timeout(10.millis)).map { case (result, elapsed) =>
      println(s"Result: $result")
      println(s"Waited longer than the timeout: ${elapsed.toMillis > 100}")
    }
  }.provide(transactorLayer)
}
```

When the caller must get its answer on time, add `ZIO#disconnect` before the timeout. The fiber then stops waiting at the deadline, while the statement finishes in the background and still holds its connection until it does:

```scala mdoc
runDemo {
  ZIO.serviceWithZIO[TransactorZIO] { transactor =>
    timed(transactor.connect(slowCount.queryOne[Long]).disconnect.timeout(10.millis)).map { case (result, elapsed) =>
      println(s"Result: $result")
      println(s"Returned before the query finished: ${elapsed.toMillis < 100}")
    }
  }.provide(transactorLayer)
}
```

To stop the statement itself, set a limit in the database, such as PostgreSQL's `statement_timeout` on the pool's connections.

Inside a `TransactorZIO#transactZIO`, put the time limit on the non-database effect with `ZIO#timeoutFail`. The timeout becomes a typed failure of the body, and the transaction rolls back. Here the payment provider hangs, so the sale fails after 50 milliseconds and the stock stays as it was:

```scala mdoc:silent
val hangingPayments: ZLayer[Any, Nothing, Payments] =
  ZLayer.succeed(new Payments {
    def charge(customer: String, copies: Int): IO[PaymentDeclined, Unit] = ZIO.never
  })

def sellWithin(bookId: Long, copies: Int, customer: String): ZIO[TransactorZIO & Payments, Throwable, Unit] =
  ZIO.serviceWithZIO[TransactorZIO] { transactor =>
    transactor.transactZIO {
      for {
        _ <- ZIO.attemptBlocking(sql"UPDATE book SET stock = stock - $copies WHERE id = $bookId".update)
        _ <- ZIO
               .serviceWithZIO[Payments](_.charge(customer, copies))
               .timeoutFail(new java.util.concurrent.TimeoutException("Payment took too long"))(50.millis)
      } yield ()
    }
  }
```

Bob's payment never answers:

```scala mdoc
runDemo {
  (for {
    before <- stockOf(2L)
    result <- sellWithin(2L, 1, "bob").either
    after  <- stockOf(2L)
  } yield {
    println(s"Sale: $result")
    println(s"Stock before: $before, after: $after")
  }).provide(transactorLayer, hangingPayments)
}
```

## Test Services Against a Fresh Database

A ZIO Test spec gets its services from layers, so a test database is a layer that creates a temporary SQLite file, builds the schema and seeds the books, and deletes the file when the test ends. `ZLayer.scoped` with `ZIO.acquireRelease` ties the file's lifetime to the layer:

```scala mdoc:silent
import java.nio.file.{Files, Path}

val testTransactorLayer: ZLayer[Any, Throwable, TransactorZIO] =
  ZLayer.scoped {
    for {
      file      <- ZIO.acquireRelease(ZIO.attemptBlocking(Files.createTempFile("bookstore-test", ".db")))(file =>
                     ZIO.attemptBlocking(Files.deleteIfExists(file)).orDie
                   )
      transactor = TransactorZIO.fromUrl(s"jdbc:sqlite:$file", SqlDialect.SQLite)
      _         <- transactor.connect {
                     createBookTable()
                     shelf.foreach(books.insert)
                   }
    } yield transactor
  }
```

Each time ZIO builds the layer, it gets a new file. Two "tests" that each add a book both start from the same three books:

```scala mdoc
List("first test", "second test").foreach { name =>
  val size = runDemo {
    ZIO.serviceWithZIO[BookRepo] { repo =>
      repo.add(Book(10L, "Scala with Cats", 2020, 2)) *> repo.all.map(_.size)
    }.provide(BookRepoLive.layer, testTransactorLayer)
  }
  println(s"$name sees $size books")
}
```

In a spec, `Spec#provide` builds the layer once for each test, so every test gets its own database, and `Spec#provideShared` builds it once for the whole suite. The companion spec below uses `Spec#provide`, so its tests can run in any order and in parallel:

<details>
<summary><code>BookRepoSpec.scala</code></summary>

```scala mdoc:embed:sql-examples/src/test/scala/sqlfromzio/BookRepoSpec.scala:show-line-numbers
```

</details>

[How to Test Database Code Against SQLite](./test-database-code-against-sqlite.md) covers the same ideas for the synchronous API, including fixtures and rollback-only tests.

## Read a Large Result Set

`Frag#queryStream` and `Frag#queryChunked` read a result set in chunks as a ZIO Blocks `Stream`, without loading every row into memory. `sql-zio` has no bridge to `ZStream`, and the stream must be consumed while its connection is open. So we run it inside `TransactorZIO#connect` and reduce it there. A terminal operation such as `Stream#runFold` returns an `Either`, and `ZIO#absolve` turns its `Left` into a failure:

```scala mdoc
runDemo {
  ZIO.serviceWithZIO[TransactorZIO] { transactor =>
    transactor.connect {
      sql"SELECT id, title, year, stock FROM book"
        .queryChunked[Book](2)
        .runFold(0)((total, chunk) => total + chunk.foldLeft(0)(_ + _.stock))
    }.absolve
  }.provide(transactorLayer)
}
```

The fold reads two rows at a time and adds up the stock across the shelf. The whole fold runs inside `TransactorZIO#connect`, so it runs on the blocking pool. When each row needs a ZIO effect, such as publishing it to a queue, collect the rows you need inside `TransactorZIO#connect` and run the effects after it returns, or read the table in pages with `Repo#pageAfter`.

## Putting It Together

The complete example below builds the bookstore application with `ZIOAppDefault`. It provides the transactor, the `BookRepo` service and the payment service as layers, sells books in `TransactorZIO#transactZIO` transactions, and prints the stock after a declined payment:

<details>
<summary><code>CompleteExample.scala</code></summary>

```scala mdoc:embed:sql-examples/src/main/scala/sqlfromzio/CompleteExample.scala:show-line-numbers
```

</details>

## Running the Examples

The companion examples live in the `sql-examples` build, which uses the published ZIO Blocks artifacts. Clone the repository and change into it:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/sql-examples
```

Every example uses a temporary SQLite file and deletes it when it exits, so you can run them in any order.

The examples share the bookstore model from [The Bookstore Model](#the-bookstore-model), defined once in `Bookstore.scala`:

<details>
<summary><code>Bookstore.scala</code>: the shared <code>Book</code> model, <code>books</code>, <code>shelf</code>, <code>createBookTable</code> and <code>stockOf</code></summary>

```scala mdoc:embed:sql-examples/src/main/scala/sqlfromzio/Bookstore.scala:show-line-numbers
```

</details>

To check that every example compiles without running any of them:

```bash
sbt compile
```

<details>
<summary><code>BookRepoLayerExample.scala</code>: provide the transactor and the <code>BookRepo</code> service as layers</summary>

```scala mdoc:embed:sql-examples/src/main/scala/sqlfromzio/BookRepoLayerExample.scala:show-line-numbers
```

</details>

Run it with:

```bash
sbt "runMain sqlfromzio.BookRepoLayerExample"
```

<details>
<summary><code>TransactZIOExample.scala</code>: a sale that charges the customer inside the transaction, and the rollback on a declined payment</summary>

```scala mdoc:embed:sql-examples/src/main/scala/sqlfromzio/TransactZIOExample.scala:show-line-numbers
```

</details>

Run it with:

```bash
sbt "runMain sqlfromzio.TransactZIOExample"
```

<details>
<summary><code>ErrorsRetriesTimeoutsExample.scala</code>: domain errors, retries and timeouts</summary>

```scala mdoc:embed:sql-examples/src/main/scala/sqlfromzio/ErrorsRetriesTimeoutsExample.scala:show-line-numbers
```

</details>

Run it with:

```bash
sbt "runMain sqlfromzio.ErrorsRetriesTimeoutsExample"
```

<details>
<summary><code>LargeResultExample.scala</code>: fold a chunked query inside <code>connect</code></summary>

```scala mdoc:embed:sql-examples/src/main/scala/sqlfromzio/LargeResultExample.scala:show-line-numbers
```

</details>

Run it with:

```bash
sbt "runMain sqlfromzio.LargeResultExample"
```

The complete example runs with:

```bash
sbt "runMain sqlfromzio.CompleteExample"
```

The ZIO Test spec runs with:

```bash
sbt "testOnly sqlfromzio.BookRepoSpec"
```

## Going Further

- [TransactorZIO](../reference/sql/transactor-zio.md) lists every method of `TransactorZIO` and its companion.
- [ZIO Integration](../reference/sql-zio.md) is the reference page for the `sql-zio` module.
- [How to Connect to PostgreSQL with a Connection Pool](./connect-to-postgresql-with-a-connection-pool.md) sizes the pool that `databaseLayer` opens.
- [SQL Transactions: Isolation, Read-Only and Savepoints](./sql-transactions.md) covers isolation levels and nested transactions with savepoints.
- [How to Test Database Code Against SQLite](./test-database-code-against-sqlite.md) shows fixtures and rollback-only tests.
