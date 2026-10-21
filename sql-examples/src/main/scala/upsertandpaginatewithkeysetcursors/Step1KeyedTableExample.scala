package upsertandpaginatewithkeysetcursors

import zio.blocks.sql.*
import zio.blocks.schema.Schema
import scala.util.Try

/**
 * How to Upsert Rows and Paginate with Keyset Cursors — Step 1: Create a Table
 * with a Real Primary Key
 *
 * Shows the upsert failing on the table createTable makes, then succeeding on a
 * table with a primary key.
 *
 * Run with: cd sql-examples && sbt "runMain
 * upsertandpaginatewithkeysetcursors.Step1KeyedTableExample"
 */
object Step1KeyedTableExample {

  case class Book(id: Long, title: String, isbn: Option[String], price: BigDecimal, stock: Int)

  object Book {
    given Schema[Book] = Schema.derived
  }

  val books = Repo.derived[Book, Long]

  // Prices and stock are fictional
  val zionomicon         = Book(1L, "Zionomicon", None, BigDecimal("39.99"), 5)
  val programmingInScala = Book(2L, "Programming in Scala", Some("9780981531601"), BigDecimal("49.95"), 3)
  val fpInScala          = Book(3L, "Functional Programming in Scala", Some("9781617290657"), BigDecimal("44.99"), 8)

  def printBooks()(using DbCon): Unit =
    sql"SELECT id, title, isbn, price, stock FROM book ORDER BY id".query[Book].foreach { b =>
      println(f"${b.id}%2d  ${b.title}%-32s ${b.isbn.getOrElse("-")}%-14s ${b.price}%6s  stock ${b.stock}%2d")
    }

  def main(args: Array[String]): Unit = {
    val dbFile = java.nio.file.Files.createTempFile("bookstore", ".db")
    dbFile.toFile.deleteOnExit()
    val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

    println(books.table.createTable(SqlDialect.SQLite).sql(SqlDialect.SQLite))

    transactor.connect {
      books.table.createTable(SqlDialect.SQLite).update
      println(Try(books.insertOrUpdate(zionomicon)))
      sql"DROP TABLE book".update
    }

    transactor.connect {
      sql"""CREATE TABLE book (
        id    INTEGER PRIMARY KEY,
        title TEXT NOT NULL,
        isbn  TEXT,
        price TEXT NOT NULL,
        stock INTEGER NOT NULL
      )""".update
      println(Try(books.insertOrUpdate(zionomicon)))
      printBooks()
    }
  }
}
