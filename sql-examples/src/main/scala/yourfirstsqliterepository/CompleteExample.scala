package yourfirstsqliterepository

import zio.blocks.sql.*
import zio.blocks.schema.Schema
import zio.blocks.maybe.Maybe
import scala.util.{Failure, Success, Try}

/**
 * Build Your First Database Repository on SQLite — Complete Example
 *
 * Connects to SQLite, derives a Table and a Repo, runs CRUD operations, a hand-written query and a transaction that rolls back.
 *
 * Run with: cd sql-examples && sbt "runMain
 * yourfirstsqliterepository.CompleteExample"
 */
object CompleteExample extends App {

  case class Book(id: Long, title: String, author: String, year: Int, isbn: Option[String])

  object Book {
    given Schema[Book] = Schema.derived
  }

  val dbFile = java.nio.file.Files.createTempFile("bookshelf", ".db")
  dbFile.toFile.deleteOnExit()

  val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

  transactor.connect {
    val version = sql"SELECT sqlite_version()".queryOne[String]
    println(s"Connected to SQLite ${version.getOrElse("unknown")}")
  }

  val bookTable = Table.derived[Book]

  println(bookTable.name)
  println(bookTable.createTable(SqlDialect.SQLite).sql(SqlDialect.SQLite))

  transactor.connect {
    bookTable.createTable(SqlDialect.SQLite).update
    val tables = sql"SELECT name FROM sqlite_master WHERE type = 'table'".query[String]
    println(tables)
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

  Try {
    transactor.transact {
      books.insert(handsOnScala)
      println(s"Inside the transaction: ${books.count} books")
      if (books.count > 3) throw new IllegalStateException("The shelf holds at most 3 books")
    }
  } match {
    case Failure(error) => println(s"Transaction failed: ${error.getMessage}")
    case Success(_)     => println("Transaction committed")
  }

  transactor.connect {
    println(s"After the rollback: ${books.count} books")
  }

  transactor.transact {
    books.delete(2L)
    books.insert(handsOnScala)
    println("Swapped Programming in Scala for Hands-on Scala Programming")
  }

  transactor.connect {
    books.all.foreach(println)
  }
}
