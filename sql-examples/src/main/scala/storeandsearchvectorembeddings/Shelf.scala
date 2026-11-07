package storeandsearchvectorembeddings

import zio.blocks.sql.*

/**
 * How to Store and Search Vector Embeddings in PostgreSQL: shared setup
 *
 * The `Embedding` type with its `DbCodec`, the transactor that reads the
 * database settings from the environment, the `book_embeddings` table and the
 * five books that the examples in this package share.
 */
final case class Embedding(values: IndexedSeq[Float])

object Embedding {

  def render(embedding: Embedding): String = embedding.values.mkString("[", ",", "]")

  def parse(text: String): Embedding = {
    val inner = text.substring(1, text.length - 1)
    if (inner.isEmpty) Embedding(IndexedSeq.empty)
    else Embedding(inner.split(',').toIndexedSeq.map(_.toFloat))
  }

  given DbCodec[Embedding] = DbCodec[String].transform(parse)(render)
}

def shelfTransactor(): JdbcTransactor =
  JdbcTransactor.fromUrl(
    sys.env.getOrElse("BOOKSTORE_DB_URL", "jdbc:postgresql://localhost:5432/bookstore"),
    sys.env.getOrElse("BOOKSTORE_DB_USER", "bookstore"),
    sys.env.getOrElse("BOOKSTORE_DB_PASSWORD", "bookstore"),
    SqlDialect.PostgreSQL
  )

// Each number scores a topic: functional programming, effects, concurrency.
val shelfBooks: List[(String, Embedding)] = List(
  "Zionomicon"                      -> Embedding(IndexedSeq(0.8f, 1.0f, 0.9f)),
  "Programming in Scala"            -> Embedding(IndexedSeq(0.5f, 0.1f, 0.1f)),
  "Functional Programming in Scala" -> Embedding(IndexedSeq(1.0f, 0.5f, 0.2f)),
  "Scala Cookbook"                  -> Embedding(IndexedSeq(0.3f, 0.1f, 0.2f)),
  "Scala with Cats"                 -> Embedding(IndexedSeq(0.9f, 0.7f, 0.1f))
)

def createShelf()(using DbCon): Unit = {
  sql"CREATE EXTENSION IF NOT EXISTS vector".update
  sql"DROP TABLE IF EXISTS book_embeddings".update
  sql"""CREATE TABLE book_embeddings (
          id        BIGSERIAL PRIMARY KEY,
          title     TEXT        NOT NULL,
          embedding VECTOR(3)   NOT NULL
        )""".update
}

def stockShelf()(using DbCon): Unit =
  shelfBooks.foreach { (title, embedding) =>
    sql"INSERT INTO book_embeddings (title, embedding) VALUES ($title, $embedding::vector)".update
  }
