---
id: wire-and-context
title: "Wire and Context in a Nutshell"
sidebar_label: "Wire and Context"
description: "The core idea of dependency injection in ZIO Blocks: Wire builds services from their constructors at compile time, and Context holds values by type."
keywords:
  - "Getting Started"
  - "Wire"
  - "Context"
  - "Dependency Injection"
---

An application is a graph of services: a web handler needs a user service, which needs a database, which needs its configuration. `Wire` builds that graph for you from ordinary constructors, checks it at compile time, and hands every resource in it to a [scope](./scope.md) so that it closes in the right order. `Context` is the small data type underneath: a set of values looked up by their type.

## The Problem It Solves

Without a dependency injection tool, `main` creates every service by hand, in the right order, and passes each one to the constructors that need it. That code grows with every service, and the order of closing resources has to be kept in sync by hand.

Runtime injection frameworks remove that code but report a missing service only when the application starts. Wire does the same job with a macro, so a broken graph is a compile error, and there is no framework and no annotations: your classes stay plain classes with constructor parameters.

## Build a Graph from Constructors

We start with three plain classes. `Database` is a resource, so it implements `AutoCloseable`; `UserService` needs a `Database`; `Database` needs a `DbConfig`:

```scala mdoc:silent
import zio.blocks.scope._

final case class DbConfig(url: String)

final class Database(config: DbConfig) extends AutoCloseable {
  println(s"Connecting to ${config.url}")
  def query(sql: String): String = s"rows for: $sql"
  def close(): Unit = println("Database closed")
}

final class UserService(db: Database) {
  def userName(id: Int): String = db.query(s"SELECT name FROM users WHERE id = $id")
}
```

`Resource.from[UserService]` reads the constructors at compile time and builds the whole graph. We pass a `Wire` only for what it cannot build on its own, here the configuration value:

```scala mdoc:silent
def userService: Resource[UserService] =
  Resource.from[UserService](
    Wire(DbConfig("jdbc:postgresql://localhost/app"))
  )
```

Allocating the resource in a scope creates the database, then the service; when the scope ends, the database closes:

```scala mdoc
Scope.global.scoped { scope =>
  import scope._
  val service = allocate(userService)
  println($(service)(_.userName(42)))
}
```

Without the `DbConfig` wire, the macro would try to build `DbConfig` from its constructor, find that it needs a `String` it has no way to invent, and stop with a compile error that names the missing type and the chain of services that needed it.

## Shared and Unique Services

Every service that the macro builds on its own is created once and shared by everything that needs it. To change that for one type, pass `Wire.unique[T]`, which creates a fresh instance for each service that depends on `T`; `Wire.shared[T]` states the default explicitly. Both wires are derived from the constructor of `T`, like the rest of the graph.

## Context: Values Looked Up by Type

`Context` holds a few values of different types and returns each one by its type. The type of the context lists what it contains, here `Context[RequestId & UserId]` (written `RequestId with UserId` on Scala 2), so asking for a type it does not contain is a compile error:

```scala mdoc:silent
import zio.blocks.context._

final case class RequestId(value: String)
final case class UserId(value: Long)

val ctx = Context(RequestId("req-17"), UserId(42L))
```

Each lookup returns the value with its own type, here a `UserId`:

```scala mdoc
ctx.get[UserId]
```

Wire uses `Context` to pass each constructor its dependencies, and you can use it directly to carry request-level values, such as a request id, through your own code.

## Learn More

The [Wire reference](../../reference/resource-management/wire.md) covers manual wires and sharing in detail, the [Context reference](../../reference/context.md) covers the full `Context` API, and [How to Fix Common Scope and Wire Compile Errors](../../guides/fix-scope-and-wire-compile-errors.md) explains each error the macro can report. The next concept in this section is [Streams](./streams.md).
