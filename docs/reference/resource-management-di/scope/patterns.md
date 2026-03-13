---
id: scope-patterns
title: "Scope — Usage Patterns"
---

For conceptual background and core mental model, see the [Scope reference](./index.md).

## Usage examples (patterns)

### Allocating and using a resource

Basic pattern for acquiring and using a single resource:

```scala
import zio.blocks.scope.*

final class FileHandle(path: String) extends AutoCloseable {
  def readAll(): String = s"contents of $path"
  def close(): Unit = println(s"closed $path")
}

object FileExample {
  def fileExample(): Unit = {
    Scope.global.scoped { scope =>
      import scope.*

      val h: $[FileHandle] =
        Resource(new FileHandle("data.txt")).allocate

      val contents: String =
        $(h)(_.readAll())

      println(contents)
    }
  }
}
```

---

### Nested scopes (child can use parent, not vice versa)

Show how parent-scoped resources can be accessed in child scopes, but not the reverse:

```scala
import zio.blocks.scope.*

final class Database extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("db closed")
}

object NestedExample {
  def nested(): Unit = {
    Scope.global.scoped { parent =>
      import parent.*

      val parentDb: $[Database] = Resource.fromAutoCloseable(new Database).allocate

      val done: String =
        parent.scoped { child =>
          import child.*

          val db: $[Database] = lower(parentDb)
          println($(db)(_.query("SELECT 1")))

          val childDb: $[Database] = Resource.fromAutoCloseable(new Database).allocate
          println($(childDb)(_.query("SELECT 2")))

          // childDb cannot be returned to the parent (not Unscoped)
          "done"
        }

      println($(parentDb)(_.query("SELECT 3")))
      done
    }
  }
}
```

Finalizers run **child first, then parent**.

---

### Chaining resource acquisition (`$[Resource[A]]` + `.allocate`)

If a method returns `Resource[A]`, `$` returns a **scoped** `Resource[A]` (because `Resource[A]` is not `Unscoped`). Allocate it without leaking:

```scala
import zio.blocks.scope.*

final class Pool extends AutoCloseable {
  def lease(): Resource[Conn] = Resource.fromAutoCloseable(new Conn)
  def close(): Unit = println("pool closed")
}

final class Conn extends AutoCloseable {
  def query(sql: String): String = s"result: $sql"
  def close(): Unit = println("connection closed")
}

object ChainingExample {
  def chaining(): Unit = {
    Scope.global.scoped { scope =>
      import scope.*

      val pool: $[Pool] = Resource.fromAutoCloseable(new Pool).allocate

      // $(pool)(_.lease()) : $[Resource[Conn]]
      val conn: $[Conn] =
        $(pool)(_.lease()).allocate

      val result: String =
        $(conn)(_.query("SELECT 1"))

      println(result)
    }
  }
}
```

This `.allocate` comes from `Scope.ScopedResourceOps` (an extension on `$[Resource[A]]`).

---

### Allocating a bare `Resource[A]` with `.allocate`

A plain `Resource[A]` also has `.allocate` as syntax sugar for `scope.allocate(resource)`:

```scala
import zio.blocks.scope.*

Scope.global.scoped { scope =>
  import scope.*

  val db: $[Database] =
    Resource.fromAutoCloseable(new Database).allocate

  $(db)(_.query("SELECT 1"))
}
```

---

### Classes with `Finalizer` parameters (cleanup-only capability)

If a class only needs cleanup registration, accept a `Finalizer`. DI macros inject it automatically:

```scala
import zio.blocks.scope.*

final case class Config(url: String)
object Config {
  implicit val unscopedConfig: Unscoped[Config] = Unscoped.derived
}

final class ConnectionPool(config: Config)(implicit ev: Finalizer) {
  private val pool = s"pool(${config.url})"
  defer(println(s"shutdown $pool"))
}

val poolResource: Resource[ConnectionPool] =
  Resource.from[ConnectionPool](
    Wire(Config("jdbc://localhost"))
  )

@main def finalizerInjection(): Unit =
  Scope.global.scoped { scope =>
    import scope.*
    val pool: $[ConnectionPool] = poolResource.allocate
    ()
  }
```

When to prefer `Finalizer` over `Scope`:

- you only need `defer`
- you want to expose minimal power to the class

---

### Classes with `Scope` parameters (scope injection)

If a class needs to allocate resources or create child scopes, accept a `Scope`:

```scala
import zio.blocks.scope.*

final case class Config(url: String)
object Config {
  implicit val unscopedConfig: Unscoped[Config] = Unscoped.derived
}

final class Connection(config: Config) extends AutoCloseable {
  def query(sql: String): String = s"[${config.url}] $sql"
  def close(): Unit = println("connection closed")
}

final class RequestHandler(config: Config)(implicit scope: Scope) {
  def handle(sql: String): String =
    scope.scoped { child =>
      import child.*
      val conn: $[Connection] = Resource.fromAutoCloseable(new Connection(config)).allocate
      $(conn)(_.query(sql))
    }
}

val handlerResource: Resource[RequestHandler] =
  Resource.from[RequestHandler](
    Wire(Config("jdbc://localhost"))
  )

@main def scopeInjection(): Unit =
  Scope.global.scoped { scope =>
    import scope.*
    val handler: $[RequestHandler] = handlerResource.allocate
    val out: String = $(handler)(_.handle("SELECT 1"))
    println(out)
  }
```

The `Scope`/`Finalizer` parameter can appear in any parameter list position; it's recognized specially by the derivation macros.

---

## Dependency injection (DI) with `Wire` + `Resource.from`

Scope includes a constructor-based dependency injection layer built on `Wire` and `Resource.from`. For full documentation including macro derivation, trait injection, diamond patterns, and error messages, see the [Wire reference](./wire.md).

---
