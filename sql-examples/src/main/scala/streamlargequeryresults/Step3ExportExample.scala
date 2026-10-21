package streamlargequeryresults

import java.nio.file.Files

import zio.blocks.chunk.Chunk
import zio.blocks.sql.*
import zio.blocks.streams.*

/**
 * How to Stream Large Query Results — Step 3: Export Every Row to a CSV File
 *
 * Writes every sale to a CSV file through Sink.foreach, then prints the line
 * count and the first lines of the file.
 *
 * Run with: cd sql-examples && sbt "runMain
 * streamlargequeryresults.Step3ExportExample"
 */
object Step3ExportExample {
  def main(args: Array[String]): Unit =
    withSalesDb { transactor =>
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
