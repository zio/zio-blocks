---
id: sql-zio
title: "SQL — ZIO Integration"
---

`zio-blocks-sql-zio` is the ZIO adapter for `zio-blocks-sql`. It wraps the
core JDBC transactor so ZIO applications can use the same SQL layer without
changing the underlying database API.

## Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-sql-zio" % "@VERSION@"
```

## Quick Start

Create a `Transactor` layer from a JDBC `DataSource` and pick the dialect-specific helper that matches your database:

```scala
import javax.sql.DataSource
import zio.*
import zio.blocks.sql.*
import zio.blocks.sql.zio.*

val dataSource: DataSource = ???
val transactorLayer: ZLayer[Any, Nothing, Transactor] =
  ZLayer.succeed(dataSource) >>> JdbcTransactor.postgresLayer

val program: ZIO[Transactor, Throwable, Option[Int]] =
  ZIO.serviceWith[Transactor] { transactor =>
    transactor.connect {
      sql"SELECT 1".queryOne[Int]
    }
  }
```

Use `JdbcTransactor.sqliteLayer` for SQLite databases.

## TransactorZIO

`TransactorZIO` wraps the synchronous transactor and exposes ZIO-friendly
operations.

```scala
import zio._
import zio.blocks.sql._
import zio.blocks.sql.zio._

val transactor = TransactorZIO.fromUrl(
  "jdbc:postgresql://localhost/mydb",
  "alice", "secret",
  SqlDialect.PostgreSQL
)
```

For production use, prefer `TransactorZIO.fromDataSource(...)` so connection
pooling is handled outside the library.

You can also create a transactor from a `DataSource` when you already have one:

```scala
val transactor = TransactorZIO.fromDataSource(dataSource, SqlDialect.PostgreSQL)
```

## Blocking Wrappers

`connect` and `transact` run synchronous code on ZIO's blocking thread pool and
return `Task`.

```scala
val users: Task[List[User]] = transactor.connect:
  userRepo.findAll

val result: Task[User] = transactor.transact:
  userRepo.insertReturning(newUser)
```

## Effect-Aware Methods

`connectZIO` and `transactZIO` let the body return a `ZIO` directly.

```scala
val program: ZIO[Any, Throwable, User] =
  transactor.transactZIO:
    for
      _    <- ZIO.attemptBlocking(userRepo.insert(newUser))
      user <- ZIO.attemptBlocking(userRepo.findById(newUser.id))
    yield user.get
```

## ZLayer

Use `ZLayer` when you want to provide `TransactorZIO` through dependency
injection:

```scala
val transactorLayer: ZLayer[Any, Nothing, TransactorZIO] =
  TransactorZIO.layer("jdbc:postgresql://localhost/mydb", SqlDialect.PostgreSQL)
```

## Thread Safety

`TransactorZIO` is safe to share. It creates a new connection per
`connect` / `transact` invocation.
