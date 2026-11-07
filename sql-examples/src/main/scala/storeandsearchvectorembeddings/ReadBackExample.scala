package storeandsearchvectorembeddings

import zio.blocks.sql.*

/**
 * How to Store and Search Vector Embeddings in PostgreSQL: Read Embeddings Back
 *
 * Reads one stored embedding and a whole row as an `Embedding`, and shows that
 * the values survive the round trip through the `vector` column.
 *
 * Run with: cd sql-examples && sbt "runMain
 * storeandsearchvectorembeddings.ReadBackExample"
 */
object ReadBackExample extends App {

  val transactor = shelfTransactor()

  transactor.transact {
    createShelf()
    stockShelf()

    val stored = sql"SELECT embedding FROM book_embeddings WHERE title = ${"Zionomicon"}".queryOne[Embedding]
    println(s"Stored:   ${stored.map(_.values.mkString("[", ", ", "]"))}")
    println(s"Expected: ${Some(shelfBooks.head._2.values.mkString("[", ", ", "]"))}")
    println(s"Same:     ${stored.contains(shelfBooks.head._2)}")

    val rows = sql"SELECT title, embedding FROM book_embeddings ORDER BY id LIMIT 2".query[(String, Embedding)]
    rows.foreach((title, embedding) => println(s"$title -> ${embedding.values.size} dimensions"))
  }
}
