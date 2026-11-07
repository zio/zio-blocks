package testdatabasecode

import scala.util.Try
import zio.blocks.sql.*

/**
 * How to Test Database Code Against SQLite — Create the Schema from Code
 *
 * Prints the `CREATE TABLE` statement derived from `Book`, shows that a table
 * created from it accepts two books with the same ID and can't run
 * `Repo#insertOrUpdate`, and that the
 * hand-written schema in `TestDb.createSchema` rejects the duplicate and still
 * has the columns the derived table expects.
 *
 * Run with: cd sql-examples && sbt "Test/runMain
 * testdatabasecode.SchemaExample"
 */
object SchemaExample extends App {

  val bookTable = Table.derived[Book]

  println(bookTable.createTable(SqlDialect.SQLite).sql(SqlDialect.SQLite))

  TestDb.withTempDb { transactor =>
    transactor.connect {
      bookTable.createTable(SqlDialect.SQLite).update
      Bookshop.books.insert(TestDb.zionomicon)
      Bookshop.books.insert(TestDb.zionomicon)
      println(s"Rows with id 1 in the derived table: ${Bookshop.books.count}")
      println(Try(Bookshop.books.insertOrUpdate(TestDb.zionomicon.copy(stock = 7))))
    }
  }

  TestDb.withTempDb { transactor =>
    transactor.connect {
      TestDb.createSchema()
      Bookshop.books.insert(TestDb.zionomicon)
      println(Try(Bookshop.books.insert(TestDb.zionomicon)))

      val columns = sql"SELECT name FROM pragma_table_info('book')".query[String]
      println(s"Columns match the derived table: ${columns == bookTable.columns.toList}")
    }
  }
}
