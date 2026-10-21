package streamlargequeryresults

import zio.blocks.streams.*
import zio.blocks.sql.*

/**
 * How to Stream Large Query Results — Step 1: Aggregate a Large Table One Chunk
 * at a Time
 *
 * Folds the 12,000 sales into copies sold per title with runFold, one chunk at
 * a time.
 *
 * Run with: cd sql-examples && sbt "runMain
 * streamlargequeryresults.Step1AggregateExample"
 */
object Step1AggregateExample {
  def main(args: Array[String]): Unit =
    withSalesDb { transactor =>
      val report: Either[Throwable, Report] =
        transactor.connect {
          allSales.queryStream[Sale].runFold(emptyReport)(addChunk)
        }

      report.foreach { r =>
        r.copiesPerTitle.toList.sorted.foreach((title, copies) => println(s"$title: $copies copies"))
        println(s"${r.rows} rows in ${r.chunks} chunks, at most ${r.largestChunk} rows per chunk")
      }
    }
}
