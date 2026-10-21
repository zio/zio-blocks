package upsertandpaginatewithkeysetcursors

import zio.blocks.sql.*
import zio.blocks.schema.Schema
import scala.annotation.tailrec

/**
 * How to Upsert Rows and Paginate with Keyset Cursors — Step 6: Walk a Large
 * Table with pageAfter
 *
 * Fills a 10,000-row sales table, walks it in pages of 50, and prints SQLite's
 * query plans for a keyset page and an OFFSET page.
 *
 * Run with: cd sql-examples && sbt "runMain
 * upsertandpaginatewithkeysetcursors.Step6PageAfterExample"
 */
object Step6PageAfterExample {

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

  def main(args: Array[String]): Unit = {
    val dbFile = java.nio.file.Files.createTempFile("bookstore", ".db")
    dbFile.toFile.deleteOnExit()
    val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

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
      println(s"After the last id: ${sales.pageAfter(10000L, 50)}")
    }

    transactor.connect {
      val (pages, rows, sold) = walkSales(0L, 0, 0, 0L)
      println(s"$pages pages, $rows rows, $sold copies sold")
    }

    transactor.connect {
      val plan = (q: Frag) => q.query[(Int, Int, Int, String)].map(_._4)
      println(plan(sql"EXPLAIN QUERY PLAN SELECT * FROM sale WHERE id > 9950 ORDER BY id ASC LIMIT 50"))
      println(plan(sql"EXPLAIN QUERY PLAN SELECT * FROM sale ORDER BY id ASC LIMIT 50 OFFSET 9950"))
    }
  }
}
