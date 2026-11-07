package mapcustomcolumntypes

import zio.blocks.schema.Schema
import zio.blocks.sql.*
import scala.util.Try

/**
 * How to Map Custom Column Types — Step 3: Store a Nested Case Class as JSON
 * (SQLite)
 *
 * Shows that `Repo.derived` expects flattened `metadata_*` columns while the
 * row codec writes one JSON `metadata` column, builds the Repo from the codec's
 * own columns instead, round-trips the JSON column and filters on a field
 * inside it with SQLite's `json_extract`.
 *
 * Run with: cd sql-examples && sbt "runMain
 * mapcustomcolumntypes.Step3JsonColumnExample"
 */
object Step3JsonColumnExample {

  case class Metadata(publisher: String, edition: Int, pages: Int)

  object Metadata {
    given Schema[Metadata] = Schema.derived
  }

  case class Book(id: Long, title: String, metadata: Metadata)

  object Book {
    given Schema[Book] = Schema.derived
  }

  // Columns taken from the codec, so the Repo writes what the codec writes.
  // ColumnMeta's type is only read by createTable, which we don't call.
  def tableFor[A](name: String, codec: DbCodec[A]): Table[A] =
    Table(name, codec, codec.columns.map(column => ColumnMeta(column, DbValue.DbString(""), nullable = false)))

  val bookCodec: DbCodec[Book] = DbCodec.derived[Book]
  val books                    = Repo(tableFor("books", bookCodec), "id", DbCodec[Long], (book: Book) => book.id)

  // Page counts are illustrative
  val zionomicon         = Book(1L, "Zionomicon", Metadata("Ziverge", 1, 700))
  val programmingInScala = Book(2L, "Programming in Scala", Metadata("Artima", 1, 776))

  def main(args: Array[String]): Unit = {
    println(Table.derived[Book].columns)
    println(bookCodec.columns)

    val dbFile = java.nio.file.Files.createTempFile("bookstore", ".db")
    dbFile.toFile.deleteOnExit()
    val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

    transactor.connect {
      sql"""CREATE TABLE books (
        id       INTEGER PRIMARY KEY,
        title    TEXT NOT NULL,
        metadata TEXT NOT NULL
      )""".update

      val derivedRepo = Repo.derived[Book, Long]("books", "id", _.id)
      println(Try(derivedRepo.insert(zionomicon)))

      books.insert(zionomicon)
      books.insert(programmingInScala)
      println(sql"SELECT title, metadata FROM books ORDER BY id".query[(String, String)])
      books.all.foreach(println)

      val publisher = "Artima"
      println(sql"SELECT id, title, metadata FROM books WHERE json_extract(metadata, '$$.publisher') = $publisher".query[Book])

      // A row whose JSON is missing a field fails when it is read
      sql"""INSERT INTO books (id, title, metadata) VALUES (3, 'Functional Programming in Scala', '{"publisher":"Manning"}')""".update
      println(Try(books.find(3L)))
    }
  }
}
