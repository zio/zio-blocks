package upsertandpaginatewithkeysetcursors

import zio.blocks.schema.Schema
import zio.blocks.sql.*

/**
 * How to Upsert Rows and Paginate with Keyset Cursors — Step 2: Insert or
 * Update Books
 *
 * Saves single books with insertOrUpdate, then applies a nightly supplier batch
 * with insertOrUpdateBatch.
 *
 * Run with: cd sql-examples && sbt "runMain
 * upsertandpaginatewithkeysetcursors.Step2InsertOrUpdateExample"
 */
object Step2InsertOrUpdateExample {

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
      books.insertOrUpdate(zionomicon)
    }

    transactor.connect {
      println(books.insertOrUpdate(programmingInScala))
      println(books.insertOrUpdate(fpInScala))
      println(books.insertOrUpdate(zionomicon.copy(stock = 4)))
      printBooks()
    }

    val supplierFeed = List(
      programmingInScala.copy(price = BigDecimal("44.95")),                // price drop
      fpInScala.copy(stock = 12),                                          // restock
      Book(4L, "Hands-on Scala Programming", None, BigDecimal("39.00"), 6) // new title
    )

    transactor.transact {
      println("Before:")
      printBooks()
      println(s"Rows affected: ${books.insertOrUpdateBatch(supplierFeed)}")
      println("After:")
      printBooks()
    }
  }
}
