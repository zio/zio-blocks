package storeandsearchvectorembeddings

import zio.blocks.sql.*

/**
 * How to Store and Search Vector Embeddings in PostgreSQL: Find the Nearest
 * Books
 *
 * Asks for the three books closest to a query embedding, by Euclidean (L2)
 * distance.
 *
 * Run with: cd sql-examples && sbt "runMain
 * storeandsearchvectorembeddings.NearestExample"
 */
object NearestExample extends App {

  val transactor = shelfTransactor()

  val query = Embedding(IndexedSeq(0.6f, 0.9f, 1.0f))

  val nearest = transactor.transact {
    createShelf()
    stockShelf()
    sql"SELECT title FROM book_embeddings ORDER BY embedding <-> $query::vector LIMIT 3".query[String]
  }

  nearest.foreach(println)
}
