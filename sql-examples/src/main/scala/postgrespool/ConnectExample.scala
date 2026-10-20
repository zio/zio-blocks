package postgrespool

import zio.blocks.sql.*

/**
 * How to Connect to PostgreSQL with a Connection Pool: Connect and Check the Database
 *
 * Opens the pool, builds a JdbcTransactor on it, runs the health check, prints the server version and closes the pool.
 *
 * Run with: cd sql-examples && sbt "runMain postgrespool.ConnectExample"
 */
object ConnectExample extends App {

  val pool = openBookstorePool()
  try {
    val transactor = JdbcTransactor.postgres(pool)
    println(s"Healthy: ${isHealthy(transactor)}")
    println(s"Server version: ${transactor.connect(serverVersion())}")
  } finally pool.close()
}
