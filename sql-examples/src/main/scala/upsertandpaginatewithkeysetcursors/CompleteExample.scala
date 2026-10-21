package upsertandpaginatewithkeysetcursors

import zio.blocks.sql.*
import zio.blocks.schema.Schema
import scala.annotation.tailrec

/**
 * How to Upsert Rows and Paginate with Keyset Cursors — Complete Example
 *
 * Creates the keyed inventory table, applies a supplier batch, imports catalog
 * defaults with DO NOTHING, applies a price-and-stock feed, then walks the
 * 10,000-row sales log with pageAfter and through one book's sales with
 * keysetAfter.
 *
 * Run with: cd sql-examples && sbt "runMain
 * upsertandpaginatewithkeysetcursors.CompleteExample"
 */
object CompleteExample {

  case class Book(id: Long, title: String, isbn: Option[String], price: BigDecimal, stock: Int)

  object Book {
    given Schema[Book] = Schema.derived
  }

  val books = Repo.derived[Book, Long]

  // Prices and stock are fictional
  val zionomicon         = Book(1L, "Zionomicon", None, BigDecimal("39.99"), 5)
  val programmingInScala = Book(2L, "Programming in Scala", Some("9780981531601"), BigDecimal("49.95"), 3)
  val fpInScala          = Book(3L, "Functional Programming in Scala", Some("9781617290657"), BigDecimal("44.99"), 8)

  case class Sale(id: Long, bookId: Long, quantity: Int)

  object Sale {
    given Schema[Sale] = Schema.derived
  }

  val sales = Repo.derived[Sale, Long]

  @tailrec
  def walkSales(cursor: Long, pages: Int, rows: Int, sold: Long)(using DbCon): (Int, Int, Long) = {
    val page = sales.pageAfter(cursor, 50)
    if (page.isEmpty) (pages, rows, sold)
    else walkSales(page.last.id, pages + 1, rows + page.size, sold + page.map(_.quantity).sum)
  }

  def salesOfBookAfter(bookId: Long, cursor: Long, limit: Int): Frag =
    sql"SELECT * FROM (SELECT id, book_id, quantity FROM sale WHERE book_id = $bookId) AS s" ++
      Frag.keysetAfter(sales.table, "id", DbValue.DbLong(cursor), limit)

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
      println(books.insertOrUpdate(zionomicon.copy(stock = 4)))
      println(books.insertOrUpdate(programmingInScala))
      println(books.insertOrUpdate(fpInScala))
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

    val supplierRow   = zionomicon.copy(title = "ZIONOMICON (PAPERBACK)", price = BigDecimal("34.99"), stock = 20)
    val priceAndStock = Upsert.insertDoUpdate(books.table, supplierRow, "id", Seq("price", "stock"))

    println(priceAndStock.sql(SqlDialect.SQLite))

    transactor.connect {
      println(priceAndStock.update)
      printBooks()
    }

    transactor.transact {
      sql"CREATE TABLE sale (id INTEGER PRIMARY KEY, book_id INTEGER NOT NULL, quantity INTEGER NOT NULL)".update
      val generated = (1L to 10000L).map(i => Sale(i, i % 3 + 1, (i % 5 + 1).toInt))
      println(sales.insertBatch(generated))
    }

    transactor.connect {
      val first  = sales.pageAfter(0L, 50)
      val second = sales.pageAfter(first.last.id, 50)
      println(s"First page:  ${first.size} rows, ids ${first.head.id} to ${first.last.id}")
      println(s"Second page: ${second.size} rows, ids ${second.head.id} to ${second.last.id}")
      val (pages, rows, sold) = walkSales(0L, 0, 0, 0L)
      println(s"$pages pages, $rows rows, $sold copies sold")
    }

    transactor.connect {
      val first = salesOfBookAfter(2L, 0L, 5).query[Sale]
      val next  = salesOfBookAfter(2L, first.last.id, 5).query[Sale]
      println(first)
      println(next)
    }
  }
}
