package postgrespool

import zio.blocks.sql.*

/**
 * How to Connect to PostgreSQL with a Connection Pool: Run the Repository
 *
 * Prints the CREATE TABLE statement for PostgreSQL, creates the book table, stocks the shelf and lists the books.
 *
 * Run with: cd sql-examples && sbt "runMain postgrespool.RepositoryExample"
 */
object RepositoryExample extends App {

  println(bookTable.createTable(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL))

  val pool = openBookstorePool()
  try {
    val transactor = JdbcTransactor.postgres(pool)
    transactor.transact(stockShelf()).foreach(println)
  } finally pool.close()
}
