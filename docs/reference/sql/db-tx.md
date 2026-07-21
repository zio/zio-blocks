---
id: db-tx
title: "DbTx"
description: "Reference for DbTx, the sql module's transactional scope marker that extends DbCon with commit-on-success and rollback-on-failure semantics."
keywords:
  - "DbTx transaction scope"
  - "Transactor transact context"
  - "DbCon subtype marker"
  - "auto-commit disabled transaction"
  - "commit rollback semantics"
  - "sql module connection context"
  - "JDBC transaction lifecycle"
---

`DbTx` is a marker trait in the `sql` module that extends `DbCon` to signal a transactional execution scope. It declares no members of its own — its distinct type is what instructs `Transactor#transact` to disable auto-commit, commit the connection on success, and roll back on any thrown exception. You never construct a `DbTx` directly; the `Transactor` creates one and supplies it as a given context to the block passed to `transact`. The connection is always closed when the block exits, whether it commits, rolls back, or throws.

Key properties:
- **Transactional context marker** — A `DbTx` value in scope guarantees the underlying JDBC connection has auto-commit disabled.
- **Commit-on-success semantics** — The `Transactor` commits the connection when the `transact` block returns normally.
- **Rollback-on-failure semantics** — Any uncaught exception causes the `Transactor` to roll back the connection before re-throwing.

The structural declaration of `DbTx` is:

```scala
trait DbTx extends DbCon
```

Every context member that `DbTx` exposes is inherited from `DbCon`, which declares the three fields every SQL operation consumes:

```scala
trait DbCon {
  def connection: DbConnection
  def dialect:    SqlDialect
  def logger:     SqlLogger
}
```

## Quick Showcase

The following example opens a transaction via `Transactor#transact`, accesses all three context members, and combines a `Repo` CRUD operation with a hand-written `Frag` query — both of which accept `DbTx` transparently in place of `DbCon`:

```scala
import zio.blocks.sql._
import zio.blocks.schema.Schema

case class User(id: Int, name: String, email: String)
object User {
  implicit val schema: Schema[User] = Schema.derived
}

val repo = Repo.derived[User, Int]("id", _.id)
val tx   = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

tx.transact { given ctx: DbTx =>
  // All three context members are accessible on ctx
  val conn: DbConnection = ctx.connection   // managed JDBC connection — do not close manually
  val d:    SqlDialect   = ctx.dialect      // e.g. SqlDialect.SQLite
  val log:  SqlLogger    = ctx.logger       // e.g. SqlLogger.noop

  // Repo and Frag operations accept DbTx because DbTx extends DbCon
  repo.table.createTable(ctx.dialect).update
  repo.insert(User(1, "Alice", "alice@example.com"))
  repo.insert(User(2, "Bob",   "bob@example.com"))

  val all:    List[User] = repo.findAll
  val custom: List[User] =
    sql"SELECT id, name, email FROM user WHERE name LIKE ${"A%"}".query[User]
}
// On normal return: transaction commits and connection closes.
// On any exception: transaction rolls back, then the exception propagates.
```
