package upsertandpaginatewithkeysetcursors

import zio.blocks.sql.*
import zio.blocks.schema.Schema

/**
 * How to Upsert Rows and Paginate with Keyset Cursors — Step 5: Compare the
 * Upsert SQL for SQLite and PostgreSQL
 *
 * Prints the upsert statement and the CREATE TABLE statement for both dialects.
 * Needs no database.
 *
 * Run with: cd sql-examples && sbt "runMain
 * upsertandpaginatewithkeysetcursors.Step5DialectSqlExample"
 */
object Step5DialectSqlExample {

  case class Book(id: Long, title: String, isbn: Option[String], price: BigDecimal, stock: Int)

  object Book {
    given Schema[Book] = Schema.derived
  }

  val books = Repo.derived[Book, Long]

  // Prices and stock are fictional
  val zionomicon         = Book(1L, "Zionomicon", None, BigDecimal("39.99"), 5)
  val programmingInScala = Book(2L, "Programming in Scala", Some("9780981531601"), BigDecimal("49.95"), 3)
  val fpInScala          = Book(3L, "Functional Programming in Scala", Some("9781617290657"), BigDecimal("44.99"), 8)

  def main(args: Array[String]): Unit = {
    val upsert = Upsert.insertDoUpdate(books.table, zionomicon, "id")

    println(upsert.sql(SqlDialect.SQLite))
    println(upsert.sql(SqlDialect.PostgreSQL))
    println(upsert.sql(SqlDialect.SQLite) == upsert.sql(SqlDialect.PostgreSQL))
    println(upsert.queryParams)

    println(books.table.createTable(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL))
  }
}
