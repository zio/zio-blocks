package yourfirstsqliterepository

import zio.blocks.schema.Schema
import zio.blocks.sql.*

/**
 * Build Your First Database Repository on SQLite — Concept 4: Find, Update and Delete Rows
 *
 * Finds a book that exists and one that does not, updates a book's authors and deletes a book.
 *
 * Run with: cd sql-examples && sbt "runMain
 * yourfirstsqliterepository.Concept4FindUpdateDeleteExample"
 */
object Concept4FindUpdateDeleteExample extends App {

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

  // Setup: insert the four books
  transactor.connect {
    books.insert(zionomicon)
    books.insert(programmingInScala)
    books.insert(fpInScala)
    books.insert(handsOnScala)
  }

  transactor.connect {
    println(books.find(2L).fold("No book with id 2")(book => s"Found: $book"))
    println(books.find(42L).fold("No book with id 42")(book => s"Found: $book"))
  }

  transactor.connect {
    val updated = books.update(fpInScala.copy(author = "Paul Chiusano and Rúnar Bjarnason"))
    println(s"Rows updated: $updated")
    println(books.find(3L).fold("No book with id 3")(book => s"Authors: ${book.author}"))
  }

  transactor.connect {
    val deleted = books.delete(4L)
    println(s"Rows deleted: $deleted")
    println(s"Books on the shelf: ${books.count}")
  }
}
