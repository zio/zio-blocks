package mapcustomcolumntypes

import zio.blocks.schema.{Schema, SchemaError}
import zio.blocks.sql.*
import scala.util.Try

/**
 * How to Map Custom Column Types — Step 1: Store a Newtype or an Opaque Type
 * in One Column
 *
 * Shows that a one-field case class with a derived Schema is a record (its
 * column becomes `isbn_value` and its value JSON), then stores a validated
 * `Isbn` newtype and an opaque `BookId` as plain columns, queries by ISBN and
 * reads back a row whose ISBN fails validation.
 *
 * Run with: cd sql-examples && sbt "runMain
 * mapcustomcolumntypes.Step1NewtypeExample"
 */
object Step1NewtypeExample {

  // A one-field case class with a derived Schema is a record, not a column
  final case class PlainIsbn(value: String)

  object PlainIsbn {
    given Schema[PlainIsbn] = Schema.derived
  }

  case class PlainBook(id: Long, isbn: PlainIsbn)

  object PlainBook {
    given Schema[PlainBook] = Schema.derived
  }

  // A validated newtype: its Schema is a String with a check on the way in
  final case class Isbn private (value: String)

  object Isbn {
    def parse(input: String): Either[SchemaError, Isbn] = {
      val digits = input.filterNot(c => c == '-' || c == ' ')
      if (digits.length != 13 || !digits.forall(_.isDigit))
        Left(SchemaError.validationFailed(s"ISBN must have 13 digits, got '$input'"))
      else {
        val weighted = digits.init.zipWithIndex.map { case (c, i) => c.asDigit * (if (i % 2 == 0) 1 else 3) }
        if ((10 - weighted.sum % 10) % 10 == digits.last.asDigit) Right(new Isbn(digits))
        else Left(SchemaError.validationFailed(s"ISBN check digit is wrong in '$input'"))
      }
    }

    def unsafe(input: String): Isbn = parse(input).fold(e => throw e, identity)

    given Schema[Isbn] = Schema[String].transform(unsafe, _.value)
  }

  // An opaque type: the macros see through it to Long
  object ids {
    opaque type BookId = Long

    object BookId {
      def apply(value: Long): BookId = value
      def unwrap(id: BookId): Long   = id
    }
  }

  import ids.BookId

  case class Book(id: BookId, title: String, isbn: Isbn)

  object Book {
    given Schema[Book] = Schema.derived
  }

  val books = Repo.derived[Book, BookId]("books", "id", _.id)

  def main(args: Array[String]): Unit = {
    println(Table.derived[PlainBook].columns)
    println(DbCodec.derived[PlainBook].toDbValues(PlainBook(2L, PlainIsbn("9780981531601"))))

    val dbFile = java.nio.file.Files.createTempFile("bookstore", ".db")
    dbFile.toFile.deleteOnExit()
    val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

    transactor.connect {
      sql"""CREATE TABLE books (
        id    INTEGER PRIMARY KEY,
        title TEXT NOT NULL,
        isbn  TEXT NOT NULL
      )""".update
      books.insert(Book(BookId(1L), "Programming in Scala", Isbn.unsafe("978-0-9815316-0-1")))
      books.insert(Book(BookId(2L), "Functional Programming in Scala", Isbn.unsafe("978-1-61729-065-7")))
      println(sql"SELECT id, title, isbn FROM books ORDER BY id".query[(Long, String, String)])
      books.all.foreach(println)

      // The sql interpolator has no parameter instance for Isbn: pass the String
      val isbn = Isbn.unsafe("9780981531601")
      println(sql"SELECT title FROM books WHERE isbn = ${isbn.value}".query[String])

      // Another program writes an ISBN with a wrong check digit
      sql"INSERT INTO books (id, title, isbn) VALUES (3, 'Scala with Cats', '9781617290658')".update
      println(Try(books.all))
      println(books.find(BookId(2L)))
    }
  }
}
