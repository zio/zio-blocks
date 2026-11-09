package batchwritesmanyrows

import scala.util.Try
import zio.blocks.sql.*
import batchwritesmanyrows.Readings.*

/**
 * How to Insert and Update Many Rows in Batches — Complete Example
 *
 * Imports 10,000 readings in chunks of 1,000 (one transaction per chunk), flags
 * the hot ones with one UPDATE, then applies a recalibration feed with
 * insertOrUpdateBatch. A bad chunk at the end shows that earlier chunks stay
 * written.
 *
 * Run with: cd sql-examples && sbt "runMain
 * batchwritesmanyrows.CompleteExample"
 */
object CompleteExample {

  def importInChunks(transactor: JdbcTransactor, rows: List[Reading], chunkSize: Int): Int = {
    var written = 0
    rows.grouped(chunkSize).foreach { chunk =>
      written += transactor.transact(repo.insertBatch(chunk))
    }
    written
  }

  def main(args: Array[String]): Unit = {
    val transactor = newTransactor()
    transactor.connect(createTable())

    val feed = sample(1L, 10000)
    println(s"imported ${importInChunks(transactor, feed, 1000)} readings in chunks of 1000")

    transactor.transact {
      val threshold = 30.0
      val flagged   = sql"UPDATE reading SET flagged = 1 WHERE celsius > $threshold".update
      println(s"flagged $flagged readings above $threshold")
    }

    val recalibration = transactor.connect(repo.all.take(1000)).map(r => r.copy(celsius = r.celsius - 0.2))
    transactor.transact {
      println(s"recalibration batch: ${repo.insertOrUpdateBatch(recalibration)} rows affected")
    }

    val badChunk = sample(10001L, 2) ++ sample(10002L, 1) ++ sample(5L, 1)
    Try(importInChunks(transactor, badChunk, 2)) match {
      case scala.util.Success(n) => println(s"late feed imported $n readings")
      case scala.util.Failure(e) => println(s"late feed stopped: ${e.getMessage}")
    }

    transactor.connect {
      println(s"readings in table: ${repo.count}")
      println(s"flagged in table: ${sql"SELECT COUNT(*) FROM reading WHERE flagged = 1".query[Long].head}")
    }
  }
}
