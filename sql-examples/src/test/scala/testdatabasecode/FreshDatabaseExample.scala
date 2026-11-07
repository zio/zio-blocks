package testdatabasecode

import java.nio.file.{Files, Paths}
import scala.util.Try
import zio.blocks.sql.*

/**
 * How to Test Database Code Against SQLite — Give Each Test Its Own Database
 *
 * Shows that `jdbc:sqlite::memory:` loses its tables between blocks, then runs
 * two "tests" with `TestDb.withTempDb` and checks that each one used its own
 * file and that no file is left behind.
 *
 * Run with: cd sql-examples && sbt "Test/runMain
 * testdatabasecode.FreshDatabaseExample"
 */
object FreshDatabaseExample extends App {

  val inMemory = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)

  inMemory.connect(TestDb.createSchema())
  println(Try(inMemory.connect(Bookshop.books.count)))

  val usedFiles = List("first test", "second test").map { _ =>
    TestDb.withTempDb { transactor =>
      transactor.connect(sql"SELECT file FROM pragma_database_list WHERE name = 'main'".queryOne[String].getOrElse(""))
    }
  }

  println(s"Each test used its own file: ${usedFiles.distinct.size == usedFiles.size}")
  println(s"Files left behind: ${usedFiles.count(file => Files.exists(Paths.get(file)))}")
}
