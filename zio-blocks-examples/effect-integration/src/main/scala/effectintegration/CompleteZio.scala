//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using dep dev.zio::zio-blocks-sql:0.0.56
//> using dep dev.zio::zio-blocks-streams:0.0.56
//> using dep dev.zio::zio-blocks-scope:0.0.56
//> using dep dev.zio::zio-blocks-async:0.0.56
//> using dep org.xerial:sqlite-jdbc:3.53.4.0
//> using dep dev.zio::zio:2.1.27
//> using dep dev.zio::zio-streams:2.1.27
//> using options -experimental
//> using jvm 21
//> using file Orders.scala
//> using file OrderArchive.scala
//> using file PaymentGateway.scala
//> using file Cancellable.scala
//> using file Opened.scala
//> using file ZioResources.scala
//> using file ZioAsync.scala
//> using file BlockingPull.scala
//> using file ZioStreams.scala
// Run from this folder:  scala-cli run CompleteZio.scala
// Or from zio-blocks-examples:  sbt "effectIntegration/runMain effectintegration.CompleteZio"

package effectintegration

import java.nio.file.Files
import zio._
import zio.blocks.schema._
import zio.blocks.sql._

object CompleteZio extends ZIOAppDefault {
  val gateway = new PaymentGateway(latencyMillis = 50)

  val requests = List(
    """{"id":1,"customer":"Ada","totalCents":4200}""",
    """{"id":2,"customer":"Grace","totalCents":1999}""",
    """{"id":3,"customer":"Linus"}"""
  )

  def placeOrder(transactor: JdbcTransactor, archive: OrderArchive)(body: String): Task[String] =
    for {
      order <- ZIO.fromEither(body.fromJson[Order])
      auth  <- ZioAsync.fromAsync(gateway.authorize(order)).timeoutFail(new Exception("payment timed out"))(1.second)
      _     <- ZIO.attemptBlocking(transactor.transact(OrdersDb.insert(order)))
      _     <- ZIO.attemptBlocking(archive.append(order))
    } yield s"order ${order.id} placed ($auth)"

  val run =
    ZIO.scoped {
      for {
        transactor <- ZIO.attemptBlocking(OrdersDb.open())
        path       <- ZIO.attemptBlocking(Files.createTempFile("archive", ".jsonl"))
        archive    <- ZioResources.fromResource(OrderArchive.resource(path))
        _          <- ZIO.foreachDiscard(requests) { body =>
               placeOrder(transactor, archive)(body).foldZIO(
                 error => Console.printLine(s"rejected: ${error.getMessage}"),
                 placed => Console.printLine(placed)
               )
             }
        revenue <- ZIO.attemptBlocking(transactor.connect(OrdersDb.totals().runFold(0L)(_ + _))).absolve
        _       <- Console.printLine(s"revenue: $revenue cents")
        events  <- ZIO.attemptBlocking(
                    OrderEvents.write(
                      """{"orderId":1,"status":"shipped"}""",
                      """{"orderId":2,"status":"paid"}""",
                      """{"orderId":2,"status":"shipped"}"""
                    )
                  )
        _ <- ZioStreams
               .toZStream(OrderEvents.fromFile(events))
               .filter(_.status == "shipped")
               .foreach(event => Console.printLine(s"shipped: order ${event.orderId}"))
      } yield ()
    }
}
