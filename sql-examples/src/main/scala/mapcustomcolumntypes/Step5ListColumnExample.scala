package mapcustomcolumntypes

import zio.blocks.schema.Schema
import zio.blocks.sql.*
import scala.util.Try

/**
 * How to Map Custom Column Types — Step 5: Store a List as JSON (SQLite)
 *
 * Shows that `Table.derived` throws for a `List[String]` field, then stores
 * the list as a JSON array in a TEXT column through a Repo built from the
 * codec's columns, and filters on one tag with SQLite's `json_each`.
 *
 * Run with: cd sql-examples && sbt "runMain
 * mapcustomcolumntypes.Step5ListColumnExample"
 */
object Step5ListColumnExample {

  case class Book(id: Long, title: String, tags: List[String])

  object Book {
    given Schema[Book] = Schema.derived
  }

  def tableFor[A](name: String, codec: DbCodec[A]): Table[A] =
    Table(name, codec, codec.columns.map(column => ColumnMeta(column, DbValue.DbString(""), nullable = false)))

  val bookCodec: DbCodec[Book] = DbCodec.derived[Book]
  val books                    = Repo(tableFor("books", bookCodec), "id", DbCodec[Long], (book: Book) => book.id)

  def main(args: Array[String]): Unit = {
    println(Try(Table.derived[Book]))

    val dbFile = java.nio.file.Files.createTempFile("bookstore", ".db")
    dbFile.toFile.deleteOnExit()
    val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

    transactor.connect {
      sql"CREATE TABLE books (id INTEGER PRIMARY KEY, title TEXT NOT NULL, tags TEXT NOT NULL)".update
      books.insert(Book(1L, "Zionomicon", List("zio", "effects", "concurrency")))
      books.insert(Book(2L, "Programming in Scala", List("scala", "fundamentals")))
      books.insert(Book(3L, "Functional Programming in Scala", List("scala", "functional-programming")))
      println(sql"SELECT title, tags FROM books ORDER BY id".query[(String, String)])

      val tag = "scala"
      println(
        sql"SELECT id, title, tags FROM books WHERE EXISTS (SELECT 1 FROM json_each(books.tags) WHERE value = $tag) ORDER BY id"
          .query[Book]
      )
    }
  }
}
