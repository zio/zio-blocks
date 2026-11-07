//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using dep dev.zio::zio-blocks-sql:0.0.56
//> using dep dev.zio::zio-blocks-streams:0.0.56
//> using dep org.xerial:sqlite-jdbc:3.53.4.0
//> using dep org.typelevel::cats-effect:3.7.1
//> using options -experimental
//> using jvm 21
//> using file Orders.scala
// Run from this folder:  scala-cli run Step2BlockingCats.scala
// Or from zio-blocks-examples:  sbt "effectIntegration/runMain effectintegration.Step2BlockingCats"

package effectintegration

import cats.effect.{IO, IOApp}
import zio.blocks.sql._

object Step2BlockingCats extends IOApp.Simple {
  def thread: String = Thread.currentThread.getName

  def saveOrder(transactor: JdbcTransactor, order: Order): IO[Unit] =
    IO.blocking {
      println(s"insert order ${order.id} on $thread")
      transactor.transact(OrdersDb.insert(order))
    }.void

  // The stream must be consumed inside `connect`; its Either joins the error channel.
  def revenue(transactor: JdbcTransactor): IO[Long] =
    IO.blocking(transactor.connect(OrdersDb.totals().runFold(0L)(_ + _))).rethrow

  val run =
    for {
      _          <- IO(println(s"compute work on $thread"))
      transactor <- IO.blocking(OrdersDb.open())
      _          <- saveOrder(transactor, Order(1, "Ada", 4200))
      _          <- saveOrder(transactor, Order(2, "Grace", 1999))
      orders     <- IO.blocking(transactor.connect(OrdersDb.all()))
      _          <- IO.println(s"orders: $orders")
      total      <- revenue(transactor)
      _          <- IO.println(s"revenue: $total cents")
      duplicate  <- saveOrder(transactor, Order(1, "Ada", 4200)).attempt
      _          <- IO.println(s"duplicate id: ${duplicate.left.map(_.getClass.getName)}")
    } yield ()
}
