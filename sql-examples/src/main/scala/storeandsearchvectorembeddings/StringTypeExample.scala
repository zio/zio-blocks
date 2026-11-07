package storeandsearchvectorembeddings

import java.sql.SQLException
import zio.blocks.sql.*

/**
 * How to Store and Search Vector Embeddings in PostgreSQL: Skip the Cast with
 * stringtype=unspecified
 *
 * Runs the same cast-free insert and search twice: on a connection with the
 * default settings, where PostgreSQL rejects the `varchar` parameter, and on a
 * connection whose URL sets `stringtype=unspecified`, where it accepts it.
 *
 * Run with: cd sql-examples && sbt "runMain
 * storeandsearchvectorembeddings.StringTypeExample"
 */
object StringTypeExample extends App {

  val baseUrl = sys.env.getOrElse("BOOKSTORE_DB_URL", "jdbc:postgresql://localhost:5432/bookstore")
  val user    = sys.env.getOrElse("BOOKSTORE_DB_USER", "bookstore")
  val secret  = sys.env.getOrElse("BOOKSTORE_DB_PASSWORD", "bookstore")

  val query = Embedding(IndexedSeq(0.6f, 0.9f, 1.0f))

  def withoutCasts(label: String, url: String): Unit = {
    val transactor = JdbcTransactor.fromUrl(url, user, secret, SqlDialect.PostgreSQL)
    try {
      val nearest = transactor.transact {
        createShelf()
        sql"INSERT INTO book_embeddings (title, embedding) VALUES (${"Zionomicon"}, $query)".update
        sql"SELECT title FROM book_embeddings ORDER BY embedding <-> $query LIMIT 1".query[String]
      }
      println(s"$label: stored and found ${nearest.mkString(", ")}")
    } catch {
      case e: SQLException => println(s"$label: ${e.getMessage.linesIterator.next()}")
    }
  }

  withoutCasts("default settings          ", baseUrl)
  withoutCasts(
    "stringtype=unspecified    ",
    baseUrl + (if (baseUrl.contains("?")) "&" else "?") + "stringtype=unspecified"
  )
}
