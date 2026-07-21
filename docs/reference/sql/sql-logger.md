---
id: sql-logger
title: "SqlLogger"
description: "Reference for SqlLogger, the observability callback interface that reports SQL execution success and failure events in the sql module."
keywords:
  - "SqlLogger observability"
  - "SQL execution logging"
  - "SuccessEvent ErrorEvent"
  - "noop sql logger"
  - "DbCon logger"
  - "JdbcTransactor constructor"
  - "SQL query monitoring"
---

`SqlLogger` is a callback interface for observing SQL execution. It defines two methods — `onSuccess` and `onError` — that the module calls synchronously after every statement completes. `SuccessEvent` carries the rendered SQL string, the bound parameters, the wall-clock duration, and the number of affected rows; `ErrorEvent` carries the same fields plus the `Throwable` that caused the failure. The predefined `SqlLogger.noop` instance discards all events silently and is the default when no custom logger is configured.

Key properties:
- **Synchronous** — both callback methods are called on the database thread that executed the statement; implementations must be fast and must not block.
- **Full event data** — each event carries the rendered SQL string, bound parameters as `IndexedSeq[DbValue]`, wall-clock duration as `java.time.Duration`, and (for success) a row count.
- **Zero-cost default** — `SqlLogger.noop` is a pre-built instance whose callbacks are no-ops; it adds no overhead when logging is not needed.
- **Carried by `DbCon`** — every SQL operation running inside a `Transactor#connect` or `Transactor#transact` block reports through the logger embedded in the active `DbCon`.

The structural shape of `SqlLogger` is:

```scala
trait SqlLogger {
  def onSuccess(event: SqlLogger.SuccessEvent): Unit
  def onError(event: SqlLogger.ErrorEvent): Unit
}

object SqlLogger {
  final case class SuccessEvent(
    sql: String,
    params: IndexedSeq[DbValue],
    duration: java.time.Duration,
    rowCount: Int
  )

  final case class ErrorEvent(
    sql: String,
    params: IndexedSeq[DbValue],
    duration: java.time.Duration,
    error: Throwable
  )

  val noop: SqlLogger
}
```

## Quick Showcase

The following example creates a simple in-memory `SqlLogger` that accumulates log lines and shows how each field of `SuccessEvent` can be inspected:

```scala
import zio.blocks.sql.{SqlLogger, DbValue}
import java.time.Duration
import scala.collection.mutable

// Build a collecting logger for testing or debugging
val logLines = mutable.ArrayBuffer.empty[String]

val collectingLogger: SqlLogger = new SqlLogger {
  def onSuccess(event: SqlLogger.SuccessEvent): Unit = {
    val paramStr = event.params.map {
      case DbValue.DbInt(n)    => n.toString
      case DbValue.DbString(s) => s""""$s""""
      case other               => other.toString
    }.mkString(", ")
    logLines += s"[${event.duration.toMillis}ms rows=${event.rowCount}] ${event.sql} -- [$paramStr]"
  }

  def onError(event: SqlLogger.ErrorEvent): Unit =
    logLines += s"[ERROR] ${event.error.getMessage} | ${event.sql}"
}

// Simulate the callback the module would make after a successful SELECT
collectingLogger.onSuccess(
  SqlLogger.SuccessEvent(
    sql      = "SELECT id, name FROM users WHERE id = ?",
    params   = IndexedSeq(DbValue.DbInt(42)),
    duration = Duration.ofMillis(3),
    rowCount = 1
  )
)

logLines.head
// "[3ms rows=1] SELECT id, name FROM users WHERE id = ? -- [42]"
```

## Construction / Creating Instances

`SqlLogger` has no `apply` constructor. Create an instance by extending the trait directly with an anonymous class or a named class. The `noop` singleton is available as `SqlLogger.noop` for contexts where observability is not needed.

### `SqlLogger.noop` — The predefined no-op logger

`SqlLogger.noop` is a pre-built instance whose `onSuccess` and `onError` implementations return `()` immediately. It is the default logger passed to `JdbcTransactor` when no custom logger is provided:

```scala
import zio.blocks.sql.SqlLogger

// noop discards all events
SqlLogger.noop.onSuccess(???) // returns () without executing the body
SqlLogger.noop.onError(???)   // returns () without executing the body

// Use noop when you do not need SQL observability
val noLogging: SqlLogger = SqlLogger.noop
```

