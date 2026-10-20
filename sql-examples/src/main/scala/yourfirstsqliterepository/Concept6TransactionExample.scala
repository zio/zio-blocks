package yourfirstsqliterepository

import zio.blocks.sql.*
import zio.blocks.schema.Schema
import scala.util.{Failure, Success, Try}

/**
 * Build Your First Database Repository on SQLite — Concept 6: Group Writes in a Transaction
 *
 * Shows a transaction that rolls back after an exception and one that commits two writes together.
 *
 * Run with: cd sql-examples && sbt "runMain
 * yourfirstsqliterepository.Concept6TransactionExample"
 */
object Concept6TransactionExample extends App {

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
