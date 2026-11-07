//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using dep dev.zio::zio-blocks-sql:0.0.56
//> using dep dev.zio::zio-blocks-streams:0.0.56
//> using dep dev.zio::zio-blocks-async:0.0.56
//> using dep org.typelevel::cats-effect:3.7.1
//> using options -experimental
//> using jvm 21
//> using file Orders.scala
//> using file PaymentGateway.scala
//> using file Cancellable.scala
//> using file CatsAsync.scala
// Run from this folder:  scala-cli run Step4AsyncCats.scala
// Or from zio-blocks-examples:  sbt "effectIntegration/runMain effectintegration.Step4AsyncCats"

package effectintegration

import scala.concurrent.duration._
import cats.effect.{IO, IOApp}
import cats.effect.std.Dispatcher
import zio.blocks.async._

object Step4AsyncCats extends IOApp.Simple {
  val gateway = new PaymentGateway(latencyMillis = 300)
  val order   = Order(1, "Ada", 4200)

  // A cats-effect service that ZIO Blocks code wants to call.
  def fraudCheck(order: Order): IO[Boolean] =
    IO.sleep(1.second)
      .as(order.totalCents < 100000)
      .onCancel(IO.println(s"fraud check of order ${order.id} cancelled"))

  val run =
    Dispatcher.parallel[IO].use { implicit dispatcher =>
      for {
        auth     <- CatsAsync.fromAsync(gateway.authorize(order))
        _        <- IO.println(s"authorized: $auth")
        timedOut <- CatsAsync.fromAsync(gateway.authorize(order)).timeoutTo(100.millis, IO.pure("timed out"))
        _        <- IO.println(s"with a 100 ms timeout: $timedOut")
        passed   <- IO.blocking(CatsAsync.toAsync(fraudCheck(order)).block)
        _        <- IO.println(s"fraud check passed: $passed")
        running  <- IO(CatsAsync.toAsync(fraudCheck(order)).start)
        _        <- IO.sleep(100.millis)
        _        <- IO(running.cancel())
        _        <- IO.sleep(100.millis)
        _        <- IO.println("after cancelling the Async")
      } yield ()
    }
}
