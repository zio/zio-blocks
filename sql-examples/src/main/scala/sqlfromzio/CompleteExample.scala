package sqlfromzio

import zio.*
import zio.blocks.schema.Schema
import zio.blocks.sql.zio.*
import zio.blocks.sql.*

/**
 * How to Use the sql Module from a ZIO Application — Complete Example
 *
 * A bookstore application: layers for the transactor, the `BookRepo` service
 * and the payment service, sales in `transactZIO` transactions, and the stock
 * after a declined payment.
 *
 * Run with: cd sql-examples && sbt "runMain sqlfromzio.CompleteExample"
 */
object CompleteExample extends ZIOAppDefault {

  val dbFile = java.nio.file.Files.createTempFile("bookstore", ".db")
  dbFile.toFile.deleteOnExit()

  val transactorLayer: ZLayer[Any, Nothing, TransactorZIO] =
    TransactorZIO.layer(s"jdbc:sqlite:$dbFile", SqlDialect.SQLite)

  def sell(bookId: Long, copies: Int, customer: String): ZIO[TransactorZIO & Payments, Throwable, Unit] =
    ZIO.serviceWithZIO[TransactorZIO] { transactor =>
      transactor.transactZIO {
        for {
          updated <- ZIO.attemptBlocking(
                       sql"UPDATE book SET stock = stock - $copies WHERE id = $bookId AND stock >= $copies".update
                     )
          _ <- ZIO.fail(OutOfStock(bookId)).when(updated == 0)
          _ <- ZIO.serviceWithZIO[Payments](_.charge(customer, copies))
        } yield ()
      }
    }

  val program: ZIO[TransactorZIO & BookRepo & Payments, Throwable, Unit] =
    for {
      _ <- ZIO.serviceWithZIO[TransactorZIO](_.connect {
             createBookTable()
             shelf.foreach(books.insert)
           })
      _       <- ZIO.serviceWithZIO[BookRepo](_.add(Book(4L, "Scala with Cats", 2020, 2)))
      _       <- sell(1L, 2, "alice")
      mallory <- sell(1L, 1, "mallory").either
      _       <- Console.printLine(s"Mallory: $mallory")
      all     <- ZIO.serviceWithZIO[BookRepo](_.all)
      _       <- ZIO.foreachDiscard(all)(book => Console.printLine(s"${book.title}: ${book.stock} in stock"))
    } yield ()

  val run =
    program.provide(transactorLayer, BookRepoLive.layer, paymentsLayer)
}
