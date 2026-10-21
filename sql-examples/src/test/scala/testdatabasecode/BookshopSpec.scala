package testdatabasecode

import scala.util.Try
import zio.*
import zio.blocks.maybe.Maybe
import zio.test.*
import zio.blocks.sql.*

import TestDb.*

/**
 * How to Test Database Code Against SQLite — the complete spec
 *
 * Every test runs against its own SQLite file, created by `TestDb.withTestDb`
 * with the schema and the three fixture books, and deleted afterwards.
 *
 * Run with: cd sql-examples && sbt "testOnly testdatabasecode.BookshopSpec"
 */
object BookshopSpec extends ZIOSpecDefault {

  private def dbTest(label: String)(body: JdbcTransactor => TestResult) =
    test(label)(ZIO.attemptBlocking(withTestDb(body)))

  private val handsOnScala = Book(4L, "Hands-on Scala Programming", "Li Haoyi", 2020, None, 2)

  def spec = suite("Bookshop")(
    dbTest("finds a book by its title") { transactor =>
      val found   = transactor.connect(Bookshop.findByTitle("Zionomicon"))
      val missing = transactor.connect(Bookshop.findByTitle("Scala Puzzlers"))
      assertTrue(found == Maybe.present(zionomicon), missing == Maybe.absent[Book])
    },
    dbTest("restock adds copies to one book only") { transactor =>
      val updated = transactor.transact(Bookshop.restock(fpInScala.id, 4))
      val shelf   = transactor.connect(Bookshop.books.all.sortBy(_.id))
      assertTrue(updated == 1, shelf == List(zionomicon, programmingInScala, fpInScala.copy(stock = 4)))
    },
    dbTest("a failing insert rolls back the books added before it") { transactor =>
      val result = Try(transactor.transact(Bookshop.addAll(List(handsOnScala, zionomicon))))
      val shelf  = transactor.connect(Bookshop.books.all.sortBy(_.id))
      assertTrue(result.isFailure, shelf == fixtures)
    },
    dbTest("the hand-written table has the columns Book needs") { transactor =>
      val columns = transactor.connect(sql"SELECT name FROM pragma_table_info('book')".query[String])
      assertTrue(columns == Table.derived[Book].columns.toList)
    }
  )
}
