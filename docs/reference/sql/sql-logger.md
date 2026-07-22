---
id: sql-logger
title: "SqlLogger"
description: "Reference for SqlLogger, the callback interface for observing SQL execution."
keywords:
  - "SqlLogger observability"
  - "SQL execution logging"
  - "SuccessEvent ErrorEvent"
  - "sql module"
---

`SqlLogger` is a callback interface that reports SQL execution events. Every statement calls either `onSuccess` or `onError` with details about the execution: rendered SQL, bound parameters, duration, and row count.

Key points:

- **Synchronous** — Callbacks run on the JDBC thread immediately after statement completion. Keep implementations fast; use a background thread for heavy I/O.
- **SqlLogger.noop** — Pre-built no-op instance that discards all events. Default when no custom logger is configured.

## Core API

```scala
trait SqlLogger {
  def onSuccess(event: SqlLogger.SuccessEvent): Unit
  def onError(event: SqlLogger.ErrorEvent): Unit
}
```

## Usage

Implement the trait to observe SQL execution:

```scala
import zio.blocks.sql.SqlLogger

val myLogger: SqlLogger = new SqlLogger {
  def onSuccess(event: SqlLogger.SuccessEvent): Unit =
    println(s"OK (${event.duration.toMillis}ms, ${event.rowCount} rows): ${event.sql}")

  def onError(event: SqlLogger.ErrorEvent): Unit =
    println(s"FAIL: ${event.error.getMessage}")
}
```

Pass the logger when creating a `JdbcTransactor`:

```scala
val tx = JdbcTransactor(dataSource, SqlDialect.PostgreSQL, myLogger)
```

Use the predefined no-op logger when you don't need logging:

```scala
val tx = JdbcTransactor(dataSource, SqlDialect.PostgreSQL, SqlLogger.noop)
```
