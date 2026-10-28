//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using dep dev.zio::zio-blocks-sql:0.0.56
//> using dep dev.zio::zio-blocks-streams:0.0.56
//> using dep dev.zio::zio-blocks-async:0.0.56
//> using dep dev.zio::zio:2.1.27
//> using options -experimental
//> using jvm 21
//> using file Orders.scala
//> using file PaymentGateway.scala
//> using file Cancellable.scala
//> using file ZioAsync.scala
// Run from this folder:  scala-cli run Step4AsyncZio.scala
// Or from zio-blocks-examples:  sbt "effectIntegration/runMain effectintegration.Step4AsyncZio"

package effectintegration

import zio._
import zio.blocks.async._

object Step4AsyncZio extends ZIOAppDefault {
  val gateway = new PaymentGateway(latencyMillis = 300)
  val order   = Order(1, "Ada", 4200)

  // A ZIO service that ZIO Blocks code wants to call.
  def fraudCheck(order: Order): Task[Boolean] =
    ZIO
      .sleep(1.second)
      .as(order.totalCents < 100000)
      .onInterrupt(Console.printLine(s"fraud check of order ${order.id} interrupted").orDie)

  val run =
    ZIO.runtime[Any].flatMap { implicit runtime =>
      for {
        auth     <- ZioAsync.fromAsync(gateway.authorize(order))
        _        <- Console.printLine(s"authorized: $auth")
        timedOut <- ZioAsync.fromAsync(gateway.authorize(order)).timeout(100.millis)
        _        <- Console.printLine(s"with a 100 ms timeout: $timedOut")
        passed   <- ZIO.attemptBlocking(ZioAsync.toAsync(fraudCheck(order)).block)
        _        <- Console.printLine(s"fraud check passed: $passed")
        running  <- ZIO.succeed(ZioAsync.toAsync(fraudCheck(order)).start)
        _        <- ZIO.sleep(100.millis)
        _        <- ZIO.succeed(running.cancel())
        _        <- ZIO.sleep(100.millis)
        _        <- Console.printLine("after cancelling the Async")
      } yield ()
    }
}
