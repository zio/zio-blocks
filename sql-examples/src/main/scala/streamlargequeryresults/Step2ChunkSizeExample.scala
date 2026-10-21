package streamlargequeryresults

import scala.util.Try

import zio.blocks.sql.*
import zio.blocks.streams.*

/**
 * How to Stream Large Query Results — Step 2: Choose a Chunk Size
 *
 * Runs the same fold with the default chunk size of 64, with 1,000 and with 10,
 * and shows that a chunk size of 0 is rejected up front.
 *
 * Run with: cd sql-examples && sbt "runMain
 * streamlargequeryresults.Step2ChunkSizeExample"
 */
object Step2ChunkSizeExample {
  def main(args: Array[String]): Unit =
    withSalesDb { transactor =>
      def show(label: String, report: Either[Throwable, Report]): Unit =
        report.foreach(r =>
          println(s"$label: ${r.rows} rows in ${r.chunks} chunks, at most ${r.largestChunk} rows per chunk")
        )

      val defaultChunks: Either[Throwable, Report] =
        transactor.connect {
          allSales.queryStream[Sale].runFold(emptyReport)(addChunk)
        }

      val bigChunks: Either[Throwable, Report] =
        transactor.connect {
          allSales.queryChunked[Sale](1000).runFold(emptyReport)(addChunk)
        }

      val smallChunks: Either[Throwable, Report] =
        transactor.connect {
          allSales.queryChunked[Sale](10).runFold(emptyReport)(addChunk)
        }

      show("Default (64)", defaultChunks)
      show("1000", bigChunks)
      show("10", smallChunks)

      println(Try(transactor.connect(allSales.queryChunked[Sale](0))))
    }
}
