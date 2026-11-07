package postgrespool

import zio.blocks.scope.*
import zio.blocks.sql.*

/**
 * How to Connect to PostgreSQL with a Connection Pool: Complete Example
 *
 * Opens the pool in a Scope, prints the server version, stocks the shelf and
 * prints a read-only report. The scope closes the pool when the block ends.
 *
 * Run with: cd sql-examples && sbt "runMain postgrespool.CompleteExample"
 */
object CompleteExample extends App {

  Scope.global.scoped { scope =>
    import scope.*
    val transactor: $[JdbcTransactor] =
      Resource
        .fromAutoCloseable(openBookstorePool())
        .map(JdbcTransactor.postgres)
        .allocate

    val version = $(transactor)(_.connect(serverVersion()))
    println(s"Server version: $version")

    val stocked = $(transactor)(_.transact(stockShelf()).map(_.title))
    println(s"Stocked: ${stocked.mkString(", ")}")

    val report = $(transactor)(_.transact(TransactionIsolation.RepeatableRead, readOnly = true) {
      val (count, shelf) = shelfReport()
      s"$count books, newest from ${shelf.map(_.year).max}"
    })
    println(s"Read-only report: $report")
  }
}
