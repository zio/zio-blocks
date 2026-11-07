package storeandsearchvectorembeddings

import zio.blocks.sql.*

/**
 * How to Store and Search Vector Embeddings in PostgreSQL: Pick a Distance
 * Operator
 *
 * Ranks the same books with each of the three pgvector distance operators and
 * prints the distance next to every title.
 *
 * Run with: cd sql-examples && sbt "runMain
 * storeandsearchvectorembeddings.OperatorsExample"
 */
object OperatorsExample extends App {

  val transactor = shelfTransactor()

  val query = Embedding(IndexedSeq(0.6f, 0.9f, 1.0f))

  transactor.transact {
    createShelf()
    stockShelf()

    println("Euclidean distance (<->):")
    sql"SELECT title, embedding <-> $query::vector FROM book_embeddings ORDER BY embedding <-> $query::vector LIMIT 3"
      .query[(String, Double)]
      .foreach((title, distance) => println(f"  $title%-32s $distance%.4f"))

    println("Cosine distance (<=>):")
    sql"SELECT title, embedding <=> $query::vector FROM book_embeddings ORDER BY embedding <=> $query::vector LIMIT 3"
      .query[(String, Double)]
      .foreach((title, distance) => println(f"  $title%-32s $distance%.4f"))

    println("Negative inner product (<#>):")
    sql"SELECT title, embedding <#> $query::vector FROM book_embeddings ORDER BY embedding <#> $query::vector LIMIT 3"
      .query[(String, Double)]
      .foreach((title, distance) => println(f"  $title%-32s $distance%.4f"))
  }
}
