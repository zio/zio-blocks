---
id: sql-transactions
title: "SQL Transactions: Isolation, Read-Only and Savepoints"
---

This guide covers ZIO Blocks' SQL transaction system: how `transact` works under the hood, which isolation levels are available on each database, and how nested transactions use savepoints. To set up a connection pool, see [How to Connect to PostgreSQL with a Connection Pool](./connect-to-postgresql-with-a-connection-pool.md).

**What we'll cover:**

- Opening transactions with `transactor.transact { ... }` and tuning isolation + readOnly
- The `TransactionIsolation` enum and how SQLite vs PostgreSQL handle each level
- Nested transactions via SQL savepoints (`zib_tx_1 .. zib_tx_N`)
- Error semantics: inner rollback vs outer commit

## Basic Usage

Every database interaction goes through a `Transactor`. The simplest form opens a connection, disables auto-commit, runs your code, and commits on success (or rolls back on failure):

```scala
transactor.transact {
  sql"INSERT INTO users (name) VALUES ('Alice')".update
  sql"INSERT INTO orders (user_id, total) VALUES (1, 99.50)".update
}
```

Both inserts run inside a single transaction. If either fails, both are rolled back.

To control isolation level and read-only mode, pass them explicitly:

```scala
transactor.transact(TransactionIsolation.RepeatableRead, readOnly = false) {
  val users = sql"SELECT * FROM users WHERE active = true".query[User].toList
  // ... process users ...
}
```

The one-argument `Transactor#transact` sets neither the isolation level nor the read-only flag, so it never forces `Serializable`. It keeps the connection's current settings, which are the JDBC driver's defaults unless your connection pool configures them: on PostgreSQL that is `ReadCommitted`, and on SQLite every transaction behaves as `Serializable`, the only level SQLite supports. To get a specific level, use the two-argument overload shown above.

## TransactionIsolation

The `TransactionIsolation` enum has four values, each mapping to the corresponding `java.sql.Connection.TRANSACTION_*` constant:

| Enum value           | JDBC constant                        |
|----------------------|--------------------------------------|
| `ReadUncommitted`    | `TRANSACTION_READ_UNCOMMITTED`       |
| `ReadCommitted`      | `TRANSACTION_READ_COMMITTED`         |
| `RepeatableRead`     | `TRANSACTION_REPEATABLE_READ`        |
| `Serializable`       | `TRANSACTION_SERIALIZABLE`           |

When you pass a level to `transactor.transact`, it calls `Connection.setTransactionIsolation` before starting the transaction. The previous level is restored in a `finally` block after the transaction completes.

### SQLite vs PostgreSQL

The two supported dialects differ significantly in how they honor isolation levels:

| Level             | PostgreSQL                           | SQLite                               |
|-------------------|--------------------------------------|--------------------------------------|
| `ReadUncommitted` | Accepted, behaves as `ReadCommitted` | Accepted, treated as `SERIALIZABLE`  |
| `ReadCommitted`   | Full support (default PG level)      | Accepted, treated as `SERIALIZABLE`  |
| `RepeatableRead`  | Full support (MVCC snapshot)         | Accepted, treated as `SERIALIZABLE`  |
| `Serializable`    | Full support (SSI)                   | Native (default and only true level) |

SQLite natively supports only `SERIALIZABLE`. The driver accepts the `setTransactionIsolation` call without error, but the engine ignores the requested level and behaves as serializable. This is a SQLite limitation, not a ZIO Blocks one. If your application relies on weaker isolation guarantees (e.g., `ReadCommitted` for higher concurrency), test on PostgreSQL where those semantics are real.

PostgreSQL accepts all four levels but implements three of them: `ReadUncommitted` behaves exactly like `ReadCommitted`, so PostgreSQL never allows dirty reads. `ReadCommitted` is PostgreSQL's default level for every new transaction; `RepeatableRead` gives you a consistent snapshot for the duration of the transaction; `Serializable` also detects serialization anomalies with Serializable Snapshot Isolation (SSI) and fails one of the conflicting transactions, which your application must retry.

## Read-Only Transactions

Passing `readOnly = true` marks the connection as read-only at the JDBC level:

```scala
transactor.transact(TransactionIsolation.ReadCommitted, readOnly = true) {
  sql"SELECT COUNT(*) FROM users".query[Int].toOne
}
```

PostgreSQL uses the read-only flag to enable optimization paths (e.g., avoiding WAL writes for read-only transactions). SQLite may ignore the flag depending on the driver version, but it's still set on the connection for consistency. The previous `readOnly` value is always restored after the transaction, regardless of dialect.

## Nested Transactions via Savepoints

Nested transactions are emulated via SQL savepoints on the same JDBC connection. When you're already inside `transactor.transact { ... }`, you can nest further work using the ambient `DbTx`:

```scala
transactor.transact {
  sql"INSERT INTO orders (total) VALUES (100)".update

  summon[DbTx].transact {
    sql"INSERT INTO order_items (order_id, product_id) VALUES (1, 42)".update
  }

  sql"UPDATE inventory SET stock = stock - 1 WHERE product_id = 42".update
}
```

