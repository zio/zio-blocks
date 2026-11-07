package writejoinsandinspectgeneratedsql

import zio.blocks.schema.Schema
import zio.blocks.sql.query.*
import zio.blocks.sql.*

case class Author(authorId: Int, name: String, born: Option[Int])
object Author {
  given Schema[Author] = Schema.derived
}

case class Book(bookId: Int, title: String, year: Int, firstAuthorId: Int)
object Book {
  given Schema[Book] = Schema.derived
}

case class BookColumns(bookId: Option[Int], title: Option[String], year: Option[Int], firstAuthorId: Option[Int]) {
  def toBook: Option[Book] =
    for {
      id     <- bookId
      t      <- title
      y      <- year
      author <- firstAuthorId
    } yield Book(id, t, y, author)
}
object BookColumns {
  given Schema[BookColumns] = Schema.derived
}

/**
 * How to Write Joins and Inspect the Generated SQL: the library tables
 *
 * Defines the `author` and `book` tables, the `Rel`s that link them, and a
 * `Library.open()` that creates and fills a temporary SQLite database. Shared
 * by every example in this package; it has no `main` of its own.
 */
object Library {
  val authors = Table.derived[Author]
  val books   = Table.derived[Book]

  val bookAuthor  = Rel.manyToOne(books, _.firstAuthorId, authors, _.authorId)
  val authorBooks = Rel.oneToMany(authors, _.authorId, books, _.firstAuthorId)

  /**
   * Creates a temporary SQLite file with both tables, inserts four authors and
   * four books, and returns its transactor.
   */
  def open(): JdbcTransactor = {
    val dbFile = java.nio.file.Files.createTempFile("library", ".db")
    dbFile.toFile.deleteOnExit()
    val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

    transactor.connect {
      sql"""CREATE TABLE author (
        author_id INTEGER PRIMARY KEY,
        name      TEXT NOT NULL,
        born      INTEGER
      )""".update
      sql"""CREATE TABLE book (
        book_id         INTEGER PRIMARY KEY,
        title           TEXT NOT NULL,
        year            INTEGER NOT NULL,
        first_author_id INTEGER NOT NULL REFERENCES author (author_id)
      )""".update
    }

    val authorRepo = Repo.derived[Author, Int]
    val bookRepo   = Repo.derived[Book, Int]

    transactor.connect {
      authorRepo.insert(Author(1, "Martin Odersky", Some(1958)))
      authorRepo.insert(Author(2, "John A. De Goes", None))
      authorRepo.insert(Author(3, "Paul Chiusano", None))
      authorRepo.insert(Author(4, "Li Haoyi", None))

      bookRepo.insert(Book(1, "Zionomicon", 2024, 2))
      bookRepo.insert(Book(2, "Programming in Scala", 2008, 1))
      bookRepo.insert(Book(3, "Functional Programming in Scala", 2014, 3))
      bookRepo.insert(Book(4, "Programming in Scala, Fifth Edition", 2021, 1))
    }

    transactor
  }
}
