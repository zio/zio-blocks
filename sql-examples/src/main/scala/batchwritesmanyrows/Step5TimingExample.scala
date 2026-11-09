package batchwritesmanyrows

import zio.blocks.sql.*
import batchwritesmanyrows.Readings.*

/**
 * How to Insert and Update Many Rows in Batches — Timing the Insert Methods
 *
 * Times a loop of insert, insertBatch and insertAll on a file-backed SQLite
 * database, each run in a fresh table. The numbers depend on the machine, the
 * disk and the SQLite build, so treat them as a shape, not a benchmark.
 *
 * Run with: cd sql-examples && sbt "runMain batchwritesmanyrows.Step5TimingExample"
 */
object Step5TimingExample {

  def timeMs[A](body: => A): (A, Double) = {
    val start  = System.nanoTime()
    val result = body
    (result, (System.nanoTime() - start) / 1e6)
  }

  def freshTable(transactor: JdbcTransactor): Unit =
    transactor.connect {
      repo.clear()
      ()
    }

  def main(args: Array[String]): Unit = {
    val transactor = newTransactor()
    transactor.connect(createTable())

    for (n <- List(1000, 5000, 10000)) {
      val rows = sample(1L, n)
      println(s"--- $n rows ---")

      freshTable(transactor)
      val (_, loopMs) = timeMs {
        transactor.transact(rows.foreach(repo.insert))
      }
      println(f"loop of insert, one transaction: $loopMs%8.1f ms")

      freshTable(transactor)
      val (_, batchMs) = timeMs {
        transactor.transact(repo.insertBatch(rows))
      }
      println(f"insertBatch, one transaction:    $batchMs%8.1f ms")

      freshTable(transactor)
      val (_, allMs) = timeMs {
        transactor.transact(repo.insertAll(rows))
      }
      println(f"insertAll, one transaction:      $allMs%8.1f ms")
    }

    val small = sample(100000L, 1000)
    freshTable(transactor)
    val (_, autoMs) = timeMs {
      small.foreach(r => transactor.connect(repo.insert(r)))
    }
    println(f"--- 1000 rows, one transaction per insert: $autoMs%8.1f ms")
  }
}
