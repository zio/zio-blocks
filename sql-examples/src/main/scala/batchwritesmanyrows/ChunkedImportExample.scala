package batchwritesmanyrows

import scala.util.Try
import zio.blocks.sql.*
import batchwritesmanyrows.Readings.*

/**
 * How to Insert and Update Many Rows in Batches — Importing in Chunks
 *
 * Imports a feed one chunk at a time, one transaction per chunk. The feed
 * repeats ID 3 in its third chunk, so that chunk fails and rolls back while the
 * two chunks before it stay committed.
 *
 * Run with: cd sql-examples && sbt "runMain
 * batchwritesmanyrows.ChunkedImportExample"
 */
object ChunkedImportExample {

  /**
   * Inserts `rows` in chunks of `chunkSize`, each chunk in its own transaction.
   */
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

    val feed = sample(1L, 8) ++ sample(3L, 1) ++ sample(9L, 3)
    println(s"feed has ${feed.size} readings, chunks of 4")

    Try(importInChunks(transactor, feed, 4)) match {
      case scala.util.Success(n) => println(s"imported $n readings")
      case scala.util.Failure(e) => println(s"import stopped: ${e.getMessage}")
    }

    transactor.connect {
      println(s"readings in table: ${repo.count}")
      println(s"ids in table: ${repo.all.map(_.id).mkString(", ")}")
    }
  }
}
