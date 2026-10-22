package mapcustomcolumntypes

import zio.blocks.schema.Schema
import zio.blocks.typeid.TypeId
import zio.blocks.sql.*
import scala.util.Try

/**
 * How to Map Custom Column Types — Step 6: Store a List as a PostgreSQL Array
 *
 * Shows the derived JSON codec failing against a `text[]` column, swaps in
 * `PgCodec.listStringCodec` for the `tags` field, filters with `ANY` and `&&`,
 * and reads back an empty array and a NULL array.
 *
 * Needs a running PostgreSQL; see the guide for the docker command. Set
 * BOOKSTORE_DB_PASSWORD (and BOOKSTORE_DB_URL / BOOKSTORE_DB_USER if they
 * differ from the defaults).
 *
 * Run with: cd sql-examples && sbt "runMain
 * mapcustomcolumntypes.Step6ArrayPostgresExample"
 */
object Step6ArrayPostgresExample {

  case class Book(id: Long, title: String, tags: List[String])

  object Book {
    given Schema[Book] = Schema.derived
  }

  def tableFor[A](name: String, codec: DbCodec[A]): Table[A] =
    Table(name, codec, codec.columns.map(column => ColumnMeta(column, DbValue.DbString(""), nullable = false)))

  // The derived codec writes tags as JSON text
  val jsonBookCodec: DbCodec[Book] = DbCodec.derived[Book]

  // The same codec with a PostgreSQL array for the tags field
  given bookCodec: DbCodec[Book] =
    DbCodec.derivedWith[Book](_.instance(TypeId.of[Book], "tags", PgCodec.listStringCodec))

  val books = Repo(tableFor("books", bookCodec), "id", DbCodec[Long], (book: Book) => book.id)

  val baseUrl  = sys.env.getOrElse("BOOKSTORE_DB_URL", "jdbc:postgresql://localhost:5432/bookstore")
  val user     = sys.env.getOrElse("BOOKSTORE_DB_USER", "bookstore")
  val password = sys.env("BOOKSTORE_DB_PASSWORD")

  def main(args: Array[String]): Unit = {
    val transactor = JdbcTransactor.fromUrl(baseUrl, user, password, SqlDialect.PostgreSQL)

    transactor.connect {
      sql"DROP TABLE IF EXISTS books".update
      sql"CREATE TABLE books (id BIGINT PRIMARY KEY, title TEXT NOT NULL, tags TEXT[])".update

      val jsonBooks = Repo(tableFor("books", jsonBookCodec), "id", DbCodec[Long], (book: Book) => book.id)
      println(Try(jsonBooks.insert(Book(1L, "Zionomicon", List("zio")))).fold(_.getMessage.linesIterator.next(), n => s"Inserted $n"))

      books.insert(Book(1L, "Zionomicon", List("zio", "effects", "concurrency")))
      books.insert(Book(2L, "Programming in Scala", List("scala", "fundamentals")))
      books.insert(Book(3L, "Functional Programming in Scala", List("scala", "functional-programming")))
      println(sql"SELECT title, tags::text FROM books ORDER BY id".query[(String, String)])

      val tag = "scala"
      println(sql"SELECT id, title, tags FROM books WHERE $tag = ANY(tags) ORDER BY id".query[Book])

      import PgCodec.given
      val wanted = List("effects", "functional-programming")
      println(
        Try(sql"SELECT title FROM books WHERE tags && $wanted ORDER BY id".query[String])
          .fold(_.getMessage.linesIterator.next(), _.toString)
      )
      println(sql"SELECT title FROM books WHERE tags && $wanted::text[] ORDER BY id".query[String])

      books.insert(Book(4L, "Scala with Cats", Nil))
      sql"INSERT INTO books (id, title, tags) VALUES (5, 'Hands-on Scala Programming', NULL)".update
      println(books.find(4L))
      println(books.find(5L))
      sql"DROP TABLE books".update
    }
  }
}
