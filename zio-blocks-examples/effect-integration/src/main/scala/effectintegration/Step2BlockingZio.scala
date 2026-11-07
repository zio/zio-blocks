//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using dep dev.zio::zio-blocks-sql:0.0.56
//> using dep dev.zio::zio-blocks-streams:0.0.56
//> using dep org.xerial:sqlite-jdbc:3.53.4.0
//> using dep dev.zio::zio:2.1.27
//> using options -experimental
//> using jvm 21
//> using file Orders.scala
// Run from this folder:  scala-cli run Step2BlockingZio.scala
// Or from zio-blocks-examples:  sbt "effectIntegration/runMain effectintegration.Step2BlockingZio"

package effectintegration

import zio._
import zio.blocks.sql._

object Step2BlockingZio extends ZIOAppDefault {
  def thread: String = Thread.currentThread.getName

  def saveOrder(transactor: JdbcTransactor, order: Order): Task[Unit] =
    ZIO.attemptBlocking {
      println(s"insert order ${order.id} on $thread")
      transactor.transact(OrdersDb.insert(order))
    }.unit

  // The stream must be consumed inside `connect`; its Either joins the error channel.
  def revenue(transactor: JdbcTransactor): Task[Long] =
    ZIO.attemptBlocking(transactor.connect(OrdersDb.totals().runFold(0L)(_ + _))).absolve

  val run =
    for {
      _          <- ZIO.succeed(println(s"compute work on $thread"))
      transactor <- ZIO.attemptBlocking(OrdersDb.open())
      _          <- saveOrder(transactor, Order(1, "Ada", 4200))
      _          <- saveOrder(transactor, Order(2, "Grace", 1999))
      orders     <- ZIO.attemptBlocking(transactor.connect(OrdersDb.all()))
      _          <- Console.printLine(s"orders: $orders")
      total      <- revenue(transactor)
      _          <- Console.printLine(s"revenue: $total cents")
      duplicate  <- saveOrder(transactor, Order(1, "Ada", 4200)).either
      _          <- Console.printLine(s"duplicate id: ${duplicate.left.map(_.getClass.getName)}")
    } yield ()
}
