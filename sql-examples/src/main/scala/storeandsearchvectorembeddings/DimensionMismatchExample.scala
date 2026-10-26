package storeandsearchvectorembeddings

import java.sql.SQLException
import zio.blocks.sql.*

/**
 * How to Store and Search Vector Embeddings in PostgreSQL: Reject the Wrong
 * Number of Dimensions
 *
 * Inserts an embedding with four numbers into a `vector(3)` column, prints the
 * error PostgreSQL raises, and shows that the table still holds only the
 * stocked books.
 *
 * Run with: cd sql-examples && sbt "runMain
 * storeandsearchvectorembeddings.DimensionMismatchExample"
 */
object DimensionMismatchExample extends App {

  val transactor = shelfTransactor()

  transactor.transact {
    createShelf()
    stockShelf()
  }

  val tooLong = Embedding(IndexedSeq(0.1f, 0.2f, 0.3f, 0.4f))

  try
    transactor.transact {
      sql"INSERT INTO book_embeddings (title, embedding) VALUES (${"Too Long"}, $tooLong::vector)".update
    }
  catch {
    case e: SQLException => println(s"Rejected: ${e.getMessage}")
  }

  val count = transactor.connect(sql"SELECT COUNT(*) FROM book_embeddings".queryOne[Long].getOrElse(0L))
  println(s"Rows stored: $count")
}
