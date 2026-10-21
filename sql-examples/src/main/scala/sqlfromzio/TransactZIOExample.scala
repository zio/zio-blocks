package sqlfromzio

import zio.*
import zio.blocks.schema.Schema
import zio.blocks.sql.zio.*
import zio.blocks.sql.*

final case class PaymentDeclined(customer: String) extends Exception(s"Payment declined for $customer")
final case class OutOfStock(bookId: Long)          extends Exception(s"Book $bookId is out of stock")

trait Payments {
  def charge(customer: String, copies: Int): IO[PaymentDeclined, Unit]
}

val paymentsLayer: ZLayer[Any, Nothing, Payments] =
  ZLayer.succeed(new Payments {
    def charge(customer: String, copies: Int): IO[PaymentDeclined, Unit] =
      if (customer == "mallory") ZIO.fail(PaymentDeclined(customer)) else ZIO.unit
  })

/**
 * How to Use the sql Module from a ZIO Application — Mix Database Writes with
 * Other Effects in One Transaction
 *
 * A sale lowers the stock and charges the customer inside one `transactZIO`. A
 * declined payment rolls the stock change back.
 *
 * Run with: cd sql-examples && sbt "runMain sqlfromzio.TransactZIOExample"
 */
object TransactZIOExample extends ZIOAppDefault {

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

  val run =
    (for {
      _ <- ZIO.serviceWithZIO[TransactorZIO](_.connect {
             createBookTable()
             shelf.foreach(books.insert)
           })
      before  <- stockOf(1L)
      alice   <- sell(1L, 2, "alice").either
      mallory <- sell(1L, 1, "mallory").either
      after   <- stockOf(1L)
    } yield {
      println(s"Stock before: $before")
      println(s"Alice: $alice")
      println(s"Mallory: $mallory")
      println(s"Stock after: $after")
    }).provide(transactorLayer, paymentsLayer)
}