### Custom implementation — Extend the trait

Implement both methods to integrate with any logging framework. Keep implementations fast: the methods run synchronously on the JDBC thread. For high-throughput workloads, prefer appending to a lock-free queue and draining it on a background thread rather than writing to a file or a remote sink inline.

```scala
import zio.blocks.sql.{SqlLogger, DbValue}

val printingLogger: SqlLogger = new SqlLogger {
  def onSuccess(event: SqlLogger.SuccessEvent): Unit =
    println(s"OK (${event.duration.toMillis}ms, ${event.rowCount} rows): ${event.sql}")

  def onError(event: SqlLogger.ErrorEvent): Unit =
    println(s"FAIL (${event.duration.toMillis}ms): ${event.sql} — ${event.error.getMessage}")
}
```

## Core Operations

### `onSuccess` — Handle a successful statement

`onSuccess(event: SqlLogger.SuccessEvent): Unit` is called after every statement that completes without throwing. The `SuccessEvent` fields are:

- `sql: String` — the rendered SQL string with `?` placeholders, as passed to JDBC.
- `params: IndexedSeq[DbValue]` — the bound parameter values in position order, parallel to the `?` tokens in `sql`.
- `duration: java.time.Duration` — the wall-clock time from statement preparation to JDBC return.
- `rowCount: Int` — the number of rows affected (for `UPDATE`/`INSERT`/`DELETE`) or returned (for `SELECT`).

```scala
import zio.blocks.sql.{SqlLogger, DbValue}
import java.time.Duration

val event = SqlLogger.SuccessEvent(
  sql      = "INSERT INTO orders (user_id, amount) VALUES (?, ?)",
  params   = IndexedSeq(DbValue.DbInt(7), DbValue.DbBigDecimal(BigDecimal("19.99"))),
  duration = Duration.ofMillis(5),
  rowCount = 1
)

event.sql      // "INSERT INTO orders (user_id, amount) VALUES (?, ?)"
event.rowCount // 1
event.params   // IndexedSeq(DbValue.DbInt(7), DbValue.DbBigDecimal(19.99))
```

### `onError` — Handle a failed statement

`onError(event: SqlLogger.ErrorEvent): Unit` is called after every statement that throws an exception. The `ErrorEvent` shares the `sql`, `params`, and `duration` fields with `SuccessEvent` and adds `error: Throwable` — the exception that caused the failure. The module does not swallow the exception; it calls `onError` and then re-throws so the caller's error handling is not bypassed.

```scala
import zio.blocks.sql.{SqlLogger, DbValue}
import java.time.Duration

val event = SqlLogger.ErrorEvent(
  sql      = "SELECT * FROM missing_table WHERE id = ?",
  params   = IndexedSeq(DbValue.DbInt(1)),
  duration = Duration.ofMillis(2),
  error    = new RuntimeException("Table 'missing_table' doesn't exist")
)

event.error.getMessage // "Table 'missing_table' doesn't exist"
event.sql              // "SELECT * FROM missing_table WHERE id = ?"
```

## Integration

`SqlLogger` is carried in `DbCon` as the `logger` member. `Transactor#connect` and `Transactor#transact` create a `DbCon` that includes the logger configured at `JdbcTransactor` construction time; every `Frag` execution method and every `Repo` CRUD method in that scope reports through it automatically.

The `JdbcTransactor` constructor accepts a `sqlLogger: SqlLogger` as an optional third parameter that defaults to `SqlLogger.noop`. Pass a custom instance to enable logging for all connections opened through that transactor:

```scala
// Pseudocode — JdbcTransactor lives in the sql-zio or JDBC-backed module
// val transactor = JdbcTransactor(dataSource, SqlDialect.PostgreSQL, myLogger)
```

`SuccessEvent` and `ErrorEvent` both expose `params: IndexedSeq[DbValue]`, which is the same `IndexedSeq[DbValue]` sequence stored in `Frag#params`. Because `DbValue` is a typed sealed ADT rather than an `Any`-keyed map, logger implementations can pattern-match on each element to produce type-aware representations — for example, redacting `DbValue.DbString` values that might contain PII, or formatting `DbValue.DbInstant` values as human-readable timestamps.

For the connection context that carries the logger, see [DbCon](./db-con.md). For the parameter value type exposed by both event types, see [DbValue](./db-value.md).
