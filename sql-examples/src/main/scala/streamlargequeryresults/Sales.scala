package streamlargequeryresults

import java.nio.file.Files
import java.time.LocalDate

import zio.blocks.chunk.Chunk
import zio.blocks.schema.Schema
import zio.blocks.sql.*

/**
 * How to Stream Large Query Results: shared setup
 *
 * The Sale row type with its Schema, the Table and Repo for it, the query the
 * examples stream from, the report helpers, and withSalesDb, which seeds 12,000
 * sales into a temporary SQLite file and deletes the file when the block ends.
 */
case class Sale(id: Long, title: String, quantity: Int, soldOn: LocalDate)

object Sale {
  given Schema[Sale] = Schema.derived
}

val saleTable = Table.derived[Sale]
val sales     = Repo.derived[Sale, Long]

val titles = Vector("Zionomicon", "Programming in Scala", "Functional Programming in Scala")

val saleCount = 12000

val allSales = sql"SELECT id, title, quantity, sold_on FROM sale ORDER BY id"

case class Report(copiesPerTitle: Map[String, Int], rows: Int, chunks: Int, largestChunk: Int)

val emptyReport = Report(Map.empty, 0, 0, 0)

def addChunk(report: Report, chunk: Chunk[Sale]): Report = {
  val copies = chunk.foldLeft(report.copiesPerTitle) { (totals, sale) =>
    totals.updated(sale.title, totals.getOrElse(sale.title, 0) + sale.quantity)
  }
  Report(copies, report.rows + chunk.size, report.chunks + 1, report.largestChunk.max(chunk.size))
}

def csvLine(sale: Sale): String =
  s"${sale.id},${sale.title},${sale.quantity},${sale.soldOn}"

def withSalesDb[A](f: JdbcTransactor => A): A = {
  val dbFile     = Files.createTempFile("bookstore-sales", ".db")
  val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)
  try {
    transactor.transact {
      saleTable.createTable(SqlDialect.SQLite).update
      // In every 7 sales, 3 are of Zionomicon and 2 of each other title
      sales.insertBatch((1 to saleCount).map { i =>
        Sale(i.toLong, titles(i % 7 % 3), 1 + i % 4, LocalDate.of(2026, 1, 1).plusDays((i % 365).toLong))
      })
    }
    f(transactor)
  } finally Files.deleteIfExists(dbFile)
}
