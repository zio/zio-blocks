package postgrespool

import com.zaxxer.hikari.HikariDataSource
import java.sql.SQLException
import zio.blocks.sql.*

/**
 * How to Connect to PostgreSQL with a Connection Pool: Pool Exhaustion
 *
 * Uses a pool of one connection and borrows a second connection inside a
 * transaction, so the second borrow times out.
 *
 * Run with: cd sql-examples && sbt "runMain postgrespool.PoolExhaustionExample"
 */
object PoolExhaustionExample extends App {

  val config = bookstorePoolConfig()
  config.setMaximumPoolSize(1)
  config.setConnectionTimeout(1000)

  val pool = new HikariDataSource(config)
  try {
    val transactor = JdbcTransactor.postgres(pool)
    try
      transactor.transact {
        transactor.connect(sql"SELECT 1".queryOne[Int].getOrElse(0))
      }
    catch {
      case e: SQLException => println(s"${e.getClass.getSimpleName}: ${e.getMessage}")
    }
  } finally pool.close()
}
