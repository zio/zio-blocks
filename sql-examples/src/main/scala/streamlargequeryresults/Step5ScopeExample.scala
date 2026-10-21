package streamlargequeryresults

import zio.blocks.chunk.Chunk
import zio.blocks.streams.*
import zio.blocks.sql.*

/**
 * How to Stream Large Query Results — Step 5: Keep the Stream Inside the
 * Connection Block
 *
 * Returns a stream out of its connect block and fails on the first pull, then
 * runs the terminal operation inside the block.
 *
 * Run with: cd sql-examples && sbt "runMain
 * streamlargequeryresults.Step5ScopeExample"
 */
object Step5ScopeExample {
  def main(args: Array[String]): Unit =
    withSalesDb { transactor =>
      val escaped: Stream[Throwable, Chunk[Sale]] =
        transactor.connect(allSales.queryStream[Sale])

      println(escaped.runFold(0)((rows, chunk) => rows + chunk.size))

      println(transactor.connect(allSales.queryStream[Sale].runFold(0)((rows, chunk) => rows + chunk.size)))
    }
}
