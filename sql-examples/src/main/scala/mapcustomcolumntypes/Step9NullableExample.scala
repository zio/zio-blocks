package mapcustomcolumntypes

import zio.blocks.schema.{Schema, SchemaError}
import zio.blocks.schema.json.{JsonCodec, JsonFormat}
import zio.blocks.typeid.TypeId
import zio.blocks.sql.*
import scala.util.Try

/**
 * How to Map Custom Column Types — Step 9: Make a Custom Column Nullable
 *
 * Shows what a non-optional field reads from SQL NULL, that the derived codec
 * fails on NULL for an `Option` of a newtype and rejects an `Option` of a
 * nested case class, then fixes both fields with `DbCodec.derivedWith`.
 *
 * Run with: cd sql-examples && sbt "runMain
 * mapcustomcolumntypes.Step9NullableExample"
 */
object Step9NullableExample {

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

  case class Metadata(publisher: String, edition: Int, pages: Int)

  object Metadata {
    given Schema[Metadata]    = Schema.derived
    given JsonCodec[Metadata] = Schema[Metadata].derive(JsonFormat)
  }

  case class Book(id: Long, title: String, isbn: Option[Isbn], metadata: Option[Metadata])

  object Book {
    given Schema[Book] = Schema.derived
  }

  case class ShelfBook(id: Long, title: String, isbn: Option[Isbn])

  object ShelfBook {
    given Schema[ShelfBook] = Schema.derived
  }

  def tableFor[A](name: String, codec: DbCodec[A]): Table[A] =
    Table(name, codec, codec.columns.map(column => ColumnMeta(column, DbValue.DbString(""), nullable = false)))

  // Read the column as Option[String] first, so NULL never reaches Isbn.unsafe
  val optionalIsbn: DbCodec[Option[Isbn]] =
    DbCodec[Option[String]].transform(_.map(Isbn.unsafe))(_.map(_.value))

  val bookCodec: DbCodec[Book] =
    DbCodec.derivedWith[Book](
      _.instance(TypeId.of[Book], "isbn", optionalIsbn)
        .instance(TypeId.of[Book], "metadata", DbCodec.jsonbOption[Metadata])
    )

  val books = Repo(tableFor("books", bookCodec), "id", DbCodec[Long], (book: Book) => book.id)

  def main(args: Array[String]): Unit = {
    val dbFile = java.nio.file.Files.createTempFile("bookstore", ".db")
    dbFile.toFile.deleteOnExit()
    val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

    transactor.connect {
      // Non-optional fields don't fail on NULL
      println(sql"SELECT CAST(NULL AS TEXT), CAST(NULL AS INTEGER)".query[(String, Int)])

      println(Try(DbCodec.derived[Book]).fold(_.getMessage, _ => "derived"))

      sql"""CREATE TABLE books (
        id       INTEGER PRIMARY KEY,
        title    TEXT NOT NULL,
        isbn     TEXT,
        metadata TEXT
      )""".update
      sql"INSERT INTO books (id, title, isbn) VALUES (1, 'Zionomicon', NULL)".update
      println(Try(sql"SELECT id, title, isbn FROM books".query[ShelfBook]))

      sql"DELETE FROM books".update
      // Page counts are illustrative
      books.insert(Book(1L, "Zionomicon", None, None))
      books.insert(Book(2L, "Programming in Scala", Some(Isbn.unsafe("978-0-9815316-0-1")), Some(Metadata("Artima", 1, 776))))
      println(sql"SELECT title, isbn, metadata FROM books ORDER BY id".query[(String, Option[String], Option[String])])
      books.all.foreach(println)
    }
  }
}
