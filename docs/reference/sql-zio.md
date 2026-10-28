---
id: sql-zio
title: "ZIO Integration"
---

`zio-blocks-sql-zio` is the ZIO adapter for the [`sql` module](./sql/index.md). It lets a ZIO application use the same `sql"..."` fragments, `Repo` and `Table` values as a plain Scala program, with every JDBC call moved off ZIO's main thread pool.

The module offers two integration styles. The `TransactorZIO` class wraps the synchronous transactor in methods that return ZIO effects; it is the one to use in ZIO code. The `JdbcTransactor.postgresLayer` and `JdbcTransactor.sqliteLayer` extension methods build a `ZLayer` of the synchronous `Transactor` from a `DataSource`, for code that already works with `Transactor`. This page gives an overview of both; [TransactorZIO](./sql/transactor-zio.md) documents each method, and [How to Use the sql Module from a ZIO Application](../guides/sql-from-zio.md) builds a complete ZIO service on it.

## Installation

Add `sql-zio` to your `build.sbt`. It brings in the `sql` module and ZIO:

```scala
// Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-sql-zio" % "@VERSION@"

// Scala 3.9 and newer, which also needs -experimental
libraryDependencies += "dev.zio" %% "zio-blocks-next-sql-zio" % "@VERSION@"
scalacOptions += "-experimental"
```

`sql` and `sql-zio` are published for Scala 3 only and run on the JVM. You also need the JDBC driver of your database; the examples on this page use SQLite (`"org.xerial" % "sqlite-jdbc"`).

## Imports

The examples import ZIO, the ZIO integration and the `sql` module, in this order:

```scala mdoc:silent
import zio._
import zio.blocks.schema.Schema
import zio.blocks.sql.zio._
import zio.blocks.sql._
```

:::warning[Put `import zio.blocks.sql._` last]
With `sql-zio` on the classpath, `zio.blocks.sql` has a subpackage named `zio`: this module's package, `zio.blocks.sql.zio`. After `import zio.blocks.sql._`, the name `zio` means that subpackage instead of the root `zio` package, so any later import or name that starts with `zio`, such as `import zio.blocks.schema.Schema`, fails with `value blocks is not a member of zio.blocks.sql.zio`. Put `import zio.blocks.sql._` after every other `zio` import, or write `_root_.zio` where you need the root package after it.
:::

We run the effects on this page with a small helper; in an application, `ZIOAppDefault#run` does this for you:

```scala mdoc:silent
def runDemo[A](effect: ZIO[Any, Any, A]): A =
  Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
  }
```

## Quick Start

`TransactorZIO.fromUrl` takes a JDBC URL and a dialect. We point it at a temporary SQLite file and derive a repository for a `Book` record:

```scala mdoc:silent
case class Book(id: Long, title: String, year: Int)

object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

val books: Repo[Book, Long] = Repo.derived[Book, Long]

val dbFile = java.nio.file.Files.createTempFile("library", ".db")
dbFile.toFile.deleteOnExit()

val transactor: TransactorZIO = TransactorZIO.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)
```

`TransactorZIO#connect` and `TransactorZIO#transact` take the same synchronous body as `JdbcTransactor#connect` and `JdbcTransactor#transact`, run it on ZIO's blocking thread pool and return a `Task`. We create the table, insert two books in one transaction, then read them back:

```scala mdoc
runDemo {
  for {
    _   <- transactor.connect {
             sql"CREATE TABLE book (id INTEGER PRIMARY KEY, title TEXT NOT NULL, year INTEGER NOT NULL)".update
           }
    _   <- transactor.transact {
             books.insert(Book(1L, "Zionomicon", 2024))
             books.insert(Book(2L, "Programming in Scala", 2008))
           }
    all <- transactor.connect(books.all)
  } yield all
}
```

