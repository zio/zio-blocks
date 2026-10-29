---
id: scope
title: "Scope in a Nutshell"
sidebar_label: "Scope"
description: "The core idea of the Scope block: open files, connections and other resources inside a region of code that closes them for you, with lifetimes checked at compile time."
keywords:
  - "Getting Started"
  - "Scope"
  - "Resource"
  - "Resource Safety"
---

`Scope` manages resources: files, network connections, database pools and anything else that must be closed after use. You open resources inside a scope, and the scope closes all of them when it ends, in reverse order, even when the code inside fails. The compiler checks that no resource escapes the scope that owns it.

## The Problem It Solves

Plain Scala closes resources with `try`/`finally`, one level of nesting per resource. With three resources the closing logic outgrows the real work, and it is easy to forget one `close` or to return a reader from a method after its file has been closed.

A scope replaces that nesting with one region of code. Every resource opened in it is registered with the scope, and closing happens in one place, automatically.

## Open a Resource in a Scope

A `Resource` is a recipe: it says how to acquire a value and how to release it, but does nothing until a scope allocates it. Here we describe a connection with `Resource.acquireRelease`, printing a message in both steps so we can see when they run:

```scala mdoc:silent
import zio.blocks.scope._

final class Connection(val name: String) {
  def query(sql: String): String = s"$name answered: $sql"
}

def connection(name: String): Resource[Connection] =
  Resource.acquireRelease {
    println(s"Opening $name")
    new Connection(name)
  } { conn =>
    println(s"Closing ${conn.name}")
  }
```

`Scope.global.scoped` opens a scope for the block of code we pass it. Inside, `allocate` runs a recipe and registers the release step, and `$(value)(...)` is how we call methods on a scoped value:

```scala mdoc
Scope.global.scoped { scope =>
  import scope._

  val users  = allocate(connection("users-db"))
  val orders = allocate(connection("orders-db"))

  println($(users)(_.query("SELECT name FROM users")))
  println($(orders)(_.query("SELECT id FROM orders")))
}
```

Both connections close when the block ends, the last one opened first. Classes that implement `AutoCloseable` need no release step at all: `Resource(new FileInputStream(file))` closes the stream for you.

## Why the Compiler Can Check It

Inside a scope, `allocate` returns a value of type `$[Connection]`, not a plain `Connection`. The `$` type is tied to the scope that owns the resource, so the compiler rejects a block that tries to return the resource. Plain data, such as the `String` that `query` returns, leaves the scope freely.

In practice this turns the classic bug, handing out a reader whose file is about to close, into a compile error. Code that reaches a resource some other way after its scope has closed, for example from a callback that runs later, gets an `IllegalStateException` that says the scope is closed, instead of silently reading from a closed file.

## What Else Scope Gives You

The block covers more than one flat region:

- Child scopes nest inside a parent and close before it, for per-request or per-task resources.
- `Resource.shared` lets several services use one resource, which closes when the last user is done.
- [Wire](./wire-and-context.md) builds whole applications of services and their resources from constructors.

## Learn More

The [Compile-Time Resource Safety with Scope](../../guides/compile-time-resource-safety-with-scope.md) tutorial goes through the block step by step, and the [Resource Management reference](../../reference/resource-management/index.md) documents `Scope`, `Resource` and the related types. The next concept in this section is [Wire and Context](./wire-and-context.md).
