package postgrespool

import java.util.concurrent.{ExecutorService, Executors}
import zio.blocks.sql.*

/**
 * How to Connect to PostgreSQL with a Connection Pool: Many Concurrent Callers
 *
 * Runs 50 lookups at once on a pool of 10 connections, each holding its
 * connection for 0.2 seconds, and prints how long they took.
 *
 * Run with: cd sql-examples && sbt "runMain
 * postgrespool.ConcurrentCallersExample"
 */
object ConcurrentCallersExample {

  def runLookups(transactor: JdbcTransactor, executor: ExecutorService, callers: Int): Long = {
    val started = System.nanoTime()
    val lookups = (1 to callers).map { _ =>
      executor.submit[Int](() => transactor.connect(sql"SELECT 1 FROM pg_sleep(0.2)".queryOne[Int].getOrElse(0)))
    }
    lookups.foreach(_.get())
    (System.nanoTime() - started) / 1000000
  }

  def main(args: Array[String]): Unit = {
    val pool = openBookstorePool()
    // On JDK 21 or newer, Executors.newVirtualThreadPerTaskExecutor() gives each caller a virtual thread.
    // This example uses platform threads, so it also compiles on JDK 17.
    val executor = Executors.newFixedThreadPool(50)
    try {
      val transactor = JdbcTransactor.postgres(pool)
      val elapsed    = runLookups(transactor, executor, callers = 50)
      println(s"50 callers took $elapsed ms")
      println(s"Pool size: ${pool.getMaximumPoolSize} connections")
    } finally {
      executor.shutdown()
      pool.close()
    }
  }
}
