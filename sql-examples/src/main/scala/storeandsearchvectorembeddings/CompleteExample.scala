package storeandsearchvectorembeddings

import java.sql.SQLException
import zio.blocks.sql.*

/**
 * How to Store and Search Vector Embeddings in PostgreSQL: Complete Example
 *
 * Creates the table, stores five books, searches by cosine distance, reads one
 * embedding back, shows the error for a vector with the wrong number of
 * dimensions and adds an HNSW index.
 *
 * Run with: cd sql-examples && sbt "runMain
 * storeandsearchvectorembeddings.CompleteExample"
 */
object CompleteExample extends App {

  val transactor = shelfTransactor()

  val query = Embedding(IndexedSeq(0.6f, 0.9f, 1.0f))

  transactor.transact {
    createShelf()
    stockShelf()
  }

  val nearest = transactor.connect {
    sql"SELECT title, embedding <=> $query::vector FROM book_embeddings ORDER BY embedding <=> $query::vector LIMIT 3"
      .query[(String, Double)]
  }
  nearest.foreach((title, distance) => println(f"$title%-32s $distance%.4f"))

  val stored = transactor.connect {
    sql"SELECT embedding FROM book_embeddings WHERE title = ${"Zionomicon"}".queryOne[Embedding]
  }
  println(s"Zionomicon: ${stored.map(_.values.mkString("[", ", ", "]"))}")

  try
    transactor.transact {
      val tooLong = Embedding(IndexedSeq(0.1f, 0.2f, 0.3f, 0.4f))
      sql"INSERT INTO book_embeddings (title, embedding) VALUES (${"Too Long"}, $tooLong::vector)".update
    }
  catch {
    case e: SQLException => println(s"Rejected: ${e.getMessage}")
  }

  transactor.transact {
    sql"CREATE INDEX book_embeddings_hnsw ON book_embeddings USING hnsw (embedding vector_cosine_ops)".update
  }
  println("Index book_embeddings_hnsw created")
}
