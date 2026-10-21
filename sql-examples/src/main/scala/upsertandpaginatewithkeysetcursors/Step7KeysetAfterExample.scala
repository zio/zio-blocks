package upsertandpaginatewithkeysetcursors

import zio.blocks.schema.Schema
import zio.blocks.sql.*
import scala.util.Try

/**
 * How to Upsert Rows and Paginate with Keyset Cursors — Step 7: Page Through
 * Your Own Query with keysetAfter
 *
 * Pages through the sales of one book with Frag.keysetAfter.
 *
 * Run with: cd sql-examples && sbt "runMain
 * upsertandpaginatewithkeysetcursors.Step7KeysetAfterExample"
 */
object Step7KeysetAfterExample {

  case class Sale(id: Long, bookId: Long, quantity: Int)

  object Sale {
    given Schema[Sale] = Schema.derived
  }

  val sales = Repo.derived[Sale, Long]

  def salesOfBookAfter(bookId: Long, cursor: Long, limit: Int): Frag =
    sql"SELECT * FROM (SELECT id, book_id, quantity FROM sale WHERE book_id = $bookId) AS s" ++
      Frag.keysetAfter(sales.table, "id", DbValue.DbLong(cursor), limit)

  def main(args: Array[String]): Unit = {
    val dbFile = java.nio.file.Files.createTempFile("bookstore", ".db")
    dbFile.toFile.deleteOnExit()
    val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

    transactor.transact {
      sql"CREATE TABLE sale (id INTEGER PRIMARY KEY, book_id INTEGER NOT NULL, quantity INTEGER NOT NULL)".update
      val generated = (1L to 10000L).map(i => Sale(i, i % 3 + 1, (i % 5 + 1).toInt))
      println(sales.insertBatch(generated))
    }

    println(Frag.keysetAfter(sales.table, "id", DbValue.DbLong(0L), 5).sql(SqlDialect.SQLite))
    println(salesOfBookAfter(2L, 0L, 5).sql(SqlDialect.SQLite))

    transactor.connect {
      val first = salesOfBookAfter(2L, 0L, 5).query[Sale]
      val next  = salesOfBookAfter(2L, first.last.id, 5).query[Sale]
      println(first)
      println(next)
    }

    println(Try(Frag.keysetAfter(sales.table, "sold_at", DbValue.DbLong(0L), 5)))
    println(Try(Frag.keysetAfter(sales.table, "id", DbValue.DbLong(0L), 0)))
  }
}
