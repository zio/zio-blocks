package streamlargequeryresults

import java.nio.file.Files

import zio.blocks.streams.*
import zio.blocks.sql.*

/**
 * How to Stream Large Query Results — Step 4: Filter and Reshape Rows on the
 * Way Out
 *
 * Exports only the Zionomicon sales as date,copies lines, written in batches of
 * 500 lines.
 *
 * Run with: cd sql-examples && sbt "runMain
 * streamlargequeryresults.Step4TransformExample"
 */
object Step4TransformExample {
  def main(args: Array[String]): Unit =
    withSalesDb { transactor =>
      val zionomiconFile = Files.createTempFile("zionomicon-sales", ".csv")
      try {
        val zionomiconBatches: Either[Throwable, Long] = {
          val writer = Files.newBufferedWriter(zionomiconFile)
          try
            transactor.connect {
              allSales
                .queryChunked[Sale](1000)
                .flatMap(chunk => Stream.fromChunk(chunk))
                .filter(_.title == "Zionomicon")
                .map(sale => s"${sale.soldOn},${sale.quantity}")
                .grouped(500)
                .tapEach(lines => lines.foreach { line => writer.write(line); writer.newLine() })
                .count
            }
          finally writer.close()
        }

        println(s"Batches written: $zionomiconBatches")
        println(s"Lines in the file: ${Files.readAllLines(zionomiconFile).size}")
        Files.readAllLines(zionomiconFile).subList(0, 3).forEach(line => println(line))
      } finally Files.deleteIfExists(zionomiconFile)
    }
}
