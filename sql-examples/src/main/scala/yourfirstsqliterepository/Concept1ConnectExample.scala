package yourfirstsqliterepository

import zio.blocks.schema.Schema
import zio.blocks.sql.*

/**
 * Build Your First Database Repository on SQLite — Concept 1: Connect to SQLite
 *
 * Creates a transactor for a temporary SQLite file and prints the SQLite version.
 *
 * Run with: cd sql-examples && sbt "runMain
 * yourfirstsqliterepository.Concept1ConnectExample"
 */
object Concept1ConnectExample extends App {

  val dbFile = java.nio.file.Files.createTempFile("bookshelf", ".db")
  dbFile.toFile.deleteOnExit()

  val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

  transactor.connect {
    val version = sql"SELECT sqlite_version()".queryOne[String]
    println(s"Connected to SQLite ${version.getOrElse("unknown")}")
  }
}
