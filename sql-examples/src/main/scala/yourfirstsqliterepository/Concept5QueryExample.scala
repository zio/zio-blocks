package yourfirstsqliterepository

import zio.blocks.sql.*
import zio.blocks.schema.Schema
import zio.blocks.maybe.Maybe

/**
 * Build Your First Database Repository on SQLite — Concept 5: Write Your Own Query
 *
 * Runs a hand-written query with an Int parameter and a queryOne lookup by title.
 *
 * Run with: cd sql-examples && sbt "runMain
 * yourfirstsqliterepository.Concept5QueryExample"
 */
object Concept5QueryExample extends App {

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

  // Setup: apply the update and delete from the previous section
  transactor.connect {
    books.update(fpInScala.copy(author = "Paul Chiusano and Rúnar Bjarnason"))
    books.delete(4L)
  }

  val publishedBefore = 2010

  transactor.connect {
    val classics = sql"SELECT id, title, author, year, isbn FROM book WHERE year < $publishedBefore".query[Book]
    classics.foreach(println)
  }

  def findByTitle(title: String)(using DbCon): Maybe[Book] =
    sql"SELECT id, title, author, year, isbn FROM book WHERE title = $title".queryOne[Book]

  transactor.connect {
    println(findByTitle("Zionomicon").fold("Not on the shelf")(book => s"Found: $book"))
    println(findByTitle("Scala Puzzlers").fold("Not on the shelf")(book => s"Found: $book"))
  }
}
