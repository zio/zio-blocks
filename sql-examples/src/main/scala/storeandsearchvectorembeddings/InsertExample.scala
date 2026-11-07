package storeandsearchvectorembeddings

import zio.blocks.sql.*

/**
 * How to Store and Search Vector Embeddings in PostgreSQL: Insert Embeddings
 *
 * Creates the table, inserts five books with their embeddings, and counts the
 * rows.
 *
 * Run with: cd sql-examples && sbt "runMain
 * storeandsearchvectorembeddings.InsertExample"
 */
object InsertExample extends App {

  val transactor = shelfTransactor()

  val count = transactor.transact {
    createShelf()
    stockShelf()
    sql"SELECT COUNT(*) FROM book_embeddings".queryOne[Long].getOrElse(0L)
  }

  println(s"Stored $count embeddings")
}