The table is written by hand because `Table#createTable` declares no `PRIMARY KEY` (see [Table](./sql/table.md#createtable--generate-a-create-table-statement)).

:::warning
Don't use `jdbc:sqlite::memory:` with `TransactorZIO.fromUrl`. It opens a new connection for every `TransactorZIO#connect`, `TransactorZIO#transact`, `TransactorZIO#connectZIO` and `TransactorZIO#transactZIO` call, and each new connection to `:memory:` gets its own empty database, so the table created in one call is gone in the next. Use a file, as above, or a pooled `DataSource` with `TransactorZIO.fromDataSource`.
:::

## Effect-Aware Methods

`TransactorZIO#connectZIO` and `TransactorZIO#transactZIO` take a body that returns a `ZIO` effect, so a transaction can include calls to other ZIO services. `TransactorZIO#transactZIO` commits when the effect succeeds and rolls back when it fails.

Only the connection setup, the commit and the rollback run on the blocking pool; the body's effect runs on the fiber's own thread. Wrap every statement in the body in `ZIO.attemptBlocking`, which moves it to the blocking pool and turns its exception into a failure that triggers the rollback:

```scala mdoc
runDemo {
  for {
    added <- transactor.transactZIO {
               for {
                 _    <- ZIO.attemptBlocking(books.insert(Book(3L, "Functional Programming in Scala", 2014)))
                 book <- ZIO.attemptBlocking(books.find(3L))
               } yield book
             }
    failed <- transactor.transactZIO {
                ZIO.attemptBlocking(books.insert(Book(4L, "Unpublished", 2030))) *>
                  ZIO.fail(new RuntimeException("payment declined"))
              }.either
    count <- transactor.connect(books.count)
  } yield (added, failed.left.map(_.getMessage), count)
}
```

The second transaction inserted a book, then failed, so `TransactorZIO#transactZIO` rolled the insert back and the table holds three books.

:::danger[Two ways `TransactorZIO#transactZIO` commits instead of rolling back]
In 0.0.56, a known bug makes `TransactorZIO#transactZIO` commit, not roll back, in two cases. A statement written directly in the body, outside `ZIO.attemptBlocking`, runs while the body's effect is being built; if it throws, the statements before it are committed. And interrupting a running `TransactorZIO#transactZIO` from outside, for example with `ZIO#timeout` or `ZIO#race`, commits the statements that already ran. Wrap every statement in `ZIO.attemptBlocking`, and put time limits inside the body with `ZIO#timeoutFail` instead of around the transaction, as [How to Use the sql Module from a ZIO Application](../guides/sql-from-zio.md#mix-database-writes-with-other-effects-in-one-transaction) shows.
:::

## Providing a Transactor as a ZLayer

Both integration styles can come from a `ZLayer`, so the rest of the application only names the service it needs.

### `TransactorZIO.layer`

`TransactorZIO.layer` builds a `ZLayer[Any, Nothing, TransactorZIO]` from a JDBC URL and a dialect. Code that needs the database reads `TransactorZIO` from its environment:

```scala mdoc:silent
val transactorLayer: ZLayer[Any, Nothing, TransactorZIO] =
  TransactorZIO.layer(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

val titles: ZIO[TransactorZIO, Throwable, List[String]] =
  ZIO.serviceWithZIO[TransactorZIO](_.connect(books.all.map(_.title)))
```

Providing the layer gives an effect that needs nothing else:

```scala mdoc
runDemo(titles.provide(transactorLayer))
```

For a connection pool, build the transactor with `TransactorZIO.fromDataSource` inside a layer, as [How to Use the sql Module from a ZIO Application](../guides/sql-from-zio.md#provide-the-database-as-a-zlayer) does with HikariCP.

### `JdbcTransactor.postgresLayer` and `JdbcTransactor.sqliteLayer`

`JdbcTransactor.postgresLayer` and `JdbcTransactor.sqliteLayer` turn a layer of a `javax.sql.DataSource` into a layer of the synchronous [`Transactor`](./sql/transactor.md), with the PostgreSQL or SQLite dialect. They are extension methods defined in `zio.blocks.sql.zio`, so they need `import zio.blocks.sql.zio._`.

A `Transactor` runs its body on the calling thread, so each call must be wrapped in `ZIO.attemptBlocking`; otherwise the JDBC call blocks one of ZIO's main threads. Here the `DataSource` is SQLite's own `SQLiteDataSource`:

```scala mdoc:silent
val dataSourceLayer: ZLayer[Any, Nothing, javax.sql.DataSource] =
  ZLayer.succeed {
    val dataSource = new org.sqlite.SQLiteDataSource()
    dataSource.setUrl(s"jdbc:sqlite:$dbFile")
    dataSource: javax.sql.DataSource
  }

val syncTransactorLayer: ZLayer[Any, Nothing, Transactor] =
  dataSourceLayer >>> JdbcTransactor.sqliteLayer

val bookCount: ZIO[Transactor, Throwable, Long] =
  ZIO.serviceWithZIO[Transactor](transactor => ZIO.attemptBlocking(transactor.connect(books.count)))
```

The effect counts the books through the synchronous transactor:

```scala mdoc
runDemo(bookCount.provide(syncTransactorLayer))
```

Prefer `TransactorZIO` in ZIO code: it does the `ZIO.attemptBlocking` wrapping for you.

## Thread Safety

`TransactorZIO` holds no mutable state and is safe to share between fibers. It opens a new connection for each `TransactorZIO#connect`, `TransactorZIO#transact`, `TransactorZIO#connectZIO` and `TransactorZIO#transactZIO` call and closes it afterwards. Because each call has its own connection, two calls never share a transaction; to run several operations in one transaction, call them inside one `TransactorZIO#transact` or `TransactorZIO#transactZIO`.

## See Also

- [TransactorZIO](./sql/transactor-zio.md) — Reference for every `TransactorZIO` method.
- [How to Use the sql Module from a ZIO Application](../guides/sql-from-zio.md) — Guide to running `sql` queries and transactions as ZIO effects with `TransactorZIO`.
