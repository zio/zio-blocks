package streamlargequeryresults

import zio.blocks.streams.*
import zio.blocks.sql.*

/**
 * How to Stream Large Query Results — Step 6: Handle Query and Decoding Errors
 *
 * Shows the Left produced by a misspelled column and by a row that can't be
 * decoded.
 *
 * Run with: cd sql-examples && sbt "runMain
 * streamlargequeryresults.Step6ErrorsExample"
 */
object Step6ErrorsExample {
  def main(args: Array[String]): Unit =
    withSalesDb { transactor =>
      val badColumn = transactor.connect {
        sql"SELECT id, titel, quantity, sold_on FROM sale"
          .queryStream[Sale]
          .runFold(0)((rows, chunk) => rows + chunk.size)
      }

      badColumn match {
        case Left(error) => println(s"Query failed: ${error.getMessage}")
        case Right(rows) => println(s"Read $rows rows")
      }

      val badRow = transactor.connect {
        sql"SELECT id, title, quantity, title AS sold_on FROM sale"
          .queryStream[Sale]
          .runFold(0)((rows, chunk) => rows + chunk.size)
      }

      badRow.left.foreach(error => println(s"${error.getClass.getSimpleName}: ${error.getMessage}"))
    }
}
