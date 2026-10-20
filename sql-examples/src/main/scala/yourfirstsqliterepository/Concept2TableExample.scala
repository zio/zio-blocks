package yourfirstsqliterepository

import zio.blocks.sql.*
import zio.blocks.schema.Schema

/**
 * Build Your First Database Repository on SQLite — Concept 2: Map a Case Class to a Table
 *
 * Derives a Table for Book, prints its CREATE TABLE statement and creates the table.
 *
 * Run with: cd sql-examples && sbt "runMain
 * yourfirstsqliterepository.Concept2TableExample"
 */
object Concept2TableExample extends App {

  case class Book(id: Long, title: String, author: String, year: Int, isbn: Option[String])

  object Book {
    given Schema[Book] = Schema.derived
  }

  val dbFile = java.nio.file.Files.createTempFile("bookshelf", ".db")
  dbFile.toFile.deleteOnExit()

  val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

  val bookTable = Table.derived[Book]

  println(bookTable.name)
  println(bookTable.createTable(SqlDialect.SQLite).sql(SqlDialect.SQLite))

  transactor.connect {
    bookTable.createTable(SqlDialect.SQLite).update
    val tables = sql"SELECT name FROM sqlite_master WHERE type = 'table'".query[String]
    println(tables)
  }
}
