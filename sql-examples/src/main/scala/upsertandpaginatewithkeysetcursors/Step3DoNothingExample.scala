package upsertandpaginatewithkeysetcursors

import zio.blocks.sql.*
import zio.blocks.schema.Schema

/**
 * How to Upsert Rows and Paginate with Keyset Cursors — Step 3: Keep Existing
 * Rows Untouched
 *
 * Imports catalog defaults with Upsert.insertDoNothing and prints which rows
 * were inserted.
 *
 * Run with: cd sql-examples && sbt "runMain
 * upsertandpaginatewithkeysetcursors.Step3DoNothingExample"
 */
object Step3DoNothingExample {

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

    transactor.connect {
      sql"""CREATE TABLE book (
        id    INTEGER PRIMARY KEY,
        title TEXT NOT NULL,
        isbn  TEXT,
        price TEXT NOT NULL,
        stock INTEGER NOT NULL
      )""".update
      books.insertOrUpdate(programmingInScala)
      books.insertOrUpdate(fpInScala)
      books.insertOrUpdate(zionomicon.copy(stock = 4))
    }

    val catalogDefaults = List(
      zionomicon,                                                 // stock 5 in the catalog, 4 in the shop
      Book(5L, "Programming Scala", None, BigDecimal("59.99"), 2) // not in the shop yet
    )

    transactor.connect {
      catalogDefaults.foreach { book =>
        val inserted = Upsert.insertDoNothing(books.table, book, "id").update
        println(s"${book.title}: $inserted")
      }
      printBooks()
    }
  }
}
