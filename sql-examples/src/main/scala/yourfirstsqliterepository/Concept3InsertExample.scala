package yourfirstsqliterepository

import zio.blocks.sql.*
import zio.blocks.schema.Schema

/**
 * Build Your First Database Repository on SQLite — Concept 3: Insert Rows with a Repository
 *
 * Derives a Repo for Book, inserts four books and lists them.
 *
 * Run with: cd sql-examples && sbt "runMain
 * yourfirstsqliterepository.Concept3InsertExample"
 */
object Concept3InsertExample extends App {

  case class Book(id: Long, title: String, author: String, year: Int, isbn: Option[String])

  object Book {
    given Schema[Book] = Schema.derived
  }

  val dbFile = java.nio.file.Files.createTempFile("bookshelf", ".db")
  dbFile.toFile.deleteOnExit()

  val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

  val bookTable = Table.derived[Book]

  // Setup: create the table
  transactor.connect {
    bookTable.createTable(SqlDialect.SQLite).update
  }

  val books = Repo.derived[Book, Long]

  val zionomicon         = Book(1L, "Zionomicon", "John A. De Goes", 2024, None)
  val programmingInScala = Book(2L, "Programming in Scala", "Martin Odersky", 2008, Some("9780981531601"))
  val fpInScala          = Book(3L, "Functional Programming in Scala", "Paul Chiusano", 2014, Some("9781617290657"))
  val handsOnScala       = Book(4L, "Hands-on Scala Programming", "Li Haoyi", 2020, None)

  transactor.connect {
    val inserted = books.insert(zionomicon)
    println(s"Rows inserted: $inserted")

    books.insert(programmingInScala)
    books.insert(fpInScala)
    books.insert(handsOnScala)

    println(s"Books on the shelf: ${books.count}")
    books.all.foreach(println)
  }
}
