package mapcustomcolumntypes

import zio.blocks.schema.Schema
import zio.blocks.sql.*
import scala.util.Try

/**
 * How to Map Custom Column Types — Step 4: Store a Nested Case Class in a
 * PostgreSQL jsonb Column
 *
 * Shows the insert into a jsonb column failing on a default connection, then
 * succeeding once the JDBC URL has `stringtype=unspecified`, and filters on a
 * field inside the JSON with PostgreSQL's `->>` operator.
 *
 * Needs a running PostgreSQL; see the guide for the docker command. Set
 * BOOKSTORE_DB_PASSWORD (and BOOKSTORE_DB_URL / BOOKSTORE_DB_USER if they
 * differ from the defaults).
 *
 * Run with: cd sql-examples && sbt "runMain
 * mapcustomcolumntypes.Step4JsonbPostgresExample"
 */
object Step4JsonbPostgresExample {

  case class Metadata(publisher: String, edition: Int, pages: Int)

  object Metadata {
    given Schema[Metadata] = Schema.derived
  }

  case class Book(id: Long, title: String, metadata: Metadata)

  object Book {
    given Schema[Book] = Schema.derived
  }

  def tableFor[A](name: String, codec: DbCodec[A]): Table[A] =
    Table(name, codec, codec.columns.map(column => ColumnMeta(column, DbValue.DbString(""), nullable = false)))

  val bookCodec: DbCodec[Book] = DbCodec.derived[Book]
  val books                    = Repo(tableFor("books", bookCodec), "id", DbCodec[Long], (book: Book) => book.id)

  // Page counts are illustrative
  val zionomicon         = Book(1L, "Zionomicon", Metadata("Ziverge", 1, 700))
  val programmingInScala = Book(2L, "Programming in Scala", Metadata("Artima", 1, 776))

  val baseUrl  = sys.env.getOrElse("BOOKSTORE_DB_URL", "jdbc:postgresql://localhost:5432/bookstore")
  val user     = sys.env.getOrElse("BOOKSTORE_DB_USER", "bookstore")
  val password = sys.env("BOOKSTORE_DB_PASSWORD")

  // Let PostgreSQL infer the type of string parameters, so JSON text can go into jsonb
  val jsonbUrl = baseUrl + (if (baseUrl.contains("?")) "&" else "?") + "stringtype=unspecified"

  def main(args: Array[String]): Unit = {
    val plain = JdbcTransactor.fromUrl(baseUrl, user, password, SqlDialect.PostgreSQL)
    plain.connect {
      sql"DROP TABLE IF EXISTS books".update
      sql"""CREATE TABLE books (
        id       BIGINT PRIMARY KEY,
        title    TEXT NOT NULL,
        metadata JSONB NOT NULL
      )""".update
      println(Try(books.insert(zionomicon)).fold(_.getMessage.linesIterator.next(), n => s"Inserted $n"))
    }

    val transactor = JdbcTransactor.fromUrl(jsonbUrl, user, password, SqlDialect.PostgreSQL)
    transactor.connect {
      books.insert(zionomicon)
      books.insert(programmingInScala)
      println(sql"SELECT title, metadata::text FROM books ORDER BY id".query[(String, String)])
      books.all.foreach(println)

      val publisher = "Artima"
      println(sql"SELECT id, title, metadata FROM books WHERE metadata->>'publisher' = $publisher".query[Book])
      sql"DROP TABLE books".update
    }
  }
}
