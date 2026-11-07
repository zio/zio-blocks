package mapcustomcolumntypes

import java.time.Instant
import zio.blocks.schema.{Schema, SchemaError}
import zio.blocks.typeid.TypeId
import zio.blocks.sql.*

/**
 * How to Map Custom Column Types — Complete Example (SQLite)
 *
 * One `books` table with every custom column type from the guide: an opaque
 * `BookId`, a validated `Isbn` newtype, a `Format` enum, `Metadata` as JSON, a
 * `List[String]` of tags as a JSON array and an `Instant` stored as epoch
 * milliseconds. Inserts three books, reads them back and runs one query per
 * column type.
 *
 * Run with: cd sql-examples && sbt "runMain
 * mapcustomcolumntypes.CompleteExample"
 */
object CompleteExample {

  object ids {
    opaque type BookId = Long

    object BookId {
      def apply(value: Long): BookId = value
      def unwrap(id: BookId): Long   = id
    }
  }

  import ids.BookId

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

  enum Format {
    case Hardcover, Paperback, Ebook
  }

  object Format {
    given Schema[Format] = Schema.derived
  }

  case class Metadata(publisher: String, edition: Int, pages: Int)

  object Metadata {
    given Schema[Metadata] = Schema.derived
  }

  case class Book(
    id: BookId,
    isbn: Option[Isbn],
    title: String,
    format: Format,
    metadata: Metadata,
    tags: List[String],
    publishedAt: Instant
  )

  object Book {
    given Schema[Book] = Schema.derived
  }

  // Columns taken from the codec, so the Repo writes what the codec writes.
  // ColumnMeta's type is only read by createTable, which we don't call.
  def tableFor[A](name: String, codec: DbCodec[A]): Table[A] =
    Table(name, codec, codec.columns.map(column => ColumnMeta(column, DbValue.DbString(""), nullable = false)))

  // Read the column as Option[String] first, so NULL never reaches Isbn.unsafe
  val optionalIsbn: DbCodec[Option[Isbn]] =
    DbCodec[Option[String]].transform(_.map(Isbn.unsafe))(_.map(_.value))

  val bookCodec: DbCodec[Book] = DbCodec.derivedWith[Book](_.instance(TypeId.of[Book], "isbn", optionalIsbn))
  val books                    = Repo(tableFor("books", bookCodec), "id", DbCodec[BookId], (book: Book) => book.id)

  // Page counts and dates are illustrative
  val shelf = List(
    Book(
      BookId(1L),
      None,
      "Zionomicon",
      Format.Ebook,
      Metadata("Ziverge", 1, 700),
      List("zio", "effects", "concurrency"),
      Instant.parse("2021-07-15T09:30:00Z")
    ),
    Book(
      BookId(2L),
      Some(Isbn.unsafe("978-0-9815316-0-1")),
      "Programming in Scala",
      Format.Paperback,
      Metadata("Artima", 1, 776),
      List("scala", "fundamentals"),
      Instant.parse("2008-11-26T00:00:00Z")
    ),
    Book(
      BookId(3L),
      Some(Isbn.unsafe("978-1-61729-065-7")),
      "Functional Programming in Scala",
      Format.Paperback,
      Metadata("Manning", 1, 320),
      List("scala", "functional-programming"),
      Instant.parse("2014-09-14T00:00:00Z")
    )
  )

  def main(args: Array[String]): Unit = {
    val dbFile = java.nio.file.Files.createTempFile("bookstore", ".db")
    dbFile.toFile.deleteOnExit()
    val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

    transactor.transact {
      sql"""CREATE TABLE books (
        id           INTEGER PRIMARY KEY,
        isbn         TEXT,
        title        TEXT NOT NULL,
        format       TEXT NOT NULL,
        metadata     TEXT NOT NULL,
        tags         TEXT NOT NULL,
        published_at INTEGER NOT NULL
      )""".update
      books.insertBatch(shelf)
    }

    transactor.connect {
      println(sql"SELECT * FROM books WHERE id = 2".query[(Long, String, String, String, String, String, Long)])
      println(books.all == shelf)
      println(books.find(BookId(1L)))

      val format = Format.Paperback
      println(sql"SELECT title FROM books WHERE format = $format ORDER BY id".query[String])

      val publisher = "Ziverge"
      println(sql"SELECT title FROM books WHERE json_extract(metadata, '$$.publisher') = $publisher".query[String])

      val tag = "scala"
      println(
        sql"SELECT title FROM books WHERE EXISTS (SELECT 1 FROM json_each(books.tags) WHERE value = $tag) ORDER BY id"
          .query[String]
      )

      val cutoff = Instant.parse("2010-01-01T00:00:00Z")
      println(sql"SELECT title FROM books WHERE published_at >= $cutoff ORDER BY published_at".query[String])
    }
  }
}
