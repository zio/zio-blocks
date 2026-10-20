package postgrespool

import java.sql.SQLException
import zio.blocks.sql.*

/**
 * How to Connect to PostgreSQL with a Connection Pool: Read-Only Report
 *
 * Prints a report from a RepeatableRead, read-only transaction, then shows the error PostgreSQL raises for a write
 * inside a read-only transaction.
 *
 * Run with: cd sql-examples && sbt "runMain postgrespool.ReadOnlyReportExample"
 */
object ReadOnlyReportExample extends App {

  val pool = openBookstorePool()
  try {
    val transactor = JdbcTransactor.postgres(pool)
    transactor.transact(stockShelf())

    val (count, shelf) = transactor.transact(TransactionIsolation.RepeatableRead, readOnly = true)(shelfReport())
    println(s"The shelf holds $count books:")
    shelf.foreach(println)

    try
      transactor.transact(TransactionIsolation.ReadCommitted, readOnly = true) {
        books.insert(Book(4L, "Scala with Cats", "Noel Welsh", 2020, None))
      }
    catch {
      case e: SQLException => println(s"Write rejected: ${e.getMessage}")
    }
  } finally pool.close()
}
