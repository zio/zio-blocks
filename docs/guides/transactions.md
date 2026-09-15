---
id: transactions
title: "Transactions and Isolation"
---

# Transactions and Isolation

The SQL module provides two execution modes through `Transactor`: non-transactional connections (`connect`) and transactional scopes (`transact`). This guide explains the semantics, platform-specific behavior, and isolation characteristics of each.

## connect vs transact

| Aspect | `connect` | `transact` |
|--------|-----------|------------|
| **Auto-commit** | Left at default (typically `true`) | Disabled before body executes |
| **On success** | Connection closed | `commit()` called, then connection closed |
| **On exception** | Connection closed | `rollback()` called, then connection closed |
| **Use case** | Single reads, DDL | Multi-statement writes, atomicity required |

```scala mdoc:reset
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class User(id: Int, name: String)
object User { implicit val schema: Schema[User] = Schema.derived }

val repo       = Repo.derived[User, Int]("users", "id", _.id)
val transactor = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
```

### connect — Non-transactional access

Inside `connect`, auto-commit is left at its JDBC default. Each statement commits immediately:

```scala mdoc:compile-only
// Each statement commits independently — no atomicity guarantee
transactor.connect {
  repo.table.createTable(summon[DbCon].dialect).update
  repo.insert(User(1, "Alice"))
  repo.insert(User(2, "Bob"))
}
```

### transact — Transactional scope

Inside `transact`, auto-commit is disabled. The body runs inside a transaction that commits on success and rolls back on any exception:

```scala mdoc:compile-only
// Either both inserts commit, or neither does
transactor.transact {
  repo.table.createTable(summon[DbTx].dialect).update
  repo.insert(User(1, "Alice"))
  repo.insert(User(2, "Bob"))
}
```

If the second insert throws, the first is rolled back:

```scala mdoc:compile-only
import zio.blocks.maybe.Maybe

transactor.transact {
  repo.table.createTable(summon[DbTx].dialect).update
  repo.insert(User(1, "Alice"))
  // If this throws, Alice's insert is rolled back
  val user: Maybe[User] = repo.find(1)
  user
}
```

## Platform-Specific Transaction Behavior

### PostgreSQL

`JdbcTransactor.transact` for PostgreSQL (and other non-SQLite dialects) follows the standard JDBC pattern:

1. `conn.setAutoCommit(false)` — begins a transaction
2. Execute the body
3. On success: `conn.commit()`
4. On exception: `conn.rollback()`, then re-throw
5. `conn.setAutoCommit(prevAutoCommit)` — restore original state
6. Close connection

The transaction uses the **connection's default isolation level**, which for PostgreSQL is `READ COMMITTED`.

#### PostgreSQL isolation levels and READ UNCOMMITTED

PostgreSQL implements four standard SQL isolation levels, but with an important caveat:

| SQL Standard | PostgreSQL Behavior |
|-------------|---------------------|
| `READ UNCOMMITTED` | **Actually behaves as `READ COMMITTED`** — PostgreSQL never allows dirty reads, even at this level |
| `READ COMMITTED` | Default. Each statement sees only data committed before the statement began |
| `REPEATABLE READ` | Each transaction sees a snapshot as of the start of the first non-transaction-control statement |
| `SERIALIZABLE` | Equivalent to true serializability (SSI — Serializable Snapshot Isolation) |

:::caution
PostgreSQL does not implement true dirty reads. Setting `READ UNCOMMITTED` does not give you dirty-read semantics — it behaves identically to `READ COMMITTED`. This is a PostgreSQL design choice, not a limitation of the SQL module.
:::

The `Transactor` API in v1 does not expose a method to set the transaction isolation level programmatically. The isolation level is determined by the JDBC connection's default (typically `READ COMMITTED` for PostgreSQL). To use a different isolation level, configure it at the connection pool level or use a raw `Frag` query to execute `SET TRANSACTION ISOLATION LEVEL` within the transaction body.

### SQLite

SQLite transactions use `BEGIN IMMEDIATE` instead of the standard `BEGIN`. This is because SQLite is single-consumer: a `DEFERRED` transaction would allow a concurrent writer to acquire the write lock between the `SELECT` and the subsequent `INSERT`/`UPDATE`/`DELETE`, causing `SQLITE_BUSY`.

`BEGIN IMMEDIATE` acquires the reserved write lock at transaction start, preventing the dequeue race in queue-worker patterns. The connection also sets `busy_timeout=5000` so contending connections wait up to 5 seconds instead of failing immediately.

```
SQLite transact lifecycle:
  PRAGMA busy_timeout = 5000
  BEGIN IMMEDIATE
  ... body ...
  COMMIT  (on success)
  ROLLBACK (on exception)
  close
```

:::info
SQLite's `BEGIN IMMEDIATE` does not map to a standard SQL isolation level. SQLite's default journal mode (`DELETE`) provides serializable isolation, and `BEGIN IMMEDIATE` does not change this — it only controls when the write lock is acquired.
:::

## Connection Lifecycle

Both `connect` and `transact` guarantee the connection is closed when the block exits, regardless of success or failure. The connection is never returned to a pool or reused across calls without explicit `DataSource` pooling.

For `transact`, the connection lifecycle is:

```
acquire → setAutoCommit(false) → body → commit/rollback → restore autoCommit → close
```

If `setAutoCommit(false)` fails, the connection is closed immediately without executing the body. If `commit()` fails after a successful body, the transaction is rolled back and the commit exception propagates (the body's return value is discarded).

## v1 Limitations

- **No programmatic isolation level control.** The `transact` method uses the connection's default isolation level. To change it, configure the connection pool or execute `SET TRANSACTION ISOLATION LEVEL` inside the transaction body.
- **No savepoint API.** Nested transactions (savepoints) are not exposed. Use multiple `transact` calls or manage savepoints via raw SQL.
- **No two-phase commit.** Distributed transactions across multiple databases are not supported.
- **No read-only transaction hint.** PostgreSQL supports `SET TRANSACTION READ ONLY` but this is not exposed by the API.

## Going Further

- **[Transactor Reference](../reference/sql/transactor.md)** — Full API reference for `Transactor` and `JdbcTransactor`
- **[DbTx Reference](../reference/sql/db-tx.md)** — Transaction scope marker type
- **[SQL Query DSL Guide](./sql-query-dsl.md)** — Typed query construction and execution
