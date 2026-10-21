package mapcustomcolumntypes

import java.time.Instant
import zio.blocks.schema.Schema
import zio.blocks.sql.*
import scala.util.Try

/**
 * How to Map Custom Column Types — Step 7: Store an Instant on SQLite
 *
 * Shows what SQLite stores for an `Instant` (epoch milliseconds), that the
 * TEXT column `createTable` declares can't be read back, and that an INTEGER
 * column round-trips the value to the millisecond.
 *
 * Run with: cd sql-examples && sbt "runMain
 * mapcustomcolumntypes.Step7InstantSqliteExample"
 */
object Step7InstantSqliteExample {

  case class Book(id: Long, title: String, publishedAt: Instant)

  object Book {
    given Schema[Book] = Schema.derived
  }

  val books = Repo.derived[Book, Long]("books", "id", _.id)

  // Illustrative date, with microseconds to show the precision SQLite keeps
  val zionomicon = Book(1L, "Zionomicon", Instant.parse("2021-07-15T09:30:00.123456Z"))

  def main(args: Array[String]): Unit = {
    println(books.table.createTable(SqlDialect.SQLite).sql(SqlDialect.SQLite))

    val dbFile = java.nio.file.Files.createTempFile("bookstore", ".db")
    dbFile.toFile.deleteOnExit()
    val transactor = JdbcTransactor.fromUrl(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

    transactor.connect {
      sql"CREATE TABLE books (id INTEGER PRIMARY KEY, title TEXT NOT NULL, published_at TEXT NOT NULL)".update
      books.insert(zionomicon)
      println(sql"SELECT published_at, typeof(published_at) FROM books".query[(String, String)])
      println(Try(books.all))
      sql"DROP TABLE books".update

      sql"CREATE TABLE books (id INTEGER PRIMARY KEY, title TEXT NOT NULL, published_at INTEGER NOT NULL)".update
      books.insert(zionomicon)
      println(sql"SELECT published_at, typeof(published_at) FROM books".query[(Long, String)])
      println(books.all)

      val cutoff = Instant.parse("2020-01-01T00:00:00Z")
      println(sql"SELECT title FROM books WHERE published_at >= $cutoff".query[String])
    }
  }
}
