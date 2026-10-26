package storeandsearchvectorembeddings

import zio.blocks.sql.*

/**
 * How to Store and Search Vector Embeddings in PostgreSQL: Speed Up Search with
 * an HNSW Index
 *
 * Creates an HNSW index with the cosine operator class, then asks PostgreSQL
 * for the query plan of a cosine search and of a Euclidean search. Only the
 * search whose operator matches the operator class can use the index.
 *
 * Run with: cd sql-examples && sbt "runMain
 * storeandsearchvectorembeddings.HnswIndexExample"
 */
object HnswIndexExample extends App {

  val transactor = shelfTransactor()

  val query = Embedding(IndexedSeq(0.6f, 0.9f, 1.0f))

  transactor.transact {
    createShelf()
    stockShelf()

    sql"CREATE INDEX book_embeddings_hnsw ON book_embeddings USING hnsw (embedding vector_cosine_ops)".update

    // With five rows PostgreSQL prefers a sequential scan, so turn it off for this transaction to see what the index can do.
    sql"SET LOCAL enable_seqscan = off".update

    val cosinePlan =
      sql"EXPLAIN SELECT title FROM book_embeddings ORDER BY embedding <=> $query::vector LIMIT 3".query[String]
    val l2Plan =
      sql"EXPLAIN SELECT title FROM book_embeddings ORDER BY embedding <-> $query::vector LIMIT 3".query[String]

    println(s"Cosine search uses the index:    ${cosinePlan.exists(_.contains("book_embeddings_hnsw"))}")
    println(s"Euclidean search uses the index: ${l2Plan.exists(_.contains("book_embeddings_hnsw"))}")
  }
}