The nested block runs inside a savepoint named `zib_tx_1`. If it fails, only the inner work is rolled back (via `ROLLBACK TO SAVEPOINT`). The outer transaction continues and can still commit.

### How Savepoints Work

Each nesting level gets a savepoint name derived from the current depth: `zib_tx_1`, `zib_tx_2`, and so on. The mechanism:

1. **Depth increments** before creating the savepoint
2. **`SAVEPOINT zib_tx_<depth>`** is issued on the connection
3. The nested body executes with the ambient `DbTx` in scope
4. On **success**: `RELEASE SAVEPOINT zib_tx_<depth>` is called
5. On **failure**: `ROLLBACK TO SAVEPOINT zib_tx_<depth>` is called, then the exception is rethrown
6. **Depth decrements** in a `finally` block, guaranteeing the counter resets even after exceptions

The depth counter resets after each inner block finishes, so sibling nested transactions reuse the same name sequence without leaking savepoints.

### Three-Level Example

```scala
transactor.transact {
  // Depth 0: outer transaction (real COMMIT/ROLLBACK)
  sql"INSERT INTO t VALUES (1)".update

  summon[DbTx].transact {
    // Depth 1: savepoint zib_tx_1
    sql"INSERT INTO t VALUES (2)".update

    summon[DbTx].transact {
      // Depth 2: savepoint zib_tx_2
      sql"INSERT INTO t VALUES (3)".update
    }
    // zib_tx_2 released here

    sql"INSERT INTO t VALUES (4)".update
  }
  // zib_tx_1 released here

  sql"INSERT INTO t VALUES (5)".update
}
// COMMIT (all 5 inserts)
```

If the depth-2 block fails, only `INSERT INTO t VALUES (3)` is rolled back. Inserts 1, 2, 4, and 5 remain and will be committed.

### Error Semantics

When an inner block throws:

- The inner savepoint is rolled back via `ROLLBACK TO SAVEPOINT`
- The exception is rethrown to the caller
- The outer transaction is **not** rolled back automatically
- The outer block can catch the exception and continue, or let it propagate (triggering a full rollback at the top level)

```scala
transactor.transact {
  sql"INSERT INTO t VALUES (1)".update

  try {
    summon[DbTx].transact {
      sql"INSERT INTO t VALUES (2)".update
      throw new RuntimeException("inner failure")
      sql"INSERT INTO t VALUES (3)".update  // never reached
    }
  } catch {
    case _: RuntimeException => ()  // swallow inner failure
  }

  sql"INSERT INTO t VALUES (4)".update
}
// COMMIT: rows 1 and 4 only (row 2 rolled back by savepoint)
```

### The `transactNested` Helper

For call sites that prefer `using` parameters over extension receivers, `transactNested` is available as an alias:

```scala
import zio.blocks.sql.DbTx

transactor.transact {
  DbTx.transactNested {
    // same as summon[DbTx].transact { ... }
    sql"INSERT INTO t VALUES (1)".update
  }
}
```

Both forms are equivalent. The extension method (`summon[DbTx].transact { ... }`) is more common in practice because it reads naturally inside a `transact` block.

### Given-Priority Trick

When a `DbTx` is already in scope (inside `transactor.transact`), the extension `summon[DbTx].transact { ... }` resolves via the more specific `DbTx` receiver and reuses the same connection with a savepoint. Using `transactor.transact { ... }` instead would open a **new** connection, which is almost never what you want inside an existing transaction. The explicit `summon[DbTx]` form keeps semantics clear without hidden implicit resolution.

## Connection Pooling

To run these transactions on a HikariCP pool, build the transactor with `JdbcTransactor.postgres(dataSource)`; [How to Connect to PostgreSQL with a Connection Pool](./connect-to-postgresql-with-a-connection-pool.md) covers the pool setup, sizing it for virtual threads, and closing it.

## Summary

| Concept                    | Key Point                                               |
|----------------------------|---------------------------------------------------------|
| `transactor.transact`      | Opens connection, disables auto-commit, commits/rollbacks |
| `TransactionIsolation`     | Four levels; SQLite only true `SERIALIZABLE`            |
| `readOnly` flag            | Sets JDBC read-only; PG optimizes, SQLite may ignore    |
| Nested via `summon[DbTx]`  | Savepoints `zib_tx_1..N`, inner rollback isolated       |
| `transactNested`           | Alias using `using` parameter instead of extension      |

## Going Further

- **[Query DSL with SQL](./query-dsl-sql.md)** -- Building parameterized SQL from `SchemaExpr` queries
- **[Transactor Reference](../reference/sql/transactor)** -- `JdbcTransactor.fromDataSource` / `fromUrl` and `transact(isolation, readOnly)` overloads
- **[DbTx Reference](../reference/sql/db-tx)** -- Savepoint API (`savepoint` / `release` / `rollbackTo`) and `summon[DbTx].transact`
- **[How to Connect to PostgreSQL with a Connection Pool](./connect-to-postgresql-with-a-connection-pool.md)** -- HikariCP setup, pool sizing for virtual threads, and shutdown
