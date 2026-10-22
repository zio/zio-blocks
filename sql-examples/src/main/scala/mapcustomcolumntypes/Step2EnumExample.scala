package mapcustomcolumntypes

import zio.blocks.schema.{Modifier, Schema}
import zio.blocks.sql.*
import scala.util.Try

/**
 * How to Map Custom Column Types — Step 2: Store an Enum as Its Case Name
 *
 * Stores a Scala 3 enum as the name of its case in a TEXT column, renames the
 * stored value of one case with `@Modifier.rename`, and reads back a row whose
 * value matches no case.
 *
 * Run with: cd sql-examples && sbt "runMain
 * mapcustomcolumntypes.Step2EnumExample"
 */
object Step2EnumExample {

  enum Format {
    case Hardcover, Paperback, Ebook
  }

  object Format {
    given Schema[Format] = Schema.derived
  }

  // The same cases, stored in lower case to match an existing table
  enum LegacyFormat {
    @Modifier.rename("hardcover") case Hardcover
    @Modifier.rename("paperback") case Paperback
    @Modifier.rename("ebook") case Ebook
  }

  object LegacyFormat {
    given Schema[LegacyFormat] = Schema.derived
  }

  case class Book(id: Long, title: String, format: Format)

  object Book {
    given Schema[Book] = Schema.derived
  }

  case class LegacyBook(id: Long, title: String, format: LegacyFormat)

  object LegacyBook {
    given Schema[LegacyBook] = Schema.derived
  }

  val books       = Repo.derived[Book, Long]("books", "id", _.id)
  val legacyBooks = Repo.derived[LegacyBook, Long]("legacy_books", "id", _.id)

  def main(args: Array[String]): Unit = {
    val dbFile = java.nio.file.Files.createTempFile("bookstore", ".db")
    dbFile.toFile.deleteOnExit()
    val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

    transactor.connect {
      sql"CREATE TABLE books (id INTEGER PRIMARY KEY, title TEXT NOT NULL, format TEXT NOT NULL)".update
      books.insert(Book(1L, "Zionomicon", Format.Ebook))
      books.insert(Book(2L, "Programming in Scala", Format.Paperback))
      println(sql"SELECT title, format FROM books ORDER BY id".query[(String, String)])
      println(sql"SELECT title FROM books WHERE format = ${Format.Ebook}".query[String])

      sql"CREATE TABLE legacy_books (id INTEGER PRIMARY KEY, title TEXT NOT NULL, format TEXT NOT NULL)".update
      legacyBooks.insert(LegacyBook(1L, "Zionomicon", LegacyFormat.Ebook))
      println(sql"SELECT title, format FROM legacy_books".query[(String, String)])
      println(legacyBooks.all)

      // Another program adds a format the enum doesn't know
      sql"INSERT INTO books (id, title, format) VALUES (3, 'Functional Programming in Scala', 'Audiobook')".update
      println(Try(books.all))
    }
  }
}
