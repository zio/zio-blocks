package streamlargequeryresults

import java.nio.file.Files

import zio.blocks.chunk.Chunk
import zio.blocks.sql.*
import zio.blocks.streams.*

/**
 * How to Stream Large Query Results — Complete Example
 *
 * Creates the table, prints the per-title report, writes the CSV export, and
 * deletes its temporary files.
 *
 * Run with: cd sql-examples && sbt "runMain
 * streamlargequeryresults.CompleteExample"
 */
object CompleteExample {
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

      val exportFile = Files.createTempFile("sales-export", ".csv")
      try {
        val exported: Either[Throwable, Unit] = {
          val writer = Files.newBufferedWriter(exportFile)
          try {
            writer.write("id,title,quantity,sold_on")
            writer.newLine()
            transactor.connect {
              allSales
                .queryStream[Sale]
                .run(Sink.foreach[Chunk[Sale]] { chunk =>
                  chunk.foreach { sale =>
                    writer.write(csvLine(sale))
                    writer.newLine()
                  }
                })
            }
          } finally writer.close()
        }

        println(exported)
        println(s"Lines in the file: ${Files.readAllLines(exportFile).size}")
        Files.readAllLines(exportFile).subList(0, 4).forEach(line => println(line))
      } finally Files.deleteIfExists(exportFile)
    }
}
